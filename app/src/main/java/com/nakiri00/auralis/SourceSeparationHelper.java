package com.nakiri00.auralis;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SourceSeparationHelper {

    private static final String TAG = "SourceSeparation";
    private static final String Public_Ip = BuildConfig.Public_IP +"/separate";

    public interface SeparationCallback {
        void onSuccess(String separatedAudioPath);
        void onError(Exception e);
    }

    public void separateAudio(Context context, String originalAudioPath, SeparationCallback callback) {
        File originalFile = new File(originalAudioPath);
        if (!originalFile.exists()) {
            callback.onError(new Exception("Original file not found."));
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        RequestBody fileBody =
                BackendApiClient.createAudioRequestBody(originalFile);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", originalFile.getName(), fileBody)
                .build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(Public_Ip)
                .post(requestBody);

        BackendApiClient.enqueueAuthenticated(
                client,
                requestBuilder,
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "API call gagal", e);
                        callback.onError(e);
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) throws IOException {
                        try (Response r = response) {
                            if (!r.isSuccessful()) {
                                callback.onError(
                                        new Exception(
                                                "Server error: " + r.code()
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

                            File separatedFile = new File(
                                    context.getCacheDir(),
                                    "separated_"
                                            + System.currentTimeMillis()
                                            + ".mp3"
                            );

                            try (
                                    InputStream is = r.body().byteStream();
                                    FileOutputStream fos =
                                            new FileOutputStream(separatedFile)
                            ) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;

                                while ((bytesRead = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                }

                                callback.onSuccess(
                                        separatedFile.getAbsolutePath()
                                );
                            } catch (Exception e) {
                                callback.onError(e);
                            }
                        }
                    }
                },
                error -> {
                    Log.e(
                            TAG,
                            "Firebase authentication failed",
                            error
                    );

                    callback.onError(
                            new Exception(
                                    "Authentication failed. Please reopen the app.",
                                    error
                            )
                    );
                }
        );
    }
}