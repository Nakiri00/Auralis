package com.nakiri00.auralis;

public class ChordTimestamp {
    private double timeSeconds;
    private String chordName;

    // Required by Firestore when reading structured chord history.
    public ChordTimestamp() {
        this(0.0, "-");
    }

    public ChordTimestamp(double timeSeconds, String chordName) {
        this.timeSeconds = timeSeconds;
        this.chordName = chordName != null ? chordName : "-";
    }

    public double getTimeSeconds() { return timeSeconds; }
    public String getChordName() { return chordName; }

    public void setTimeSeconds(double timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public void setChordName(String chordName) {
        this.chordName = chordName != null ? chordName : "-";
    }
}
