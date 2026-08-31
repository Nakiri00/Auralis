package com.nakiri00.auralis;

import java.util.List;

public class AudioAnalysisRepository {

    private final Object analyzerLock = new Object();

    private ChordAnalyzerStrategy activeAnalyzer;

    public interface AnalysisCallback {

        void onComplete(
                List<ChordTimestamp> results,
                int keyIndex
        );

        void onError(Exception exception);
    }

    public void analyze(
            String audioPath,
            boolean isPremiumMode,
            AnalysisCallback callback
    ) {
        cancelActiveAnalysis();

        int sampleRate = 44100;

        ChordAnalyzerStrategy analyzer = isPremiumMode
                ? new LibrosaAnalyzer()
                : new TarsosDSPAnalyzer();

        synchronized (analyzerLock) {
            activeAnalyzer = analyzer;
        }

        try {
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
                        public void onError(Exception exception) {
                            if (!clearIfActive(analyzer)) {
                                return;
                            }

                            callback.onError(exception);
                        }
                    }
            );
        } catch (Exception exception) {
            if (clearIfActive(analyzer)) {
                callback.onError(exception);
            }
        }
    }

    public void cancelActiveAnalysis() {
        ChordAnalyzerStrategy analyzerToCancel;

        synchronized (analyzerLock) {
            analyzerToCancel = activeAnalyzer;
            activeAnalyzer = null;
        }

        if (analyzerToCancel != null) {
            analyzerToCancel.cancel();
        }
    }

    private boolean clearIfActive(
            ChordAnalyzerStrategy analyzer
    ) {
        synchronized (analyzerLock) {
            if (activeAnalyzer != analyzer) {
                return false;
            }

            activeAnalyzer = null;
            return true;
        }
    }
}