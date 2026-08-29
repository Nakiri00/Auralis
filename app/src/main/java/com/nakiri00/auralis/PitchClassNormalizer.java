package com.nakiri00.auralis;

/** Normalizes enharmonic note names to one sharp-based pitch-class spelling. */
public final class PitchClassNormalizer {

    private static final String[] CANONICAL_NOTES = {
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
    };

    private PitchClassNormalizer() {}

    public static String[] getCanonicalNotes() {
        return CANONICAL_NOTES.clone();
    }

    /**
     * Normalizes only the root spelling and preserves the chord quality and
     * optional display annotation. For example, "Bb Minor (ii)" becomes
     * "A# Minor (ii)".
     */
    public static String normalizeChordName(String chordName) {
        if (chordName == null) return null;

        String trimmed = chordName.trim();
        RootToken root = parseRoot(trimmed);
        if (root == null) return trimmed;

        return CANONICAL_NOTES[root.pitchClass]
                + trimmed.substring(root.length);
    }

    /** Returns 0-11 for the chord root or -1 when the input has no note root. */
    public static int getPitchClassIndex(String chordName) {
        if (chordName == null) return -1;
        RootToken root = parseRoot(chordName.trim());
        return root != null ? root.pitchClass : -1;
    }

    private static RootToken parseRoot(String value) {
        if (value.isEmpty()) return null;

        char note = Character.toUpperCase(value.charAt(0));
        if (note < 'A' || note > 'G') return null;

        String root = String.valueOf(note);
        int length = 1;
        if (value.length() > 1) {
            char accidental = value.charAt(1);
            if (accidental == '#' || accidental == 'b') {
                root += accidental;
                length = 2;
            }
        }

        int pitchClass;
        switch (root) {
            case "C":
            case "B#":
                pitchClass = 0;
                break;
            case "C#":
            case "Db":
                pitchClass = 1;
                break;
            case "D":
                pitchClass = 2;
                break;
            case "D#":
            case "Eb":
                pitchClass = 3;
                break;
            case "E":
            case "Fb":
                pitchClass = 4;
                break;
            case "E#":
            case "F":
                pitchClass = 5;
                break;
            case "F#":
            case "Gb":
                pitchClass = 6;
                break;
            case "G":
                pitchClass = 7;
                break;
            case "G#":
            case "Ab":
                pitchClass = 8;
                break;
            case "A":
                pitchClass = 9;
                break;
            case "A#":
            case "Bb":
                pitchClass = 10;
                break;
            case "B":
            case "Cb":
                pitchClass = 11;
                break;
            default:
                return null;
        }
        return new RootToken(pitchClass, length);
    }

    private static final class RootToken {
        final int pitchClass;
        final int length;

        RootToken(int pitchClass, int length) {
            this.pitchClass = pitchClass;
            this.length = length;
        }
    }
}
