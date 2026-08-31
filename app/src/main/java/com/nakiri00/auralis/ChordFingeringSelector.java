package com.nakiri00.auralis;

import java.util.List;
import java.util.Locale;

public final class ChordFingeringSelector {

    private static final int[] STANDARD_TUNING_MIDI = {
            40, // E2
            45, // A2
            50, // D3
            55, // G3
            59, // B3
            64  // E4
    };

    private ChordFingeringSelector() {
    }

    public static Selection select(
            ChordGroup group
    ) {
        if (
                group == null
                        || group.getPositions() == null
                        || group.getPositions().isEmpty()
        ) {
            return null;
        }

        int rootPitchClass =
                getRootPitchClass(
                        group.getChordName()
                );

        if (rootPitchClass < 0) {
            return null;
        }

        boolean minor =
                group.getChordName() != null
                        && group.getChordName()
                        .toLowerCase(Locale.US)
                        .contains("minor");

        int thirdPitchClass =
                Math.floorMod(
                        rootPitchClass
                                + (minor ? 3 : 4),
                        12
                );

        int fifthPitchClass =
                Math.floorMod(
                        rootPitchClass + 7,
                        12
                );

        List<String> positions =
                group.getPositions();

        List<Integer> baseFrets =
                group.getBaseFrets();

        Selection bestSelection = null;
        double bestScore =
                Double.NEGATIVE_INFINITY;

        for (
                int index = 0;
                index < positions.size();
                index++
        ) {
            String fretString =
                    positions.get(index);

            int[] frets =
                    parseFrets(fretString);

            if (frets == null) {
                continue;
            }

            int baseFret =
                    baseFrets != null
                            && index < baseFrets.size()
                            ? baseFrets.get(index)
                            : 1;

            PositionInfo info =
                    inspectPosition(frets);

            if (info.soundingStrings == 0) {
                continue;
            }

            int bassPitchClass =
                    Math.floorMod(
                            info.lowestMidi,
                            12
                    );

            double score = 0.0;

            // Prioritas utama: nada terendah adalah root.
            if (bassPitchClass == rootPitchClass) {
                score += 100.0;
            } else if (
                    bassPitchClass
                            == fifthPitchClass
            ) {
                score += 30.0;
            } else if (
                    bassPitchClass
                            == thirdPitchClass
            ) {
                score += 20.0;
            }

            // Posisi yang lebih mudah dimainkan.
            score -= info.fretSpan * 5.0;
            score -= info.minimumPositiveFret * 0.8;
            score -= Math.max(0, baseFret - 1) * 0.5;

            // Open string membantu untuk posisi dasar.
            score += info.openStrings * 2.0;

            // Sedikit penalti untuk terlalu banyak senar mati.
            score -= info.mutedStrings * 0.5;

            if (score > bestScore) {
                bestScore = score;

                bestSelection =
                        new Selection(
                                fretString,
                                baseFret,
                                info.minimumPositiveFret,
                                info.openStrings > 0
                        );
            }
        }

        return bestSelection;
    }

    private static PositionInfo inspectPosition(
            int[] frets
    ) {
        int lowestMidi =
                Integer.MAX_VALUE;

        int minimumPositiveFret =
                Integer.MAX_VALUE;

        int maximumFret = 0;
        int openStrings = 0;
        int mutedStrings = 0;
        int soundingStrings = 0;

        for (
                int stringIndex = 0;
                stringIndex < frets.length;
                stringIndex++
        ) {
            int fret =
                    frets[stringIndex];

            if (fret < 0) {
                mutedStrings++;
                continue;
            }

            soundingStrings++;

            int midi =
                    STANDARD_TUNING_MIDI[stringIndex]
                            + fret;

            lowestMidi =
                    Math.min(
                            lowestMidi,
                            midi
                    );

            if (fret == 0) {
                openStrings++;
            } else {
                minimumPositiveFret =
                        Math.min(
                                minimumPositiveFret,
                                fret
                        );

                maximumFret =
                        Math.max(
                                maximumFret,
                                fret
                        );
            }
        }

        if (
                minimumPositiveFret
                        == Integer.MAX_VALUE
        ) {
            minimumPositiveFret = 0;
        }

        int fretSpan =
                minimumPositiveFret > 0
                        ? maximumFret
                        - minimumPositiveFret
                        : 0;

        return new PositionInfo(
                lowestMidi,
                minimumPositiveFret,
                fretSpan,
                openStrings,
                mutedStrings,
                soundingStrings
        );
    }

    private static int[] parseFrets(
            String fretString
    ) {
        if (fretString == null) {
            return null;
        }

        String[] values =
                fretString.trim()
                        .split("\\s+");

        if (values.length != 6) {
            return null;
        }

        int[] result =
                new int[6];

        try {
            for (
                    int index = 0;
                    index < values.length;
                    index++
            ) {
                String value =
                        values[index];

                if (
                        value.equalsIgnoreCase("X")
                                || value.equals("-1")
                ) {
                    result[index] = -1;
                } else {
                    result[index] =
                            Integer.parseInt(
                                    value
                            );
                }
            }

            return result;

        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static int getRootPitchClass(
            String chordName
    ) {
        if (
                chordName == null
                        || chordName.trim().isEmpty()
        ) {
            return -1;
        }

        String root =
                chordName.trim()
                        .split("\\s+")[0]
                        .replace("♯", "#")
                        .replace("♭", "b");

        switch (root) {
            case "C":
            case "B#":
                return 0;

            case "C#":
            case "Db":
                return 1;

            case "D":
                return 2;

            case "D#":
            case "Eb":
                return 3;

            case "E":
            case "Fb":
                return 4;

            case "F":
            case "E#":
                return 5;

            case "F#":
            case "Gb":
                return 6;

            case "G":
                return 7;

            case "G#":
            case "Ab":
                return 8;

            case "A":
                return 9;

            case "A#":
            case "Bb":
                return 10;

            case "B":
            case "Cb":
                return 11;

            default:
                return -1;
        }
    }

    public static final class Selection {

        private final String fretPositions;
        private final int baseFret;
        private final int firstFingerFret;
        private final boolean hasOpenString;

        Selection(
                String fretPositions,
                int baseFret,
                int firstFingerFret,
                boolean hasOpenString
        ) {
            this.fretPositions =
                    fretPositions;

            this.baseFret =
                    baseFret;

            this.firstFingerFret =
                    firstFingerFret;

            this.hasOpenString =
                    hasOpenString;
        }

        public String getFretPositions() {
            return fretPositions;
        }

        public int getBaseFret() {
            return baseFret;
        }

        public int getFirstFingerFret() {
            return firstFingerFret;
        }

        public boolean hasOpenString() {
            return hasOpenString;
        }
    }

    private static final class PositionInfo {

        final int lowestMidi;
        final int minimumPositiveFret;
        final int fretSpan;
        final int openStrings;
        final int mutedStrings;
        final int soundingStrings;

        PositionInfo(
                int lowestMidi,
                int minimumPositiveFret,
                int fretSpan,
                int openStrings,
                int mutedStrings,
                int soundingStrings
        ) {
            this.lowestMidi = lowestMidi;
            this.minimumPositiveFret =
                    minimumPositiveFret;
            this.fretSpan = fretSpan;
            this.openStrings = openStrings;
            this.mutedStrings = mutedStrings;
            this.soundingStrings =
                    soundingStrings;
        }
    }
}