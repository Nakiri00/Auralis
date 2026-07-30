package com.nakiri00.auralis;

import android.app.Application;
import android.media.MediaPlayer;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    private static final String TAG = "LibraryViewModel";

    private final LibraryRepository repository = new LibraryRepository();

    private final MutableLiveData<List<ChordGroup>> chordGroups =
        new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(
        false
    );
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>(
        null
    );

    private MediaPlayer mediaPlayer;
    private boolean isLoadStarted = false;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<ChordGroup>> getChordGroups() {
        return chordGroups;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    /**
     * Load semua ChordGroup dari assets. Idempotent — aman dipanggil berulang kali.
     */
    public void loadChords() {
        if (isLoadStarted) return;
        isLoadStarted = true;
        isLoading.postValue(true);

        new Thread(() -> {
            List<ChordGroup> loaded = repository.loadChordGroupsFromAssets(
                getApplication()
            );
            chordGroups.postValue(loaded);
            isLoading.postValue(false);
        })
            .start();
    }

    /**
     * Putar audio untuk satu chord group.
     * MediaPlayer dikelola di ViewModel agar lifecycle-aware.
     */
    public void playAudio(ChordGroup group) {
        if (group.getAudioResId() == 0) {
            toastMessage.postValue(
                "Suara belum tersedia untuk " + group.getChordName()
            );
            return;
        }
        releaseMediaPlayer();
        try {
            mediaPlayer = MediaPlayer.create(
                getApplication(),
                group.getAudioResId()
            );
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> releaseMediaPlayer());
                mediaPlayer.start();
                toastMessage.postValue("Memutar " + group.getChordName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
        }
    }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        releaseMediaPlayer();
    }
}
