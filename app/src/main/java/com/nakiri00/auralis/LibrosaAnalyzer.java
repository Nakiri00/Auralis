package com.nakiri00.auralis;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void analyzeChords(String audioPath, int sampleRate, AudioAnalysisRepository.AnalysisCallback callback) {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            callback.onError(new Exception("Audio file not found."));
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();

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

            BackendApiClient.enqueueAuthenticated(
                    client,
                    requestBuilder,
                    new Callback() {
                        @Override
                        public void onFailure(
                                Call call,
                                IOException e
                        ) {
                            Log.e(
                                    TAG,
                                    "Gagal menghubungi server Librosa",
                                    e
                            );

                            callback.onError(
                                    new Exception(
                                            "Connection to Librosa Server failed.",
                                            e
                                    )
                            );
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
                                                    "Server returned an empty response."
                                            )
                                    );
                                    return;
                                }

                                try {
                                    String responseBody =
                                            r.body().string();

                                    JSONObject jsonResponse =
                                            new JSONObject(responseBody);

                                    String status =
                                            jsonResponse.optString("status");

                                    if ("success".equals(status)) {
                                        int keyIndex =
                                                jsonResponse.optInt(
                                                        "key_index",
                                                        -1
                                                );

                                        JSONArray dataArray =
                                                jsonResponse.getJSONArray("data");

                                        List<ChordTimestamp> chords =
                                                new ArrayList<>();

                                        for (
                                                int i = 0;
                                                i < dataArray.length();
                                                i++
                                        ) {
                                            JSONObject obj =
                                                    dataArray.getJSONObject(i);

                                            double time =
                                                    obj.getDouble("time");

                                            String chordName =
                                                    obj.getString("chord");

                                            chords.add(
                                                    new ChordTimestamp(
                                                            time,
                                                            chordName
                                                    )
                                            );
                                        }

                                        callback.onComplete(
                                                chords,
                                                keyIndex
                                        );
                                    } else {
                                        callback.onError(
                                                new Exception(
                                                        jsonResponse.optString(
                                                                "message",
                                                                "Chord analysis failed."
                                                        )
                                                )
                                        );
                                    }
                                } catch (Exception e) {
                                    Log.e(
                                            TAG,
                                            "Gagal parsing JSON dari server",
                                            e
                                    );

                                    callback.onError(
                                            new Exception(
                                                    "Data format from server not recognized.",
                                                    e
                                            )
                                    );
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
}