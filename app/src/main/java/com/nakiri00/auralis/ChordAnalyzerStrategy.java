package com.nakiri00.auralis;

public interface ChordAnalyzerStrategy {

    void analyzeChords(
            String audioPath,
            int sampleRate,
            AudioAnalysisRepository.AnalysisCallback callback
    );

    void cancel();
}