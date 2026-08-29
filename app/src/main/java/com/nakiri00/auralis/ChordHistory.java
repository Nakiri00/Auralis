package com.nakiri00.auralis;

import com.google.firebase.Timestamp;
import java.util.List;

public class ChordHistory {
    private String title;
    private String filePath;
    private String result;
    private List<ChordTimestamp> chords;
    private Integer keyIndex;
    private Timestamp timestamp;

    // Diperlukan constructor kosong untuk Firestore
    public ChordHistory() {}

    public ChordHistory(
            String title,
            String filePath,
            String result,
            List<ChordTimestamp> chords,
            Integer keyIndex,
            Timestamp timestamp
    ) {
        this.title = title;
        this.filePath = filePath;
        this.result = result;
        this.chords = chords;
        this.keyIndex = keyIndex;
        this.timestamp = timestamp;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<ChordTimestamp> getChords() {
        return chords;
    }

    public void setChords(List<ChordTimestamp> chords) {
        this.chords = chords;
    }

    public Integer getKeyIndex() {
        return keyIndex;
    }

    public void setKeyIndex(Integer keyIndex) {
        this.keyIndex = keyIndex;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}
