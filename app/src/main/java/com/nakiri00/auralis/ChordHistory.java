package com.nakiri00.auralis;

import com.google.firebase.Timestamp;

public class ChordHistory {
    private String title;
    private String filePath;
    private String result;
    private Timestamp timestamp;

    // Diperlukan constructor kosong untuk Firestore
    public ChordHistory() {}

    public ChordHistory(String title, String filePath, String result, Timestamp timestamp) {
        this.title = title;
        this.filePath = filePath;
        this.result = result;
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

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}