package com.example.musicchords;

import java.util.HashMap;
import java.util.Map;

public class ChordTemplates {

    public static final String[] NOTES = {
            "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B",
    };

    // Chord tone weights
    private static final float ROOT_WEIGHT       = 1.00f;
    private static final float THIRD_WEIGHT      = 0.75f;
    private static final float FIFTH_WEIGHT      = 0.55f;
    private static final float SEVENTH_WEIGHT    = 0.60f; // 7th note (minor 7th interval)
    private static final float SUSPENSION_WEIGHT = 0.55f; // sus2 / sus4 interval
    private static final float POWER_FIFTH       = 0.70f; // 5th in power chord (only 2 notes, so weighted higher)

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

            // ── POWER CHORD (5): Root + Perfect 5th (no 3rd) ───────────────
            // Ambiguous major/minor — common in rock/metal guitar
            float[] power = new float[12];
            power[i % 12]       = ROOT_WEIGHT;
            power[(i + 7) % 12] = POWER_FIFTH;
            chordTemplates.put(NOTES[i] + "5", power);

            // ── DOMINANT 7th (7): Root + Major 3rd + P5 + Minor 7th ────────
            // The V chord in most progressions (e.g. G7 in key of C)
            float[] dom7 = new float[12];
            dom7[i % 12]        = ROOT_WEIGHT;
            dom7[(i + 4) % 12]  = THIRD_WEIGHT;
            dom7[(i + 7) % 12]  = FIFTH_WEIGHT;
            dom7[(i + 10) % 12] = SEVENTH_WEIGHT;
            chordTemplates.put(NOTES[i] + "7", dom7);

            // ── MINOR 7th (m7): Root + Minor 3rd + P5 + Minor 7th ──────────
            // e.g. Am7, Dm7 — common in jazz/pop
            float[] min7 = new float[12];
            min7[i % 12]        = ROOT_WEIGHT;
            min7[(i + 3) % 12]  = THIRD_WEIGHT;
            min7[(i + 7) % 12]  = FIFTH_WEIGHT;
            min7[(i + 10) % 12] = SEVENTH_WEIGHT;
            chordTemplates.put(NOTES[i] + "m7", min7);

            // ── SUSPENDED 4th (sus4): Root + Perfect 4th + Perfect 5th ─────
            // Resolves to major/minor; very common on guitar (e.g. Dsus4, Asus4)
            float[] sus4 = new float[12];
            sus4[i % 12]       = ROOT_WEIGHT;
            sus4[(i + 5) % 12] = SUSPENSION_WEIGHT;
            sus4[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + "sus4", sus4);

            // ── SUSPENDED 2nd (sus2): Root + Major 2nd + Perfect 5th ───────
            float[] sus2 = new float[12];
            sus2[i % 12]       = ROOT_WEIGHT;
            sus2[(i + 2) % 12] = SUSPENSION_WEIGHT;
            sus2[(i + 7) % 12] = FIFTH_WEIGHT;
            chordTemplates.put(NOTES[i] + "sus2", sus2);
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
