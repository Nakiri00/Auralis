package com.nakiri00.auralis;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";
    private final YoutubeRepository youtubeRepository = new YoutubeRepository();
    private final AudioAnalysisRepository analysisRepository =
            new AudioAnalysisRepository();
    private final HistoryRepository historyRepository;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(
            false
    );
    private final MutableLiveData<String> downloadLink = new MutableLiveData<>(
            null
    );
    private final MutableLiveData<String> statusText = new MutableLiveData<>(
            ""
    );
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>(
            null
    );
    private final MutableLiveData<Boolean> isAnalyzing = new MutableLiveData<>(
            false
    );
    private final MutableLiveData<String> playerTitle = new MutableLiveData<>(
            ""
    );
    private final MutableLiveData<Boolean> playerReady = new MutableLiveData<>(
            false
    );
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(
            false
    );
    private final MutableLiveData<Integer> playerDuration =
            new MutableLiveData<>(0);
    private final MutableLiveData<Integer> playerPosition =
            new MutableLiveData<>(0);
    private final MutableLiveData<String> currentChordDisplay =
            new MutableLiveData<>("-");
    private final MutableLiveData<String> detectedKeyText    = new MutableLiveData<>("");
    private final MutableLiveData<String> capoSuggestionText = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> fileLoaded = new MutableLiveData<>(
            false
    );
    private final MutableLiveData<Integer> chordProgress = new MutableLiveData<>(0);

    public LiveData<Integer> getChordProgress() {
        return chordProgress;
    }
    private final MutableLiveData<List<String>> upcomingChords = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<String>> getUpcomingChords() {
        return upcomingChords;
    }
    private String audioTitle = "";
    private String audioFilePath = null;
    private String currentHistoryId;
    private final List<ChordTimestamp> detectedChords = new ArrayList<>();
    private final MutableLiveData<List<ChordTimestamp>> detectedChordsList = new MutableLiveData<>(new ArrayList<>());
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable seekBarRunnable;
    private final Handler chordHandler = new Handler(Looper.getMainLooper());
    private Runnable chordRunnable;
    private final AtomicLong analysisOperationId =
            new AtomicLong(0);

    private volatile SourceSeparationHelper activeSeparator;
    private volatile String activeTemporaryAnalysisPath;
    private volatile String activeHistoryAudioPath;

    public HomeViewModel(
            @NonNull Application application
    ) {
        super(application);

        historyRepository =
                new HistoryRepository(
                        application.getApplicationContext()
                );
        new Thread(
                () -> {
                    try {
                        chordHintResolver.load(
                                application.getApplicationContext()
                        );

                        handler.post(() -> {
                            lastChordHint = null;

                            updateActiveChordHint(
                                    currentChordDisplay.getValue()
                            );
                        });

                    } catch (Exception error) {
                        Log.e(
                                TAG,
                                "Failed to load chord hints",
                                error
                        );
                    }
                },
                "auralis-chord-hint-loader"
        ).start();
    }
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getDownloadLink() {
        return downloadLink;
    }

    public LiveData<String> getStatusText() {
        return statusText;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Boolean> getIsAnalyzing() {
        return isAnalyzing;
    }

    public LiveData<String> getPlayerTitle() {
        return playerTitle;
    }

    public LiveData<Boolean> getPlayerReady() {
        return playerReady;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public LiveData<Integer> getPlayerDuration() {
        return playerDuration;
    }

    public LiveData<Integer> getPlayerPosition() {
        return playerPosition;
    }

    public LiveData<String> getCurrentChordDisplay() {
        return currentChordDisplay;
    }

    public LiveData<Boolean> getFileLoaded() {
        return fileLoaded;
    }

    public String getAudioTitle() {
        return audioTitle;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public LiveData<List<ChordTimestamp>> getDetectedChords() {
        return detectedChordsList;
    }
    public LiveData<String> getDetectedKeyText()    { return detectedKeyText; }
    public LiveData<String> getCapoSuggestionText() { return capoSuggestionText; }
    public String getCurrentHistoryId() {
        return currentHistoryId;
    }

    private final AtomicLong fileImportOperationId =
            new AtomicLong(0);

    private volatile File pendingHistoryAudioDraft;

    private final ChordHintResolver chordHintResolver =
            new ChordHintResolver();

    private final MutableLiveData<ChordGroup> activeChordHint =
            new MutableLiveData<>(null);

    private ChordGroup lastChordHint;

    public LiveData<ChordGroup> getActiveChordHint() {
        return activeChordHint;
    }


//    private List<ChordTimestamp> postProcess(List<ChordTimestamp> raw) {
//        if (raw == null || raw.size() < 2) return raw;
//
//        // Tahap 1: Hapus duplikat berurutan agar bersih
//        List<ChordTimestamp> cleanedRaw = new ArrayList<>();
//        for (ChordTimestamp curr : raw) {
//            if (!cleanedRaw.isEmpty() && cleanedRaw.get(cleanedRaw.size() - 1).getChordName().equals(curr.getChordName())) {
//                continue;
//            }
//            cleanedRaw.add(curr);
//        }
//
//        List<ChordTimestamp> result = new ArrayList<>();
//        result.add(cleanedRaw.get(0));
//
//        // Tahap 2: Evaluasi durasi (0.4 detik adalah sweet spot untuk lagu pop/rock)
//        final double MIN_DURATION = 0.4;
//
//        for (int i = 1; i < cleanedRaw.size(); i++) {
//            ChordTimestamp prev = result.get(result.size() - 1);
//            ChordTimestamp curr = cleanedRaw.get(i);
//
//            double duration = curr.getTimeSeconds() - prev.getTimeSeconds();
//
//            if (duration < MIN_DURATION && result.size() > 1) {
//                // Chord sebelumnya terlalu singkat, cabut dari hasil
//                result.remove(result.size() - 1);
//
//                if (!result.isEmpty()) {
//                    ChordTimestamp prevPrev = result.get(result.size() - 1);
//                    // Kasus 1: Balik ke chord yang sama (C -> "-" -> C)
//                    if (prevPrev.getChordName().equals(curr.getChordName())) {
//                        continue;
//                    }
//                }
//
//                // Kasus 2: Transisi (C -> F singkat -> G). F dihapus.
//                // Jika curr adalah tanda "-", JANGAN dimasukkan. Biarkan chord sebelumnya memanjang.
//                if (!curr.getChordName().equals("-")) {
//                    result.add(new ChordTimestamp(prev.getTimeSeconds(), curr.getChordName()));
//                }
//            } else {
//                // Pastikan tidak ada duplikat setelah penggabungan
//                if (!curr.getChordName().equals(prev.getChordName())) {
//                    result.add(curr);
//                }
//            }
//        }
//
//        // Tahap 3: Pembersihan Akhir (Hapus tanda "-" jika durasinya hanya numpang lewat di akhir proses)
//        List<ChordTimestamp> finalResult = new ArrayList<>();
//        for (int i = 0; i < result.size(); i++) {
//            if (result.get(i).getChordName().equals("-")) {
//                // Cek durasi tanda "-" ini. Jika kurang dari 1 detik, abaikan saja.
//                double duration = (i < result.size() - 1)
//                        ? result.get(i+1).getTimeSeconds() - result.get(i).getTimeSeconds()
//                        : 0;
//                if (duration > 1.0) {
//                    finalResult.add(result.get(i)); // Hanya pertahankan "-" jika memang lagunya berhenti lama
//                }
//            } else {
//                finalResult.add(result.get(i));
//            }
//        }
//
//        return finalResult;
//    }

    // Setter untuk memasukkan data setelah analisis selesai
    public void setDetectedChords(List<ChordTimestamp> chords) {
        // Gunakan postValue karena analisis berjalan di background thread
        detectedChordsList.postValue(chords);
    }
    private void updateCurrentChordAndHint(
            String chordName
    ) {
        String safeChordName =
                chordName != null
                        ? chordName
                        : "-";

        currentChordDisplay.setValue(
                safeChordName
        );

        updateActiveChordHint(
                safeChordName
        );
    }

    private void updateActiveChordHint(
            String chordName
    ) {
        ChordGroup resolvedHint =
                chordHintResolver.resolve(
                        chordName
                );

        if (lastChordHint == resolvedHint) {
            return;
        }

        lastChordHint = resolvedHint;

        activeChordHint.setValue(
                resolvedHint
        );
    }

    private void clearActiveChordHint() {
        lastChordHint = null;
        activeChordHint.setValue(null);
    }
    // ─── YouTube Conversion ─────────────────────────────────────────────────
    public void convertYoutubeUrl(String url) {
        isLoading.postValue(true);
        downloadLink.postValue(null);
        statusText.postValue("Memproses...");

        youtubeRepository.convertYoutubeUrl(
                url,
                new YoutubeRepository.ConversionCallback() {
                    @Override
                    public void onSuccess(String responseJson) {
                        isLoading.postValue(false);
                        parseYoutubeResponse(responseJson);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        isLoading.postValue(false);
                        toastMessage.postValue(errorMessage);
                        statusText.postValue("Error: " + errorMessage);
                    }
                }
        );
    }

    private void parseYoutubeResponse(String response) {
        try {
            JsonObject json = new Gson().fromJson(response, JsonObject.class);
            String status = json.has("status")
                    ? json.get("status").getAsString()
                    : "error";
            if ("ok".equalsIgnoreCase(status) && json.has("link")) {
                audioTitle = json.has("title")
                        ? json.get("title").getAsString()
                        : "audio";
                downloadLink.postValue(json.get("link").getAsString());
                statusText.postValue(audioTitle);
            } else if ("processing".equalsIgnoreCase(status)) {
                statusText.postValue(
                        "Processing Audio, please wait..."
                );
            } else {
                String msg = json.has("mess")
                        ? json.get("mess").getAsString()
                        : "Response Format Invalid";
                statusText.postValue("Gagal: " + msg);
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON Parsing Error", e);
            statusText.postValue(
                    "Something Bad Happen, please try again."
            );
        }
    }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    // ─── Download ───────────────────────────────────────────────────────────
    public long downloadAudio(String url, String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(url)
        );
        request.setTitle(title);
        request.setDescription("Mengunduh Audio MP3");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                safeName + ".mp3"
        );
        DownloadManager dm =
                (DownloadManager) getApplication().getSystemService(
                        Context.DOWNLOAD_SERVICE
                );
        return dm.enqueue(request);
    }

    // ─── File Processing ────────────────────────────────────────────────────
    public void processAudioFile(
            Uri uri,
            String customTitle
    ) {
        long importOperationId =
                fileImportOperationId
                        .incrementAndGet();

        cancelActiveBackendRequests(false);
        releaseMediaPlayer();
        discardPendingHistoryAudioDraft();

        new Thread(() -> {
            File draft = null;

            try {
                Context context =
                        getApplication()
                                .getApplicationContext();

                String originalFileName =
                        getFileNameFromUri(
                                context,
                                uri
                        );

                if (
                        originalFileName == null
                                || originalFileName
                                .trim()
                                .isEmpty()
                ) {
                    originalFileName =
                            "audio_"
                                    + System.currentTimeMillis()
                                    + ".mp3";
                }

                String draftId =
                        HistoryIdentity.newId();

                draft =
                        HistoryAudioStorage.createDraft(
                                context,
                                draftId,
                                originalFileName
                        );

                InputStream openedInput =
                        context
                                .getContentResolver()
                                .openInputStream(uri);

                if (openedInput == null) {
                    throw new IOException(
                            "Unable to open selected audio"
                    );
                }

                try (
                        InputStream input =
                                openedInput;

                        FileOutputStream output =
                                new FileOutputStream(
                                        draft
                                )
                ) {
                    byte[] buffer =
                            new byte[16 * 1024];

                    long totalBytes = 0;
                    int bytesRead;

                    while (
                            (bytesRead = input.read(buffer)) != -1
                    ) {
                        if (
                                fileImportOperationId.get()
                                        != importOperationId
                        ) {
                            throw new IOException(
                                    "Audio import was replaced"
                            );
                        }

                        output.write(
                                buffer,
                                0,
                                bytesRead
                        );

                        totalBytes += bytesRead;
                    }

                    output.flush();
                    output.getFD().sync();

                    if (totalBytes == 0) {
                        throw new IOException(
                                "Selected audio is empty"
                        );
                    }
                }


                if (
                        fileImportOperationId.get()
                                != importOperationId
                ) {
                    HistoryAudioStorage.discardDraft(
                            context,
                            draft
                    );
                    return;
                }
                String audioFingerprint =
                        AudioFingerprint.sha256(
                                draft
                        );

                if (
                        fileImportOperationId.get()
                                != importOperationId
                ) {
                    HistoryAudioStorage.discardDraft(
                            context,
                            draft
                    );
                    return;
                }

                final String historyId =
                        HistoryIdentity.fromAudioFingerprint(
                                audioFingerprint
                        );

                Log.d(
                        TAG,
                        "Resolved stable History ID: "
                                + historyId
                );


                String resolvedTitle =
                        customTitle != null
                                && !customTitle.trim().isEmpty()
                                ? customTitle.trim()
                                : originalFileName;

                final File storedDraft =
                        draft;

                pendingHistoryAudioDraft =
                        storedDraft;

                handler.post(() -> {
                    if (
                            fileImportOperationId.get()
                                    != importOperationId
                    ) {
                        discardPendingHistoryAudioDraft();
                        return;
                    }

                    currentHistoryId =
                            historyId;

                    audioFilePath =
                            storedDraft.getAbsolutePath();

                    audioTitle =
                            resolvedTitle;

                    detectedChords.clear();

                    setDetectedChords(
                            new ArrayList<>()
                    );

                    upcomingChords.setValue(
                            new ArrayList<>()
                    );

                    currentChordDisplay.setValue("-");
                    detectedKeyText.setValue("");
                    capoSuggestionText.setValue("");
                    chordProgress.setValue(0);
                    statusText.setValue(resolvedTitle);
                    fileLoaded.setValue(true);

                    setupAudioPlayer(
                            audioFilePath,
                            audioTitle
                    );
                });

            } catch (Exception error) {
                Log.e(
                        TAG,
                        "Error processing file",
                        error
                );

                Context context =
                        getApplication()
                                .getApplicationContext();

                if (draft != null) {
                    HistoryAudioStorage.discardDraft(
                            context,
                            draft
                    );
                }

                if (
                        fileImportOperationId.get()
                                == importOperationId
                ) {
                    handler.post(() ->
                            toastMessage.setValue(
                                    "Gagal memproses file: "
                                            + error.getMessage()
                            )
                    );
                }
            }
        }, "auralis-file-import").start();
    }

    private synchronized void discardPendingHistoryAudioDraft() {
        File draft =
                pendingHistoryAudioDraft;

        pendingHistoryAudioDraft = null;

        if (draft == null) {
            return;
        }

        HistoryAudioStorage.discardDraft(
                getApplication()
                        .getApplicationContext(),
                draft
        );
    }

    private synchronized void detachDraftForSave(
            File draft
    ) {
        if (
                draft != null
                        && pendingHistoryAudioDraft != null
                        && pendingHistoryAudioDraft.equals(
                        draft
                )
        ) {
            /*
             * Draft sekarang dimiliki callback save,
             * sehingga pemilihan file baru tidak menghapusnya.
             */
            pendingHistoryAudioDraft = null;
        }
    }
    private String getFileNameFromUri(Context ctx, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (
                    Cursor c = ctx
                            .getContentResolver()
                            .query(uri, null, null, null, null)
            ) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) result = c.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = (result != null) ? result.lastIndexOf('/') : -1;
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    // ─── Audio Player ───────────────────────────────────────────────────────
    private void setupAudioPlayer(String path, String title) {
        releaseMediaPlayer();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);

            mediaPlayer.setOnPreparedListener(mp -> {
                playerTitle.setValue(title != null ? title : "");
                playerDuration.setValue(mp.getDuration());
                playerPosition.setValue(0);
                isPlaying.setValue(false);
                playerReady.setValue(true);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying.postValue(false);
                playerPosition.postValue(0);
                if (seekBarRunnable != null) handler.removeCallbacks(
                        seekBarRunnable
                );
                stopChordSync();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(
                        TAG,
                        "MediaPlayer error: what=" + what + " extra=" + extra
                );
                releaseMediaPlayer();
                toastMessage.postValue("Gagal memuat audio");
                return true;
            });

            mediaPlayer.prepareAsync(); // ← non-blocking, tidak freeze UI
        } catch (Exception e) {
            Log.e(TAG, "Error setting up audio", e);
            playerReady.postValue(false);
            toastMessage.postValue("Gagal memuat audio");
        }
    }

    public void calculateChordProgress(long currentPlaybackPositionMs, List<ChordTimestamp> chords, int currentIndex) {
        if (chords == null || chords.isEmpty() || currentIndex < 0 || currentIndex >= chords.size()) {
            return;
        }
        ChordTimestamp currentChord = chords.get(currentIndex);
        if (currentIndex + 1 < chords.size()) {
            ChordTimestamp nextChord = chords.get(currentIndex + 1);
            long currentChordTimeMs = (long) (currentChord.getTimeSeconds() * 1000);
            long nextChordTimeMs = (long) (nextChord.getTimeSeconds() * 1000);

            long durationMs = nextChordTimeMs - currentChordTimeMs;
            long elapsedMs = currentPlaybackPositionMs - currentChordTimeMs;
            if (durationMs > 0) {
                int progress = (int) (((float) elapsedMs / durationMs) * 100);
                progress = Math.max(0, Math.min(100, progress));
                chordProgress.postValue(progress);
            }
        } else {
            chordProgress.postValue(100);
        }
    }

    public void playAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            isPlaying.postValue(true);
            startSeekBarUpdate();
            startChordSync();
        }
    }

    public void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying.postValue(false);
            if (seekBarRunnable != null) handler.removeCallbacks(
                    seekBarRunnable
            );
            stopChordSync();
        }
    }

    public void seekTo(int position) {
        if (mediaPlayer == null) {
            return;
        }

        mediaPlayer.seekTo(position);
        playerPosition.setValue(position);

        double positionSeconds =
                position / 1000.0;

        updateCurrentChordAndHint(
                getCurrentChordAt(
                        positionSeconds
                )
        );
    }

    public void releaseMediaPlayer() {
        stopChordSync();
        if (seekBarRunnable != null) handler.removeCallbacks(seekBarRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playerReady.postValue(false);
        isPlaying.postValue(false);
        clearActiveChordHint();
    }

    private void startSeekBarUpdate() {
        seekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    playerPosition.postValue(mediaPlayer.getCurrentPosition());
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(seekBarRunnable);
    }

    private void startChordSync() {
        chordRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    double sec = mediaPlayer.getCurrentPosition() / 1000.0;
                    String activeChord =
                            getCurrentChordAt(sec);

                    updateCurrentChordAndHint(
                            activeChord
                    );
                    long currentPositionMs = mediaPlayer.getCurrentPosition();
                    int activeIndex = getCurrentChordIndexAt(sec);
                    calculateChordProgress(currentPositionMs, detectedChords, activeIndex);
                    List<String> nextChordsList = new ArrayList<>();
                    if (activeIndex != -1) {
                        // Ambil maksimal 4 chord berikutnya
                        for (int i = activeIndex + 1; i <= activeIndex + 4; i++) {
                            if (i < detectedChords.size()) {
                                nextChordsList.add(detectedChords.get(i).getChordName());
                            }
                        }
                    }
                    upcomingChords.postValue(nextChordsList);
                    chordHandler.postDelayed(this, 100);
                }
            }
        };
        chordHandler.post(chordRunnable);
    }

    private void stopChordSync() {
        if (chordRunnable != null) chordHandler.removeCallbacks(chordRunnable);
    }

    private String getCurrentChordAt(double currentTime) {
        String result = "-";
        for (ChordTimestamp item : detectedChords) {
            if (currentTime >= item.getTimeSeconds()) result =
                    item.getChordName();
            else break;
        }

        return result;
    }

    private int getCurrentChordIndexAt(double currentTime) {
        int currentIndex = -1;
        for (int i = 0; i < detectedChords.size(); i++) {
            if (currentTime >= detectedChords.get(i).getTimeSeconds()) {
                currentIndex = i;
            } else {
                break;
            }
        }
        return currentIndex;
    }

    // ─── Audio Analysis ─────────────────────────────────────────────────────
    public void analyzeChords(
            String audioPath,
            String title,
            boolean isPremiumUser
    ) {
        if (Boolean.TRUE.equals(isAnalyzing.getValue())) {
            return;
        }

        cancelActiveBackendRequests(false);

        final long operationId =
                analysisOperationId.incrementAndGet();

        isAnalyzing.setValue(true);
        currentChordDisplay.setValue("Cleaning Audio...");
        clearActiveChordHint();
        detectedChords.clear();
        setDetectedChords(new ArrayList<>());
        upcomingChords.setValue(new ArrayList<>());
        detectedKeyText.setValue("");
        capoSuggestionText.setValue("");
        chordProgress.setValue(0);

        Context context =
                getApplication().getApplicationContext();

        SourceSeparationHelper separator =
                new SourceSeparationHelper();

        activeSeparator = separator;

        separator.separateAudio(
                context,
                audioPath,
                new SourceSeparationHelper.SeparationCallback() {
                    @Override
                    public void onSuccess(
                            String separatedAudioPath
                    ) {
                        if (!isCurrentAnalysis(operationId)) {
                            cleanupTemporaryAnalysisFile(
                                    separatedAudioPath,
                                    audioPath
                            );
                            return;
                        }

                        if (activeSeparator == separator) {
                            activeSeparator = null;
                        }

                        handler.post(() -> {
                            if (!isCurrentAnalysis(operationId)) {
                                return;
                            }

                            String mode =
                                    isPremiumUser
                                            ? "Librosa"
                                            : "TarsosDSP";

                            currentChordDisplay.setValue(
                                    mode + " is Running"
                            );
                        });

                        executeAnalysis(
                                separatedAudioPath,
                                title,
                                audioPath,
                                isPremiumUser,
                                operationId
                        );
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isCurrentAnalysis(operationId)) {
                            return;
                        }

                        if (activeSeparator == separator) {
                            activeSeparator = null;
                        }

                        Log.e(
                                TAG,
                                "Server Spleeter gagal, "
                                        + "menggunakan file asli",
                                e
                        );

                        handler.post(() -> {
                            if (!isCurrentAnalysis(operationId)) {
                                return;
                            }

                            toastMessage.setValue(
                                    "Separation failed, "
                                            + "using original audio"
                            );

                            String mode =
                                    isPremiumUser
                                            ? "Librosa"
                                            : "TarsosDSP";

                            currentChordDisplay.setValue(
                                    mode + " is Running"
                            );
                        });

                        executeAnalysis(
                                audioPath,
                                title,
                                audioPath,
                                isPremiumUser,
                                operationId
                        );
                    }
                }
        );
    }

    private void executeAnalysis(
            String fileToAnalyze,
            String title,
            String originalAudioPath,
            boolean isPremiumUser,
            long operationId
    ) {
        if (!isCurrentAnalysis(operationId)) {
            cleanupTemporaryAnalysisFile(
                    fileToAnalyze,
                    originalAudioPath
            );
            return;
        }

        String historyAudioPath =
                originalAudioPath != null
                        && !originalAudioPath.isEmpty()
                        ? originalAudioPath
                        : fileToAnalyze;

        activeTemporaryAnalysisPath = fileToAnalyze;
        activeHistoryAudioPath = historyAudioPath;

        analysisRepository.analyze(
                fileToAnalyze,
                isPremiumUser,
                new AudioAnalysisRepository.AnalysisCallback() {
                    @Override
                    public void onComplete(
                            List<ChordTimestamp> results,
                            int keyIndex
                    ) {
                        if (!isCurrentAnalysis(operationId)) {
                            cleanupTemporaryAnalysisFile(
                                    fileToAnalyze,
                                    historyAudioPath
                            );
                            return;
                        }

                        boolean keyDetected =
                                KeyDetector.isValidKeyIndex(
                                        keyIndex
                                );

                        String keyName =
                                keyDetected
                                        ? KeyDetector.getKeyName(
                                        keyIndex
                                )
                                        : "Not detected";

                        String capoAdvice =
                                keyDetected
                                        ? CapoSuggester.suggest(
                                        keyIndex
                                )
                                        : "Capo suggestion unavailable";

                        List<ChordTimestamp> formattedChords =
                                new ArrayList<>();

                        for (ChordTimestamp item : results) {
                            String name = item.getChordName();

                            String roman = "";

                            if (
                                    keyDetected
                                            && !name.equals("-")
                                            && !name.equals("N/A")
                            ) {
                                String romanValue =
                                        KeyDetector.toRomanNumeral(
                                                keyIndex,
                                                name
                                        );

                                if (!romanValue.isEmpty()) {
                                    roman =
                                            " (" + romanValue + ")";
                                }
                            }

                            String chordWithRoman =
                                    name + roman;

                            formattedChords.add(
                                    new ChordTimestamp(
                                            item.getTimeSeconds(),
                                            chordWithRoman
                                    )
                            );
                        }
                        final Context appContext =
                                getApplication()
                                        .getApplicationContext();

                        final File draftForSave =
                                historyAudioPath != null
                                        && !historyAudioPath.trim().isEmpty()
                                        ? new File(historyAudioPath)
                                        : null;

                        final String managedAudioFileName =
                                HistoryAudioStorage.getManagedFileName(
                                        appContext,
                                        historyAudioPath
                                );

                        final boolean shouldCommitDraft =
                                draftForSave != null
                                        && HistoryAudioStorage.isDraftFile(
                                        appContext,
                                        draftForSave
                                );


                        cleanupTemporaryAnalysisFile(
                                fileToAnalyze,
                                historyAudioPath
                        );

                        activeTemporaryAnalysisPath = null;
                        activeHistoryAudioPath = null;

                        handler.post(() -> {
                            if (!isCurrentAnalysis(operationId)) {
                                return;
                            }

                            detectedChords.clear();
                            detectedChords.addAll(
                                    formattedChords
                            );

                            setDetectedChords(
                                    new ArrayList<>(
                                            formattedChords
                                    )
                            );

                            isAnalyzing.setValue(false);

                            currentChordDisplay.setValue(
                                    results.isEmpty()
                                            ? "No Chords Detected"
                                            : "Chords Detected, Let's Play!"
                            );

                            statusText.setValue(
                                    "Analisis selesai."
                            );

                            detectedKeyText.setValue(
                                    keyDetected
                                            ? "Key: " + keyName
                                            : ""
                            );

                            capoSuggestionText.setValue(
                                    keyDetected
                                            ? capoAdvice
                                            : ""
                            );

                            String historyId =
                                    HistoryIdentity.normalizeOrCreate(
                                            currentHistoryId
                                    );

                            currentHistoryId =
                                    historyId;

                            if (shouldCommitDraft) {
                                detachDraftForSave(
                                        draftForSave
                                );
                            }

                            historyRepository.saveOrUpdateHistory(
                                    historyId,
                                    title,
                                    managedAudioFileName,
                                    new ArrayList<>(
                                            formattedChords
                                    ),
                                    keyIndex,
                                    new HistoryRepository.OnSaveListener() {
                                        @Override
                                        public void onSuccess(
                                                boolean isUpdate
                                        ) {
                                            Log.d(
                                                    TAG,
                                                    isUpdate
                                                            ? "History updated"
                                                            : "History created"
                                            );

                                            if (!shouldCommitDraft) {
                                                return;
                                            }

                                            File committedFile =
                                                    HistoryAudioStorage.commitDraft(
                                                            appContext,
                                                            draftForSave
                                                    );

                                            if (committedFile == null) {
                                                Log.e(
                                                        TAG,
                                                        "History was saved, but "
                                                                + "the audio draft could "
                                                                + "not be committed"
                                                );

                                                toastMessage.postValue(
                                                        "History tersimpan, tetapi "
                                                                + "audio lokal gagal "
                                                                + "diselesaikan."
                                                );

                                                return;
                                            }

                                            if (isCurrentAnalysis(operationId)) {
                                                audioFilePath =
                                                        committedFile
                                                                .getAbsolutePath();
                                            }
                                        }

                                        @Override
                                        public void onError(
                                                Exception error
                                        ) {
                                            Log.e(
                                                    TAG,
                                                    "Failed to save history",
                                                    error
                                            );

                                            if (shouldCommitDraft) {
                                                HistoryAudioStorage.discardDraft(
                                                        appContext,
                                                        draftForSave
                                                );
                                            }

                                            if (isCurrentAnalysis(operationId)) {
                                                handler.post(() -> {
                                                    releaseMediaPlayer();

                                                    audioFilePath = null;

                                                    fileLoaded.setValue(false);
                                                    playerReady.setValue(false);

                                                    toastMessage.setValue(
                                                            "Analisis selesai, tetapi "
                                                                    + "History gagal disimpan."
                                                    );
                                                });
                                            }
                                        }
                                    }
                            );
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isCurrentAnalysis(operationId)) {
                            cleanupTemporaryAnalysisFile(
                                    fileToAnalyze,
                                    historyAudioPath
                            );
                            return;
                        }

                        Log.e(TAG, "Analysis error", e);

                        cleanupTemporaryAnalysisFile(
                                fileToAnalyze,
                                historyAudioPath
                        );

                        activeTemporaryAnalysisPath = null;
                        activeHistoryAudioPath = null;

                        handler.post(() -> {
                            if (!isCurrentAnalysis(operationId)) {
                                return;
                            }

                            isAnalyzing.setValue(false);

                            toastMessage.setValue(
                                    "Failed To Analyze: "
                                            + e.getMessage()
                            );

                            currentChordDisplay.setValue("-");
                        });
                    }
                }
        );
    }

    private boolean isCurrentAnalysis(long operationId) {
        return analysisOperationId.get() == operationId;
    }

    public void cancelAnalysis() {
        cancelActiveBackendRequests(true);
    }

    private void cancelActiveBackendRequests(
            boolean updateUi
    ) {
        analysisOperationId.incrementAndGet();

        SourceSeparationHelper separator =
                activeSeparator;

        activeSeparator = null;

        if (separator != null) {
            separator.cancel();
        }

        analysisRepository.cancelActiveAnalysis();

        String temporaryPath =
                activeTemporaryAnalysisPath;

        String historyPath =
                activeHistoryAudioPath;

        activeTemporaryAnalysisPath = null;
        activeHistoryAudioPath = null;

        cleanupTemporaryAnalysisFile(
                temporaryPath,
                historyPath
        );

        if (updateUi) {
            isAnalyzing.postValue(false);
            currentChordDisplay.postValue("-");
            statusText.postValue("Analisis dibatalkan.");
        }
    }

    private void cleanupTemporaryAnalysisFile(String fileToAnalyze, String historyAudioPath) {
        if (fileToAnalyze == null || fileToAnalyze.isEmpty()) return;

        try {
            File analysisFile = new File(fileToAnalyze);
            String analysisPath = analysisFile.getCanonicalPath();

            if (historyAudioPath != null && !historyAudioPath.isEmpty()) {
                String historyPath = new File(historyAudioPath).getCanonicalPath();
                if (analysisPath.equals(historyPath)) return;
            }

            File cacheDir = getApplication().getApplicationContext().getCacheDir();
            String cachePath = cacheDir.getCanonicalPath() + File.separator;

            if (analysisPath.startsWith(cachePath) && analysisFile.exists() && !analysisFile.delete()) {
                Log.w(TAG, "Failed to delete temporary analysis file: " + analysisPath);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to clean up temporary analysis file", e);
        }
    }

    // ─── Load History ───────────────────────────────────────────────────────
    public void loadHistoryData(
            String historyId,
            String audioPath,
            String title,
            List<ChordTimestamp> savedChords,
            Integer savedKeyIndex
    ) {
        fileImportOperationId.incrementAndGet();

        cancelActiveBackendRequests(false);
        discardPendingHistoryAudioDraft();

        currentHistoryId =
                HistoryIdentity.normalizeOrCreate(
                        historyId
                );

        boolean hasLocalAudio =
                audioPath != null
                        && new File(audioPath).isFile();

        audioFilePath =
                hasLocalAudio
                        ? audioPath
                        : null;

        audioTitle =
                title != null
                        ? title
                        : "";

        stopChordSync();
        releaseMediaPlayer();

        List<ChordTimestamp> safeChords =
                sanitizeSavedChords(
                        savedChords
                );

        detectedChords.clear();
        detectedChords.addAll(
                safeChords
        );

        setDetectedChords(
                new ArrayList<>(
                        safeChords
                )
        );

        upcomingChords.setValue(
                new ArrayList<>()
        );

        chordProgress.setValue(0);

        int normalizedKeyIndex =
                savedKeyIndex != null
                        && KeyDetector.isValidKeyIndex(
                        savedKeyIndex
                )
                        ? savedKeyIndex
                        : KeyDetector.UNKNOWN_KEY_INDEX;

        boolean keyDetected =
                KeyDetector.isValidKeyIndex(
                        normalizedKeyIndex
                );

        detectedKeyText.setValue(
                keyDetected
                        ? "Key: "
                        + KeyDetector.getKeyName(
                        normalizedKeyIndex
                )
                        : ""
        );

        capoSuggestionText.setValue(
                keyDetected
                        ? CapoSuggester.suggest(
                        normalizedKeyIndex
                )
                        : ""
        );

        currentChordDisplay.setValue(
                safeChords.isEmpty()
                        ? "No chord data found."
                        : "Data Loaded ("
                        + safeChords.size()
                        + " Chords)"
        );

        boolean hasStructuredResult =
                !safeChords.isEmpty()
                        || keyDetected;

        statusText.setValue(
                hasStructuredResult
                        ? (
                        hasLocalAudio
                                ? "Data dimuat dari History."
                                : "Hasil chord dimuat tanpa audio."
                )
                        : "Tidak ada hasil chord."
        );

        fileLoaded.setValue(
                hasLocalAudio
        );

        if (hasLocalAudio) {
            setupAudioPlayer(
                    audioPath,
                    audioTitle
            );
        } else {
            playerTitle.setValue(
                    audioTitle
            );

            playerReady.setValue(false);
            isPlaying.setValue(false);
        }
    }

    private List<ChordTimestamp> sanitizeSavedChords(
            List<ChordTimestamp> source
    ) {
        List<ChordTimestamp> result =
                new ArrayList<>();

        if (source == null) {
            return result;
        }

        for (ChordTimestamp chord : source) {
            if (chord == null) {
                continue;
            }

            double time =
                    chord.getTimeSeconds();

            if (
                    Double.isNaN(time)
                            || Double.isInfinite(time)
                            || time < 0
            ) {
                continue;
            }

            result.add(
                    new ChordTimestamp(
                            time,
                            chord.getChordName()
                    )
            );
        }

        result.sort(
                (first, second) ->
                        Double.compare(
                                first.getTimeSeconds(),
                                second.getTimeSeconds()
                        )
        );

        return result;
    }

    @Override
    protected void onCleared() {
        cancelActiveBackendRequests(false);
        youtubeRepository.cancelActiveRequest();

        releaseMediaPlayer();
        handler.removeCallbacksAndMessages(null);
        chordHandler.removeCallbacksAndMessages(null);

        super.onCleared();
    }

}
