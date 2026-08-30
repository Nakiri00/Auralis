package com.nakiri00.auralis;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    private static final String TAG = "LibraryViewModel";

    private final LibraryRepository repository;
    private final ChordAudioPlayer chordAudioPlayer;

    private final MutableLiveData<List<ChordGroup>> chordGroups =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> isLoading =
            new MutableLiveData<>(false);

    private final MutableLiveData<String> toastMessage =
            new MutableLiveData<>(null);

    private boolean isLoadStarted = false;

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        repository = new LibraryRepository();
        chordAudioPlayer = new ChordAudioPlayer(application);
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
     * Memuat semua kelompok chord dari guitar.json.
     * Pemanggilan bersifat idempotent.
     */
    public void loadChords() {
        if (isLoadStarted) {
            return;
        }

        isLoadStarted = true;
        isLoading.setValue(true);

        new Thread(() -> {
            try {
                List<ChordGroup> loadedGroups =
                        repository.loadChordGroupsFromAssets(
                                getApplication()
                        );

                chordGroups.postValue(loadedGroups);

            } catch (Exception error) {
                Log.e(
                        TAG,
                        "Failed to load chord library",
                        error
                );

                toastMessage.postValue(
                        "Gagal memuat pustaka chord"
                );

            } finally {
                isLoading.postValue(false);
            }
        }, "auralis-library-loader").start();
    }

    /**
     * Memainkan preview sintetis menggunakan posisi fingering
     * pertama yang tersedia pada sebuah grup chord.
     */
    public void playAudio(ChordGroup group) {
        if (group == null) {
            toastMessage.postValue(
                    "Data chord tidak tersedia"
            );
            return;
        }

        List<String> positions = group.getPositions();

        if (positions == null || positions.isEmpty()) {
            toastMessage.postValue(
                    "Posisi chord tidak tersedia untuk "
                            + group.getChordName()
            );
            return;
        }

        String fretPosition = positions.get(0);

        chordAudioPlayer.play(
                fretPosition,
                new ChordAudioPlayer.PlaybackCallback() {
                    @Override
                    public void onStarted() {
                        toastMessage.postValue(
                                "Memutar "
                                        + group.getChordName()
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(
                                TAG,
                                "Failed to play chord: "
                                        + group.getChordName(),
                                error
                        );

                        toastMessage.postValue(
                                "Gagal memutar "
                                        + group.getChordName()
                        );
                    }
                }
        );
    }

    /**
     * Menghentikan suara saat pengguna keluar dari Library.
     */
    public void stopAudio() {
        chordAudioPlayer.stop();
    }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        chordAudioPlayer.release();
        super.onCleared();
    }
}