package com.example.musicchords;

import static androidx.core.content.ContextCompat.startActivity;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    // Repositories
    private final YoutubeRepository youtubeRepository = new YoutubeRepository();
    private final AudioAnalysisRepository analysisRepository =
        new AudioAnalysisRepository();
    private final HistoryRepository historyRepository = new HistoryRepository();

    // LiveData — YouTube conversion
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(
        false
    );
    private final MutableLiveData<String> downloadLink = new MutableLiveData<>(
        null
    );

    // LiveData — Status text (drives resultTextView)
    private final MutableLiveData<String> statusText = new MutableLiveData<>(
        ""
    );

    // LiveData — Toast message (one-shot)
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>(
        null
    );

    // LiveData — Analysis
    private final MutableLiveData<Boolean> isAnalyzing = new MutableLiveData<>(
        false
    );

    // LiveData — Player
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

    // LiveData — File state
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

    // Internal state
    private String audioTitle = "";
    private String audioFilePath = null;
    private final List<ChordTimestamp> detectedChords = new ArrayList<>();
    private final MutableLiveData<List<ChordTimestamp>> detectedChordsList = new MutableLiveData<>(new ArrayList<>());


    // MediaPlayer & Handlers
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable seekBarRunnable;
    private final Handler chordHandler = new Handler(Looper.getMainLooper());
    private Runnable chordRunnable;

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    // ─── Getters LiveData ───────────────────────────────────────────────────
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


    private List<ChordTimestamp> postProcess(List<ChordTimestamp> raw) {
        if (raw == null || raw.size() < 2) return raw;

        // Tahap 1: Hapus duplikat berurutan agar bersih
        List<ChordTimestamp> cleanedRaw = new ArrayList<>();
        for (ChordTimestamp curr : raw) {
            if (!cleanedRaw.isEmpty() && cleanedRaw.get(cleanedRaw.size() - 1).getChordName().equals(curr.getChordName())) {
                continue;
            }
            cleanedRaw.add(curr);
        }

        List<ChordTimestamp> result = new ArrayList<>();
        result.add(cleanedRaw.get(0));

        // Tahap 2: Evaluasi durasi (0.4 detik adalah sweet spot untuk lagu pop/rock)
        final double MIN_DURATION = 0.4;

        for (int i = 1; i < cleanedRaw.size(); i++) {
            ChordTimestamp prev = result.get(result.size() - 1);
            ChordTimestamp curr = cleanedRaw.get(i);

            double duration = curr.getTimeSeconds() - prev.getTimeSeconds();

            if (duration < MIN_DURATION && result.size() > 1) {
                // Chord sebelumnya terlalu singkat, cabut dari hasil
                result.remove(result.size() - 1);

                if (!result.isEmpty()) {
                    ChordTimestamp prevPrev = result.get(result.size() - 1);
                    // Kasus 1: Balik ke chord yang sama (C -> "-" -> C)
                    if (prevPrev.getChordName().equals(curr.getChordName())) {
                        continue;
                    }
                }

                // Kasus 2: Transisi (C -> F singkat -> G). F dihapus.
                // Jika curr adalah tanda "-", JANGAN dimasukkan. Biarkan chord sebelumnya memanjang.
                if (!curr.getChordName().equals("-")) {
                    result.add(new ChordTimestamp(prev.getTimeSeconds(), curr.getChordName()));
                }
            } else {
                // Pastikan tidak ada duplikat setelah penggabungan
                if (!curr.getChordName().equals(prev.getChordName())) {
                    result.add(curr);
                }
            }
        }

        // Tahap 3: Pembersihan Akhir (Hapus tanda "-" jika durasinya hanya numpang lewat di akhir proses)
        List<ChordTimestamp> finalResult = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).getChordName().equals("-")) {
                // Cek durasi tanda "-" ini. Jika kurang dari 1 detik, abaikan saja.
                double duration = (i < result.size() - 1)
                        ? result.get(i+1).getTimeSeconds() - result.get(i).getTimeSeconds()
                        : 0;
                if (duration > 1.0) {
                    finalResult.add(result.get(i)); // Hanya pertahankan "-" jika memang lagunya berhenti lama
                }
            } else {
                finalResult.add(result.get(i));
            }
        }

        return finalResult;
    }

    // Setter untuk memasukkan data setelah analisis selesai
    public void setDetectedChords(List<ChordTimestamp> chords) {
        // Gunakan postValue karena analisis berjalan di background thread
        detectedChordsList.postValue(chords);
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
                statusText.postValue("Audio Siap: " + audioTitle);
            } else if ("processing".equalsIgnoreCase(status)) {
                statusText.postValue(
                    "Server Sedang Memproses Video. Mohon Tunggu"
                );
            } else {
                String msg = json.has("mess")
                    ? json.get("mess").getAsString()
                    : "Format Respons Tidak Dikenal";
                statusText.postValue("Gagal: " + msg);
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON Parsing Error", e);
            statusText.postValue(
                "Terjadi kesalahan saat memproses respons server."
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
    public void processAudioFile(Uri uri, String customTitle) {
        new Thread(() -> {
            try {
                Context ctx = getApplication().getApplicationContext();
                String fileName = getFileNameFromUri(ctx, uri);
                if (fileName == null) fileName =
                    "audio_" + System.currentTimeMillis() + ".mp3";

                String safeFileName = fileName.replaceAll(
                    "[\\\\/:*?\"<>|]",
                    "_"
                );
                File destFile = new File(
                    ctx.getFilesDir(),
                    "history_" + safeFileName
                );

                try (
                    InputStream in = ctx
                        .getContentResolver()
                        .openInputStream(uri);
                    FileOutputStream out = new FileOutputStream(destFile)
                ) {
                    byte[] buf = new byte[8 * 1024];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }

                String resolvedTitle = (customTitle != null &&
                    !customTitle.isEmpty())
                    ? customTitle
                    : fileName;
                handler.post(() -> {
                    audioFilePath = destFile.getAbsolutePath();
                    audioTitle = resolvedTitle;
                    detectedChords.clear();
                    currentChordDisplay.setValue("-");
                    statusText.setValue("File Siap: " + resolvedTitle);
                    fileLoaded.setValue(true);
                    setupAudioPlayer(audioFilePath, audioTitle);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing file", e);
                handler.post(() ->
                    toastMessage.setValue(
                        "Gagal memproses file: " + e.getMessage()
                    )
                );
            }
        })
            .start();
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
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
            playerPosition.postValue(position);
        }
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
                    currentChordDisplay.postValue(getCurrentChordAt(sec));
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
    public void analyzeChords(String audioPath, String title) {
        if (Boolean.TRUE.equals(isAnalyzing.getValue())) return;
        isAnalyzing.setValue(true);

        // Update UI untuk memberitahu user bahwa AI sedang bekerja
//        currentChordDisplay.setValue("Memisahkan vokal & instrumen...");
//        statusText.setValue("Sedang membersihkan audio via Server...");
        currentChordDisplay.setValue("Menganalisis Chords...");
        statusText.setValue("Menganalisis...");
        detectedChords.clear();
        setDetectedChords(new ArrayList<>());

        // Ambil context dari AndroidViewModel
        Context context = getApplication().getApplicationContext();
        SourceSeparationHelper separatorHelper = new SourceSeparationHelper();

        separatorHelper.separateAudio(context, audioPath, new SourceSeparationHelper.SeparationCallback() {
            @Override
            public void onSuccess(String separatedAudioPath) {
                handler.post(() -> {
                    currentChordDisplay.setValue("Menganalisis Chords...");
//                    statusText.setValue("Selesai. Menganalisis...");
                    statusText.setValue("Menganalisis...");
                });

                executeDspAnalysis(separatedAudioPath, title, audioPath);
            }

            @Override
            public void onError(Exception e) {
//                Log.e(TAG, "Server Gagal, menggunakan file asli", e);
//                handler.post(() -> {
//                  toastMessage.setValue("Server terputus, memakai audio asli.");
                    currentChordDisplay.setValue("Menganalisis Chords...");
                    statusText.setValue("Menganalisis...");
//                });

                executeDspAnalysis(audioPath, title, audioPath);
            }
        });
    }

    private void executeDspAnalysis(String fileToAnalyze, String title, String originalAudioPath) {
        analysisRepository.analyzeChords(audioPath, new AudioAnalysisRepository.AnalysisCallback() {
            @Override
            public void onComplete(List<ChordTimestamp> results, int keyIndex) {  // ← terima keyIndex
                detectedChords.clear();
                detectedChords.addAll(results);

                // Bangun teks hasil analisis
                StringBuilder sb = new StringBuilder();
                sb.append(title != null ? title : "Audio").append("\n");
                for (ChordTimestamp item : results) {
                    String name = item.getChordName();
                    String roman = name.equals("-")
                            ? ""
                            : "  (" + KeyDetector.toRomanNumeral(keyIndex, name) + ")";
                    sb.append(String.format(java.util.Locale.US, "[%02d:%02d] %s%s\n",
                            (int)(item.getTimeSeconds() / 60),
                            (int)(item.getTimeSeconds() % 60),
                            name,
                            roman));
                }

                // Hasilkan info key dan capo dari keyIndex
                String keyName    = KeyDetector.getKeyName(keyIndex);
                String capoAdvice = CapoSuggester.suggest(keyIndex);

                handler.post(() -> {
                    isAnalyzing.setValue(false);
                    currentChordDisplay.setValue(results.isEmpty() ? "Tidak ada chord terdeteksi." : "Analisis Selesai");
                    statusText.setValue("Analisis selesai.");
                    detectedKeyText.setValue("Key: " + keyName);           // ← tampilkan key
                    capoSuggestionText.setValue(capoAdvice);               // ← tampilkan saran capo

                    historyRepository.saveOrUpdateHistory(title, audioPath, sb.toString(),
                            new HistoryRepository.OnSaveListener() {
                                @Override public void onSuccess(boolean isUpdate) { Log.d(TAG, "History saved"); }
                                @Override public void onError(Exception e)        { Log.e(TAG, "Failed to save history", e); }
                            });
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Analysis error", e);
                handler.post(() -> {
                    isAnalyzing.setValue(false);
                    toastMessage.setValue("Gagal menganalisis audio: " + e.getMessage());
                    currentChordDisplay.setValue("-");
                });
            }
        });

    }

    // ─── Load History ───────────────────────────────────────────────────────
    public void loadHistoryData(
        String audioPath,
        String title,
        String savedChordData
    ) {
        audioFilePath = audioPath;
        audioTitle = title != null ? title : "";
        stopChordSync();
        detectedChords.clear();
        currentChordDisplay.setValue("-");

        if (savedChordData != null && !savedChordData.isEmpty()) {
            parseAndLoadChords(savedChordData);
            statusText.setValue("Data dimuat dari History.");
        } else {
            statusText.setValue("File siap. Silakan Analisis.");
        }
        fileLoaded.setValue(true);
        setupAudioPlayer(audioPath, audioTitle);
    }

    private void parseAndLoadChords(String savedData) {
        detectedChords.clear();
        int count = 0;
        for (String line : savedData.split("\n")) {
            line = line.trim();
            if (line.startsWith("[") && line.contains("]")) {
                try {
                    int bracket = line.indexOf("]");
                    String[] parts = line.substring(1, bracket).split(":");
                    if (parts.length == 2) {
                        double totalSec =
                            Integer.parseInt(parts[0]) * 60 +
                            Integer.parseInt(parts[1]);
                        detectedChords.add(
                            new ChordTimestamp(
                                totalSec,
                                line.substring(bracket + 1).trim()
                            )
                        );
                        count++;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Skipping line: " + line);
                }
            }
        }
        setDetectedChords(new ArrayList<>(detectedChords));
        currentChordDisplay.setValue(
            count > 0
                ? "Data Loaded (" + count + " Chords)"
                : "No chord data found."
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        releaseMediaPlayer();
        handler.removeCallbacksAndMessages(null);
        chordHandler.removeCallbacksAndMessages(null);
    }

}
