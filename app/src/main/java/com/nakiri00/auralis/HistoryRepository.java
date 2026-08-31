package com.nakiri00.auralis;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.FieldValue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryRepository {

    private static final String TAG =
            "HistoryRepository";

    public interface OnSaveListener {
        void onSuccess(boolean isUpdate);
        void onError(Exception e);
    }

    public interface HistoryLoadCallback {
        void onUpdate(
                List<ChordHistory> items
        );

        void onError(Exception e);
    }

    public interface OnDeleteListener {
        void onSuccess();
        void onError(Exception e);
    }

    public ListenerRegistration listenToHistory(
            String uid,
            HistoryLoadCallback callback
    ) {
        return FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(uid)
                .collection("history")
                .orderBy(
                        "timestamp",
                        Query.Direction.DESCENDING
                )
                .addSnapshotListener(
                        (value, error) -> {
                            if (error != null) {
                                Log.e(
                                        TAG,
                                        "Snapshot error",
                                        error
                                );

                                callback.onError(error);
                                return;
                            }

                            List<ChordHistory> items =
                                    new ArrayList<>();

                            if (value != null) {
                                for (
                                        DocumentSnapshot document
                                        : value.getDocuments()
                                ) {
                                    ChordHistory history =
                                            document.toObject(
                                                    ChordHistory.class
                                            );

                                    if (history != null) {
                                        /*
                                         * Firestore document ID menjadi
                                         * identity object.
                                         *
                                         * Ini juga otomatis memigrasikan
                                         * history lama saat dibaca.
                                         */
                                        history.setHistoryId(
                                                document.getId()
                                        );

                                        migrateLegacyFilePath(
                                                document,
                                                history
                                        );

                                        items.add(history);
                                    }
                                }
                            }

                            callback.onUpdate(items);
                        }
                );
    }

    private void migrateLegacyFilePath(
            DocumentSnapshot document,
            ChordHistory history
    ) {
        if (!document.contains("filePath")) {
            return;
        }

        String legacyPath =
                document.getString(
                        "filePath"
                );

        String safeFileName =
                HistoryAudioStorage
                        .sanitizeLegacyFileName(
                                legacyPath
                        );

        if (
                history.getAudioFileName() == null
                        && safeFileName != null
        ) {
            history.setAudioFileName(
                    safeFileName
            );
        }

        Map<String, Object> updates =
                new HashMap<>();

        updates.put(
                "filePath",
                FieldValue.delete()
        );

        if (safeFileName != null) {
            updates.put(
                    "audioFileName",
                    safeFileName
            );
        }

        document.getReference()
                .update(updates)
                .addOnFailureListener(error ->
                        Log.e(
                                TAG,
                                "Failed to migrate legacy "
                                        + "audio path for "
                                        + document.getId(),
                                error
                        )
                );
    }

    public void saveOrUpdateHistory(
            String historyId,
            String title,
            String audioFileName,
            String resultText,
            List<ChordTimestamp> chords,
            int keyIndex,
            OnSaveListener listener
    ) {
        String uid =
                FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            notifySaveError(
                    listener,
                    new IllegalStateException(
                            "User not authenticated"
                    )
            );
            return;
        }

        String resolvedHistoryId =
                HistoryIdentity.normalizeOrCreate(
                        historyId
                );

        FirebaseFirestore firestore =
                FirebaseFirestore.getInstance();

        DocumentReference historyDocument =
                firestore
                        .collection("users")
                        .document(uid)
                        .collection("history")
                        .document(resolvedHistoryId);

        ChordHistory history =
                new ChordHistory(
                        resolvedHistoryId,
                        title != null
                                ? title
                                : "Audio",
                        audioFileName,
                        resultText,
                        chords != null
                                ? new ArrayList<>(chords)
                                : new ArrayList<>(),
                        keyIndex,
                        new Timestamp(new Date())
                );

        /*
         * Transaction membuat pengecekan exists dan write
         * berjalan atomik.
         */
        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snapshot =
                            transaction.get(
                                    historyDocument
                            );

                    boolean isUpdate =
                            snapshot.exists();

                    transaction.set(
                            historyDocument,
                            history
                    );

                    return isUpdate;
                })
                .addOnSuccessListener(isUpdate -> {
                    Log.d(
                            TAG,
                            isUpdate
                                    ? "History updated: "
                                    + resolvedHistoryId
                                    : "History created: "
                                    + resolvedHistoryId
                    );

                    if (listener != null) {
                        listener.onSuccess(isUpdate);
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e(
                            TAG,
                            "Failed to save history: "
                                    + resolvedHistoryId,
                            error
                    );

                    notifySaveError(
                            listener,
                            error
                    );
                });
    }

    public void deleteHistory(
            String uid,
            String historyId,
            File localAudioFile,
            OnDeleteListener listener
    ) {
        if (
                uid == null
                        || uid.trim().isEmpty()
        ) {
            if (listener != null) {
                listener.onError(
                        new IllegalArgumentException(
                                "User ID is required"
                        )
                );
            }
            return;
        }

        if (
                !HistoryIdentity.isUsable(
                        historyId
                )
        ) {
            if (listener != null) {
                listener.onError(
                        new IllegalArgumentException(
                                "History ID is invalid"
                        )
                );
            }
            return;
        }

        FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(uid)
                .collection("history")
                .document(historyId)
                .delete()
                .addOnSuccessListener(unused -> {
                    HistoryAudioStorage.delete(
                            localAudioFile
                    );

                    if (listener != null) {
                        listener.onSuccess();
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e(
                            TAG,
                            "Failed to delete history",
                            error
                    );

                    if (listener != null) {
                        listener.onError(error);
                    }
                });
    }

    private void deleteLocalAudio(
            String filePath
    ) {
        if (
                filePath == null
                        || filePath.trim().isEmpty()
        ) {
            return;
        }

        File file = new File(filePath);

        if (
                file.exists()
                        && !file.delete()
        ) {
            Log.w(
                    TAG,
                    "Failed to delete local audio: "
                            + file.getAbsolutePath()
            );
        }
    }

    private void notifySaveError(
            OnSaveListener listener,
            Exception error
    ) {
        if (listener != null) {
            listener.onError(error);
        }
    }

    private void notifyDeleteError(
            OnDeleteListener listener,
            Exception error
    ) {
        if (listener != null) {
            listener.onError(error);
        }
    }
}