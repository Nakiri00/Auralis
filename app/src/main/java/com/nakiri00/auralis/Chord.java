package com.nakiri00.auralis;

public class Chord {
    private String chordName;
    private String strings; // Posisi senar (Fret)
    private int imageResId; // Gambar (nanti)
    private int audioResId; // Suara (nanti)

    public Chord(String chordName, String strings, int imageResId, int audioResId) {
        this.chordName = chordName;
        this.strings = strings;
        this.imageResId = imageResId;
        this.audioResId = audioResId;
    }

    public String getChordName() {
        return chordName;
    }

    public String getStrings() {
        return strings;
    }

    public int getImageResId() {
        return imageResId;
    }

    public int getAudioResId() {
        return audioResId;
    }
}