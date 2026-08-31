package com.nakiri00.auralis;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.List;

public class ChordHistory {

    @Exclude
    private String historyId;
    private String title;
    private String audioFileName;
    private String result;
    private List<ChordTimestamp> chords;
    private Integer keyIndex;
    private Timestamp timestamp;

    /**
     * Diperlukan Firestore.
     */
    public ChordHistory() {
    }

//    public ChordHistory(
//            String historyId,
//            String title,
//            String result,
//            String audioFileName,
//            List<ChordTimestamp> chords,
//            Integer keyIndex,
//            Timestamp timestamp
//    ) {
//        this.historyId = historyId;
//        this.title = title;
//        this.result = result;
//        this.audioFileName = audioFileName;
//        this.chords = chords;
//        this.keyIndex = keyIndex;
//        this.timestamp = timestamp;
//    }

    /**
     * Constructor kompatibilitas jika masih ada pemanggilan lama.
     */
    public ChordHistory(
            String historyId,
            String title,
            String audioFileName,
            String result,
            List<ChordTimestamp> chords,
            Integer keyIndex,
            Timestamp timestamp
    ) {
        this.historyId = historyId;
        this.title = title;
        this.audioFileName = audioFileName;
        this.result = result;
        this.chords = chords;
        this.keyIndex = keyIndex;
        this.timestamp = timestamp;
    }

    @Exclude
    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getAudioFileName() {
        return audioFileName;
    }

    public void setAudioFileName(
            String audioFileName
    ) {
        this.audioFileName = audioFileName;
    }

    public List<ChordTimestamp> getChords() {
        return chords;
    }

    public void setChords(
            List<ChordTimestamp> chords
    ) {
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

    public void setTimestamp(
            Timestamp timestamp
    ) {
        this.timestamp = timestamp;
    }
}