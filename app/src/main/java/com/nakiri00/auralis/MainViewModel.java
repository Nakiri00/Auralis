package com.nakiri00.auralis;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<AuthState> authState = new MutableLiveData<>(
        AuthState.LOADING
    );
    private boolean authStarted = false;

    public enum AuthState {
        LOADING,
        SUCCESS,
        FAILED,
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public void ensureAuthenticated() {
        if (authStarted) return;
        authStarted = true;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            authState.setValue(AuthState.SUCCESS);
            return;
        }
        auth
            .signInAnonymously()
            .addOnCompleteListener(task -> {
                authState.postValue(
                    task.isSuccessful() ? AuthState.SUCCESS : AuthState.FAILED
                );
            });
    }
}
