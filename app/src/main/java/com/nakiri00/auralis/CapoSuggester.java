package com.nakiri00.auralis;

public final class CapoSuggester {

    private static final int[] EASY_KEYS = {
            0, 2, 4, 5, 7, 9
    };

    private CapoSuggester() {
    }

    public static String suggest(
            int keyIndex
    ) {
        if (
                !KeyDetector.isValidKeyIndex(
                        keyIndex
                )
        ) {
            return "Capo suggestion unavailable";
        }

        boolean isMinor =
                keyIndex >= 12;

        int detectedRoot =
                isMinor
                        ? keyIndex - 12
                        : keyIndex;

        for (int easyKey : EASY_KEYS) {
            if (detectedRoot == easyKey) {
                return "No capo needed - already in "
                        + KeyDetector.getKeyName(
                        keyIndex
                );
            }
        }

        for (int capo = 1; capo <= 7; capo++) {
            int effectiveRoot =
                    (
                            detectedRoot
                                    - capo
                                    + 12
                    ) % 12;

            for (int easyKey : EASY_KEYS) {
                if (effectiveRoot == easyKey) {
                    String easyKeyName =
                            ChordTemplates.NOTES[
                                    effectiveRoot
                                    ] + (
                                    isMinor
                                            ? " Minor"
                                            : " Major"
                            );

                    return "Capo fret "
                            + capo
                            + " → play in "
                            + easyKeyName;
                }
            }
        }

        return "There is no capo suggestion";
    }
}