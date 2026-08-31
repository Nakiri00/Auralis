package com.nakiri00.auralis;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LibrosaAnalyzer implements ChordAnalyzerStrategy {

    private static final String TAG = "LibrosaAnalyzer";
    private static final String Public_Ip = BuildConfig.Public_IP + "/analyze_chords";
    private static final long TOTAL_TIMEOUT_SECONDS = 300L;

    private static final OkHttpClient CLIENT =
            new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(285, TimeUnit.SECONDS)
                    .callTimeout(
                            TOTAL_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
                    .retryOnConnectionFailure(true)
                    .build();

    private final AtomicBoolean canceled =
            new AtomicBoolean(false);

    private volatile BackendApiClient.RequestHandle activeRequest;

    @Override
    public void cancel() {
        canceled.set(true);

        BackendApiClient.RequestHandle request = activeRequest;
        activeRequest = null;

        if (request != null) {
            request.cancel();
        }
    }

    @Override
    public void analyzeChords(String audioPath, int sampleRate, AudioAnalysisRepository.AnalysisCallback callback) {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            callback.onError(new Exception("Audio file not found."));
            return;
        }

        RequestBody fileBody =
                BackendApiClient.createAudioRequestBody(audioFile);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), fileBody)
                .build();

        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(Public_Ip)
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
                                        return;
                                    }

                                    Log.e(
                                            TAG,
                                            "Gagal menghubungi server Librosa",
                                            e
                                    );

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
                                                                "Chord analysis timed out on the server"
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
                                                            "Server returned an empty response."
                                                    )
                                            );
                                            return;
                                        }

                                        try {
                                            JSONObject jsonResponse =
                                                    new JSONObject(
                                                            r.body().string()
                                                    );

                                            if (canceled.get()) {
                                                return;
                                            }

                                            String status =
                                                    jsonResponse.optString(
                                                            "status"
                                                    );

                                            if (!"success".equals(status)) {
                                                callback.onError(
                                                        new Exception(
                                                                jsonResponse.optString(
                                                                        "message",
                                                                        "Chord analysis failed."
                                                                )
                                                        )
                                                );
                                                return;
                                            }

                                            int keyIndex = jsonResponse.optInt("key_index", KeyDetector.UNKNOWN_KEY_INDEX);

                                            if (!KeyDetector.isValidKeyIndex(keyIndex)) {
                                                keyIndex = KeyDetector.UNKNOWN_KEY_INDEX;
                                            }

                                            JSONArray dataArray =
                                                    jsonResponse.getJSONArray(
                                                            "data"
                                                    );

                                            List<ChordTimestamp> chords =
                                                    new ArrayList<>();

                                            for (
                                                    int i = 0;
                                                    i < dataArray.length();
                                                    i++
                                            ) {
                                                if (canceled.get()) {
                                                    return;
                                                }

                                                JSONObject item =
                                                        dataArray.getJSONObject(i);

                                                chords.add(
                                                        new ChordTimestamp(
                                                                item.getDouble("time"),
                                                                item.getString("chord")
                                                        )
                                                );
                                            }

                                            if (
                                                    !canceled.get()
                                                            && !call.isCanceled()
                                            ) {
                                                callback.onComplete(
                                                        chords,
                                                        keyIndex
                                                );
                                            }
                                        } catch (Exception e) {
                                            if (
                                                    canceled.get()
                                                            || call.isCanceled()
                                            ) {
                                                return;
                                            }

                                            Log.e(
                                                    TAG,
                                                    "Gagal parsing JSON dari server",
                                                    e
                                            );

                                            callback.onError(
                                                    new Exception(
                                                            "Data format from server "
                                                                    + "not recognized.",
                                                            e
                                                    )
                                            );
                                        }
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
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "URL API tidak valid: " + Public_Ip, e);

            callback.onError(
                    new Exception(
                            "Invalid server URL configuration.",
                            e
                    )
            );
        }
    }

    private static Exception mapRequestFailure(
            Throwable error
    ) {
        if (isTimeout(error)) {
            return new Exception(
                    "Chord analysis timed out. Please try again.",
                    error
            );
        }

        return new Exception(
                "Connection to Librosa server failed.",
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
}
