package com.nakiri00.auralis;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SourceSeparationHelper {

    private static final String TAG = "SourceSeparation";

    private static final String PUBLIC_URL =
            BuildConfig.Public_IP + "/separate";

    private static final long TOTAL_TIMEOUT_SECONDS = 660L;

    private static final OkHttpClient CLIENT =
            new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(620, TimeUnit.SECONDS)
                    .callTimeout(
                            TOTAL_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
                    .retryOnConnectionFailure(true)
                    .build();

    private final AtomicBoolean canceled =
            new AtomicBoolean(false);

    private volatile BackendApiClient.RequestHandle activeRequest;
    private volatile File partialOutput;

    public interface SeparationCallback {
        void onSuccess(String separatedAudioPath);
        void onError(Exception e);
    }

    public void separateAudio(
            Context context,
            String originalAudioPath,
            SeparationCallback callback
    ) {
        if (canceled.get()) {
            return;
        }

        File originalFile = new File(originalAudioPath);

        if (!originalFile.isFile()) {
            if (!canceled.get()) {
                callback.onError(
                        new Exception("Original file not found.")
                );
            }
            return;
        }

        RequestBody fileBody =
                BackendApiClient.createAudioRequestBody(
                        originalFile
                );

        RequestBody requestBody =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                                "file",
                                originalFile.getName(),
                                fileBody
                        )
                        .build();

        Request.Builder requestBuilder =
                new Request.Builder()
                        .url(PUBLIC_URL)
                        .post(requestBody);

        BackendApiClient.RequestHandle handle =
                BackendApiClient.enqueueAuthenticated(
                        CLIENT,
                        requestBuilder,
                        new Callback() {
                            @Override
                            public void onFailure(
                                    Call call,
                                    IOException e
                            ) {
                                if (
                                        canceled.get()
                                                || call.isCanceled()
                                ) {
                                    deletePartialOutput();
                                    return;
                                }

                                Log.e(TAG, "API call gagal", e);
                                callback.onError(
                                        mapRequestFailure(e)
                                );
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response
                            ) throws IOException {
                                try (Response r = response) {
                                    if (
                                            canceled.get()
                                                    || call.isCanceled()
                                    ) {
                                        return;
                                    }

                                    if (!r.isSuccessful()) {
                                        if (r.code() == 504) {
                                            callback.onError(
                                                    new SocketTimeoutException(
                                                            "Audio separation timed out on the server"
                                                    )
                                            );
                                            return;
                                        }

                                        callback.onError(
                                                new Exception(
                                                        "Server error: "
                                                                + r.code()
                                                )
                                        );
                                        return;
                                    }

                                    if (r.body() == null) {
                                        callback.onError(
                                                new Exception(
                                                        "Server returned an empty audio response."
                                                )
                                        );
                                        return;
                                    }

                                    File separatedFile =
                                            new File(
                                                    context.getCacheDir(),
                                                    "separated_"
                                                            + System.currentTimeMillis()
                                                            + ".mp3"
                                            );

                                    partialOutput = separatedFile;

                                    try (
                                            InputStream input =
                                                    r.body().byteStream();
                                            FileOutputStream output =
                                                    new FileOutputStream(
                                                            separatedFile
                                                    )
                                    ) {
                                        byte[] buffer = new byte[8192];
                                        int bytesRead;

                                        while (
                                                (
                                                        bytesRead =
                                                                input.read(
                                                                        buffer
                                                                )
                                                ) != -1
                                        ) {
                                            if (canceled.get()) {
                                                throw new InterruptedIOException(
                                                        "Request canceled"
                                                );
                                            }

                                            output.write(
                                                    buffer,
                                                    0,
                                                    bytesRead
                                            );
                                        }
                                    } catch (Exception e) {
                                        deletePartialOutput();

                                        if (
                                                canceled.get()
                                                        || call.isCanceled()
                                        ) {
                                            return;
                                        }

                                        callback.onError(e);
                                        return;
                                    }

                                    if (
                                            canceled.get()
                                                    || call.isCanceled()
                                    ) {
                                        deletePartialOutput();
                                        return;
                                    }

                                    partialOutput = null;

                                    callback.onSuccess(
                                            separatedFile.getAbsolutePath()
                                    );
                                }
                            }
                        },
                        error -> {
                            if (canceled.get()) {
                                return;
                            }

                            if (isTimeout(error)) {
                                callback.onError(
                                        mapRequestFailure(error)
                                );
                                return;
                            }

                            Log.e(
                                    TAG,
                                    "Firebase authentication failed",
                                    error
                            );

                            callback.onError(
                                    new Exception(
                                            "Authentication failed. "
                                                    + "Please reopen the app.",
                                            error
                                    )
                            );
                        },
                        TOTAL_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );

        activeRequest = handle;

        if (canceled.get()) {
            handle.cancel();
        }
    }

    public void cancel() {
        canceled.set(true);

        BackendApiClient.RequestHandle request =
                activeRequest;

        activeRequest = null;

        if (request != null) {
            request.cancel();
        }

        deletePartialOutput();
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    private static Exception mapRequestFailure(
            Throwable error
    ) {
        if (isTimeout(error)) {
            return new Exception(
                    "Audio separation timed out. Please try again.",
                    error
            );
        }

        return new Exception(
                "Unable to connect to the audio separation server.",
                error
        );
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void deletePartialOutput() {
        File file = partialOutput;
        partialOutput = null;

        if (
                file != null
                        && file.exists()
                        && !file.delete()
        ) {
            Log.w(
                    TAG,
                    "Failed to delete partial output: "
                            + file.getAbsolutePath()
            );
        }
    }
}
