package com.nakiri00.auralis;

public class ChordTimestamp {
    private final double timeSeconds;
    private final String chordName;

    public ChordTimestamp(double timeSeconds, String chordName) {
        this.timeSeconds = timeSeconds;
        this.chordName = chordName != null ? chordName : "-";
    }

    public double getTimeSeconds() { return timeSeconds; }
    public String getChordName() { return chordName; }
}
