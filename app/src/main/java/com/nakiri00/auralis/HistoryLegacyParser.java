package com.nakiri00.auralis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HistoryLegacyParser {

    private static final Pattern CHORD_LINE_PATTERN =
            Pattern.compile(
                    "^\\[(\\d+):(\\d{1,2})\\]\\s*(.+)$"
            );

    private HistoryLegacyParser() {
    }

    static ParsedHistory parse(
            String legacyResult
    ) {
        List<ChordTimestamp> chords =
                new ArrayList<>();

        int keyIndex =
                KeyDetector.UNKNOWN_KEY_INDEX;

        boolean safeToDeleteSource = true;

        if (
                legacyResult == null
                        || legacyResult.trim().isEmpty()
        ) {
            return new ParsedHistory(
                    chords,
                    keyIndex,
                    true
            );
        }

        String[] lines =
                legacyResult.split("\\r?\\n");

        for (String rawLine : lines) {
            String line =
                    rawLine != null
                            ? rawLine.trim()
                            : "";

            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("KEY:")) {
                String keyName =
                        line.substring(4).trim();

                keyIndex =
                        findKeyIndex(keyName);

                continue;
            }

            if (!line.startsWith("[")) {
                continue;
            }

            Matcher matcher =
                    CHORD_LINE_PATTERN.matcher(line);

            if (!matcher.matches()) {
                safeToDeleteSource = false;
                continue;
            }

            try {
                int minutes =
                        Integer.parseInt(
                                matcher.group(1)
                        );

                int seconds =
                        Integer.parseInt(
                                matcher.group(2)
                        );

                String chordName =
                        matcher.group(3).trim();

                if (
                        minutes < 0
                                || seconds < 0
                                || seconds > 59
                                || chordName.isEmpty()
                ) {
                    safeToDeleteSource = false;
                    continue;
                }

                double totalSeconds =
                        minutes * 60.0
                                + seconds;

                chords.add(
                        new ChordTimestamp(
                                totalSeconds,
                                chordName
                        )
                );

            } catch (RuntimeException error) {
                safeToDeleteSource = false;
            }
        }

        return new ParsedHistory(
                chords,
                keyIndex,
                safeToDeleteSource
        );
    }

    private static int findKeyIndex(
            String keyName
    ) {
        if (
                keyName == null
                        || keyName.trim().isEmpty()
                        || keyName.equalsIgnoreCase(
                        "Not detected"
                )
        ) {
            return KeyDetector.UNKNOWN_KEY_INDEX;
        }

        for (int index = 0; index < 24; index++) {
            if (
                    KeyDetector
                            .getKeyName(index)
                            .equalsIgnoreCase(
                                    keyName.trim()
                            )
            ) {
                return index;
            }
        }

        return KeyDetector.UNKNOWN_KEY_INDEX;
    }

    static final class ParsedHistory {

        private final List<ChordTimestamp> chords;
        private final int keyIndex;
        private final boolean safeToDeleteSource;

        ParsedHistory(
                List<ChordTimestamp> chords,
                int keyIndex,
                boolean safeToDeleteSource
        ) {
            this.chords =
                    chords != null
                            ? new ArrayList<>(chords)
                            : new ArrayList<>();

            this.keyIndex = keyIndex;
            this.safeToDeleteSource =
                    safeToDeleteSource;
        }

        List<ChordTimestamp> getChords() {
            return new ArrayList<>(chords);
        }

        int getKeyIndex() {
            return keyIndex;
        }

        boolean isSafeToDeleteSource() {
            return safeToDeleteSource;
        }
    }
}