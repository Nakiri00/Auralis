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

        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/*"), originalFile);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", originalFile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(Public_Ip)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API call gagal", e);
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new Exception("Server error: " + response.code()));
                    return;
                }

                File separatedFile = new File(context.getCacheDir(), "separated_" + System.currentTimeMillis() + ".mp3");
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(separatedFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }

                    callback.onSuccess(separatedFile.getAbsolutePath());
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}