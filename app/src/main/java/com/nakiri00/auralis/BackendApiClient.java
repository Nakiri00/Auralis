package com.nakiri00.auralis;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Shared helpers for authenticated requests to the Auralis backend.
 */
public final class BackendApiClient {

    private static final ScheduledExecutorService DEADLINE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactory() {
                        @Override
                        public Thread newThread(Runnable runnable) {
                            Thread thread = new Thread(
                                    runnable,
                                    "auralis-request-deadline"
                            );
                            thread.setDaemon(true);
                            return thread;
                        }
                    }
            );

    public interface AuthenticationErrorCallback {
        void onError(Exception error);
    }

    public static final class RequestHandle {

        private final AtomicBoolean canceled =
                new AtomicBoolean(false);

        private final AtomicBoolean finished =
                new AtomicBoolean(false);

        private final AtomicReference<Call> callReference =
                new AtomicReference<>();

        private final AtomicReference<ScheduledFuture<?>> deadlineReference =
                new AtomicReference<>();

        private RequestHandle() {
        }

        public void cancel() {
            canceled.set(true);
            finished.compareAndSet(false, true);
            cancelDeadline();
            cancelAttachedCall();
        }

        private void cancelAttachedCall() {
            Call call = callReference.getAndSet(null);

            if (call != null) {
                call.cancel();
            }
        }

        public boolean isCanceled() {
            return canceled.get();
        }

        private boolean attach(Call call) {
            if (canceled.get() || finished.get()) {
                call.cancel();
                return false;
            }

            callReference.set(call);

            // Menutup race jika cancel() dipanggil tepat setelah
            // pemeriksaan pertama tetapi sebelum call disimpan.
            if (canceled.get() || finished.get()) {
                if (callReference.compareAndSet(call, null)) {
                    call.cancel();
                }
                return false;
            }

            return true;
        }

        private void armDeadline(
                long timeout,
                TimeUnit unit,
                AuthenticationErrorCallback timeoutCallback
        ) {
            if (timeout <= 0) {
                throw new IllegalArgumentException(
                        "Request timeout must be positive"
                );
            }

            ScheduledFuture<?> future =
                    DEADLINE_EXECUTOR.schedule(
                            () -> {
                                if (!expire()) {
                                    return;
                                }

                                timeoutCallback.onError(
                                        new SocketTimeoutException(
                                                "Backend request exceeded its total deadline"
                                        )
                                );
                            },
                            timeout,
                            unit
                    );

            if (!deadlineReference.compareAndSet(null, future)) {
                future.cancel(false);
                throw new IllegalStateException(
                        "Request deadline is already armed"
                );
            }

            if (finished.get()) {
                cancelDeadline();
            }
        }

        private boolean expire() {
            if (!finished.compareAndSet(false, true)) {
                return false;
            }

            canceled.set(true);
            cancelAttachedCall();
            return true;
        }

        private boolean finish() {
            if (!finished.compareAndSet(false, true)) {
                return false;
            }

            cancelDeadline();
            callReference.set(null);
            return !canceled.get();
        }

        private boolean canDeliver() {
            return !canceled.get() && !finished.get();
        }

        private void cancelDeadline() {
            ScheduledFuture<?> future =
                    deadlineReference.getAndSet(null);

            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private BackendApiClient() {
    }

    public static RequestHandle enqueueAuthenticated(
            OkHttpClient client,
            Request.Builder requestBuilder,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback
    ) {
        return enqueueAuthenticatedInternal(
                client,
                requestBuilder,
                callback,
                authenticationErrorCallback,
                null,
                null
        );
    }

    public static RequestHandle enqueueAuthenticated(
            OkHttpClient client,
            Request.Builder requestBuilder,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback,
            long totalTimeout,
            TimeUnit timeoutUnit
    ) {
        return enqueueAuthenticatedInternal(
                client,
                requestBuilder,
                callback,
                authenticationErrorCallback,
                totalTimeout,
                timeoutUnit
        );
    }

    private static RequestHandle enqueueAuthenticatedInternal(
            OkHttpClient client,
            Request.Builder requestBuilder,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback,
            Long totalTimeout,
            TimeUnit timeoutUnit
    ) {
        RequestHandle handle = new RequestHandle();
        Request unsignedRequest = requestBuilder.build();

        if (totalTimeout != null) {
            if (timeoutUnit == null) {
                throw new IllegalArgumentException(
                        "Timeout unit is required"
                );
            }

            handle.armDeadline(
                    totalTimeout,
                    timeoutUnit,
                    authenticationErrorCallback
            );
        }

        if (
                !BuildConfig.DEBUG
                        && !unsignedRequest.url().isHttps()
        ) {
            if (handle.finish()) {
                authenticationErrorCallback.onError(
                        new IllegalStateException(
                                "Backend URL must use HTTPS in release builds"
                        )
                );
            }

            return handle;
        }

        FirebaseAuth firebaseAuth =
                FirebaseAuth.getInstance();

        FirebaseUser user =
                firebaseAuth.getCurrentUser();

        if (user == null) {
            firebaseAuth
                    .signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (handle.isCanceled()) {
                            return;
                        }

                        FirebaseUser signedInUser =
                                firebaseAuth.getCurrentUser();

                        if (
                                !task.isSuccessful()
                                        || signedInUser == null
                        ) {
                            Exception error =
                                    task.getException() != null
                                            ? task.getException()
                                            : new IllegalStateException(
                                            "Anonymous authentication failed"
                                    );

                            if (handle.finish()) {
                                authenticationErrorCallback.onError(
                                        error
                                );
                            }

                            return;
                        }

                        enqueueWithUser(
                                client,
                                unsignedRequest,
                                callback,
                                authenticationErrorCallback,
                                signedInUser,
                                handle
                        );
                    });

            return handle;
        }

        enqueueWithUser(
                client,
                unsignedRequest,
                callback,
                authenticationErrorCallback,
                user,
                handle
        );

        return handle;
    }

    private static void enqueueWithUser(
            OkHttpClient client,
            Request unsignedRequest,
            Callback callback,
            AuthenticationErrorCallback authenticationErrorCallback,
            FirebaseUser user,
            RequestHandle handle
    ) {
        if (handle.isCanceled()) {
            return;
        }

        user.getIdToken(false)
                .addOnCompleteListener(task -> {
                    if (handle.isCanceled()) {
                        return;
                    }

                    if (
                            !task.isSuccessful()
                                    || task.getResult() == null
                                    || task.getResult().getToken() == null
                    ) {
                        Exception error =
                                task.getException() != null
                                        ? task.getException()
                                        : new IllegalStateException(
                                        "Firebase ID token is unavailable"
                                );

                        if (handle.finish()) {
                            authenticationErrorCallback.onError(
                                    error
                            );
                        }

                        return;
                    }

                    Request request =
                            unsignedRequest
                                    .newBuilder()
                                    .header(
                                            "Authorization",
                                            "Bearer "
                                                    + task.getResult()
                                                    .getToken()
                                    )
                                    .build();

                    Call call = client.newCall(request);

                    if (!handle.attach(call)) {
                        return;
                    }

                    call.enqueue(
                            new Callback() {
                                @Override
                                public void onFailure(
                                        Call completedCall,
                                        java.io.IOException exception
                                ) {
                                    if (handle.finish()) {
                                        callback.onFailure(
                                                completedCall,
                                                exception
                                        );
                                    }
                                }

                                @Override
                                public void onResponse(
                                        Call completedCall,
                                        okhttp3.Response response
                                ) throws java.io.IOException {
                                    if (!handle.canDeliver()) {
                                        response.close();
                                        return;
                                    }

                                    try {
                                        callback.onResponse(
                                                completedCall,
                                                response
                                        );
                                    } finally {
                                        handle.finish();
                                    }
                                }
                            }
                    );
                });
    }

    public static RequestBody createAudioRequestBody(
            File audioFile
    ) {
        return RequestBody.create(
                resolveAudioMediaType(audioFile),
                audioFile
        );
    }

    private static MediaType resolveAudioMediaType(
            File audioFile
    ) {
        String fileName =
                audioFile.getName().toLowerCase(Locale.US);

        if (fileName.endsWith(".wav")) {
            return MediaType.parse("audio/wav");
        }

        if (fileName.endsWith(".flac")) {
            return MediaType.parse("audio/flac");
        }

        if (
                fileName.endsWith(".m4a")
                        || fileName.endsWith(".mp4")
        ) {
            return MediaType.parse("audio/mp4");
        }

        if (fileName.endsWith(".aac")) {
            return MediaType.parse("audio/aac");
        }

        if (fileName.endsWith(".ogg")) {
            return MediaType.parse("audio/ogg");
        }

        return MediaType.parse("audio/mpeg");
    }
}
