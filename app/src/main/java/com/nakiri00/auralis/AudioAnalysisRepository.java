package com.nakiri00.auralis;

import java.util.List;

public class AudioAnalysisRepository {

    private static final String TAG = "AudioAnalysisRepo";

    public interface AnalysisCallback {
        void onComplete(List<ChordTimestamp> results, int keyIndex);
        void onError(Exception e);
    }

    public void analyze(String audioPath, boolean isPremiumMode, AnalysisCallback callback) {

        ChordAnalyzerStrategy analyzer;

        if (isPremiumMode) {
            analyzer = new LibrosaAnalyzer();
        } else {
            analyzer = new TarsosDSPAnalyzer();
        }

        int sampleRate = 44100; // Bisa diekstrak dari decodeAudio() jika diperlukan untuk TarsosDSP

        analyzer.analyzeChords(audioPath, sampleRate, callback);
    }


}
