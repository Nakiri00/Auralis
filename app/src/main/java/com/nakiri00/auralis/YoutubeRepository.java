package com.nakiri00.auralis;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class YoutubeRepository {

    private static final String TAG =
            "YoutubeRepository";

    private static final String CONVERT_URL =
            BuildConfig.Public_IP
                    + "/youtube/convert";

    private static final Pattern YOUTUBE_URL_PATTERN =
            Pattern.compile(
                    "((http|https)://)?"
                            + "(?:[0-9A-Z-]+\\.)?"
                            + "(?:youtu\\.be/|"
                            + "youtube(?:-nocookie)?\\.com"
                            + "\\S*[^\\w\\s-])"
                            + "([\\w-]{11})"
                            + "(?=[^\\w-]|$)"
                            + "(?![?=&+%\\w.-]*"
                            + "(?:['\"][^<>]*>|</a>))"
                            + "[?=&+%\\w.-]*",
                    Pattern.CASE_INSENSITIVE
            );

    private static final OkHttpClient CLIENT =
            new OkHttpClient.Builder()
                    .connectTimeout(
                            10,
                            TimeUnit.SECONDS
                    )
                    .readTimeout(
                            30,
                            TimeUnit.SECONDS
                    )
                    .callTimeout(
                            45,
                            TimeUnit.SECONDS
                    )
                    .build();

    private volatile BackendApiClient.RequestHandle
            activeRequest;

    public interface ConversionCallback {

        void onSuccess(String responseJson);

        void onError(String errorMessage);
    }

    public void convertYoutubeUrl(
            String url,
            ConversionCallback callback
    ) {
        if (callback == null) {
            return;
        }

        String videoId =
                extractVideoId(url);

        if (
                videoId == null
                        || videoId.isEmpty()
        ) {
            callback.onError(
                    "Invalid YouTube URL."
            );

            return;
        }

        /*
         * Pastikan request konversi sebelumnya tidak
         * mengirim callback setelah request baru dimulai.
         */
        cancelActiveRequest();

        Request.Builder requestBuilder =
                new Request.Builder()
                        .url(
                                CONVERT_URL
                                        + "?video_id="
                                        + videoId
                        )
                        .get();

        activeRequest =
                BackendApiClient.enqueueAuthenticated(
                        CLIENT,
                        requestBuilder,
                        new Callback() {
                            @Override
                            public void onFailure(
                                    @NonNull Call call,
                                    @NonNull IOException error
                            ) {
                                if (call.isCanceled()) {
                                    return;
                                }

                                Log.e(
                                        TAG,
                                        "YouTube conversion request failed",
                                        error
                                );

                                callback.onError(
                                        "Failed to connect to server: "
                                                + getSafeErrorMessage(
                                                error
                                        )
                                );
                            }

                            @Override
                            public void onResponse(
                                    @NonNull Call call,
                                    @NonNull Response response
                            ) throws IOException {
                                try (Response currentResponse =
                                             response) {

                                    if (call.isCanceled()) {
                                        return;
                                    }

                                    if (
                                            !currentResponse
                                                    .isSuccessful()
                                    ) {
                                        Log.e(
                                                TAG,
                                                "YouTube conversion API error: "
                                                        + currentResponse
                                                        .code()
                                        );

                                        callback.onError(
                                                "Server error: "
                                                        + currentResponse
                                                        .code()
                                        );

                                        return;
                                    }

                                    if (
                                            currentResponse.body()
                                                    == null
                                    ) {
                                        callback.onError(
                                                "Server returned "
                                                        + "an empty response."
                                        );

                                        return;
                                    }

                                    String responseJson =
                                            currentResponse
                                                    .body()
                                                    .string();

                                    if (!call.isCanceled()) {
                                        callback.onSuccess(
                                                responseJson
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
                                    "Authentication failed. "
                                            + "Please reopen the app."
                            );
                        }
                );
    }

    public String extractVideoId(
            String url
    ) {
        if (
                url == null
                        || url.trim().isEmpty()
        ) {
            return "";
        }

        Matcher matcher =
                YOUTUBE_URL_PATTERN.matcher(
                        url.trim()
                );

        return matcher.find()
                ? matcher.group(3)
                : "";
    }

    public void cancelActiveRequest() {
        BackendApiClient.RequestHandle request =
                activeRequest;

        activeRequest = null;

        if (request != null) {
            request.cancel();
        }
    }

    private String getSafeErrorMessage(
            Exception error
    ) {
        if (
                error == null
                        || error.getMessage() == null
                        || error.getMessage()
                        .trim()
                        .isEmpty()
        ) {
            return "Unknown network error";
        }

        return error.getMessage();
    }
}