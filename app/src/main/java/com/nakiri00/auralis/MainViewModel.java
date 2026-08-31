package com.nakiri00.auralis;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;

public class MainViewModel
        extends AndroidViewModel {

    public enum AuthState {
        LOADING,
        SUCCESS,
        FAILED
    }

    private final MutableLiveData<AuthState> authState =
            new MutableLiveData<>(
                    AuthState.LOADING
            );

    private boolean authInFlight;

    public MainViewModel(
            @NonNull Application application
    ) {
        super(application);
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    /**
     * Autentikasi hanya diperlukan untuk fitur cloud.
     * Kegagalan auth tidak boleh memblokir UI lokal.
     */
    public void ensureAuthenticated() {
        FirebaseAuth firebaseAuth =
                FirebaseAuth.getInstance();

        if (
                firebaseAuth.getCurrentUser()
                        != null
        ) {
            authInFlight = false;

            authState.setValue(
                    AuthState.SUCCESS
            );

            return;
        }

        if (authInFlight) {
            return;
        }

        authInFlight = true;

        authState.setValue(
                AuthState.LOADING
        );

        firebaseAuth
                .signInAnonymously()
                .addOnCompleteListener(task -> {
                    authInFlight = false;

                    authState.setValue(
                            task.isSuccessful()
                                    ? AuthState.SUCCESS
                                    : AuthState.FAILED
                    );
                });
    }
}