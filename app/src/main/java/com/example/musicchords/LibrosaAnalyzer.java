package com.example.musicchords;

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
    private static final String NGROK_URL = BuildConfig.NGROK_URL + "/analyze_chords";

    @Override
    public void analyzeChords(String audioPath, int sampleRate, AudioAnalysisRepository.AnalysisCallback callback) {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            callback.onError(new Exception("File audio tidak ditemukan."));
            return;
        }

        // Timeout diperpanjang karena pemrosesan AI di server butuh waktu
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/*"), audioFile);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), fileBody)
                .build();

        try {
            Request request = new Request.Builder()
                    .url(NGROK_URL)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gagal menghubungi server Librosa", e);
                    callback.onError(new Exception("Koneksi ke Server Premium (Librosa) gagal."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError(new Exception("Server error: " + response.code()));
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String status = jsonResponse.optString("status");

                        if ("success".equals(status)) {
                            // Ekstrak Key Index dan Data Chord dari respons JSON Python
                            int keyIndex = jsonResponse.optInt("key_index", -1);
                            JSONArray dataArray = jsonResponse.getJSONArray("data");
                            List<ChordTimestamp> chords = new ArrayList<>();

                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                double time = obj.getDouble("time");
                                String chordName = obj.getString("chord");
                                chords.add(new ChordTimestamp(time, chordName));
                            }

                            callback.onComplete(chords, keyIndex);
                        } else {
                            callback.onError(new Exception(jsonResponse.optString("message")));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Gagal parsing JSON dari server", e);
                        callback.onError(new Exception("Format data dari server tidak dikenali."));
                    }
                }
            });
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "URL API tidak valid: " + NGROK_URL, e);
            callback.onError(new Exception("Konfigurasi URL Server salah"));
        }
    }
}