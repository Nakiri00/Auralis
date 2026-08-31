package com.nakiri00.auralis;

import java.util.List;

public class AudioAnalysisRepository {

    private final Object remoteAnalyzerLock = new Object();
    private LibrosaAnalyzer activeRemoteAnalyzer;

    public interface AnalysisCallback {
        void onComplete(
                List<ChordTimestamp> results,
                int keyIndex
        );

        void onError(Exception e);
    }

    public void analyze(
            String audioPath,
            boolean isPremiumMode,
            AnalysisCallback callback
    ) {
        cancelActiveAnalysis();

        int sampleRate = 44100;

        if (!isPremiumMode) {
            new TarsosDSPAnalyzer().analyzeChords(
                    audioPath,
                    sampleRate,
                    callback
            );
            return;
        }

        LibrosaAnalyzer analyzer =
                new LibrosaAnalyzer();

        synchronized (remoteAnalyzerLock) {
            activeRemoteAnalyzer = analyzer;
        }

        analyzer.analyzeChords(
                audioPath,
                sampleRate,
                new AnalysisCallback() {
                    @Override
                    public void onComplete(
                            List<ChordTimestamp> results,
                            int keyIndex
                    ) {
                        if (!clearIfActive(analyzer)) {
                            return;
                        }

                        callback.onComplete(
                                results,
                                keyIndex
                        );
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!clearIfActive(analyzer)) {
                            return;
                        }

                        callback.onError(e);
                    }
                }
        );
    }

    public void cancelActiveAnalysis() {
        LibrosaAnalyzer analyzer;

        synchronized (remoteAnalyzerLock) {
            analyzer = activeRemoteAnalyzer;
            activeRemoteAnalyzer = null;
        }

        if (analyzer != null) {
            analyzer.cancel();
        }
    }

    private boolean clearIfActive(
            LibrosaAnalyzer analyzer
    ) {
        synchronized (remoteAnalyzerLock) {
            if (activeRemoteAnalyzer != analyzer) {
                return false;
            }

            activeRemoteAnalyzer = null;
            return true;
        }
    }
}