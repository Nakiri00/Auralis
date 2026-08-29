package com.nakiri00.auralis;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class YoutubeRepository {

    private static final String TAG = "YoutubeRepository";
    private static final String CONVERT_URL =
            BuildConfig.Public_IP + "/youtube/convert";

    // Singleton client — thread pool dan connection pool dipakai ulang, tidak bocor
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public interface ConversionCallback {
        void onSuccess(String responseJson);
        void onError(String errorMessage);
    }

    public void convertYoutubeUrl(String url, ConversionCallback callback) {
        String videoId = extractVideoId(url);
        if (videoId == null || videoId.isEmpty()) {
            callback.onError("Invalid URL.");
            return;
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(CONVERT_URL + "?video_id=" + videoId)
                .get();

        BackendApiClient.enqueueAuthenticated(CLIENT, requestBuilder, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "API Call Failed", e);
                callback.onError("Failed to connect to server: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // try-with-resources memastikan response.body() SELALU ditutup
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        callback.onSuccess(r.body().string());
                    } else {
                        Log.e(TAG, "API Error: " + r.code());
                        callback.onError("Server Error: " + r.code());
                    }
                }
            }
        }, error -> {
            Log.e(TAG, "Firebase authentication failed", error);
            callback.onError("Authentication failed. Please reopen the app.");
        });
    }

    public String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String pattern = "((http|https)://)?(?:[0-9A-Z-]+\\.)?(?:youtu\\.be/|youtube(?:-nocookie)?\\.com\\S*[^\\w\\s-])([\\w-]{11})(?=[^\\w-]|$)(?![?=&+%\\w.-]*(?:['\"][^<>]*>|</a>))[?=&+%\\w.-]*";
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(url);
        return m.find() ? m.group(3) : "";
    }
}
