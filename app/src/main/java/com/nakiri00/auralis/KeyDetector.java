package com.nakiri00.auralis;

import java.util.HashSet;
import java.util.Set;

/**
 * Detects the musical key of a song from its accumulated chroma vector
 * using the Krumhansl-Schmuckler key-finding algorithm, and provides
 * the set of diatonic chords for that key.
 *
 * Key index encoding: 0-11 = C..B Major, 12-23 = C..B Minor
 */
public class KeyDetector {

    // Krumhansl-Kessler key profiles (1990)
    private static final double[] MAJOR_PROFILE = {
            6.35, 2.23, 3.48, 2.33, 4.38, 4.09,
            2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    };
    private static final double[] MINOR_PROFILE = {
            6.33, 2.68, 3.52, 5.38, 2.60, 3.53,
            2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    };
    public static final int UNKNOWN_KEY_INDEX = -1;

    private static final double CHROMA_EPSILON = 1e-6;
    private static final double MIN_KEY_CORRELATION = 0.10;

    /*
     * Diatonic chords in a Major key (intervals from root):
     *   I (0, Major), ii (2, Minor), iii (4, Minor),
     *   IV (5, Major), V (7, Major), vi (9, Minor)
     *   vii° omitted (diminished, not in our chord set)
     */
    private static final int[]     MAJOR_CHORD_ROOTS    = {0, 2, 4, 5, 7, 9};
    private static final boolean[] MAJOR_CHORD_IS_MAJOR = {true, false, false, true, true, false};

    /*
     * Diatonic chords in a Natural Minor key (intervals from root):
     *   i (0, Minor), III (3, Major), iv (5, Minor),
     *   v (7, Minor), VI (8, Major), VII (10, Major)
     *   ii° omitted (diminished)
     */
    private static final int[]     MINOR_CHORD_ROOTS    = {0, 3, 5, 7, 8, 10};
    private static final boolean[] MINOR_CHORD_IS_MAJOR = {false, true, false, false, true, true};

    // -----------------------------------------------------------------------

    /**
     * Returns a key index (0-23) for the detected key.
     * 0-11  = C Major .. B Major
     * 12-23 = C Minor .. B Minor
     *
     * @param globalChroma normalized float[12] accumulated chroma vector
     */
    public static int detectKey(
            float[] globalChroma
    ) {
        if (!hasUsableChroma(globalChroma)) {
            return UNKNOWN_KEY_INDEX;
        }

        double maximumCorrelation =
                -Double.MAX_VALUE;

        int bestKey =
                UNKNOWN_KEY_INDEX;

        for (int root = 0; root < 12; root++) {
            double majorCorrelation =
                    pearsonCorrelation(
                            globalChroma,
                            rotateProfile(
                                    MAJOR_PROFILE,
                                    root
                            )
                    );

            if (
                    Double.isFinite(
                            majorCorrelation
                    )
                            && majorCorrelation
                            > maximumCorrelation
            ) {
                maximumCorrelation =
                        majorCorrelation;

                bestKey = root;
            }

            double minorCorrelation =
                    pearsonCorrelation(
                            globalChroma,
                            rotateProfile(
                                    MINOR_PROFILE,
                                    root
                            )
                    );

            if (
                    Double.isFinite(
                            minorCorrelation
                    )
                            && minorCorrelation
                            > maximumCorrelation
            ) {
                maximumCorrelation =
                        minorCorrelation;

                bestKey = root + 12;
            }
        }

        if (
                bestKey == UNKNOWN_KEY_INDEX
                        || maximumCorrelation
                        < MIN_KEY_CORRELATION
        ) {
            return UNKNOWN_KEY_INDEX;
        }

        return bestKey;
    }

    public static boolean isValidKeyIndex(
            int keyIndex
    ) {
        return keyIndex >= 0
                && keyIndex < 24;
    }

    private static boolean hasUsableChroma(
            float[] globalChroma
    ) {
        if (
                globalChroma == null
                        || globalChroma.length != 12
        ) {
            return false;
        }

        double totalEnergy = 0;
        double minimum = Double.MAX_VALUE;
        double maximum = -Double.MAX_VALUE;

        for (float value : globalChroma) {
            if (!Float.isFinite(value)) {
                return false;
            }

            totalEnergy += Math.abs(value);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }

        return totalEnergy > CHROMA_EPSILON
                && (
                maximum - minimum
        ) > CHROMA_EPSILON;
    }

    /**
     * Returns the human-readable name of the key, e.g. "C Major" or "A Minor".
     */
    public static String getKeyName(
            int keyIndex
    ) {
        if (!isValidKeyIndex(keyIndex)) {
            return "Not detected";
        }

        boolean isMinor =
                keyIndex >= 12;

        int rootIndex =
                isMinor
                        ? keyIndex - 12
                        : keyIndex;

        return ChordTemplates.NOTES[rootIndex]
                + (
                isMinor
                        ? " Minor"
                        : " Major"
        );
    }

