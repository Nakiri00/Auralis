package com.nakiri00.auralis;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.File;
import java.util.Locale;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/** Shared helpers for authenticated requests to the Auralis backend. */
public final class BackendApiClient {

    public interface AuthenticationErrorCallback {
        void onError(Exception error);
    }

    private BackendApiClient() {}

    public static void enqueueAuthenticated(
            OkHttpClient client,
            Request.Builder requestBuilder,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback
    ) {
        Request unsignedRequest = requestBuilder.build();
        if (!BuildConfig.DEBUG && !unsignedRequest.url().isHttps()) {
            authenticationErrorCallback.onError(
                    new IllegalStateException("Backend URL must use HTTPS in release builds")
            );
            return;
        }

        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            firebaseAuth.signInAnonymously().addOnCompleteListener(task -> {
                FirebaseUser signedInUser = firebaseAuth.getCurrentUser();
                if (!task.isSuccessful() || signedInUser == null) {
                    Exception error = task.getException() != null
                            ? task.getException()
                            : new IllegalStateException("Anonymous authentication failed");
                    authenticationErrorCallback.onError(error);
                    return;
                }
                enqueueWithUser(
                        client,
                        unsignedRequest,
                        callback,
                        authenticationErrorCallback,
                        signedInUser
                );
            });
            return;
        }

        enqueueWithUser(
                client,
                unsignedRequest,
                callback,
                authenticationErrorCallback,
                user
        );
    }

    private static void enqueueWithUser(
            OkHttpClient client,
            Request unsignedRequest,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback,
            FirebaseUser user
    ) {
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful()
                    || task.getResult() == null
                    || task.getResult().getToken() == null) {
                Exception error = task.getException() != null
                        ? task.getException()
                        : new IllegalStateException("Firebase ID token is unavailable");
                authenticationErrorCallback.onError(error);
                return;
            }

            Request request = unsignedRequest.newBuilder()
                    .header(
                            "Authorization",
                            "Bearer " + task.getResult().getToken()
                    )
                    .build();
            client.newCall(request).enqueue(callback);
        });
    }

    public static RequestBody createAudioRequestBody(File audioFile) {
        return RequestBody.create(resolveAudioMediaType(audioFile), audioFile);
    }

    private static MediaType resolveAudioMediaType(File audioFile) {
        String fileName = audioFile.getName().toLowerCase(Locale.US);
        if (fileName.endsWith(".wav")) return MediaType.parse("audio/wav");
        if (fileName.endsWith(".flac")) return MediaType.parse("audio/flac");
        if (fileName.endsWith(".m4a") || fileName.endsWith(".mp4")) {
            return MediaType.parse("audio/mp4");
        }
        if (fileName.endsWith(".aac")) return MediaType.parse("audio/aac");
        if (fileName.endsWith(".ogg")) return MediaType.parse("audio/ogg");
        return MediaType.parse("audio/mpeg");
    }
}
