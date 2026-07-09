package com.example.musicchords;

import java.util.List;

/**
 * Satu kelompok chord: nama chord + semua variasi posisi jari.
 * Contoh: "C major" dengan 4 posisi fingering yang berbeda.
 */
public class ChordGroup {
    private final String chordName;
    private final List<String> positions;
    private final int audioResId;

    // UBAH INI: Jadikan List agar bisa nyimpan basefret untuk tiap posisi
    private final List<Integer> baseFrets;

    public ChordGroup(String chordName, List<String> positions, int audioResId, List<Integer> baseFrets) {
        this.chordName = chordName;
        this.positions = positions;
        this.audioResId = audioResId;
        this.baseFrets = baseFrets;
    }

    public String getChordName() { return chordName; }
    public List<String> getPositions() { return positions; }
    public int getAudioResId() { return audioResId; }
    public List<Integer> getBaseFrets() { return baseFrets; } // Getter baru
}
