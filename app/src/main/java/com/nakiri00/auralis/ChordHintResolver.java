package com.nakiri00.auralis;

import android.content.Context;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChordHintResolver {

    private final Map<String, ChordGroup> chordIndex =
            new ConcurrentHashMap<>();

    /**
     * Panggil dari background thread karena membaca guitar.json.
     */
    public void load(Context context) {
        LibraryRepository repository =
                new LibraryRepository();

        List<ChordGroup> groups =
                repository.loadChordGroupsFromAssets(
                        context.getApplicationContext()
                );

        chordIndex.clear();

        for (ChordGroup group : groups) {
            if (group == null) {
                continue;
            }

            String normalizedName =
                    normalize(group.getChordName());

            if (normalizedName != null) {
                chordIndex.put(
                        normalizedName,
                        group
                );
            }
        }
    }

    public ChordGroup resolve(String detectedChordName) {
        String normalizedName =
                normalize(detectedChordName);

        if (normalizedName == null) {
            return null;
        }

        return chordIndex.get(normalizedName);
    }

    /**
     * Contoh:
     *
     * "C Major (I)"  -> "c major"
     * "A Minor (vi)" -> "a minor"
     * "D# Major"     -> "eb major"
     * "G# Minor"     -> "ab minor"
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim()
                        .replace("♯", "#")
                        .replace("♭", "b")
                        .replaceAll(
                                "\\s*\\([^)]*\\)\\s*$",
                                ""
                        )
                        .trim();

        if (cleaned.isEmpty()
                || cleaned.equals("-")
                || cleaned.equalsIgnoreCase("N/A")) {
            return null;
        }

        String[] parts =
                cleaned.split("\\s+");

        if (parts.length < 2) {
            return null;
        }

        String root =
                normalizeRoot(parts[0]);

        String quality =
                parts[1].toLowerCase(Locale.US);

        if (quality.equals("maj")) {
            quality = "major";
        } else if (quality.equals("min")) {
            quality = "minor";
        }

        if (!quality.equals("major")
                && !quality.equals("minor")) {
            return null;
        }

        return (
                root + " " + quality
        ).toLowerCase(Locale.US);
    }

    private static String normalizeRoot(String value) {
        String root =
                value.substring(0, 1)
                        .toUpperCase(Locale.US);

        if (value.length() > 1) {
            char accidental =
                    value.charAt(1);

            if (accidental == '#') {
                root += "#";
            } else if (
                    accidental == 'b'
                            || accidental == 'B'
            ) {
                root += "b";
            }
        }

        // Samakan dengan notasi yang tersedia di guitar.json.
        switch (root) {
            case "B#":
                return "C";

            case "Db":
                return "C#";

            case "D#":
                return "Eb";

            case "E#":
                return "F";

            case "Fb":
                return "E";

            case "Gb":
                return "F#";

            case "G#":
                return "Ab";

            case "A#":
                return "Bb";

            case "Cb":
                return "B";

            default:
                return root;
        }
    }
}