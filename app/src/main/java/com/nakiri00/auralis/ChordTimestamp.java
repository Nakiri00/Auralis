package com.nakiri00.auralis;

import java.util.Objects;

public class ChordTimestamp {

    private double timeSeconds;
    private String chordName;

    /**
     * Diperlukan Firestore.
     */
    public ChordTimestamp() {
        this(0.0, "-");
    }

    public ChordTimestamp(
            double timeSeconds,
            String chordName
    ) {
        this.timeSeconds = timeSeconds;
        this.chordName =
                chordName != null
                        ? chordName
                        : "-";
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(double timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public String getChordName() {
        return chordName;
    }

    public void setChordName(String chordName) {
        this.chordName =
                chordName != null
                        ? chordName
                        : "-";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ChordTimestamp)) {
            return false;
        }

        ChordTimestamp other =
                (ChordTimestamp) object;

        return Double.compare(
                timeSeconds,
                other.timeSeconds
        ) == 0
                && Objects.equals(
                chordName,
                other.chordName
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                timeSeconds,
                chordName
        );
    }
}