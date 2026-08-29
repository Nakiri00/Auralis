package com.nakiri00.auralis;

import java.util.HashMap;
import java.util.Map;

public class ChordTemplates {

    public static final String[] NOTES = PitchClassNormalizer.getCanonicalNotes();

    // Chord tone weights
    private static final float ROOT_WEIGHT       = 1.00f;
    private static final float THIRD_WEIGHT      = 0.75f;
    private static final float FIFTH_WEIGHT      = 0.55f;
    private static final Map<String, float[]> chordTemplates = new HashMap<>();

    static {
        for (int i = 0; i < NOTES.length; i++) {

            // ── TRIAD: Major (Root + Major 3rd + Perfect 5th) ──────────────
            float[] major = new float[12];
            major[i % 12]       = ROOT_WEIGHT;
            major[(i + 4) % 12] = THIRD_WEIGHT;
            major[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + " Major", major);

            // ── TRIAD: Minor (Root + Minor 3rd + Perfect 5th) ──────────────
            float[] minor = new float[12];
            minor[i % 12]       = ROOT_WEIGHT;
            minor[(i + 3) % 12] = THIRD_WEIGHT;
            minor[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + " Minor", minor);

        }
    }

    /**
     * Finds the best matching chord using cosine similarity between
     * the energy-weighted chroma vector and weighted chord templates.
     *
     * @param chroma float[12] normalized energy per pitch class (0.0 to 1.0)
     * @return best matching chord name, or "N/A" if no confident match found
     */
    public static String findBestMatchingChord(float[] chroma) {
        double totalSim = 0;
        int count = 0;
        String bestChord = "N/A";
        double maxSim = 0;

        for (Map.Entry<String, float[]> entry : chordTemplates.entrySet()) {
            double sim = cosineSimilarity(chroma, entry.getValue());
            totalSim += sim;
            count++;
            if (sim > maxSim) { maxSim = sim; bestChord = entry.getKey(); }
        }

        double avgSim = totalSim / count;
        double adaptiveThreshold = Math.max(0.45, avgSim * 1.30);
        return maxSim >= adaptiveThreshold ? bestChord : "N/A";
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Panjang vektor harus sama");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