    /**
     * Returns the set of diatonic chord names for the given key index.
     * Includes the harmonic-minor dominant (V Major) for minor keys.
     */
    public static Set<String> getDiatonicChords(
            int keyIndex
    ) {
        Set<String> diatonic =
                new HashSet<>();

        if (!isValidKeyIndex(keyIndex)) {
            return diatonic;
        }
        boolean isMinor = keyIndex >= 12;
        int rootIdx = isMinor ? keyIndex - 12 : keyIndex;

        int[]     roots   = isMinor ? MINOR_CHORD_ROOTS    : MAJOR_CHORD_ROOTS;
        boolean[] isMajor = isMinor ? MINOR_CHORD_IS_MAJOR : MAJOR_CHORD_IS_MAJOR;

        for (int i = 0; i < roots.length; i++) {
            int chordRoot = (rootIdx + roots[i]) % 12;
            diatonic.add(ChordTemplates.NOTES[chordRoot] + (isMajor[i] ? " Major" : " Minor"));
        }

        // Add harmonic-minor dominant V Major for minor keys
        if (isMinor) {
            int dominantRoot = (rootIdx + 7) % 12;
            diatonic.add(ChordTemplates.NOTES[dominantRoot] + " Major");
        }

        // Add V7 (dominant 7th chord) — diatonic in both major and minor keys
        // e.g. G7 in key of C Major, E7 in key of A Minor (harmonic minor)
        int v7Root = (rootIdx + 7) % 12;
        diatonic.add(ChordTemplates.NOTES[v7Root] + "7");

        return diatonic;
    }

    // -----------------------------------------------------------------------

    /**
     * Rotates the key profile so that pitch class j maps to scale degree (j - rootIndex).
     * For root r: rotated[j] = profile[(j - r + 12) % 12]
     */
    private static double[] rotateProfile(double[] profile, int rootIndex) {
        double[] rotated = new double[12];
        for (int j = 0; j < 12; j++) {
            rotated[j] = profile[(j - rootIndex + 12) % 12];
        }
        return rotated;
    }

    private static double pearsonCorrelation(float[] x, double[] y) {
        double sumX = 0, sumY = 0;
        for (int i = 0; i < 12; i++) { sumX += x[i]; sumY += y[i]; }
        double meanX = sumX / 12.0;
        double meanY = sumY / 12.0;

        double num = 0, denX = 0, denY = 0;
        for (int i = 0; i < 12; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            num  += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }

        if (denX <= CHROMA_EPSILON || denY <= CHROMA_EPSILON) {
            return Double.NaN;
        }
        return num / Math.sqrt(denX * denY);
    }

    /**
     * Mengonversi nama chord ke notasi Roman numeral dalam konteks key terdeteksi.
     * Contoh: key C Major, chord "F Major" → "IV"
     * Contoh: key A Minor, chord "E Major" → "V (harmonic)"
     */
    public static String toRomanNumeral(int keyIndex, String chordName) {
        if (!isValidKeyIndex(keyIndex) || chordName == null || chordName.trim().isEmpty() || "-".equals(chordName) || "N/A".equals(chordName)) {
            return "";
        }
        boolean keyIsMinor = keyIndex >= 12;
        int keyRoot = keyIsMinor ? keyIndex - 12 : keyIndex;
        String normalizedChord = PitchClassNormalizer.normalizeChordName(chordName);

        // Resolve the root by pitch class so D#/Eb, G#/Ab, and A#/Bb are equal.
        int chordRoot = PitchClassNormalizer.getPitchClassIndex(normalizedChord);
        if (chordRoot == -1) return "?";

        int interval = (chordRoot - keyRoot + 12) % 12;
        boolean chordIsMajor = normalizedChord.contains(" Major") || normalizedChord.endsWith("7")
                || normalizedChord.endsWith("5") || normalizedChord.endsWith("sus4")
                || normalizedChord.endsWith("sus2");
        boolean chordIsMinor = normalizedChord.contains(" Minor") || normalizedChord.endsWith("m7");

        // Tabel Roman numeral berdasarkan interval dalam tangga nada
        String[] majorRomans = {"I", "ii°", "II", "iii°", "III", "IV", "iv°", "V", "vi°", "VI", "vii°", "VII"};
        String[] minorRomans = {"i", "ii°", "II", "III", "iv°", "iv", "V°", "V", "VI", "vi°", "VII", "vii°"};

        String[] table = keyIsMinor ? minorRomans : majorRomans;
        String base = table[interval];

        // Sesuaikan kapital: uppercase = Major, lowercase = minor
        if (chordIsMajor) base = base.toUpperCase().replace("°", "");
        else if (chordIsMinor) base = base.toLowerCase().replace("°", "");

        // Tambahkan suffix untuk 7th, sus, dll
        if (normalizedChord.endsWith("7"))    base += "7";
        else if (normalizedChord.endsWith("m7"))  base += "7";
        else if (normalizedChord.endsWith("sus4")) base += "sus4";
        else if (normalizedChord.endsWith("sus2")) base += "sus2";
        else if (normalizedChord.endsWith("5"))    base += "5";

        return base;
    }
}
