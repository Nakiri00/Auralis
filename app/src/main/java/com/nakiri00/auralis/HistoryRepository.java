package com.nakiri00.auralis;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import android.content.Context;

import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryRepository {

    private final Context applicationContext;

    public HistoryRepository(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Context is required"
            );
        }

        applicationContext =
                context.getApplicationContext();
    }
    private static final String TAG =
            "HistoryRepository";

    public interface OnSaveListener {
        void onSuccess(boolean isUpdate);

        void onError(Exception error);
    }

    public interface HistoryLoadCallback {
        void onUpdate(List<ChordHistory> items);

        void onError(Exception error);
    }

    public interface OnDeleteListener {
        void onSuccess();

        void onError(Exception error);
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
                        (snapshot, error) -> {
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

                            if (snapshot != null) {
                                for (
                                        DocumentSnapshot document
                                        : snapshot.getDocuments()
                                ) {
                                    ChordHistory history =
                                            document.toObject(
                                                    ChordHistory.class
                                            );

                                    if (history == null) {
                                        continue;
                                    }

                                    history.setHistoryId(
                                            document.getId()
                                    );

                                    migrateLegacyFields(
                                            document,
                                            history
                                    );

                                    items.add(history);
                                }
                            }

                            boolean authoritativeSnapshot =
                                    snapshot != null
                                            && !snapshot.getMetadata()
                                            .isFromCache()
                                            && !snapshot.getMetadata()
                                            .hasPendingWrites();

                            if (authoritativeSnapshot) {
                                Set<String> referencedFiles =
                                        new HashSet<>();

                                for (ChordHistory item : items) {
                                    String fileName =
                                            item.getAudioFileName();

                                    if (
                                            fileName != null
                                                    && !fileName
                                                    .trim()
                                                    .isEmpty()
                                    ) {
                                        referencedFiles.add(
                                                fileName
                                        );
                                    }
                                }

                                HistoryAudioStorage
                                        .reconcileWithHistory(
                                                applicationContext,
                                                referencedFiles
                                        );
                            }

                            callback.onUpdate(items);
                        }
                );
    }

    /**
     * Migrasi otomatis:
     *
     * 1. filePath absolut menjadi audioFileName.
     * 2. result string menjadi chords + keyIndex.
     * 3. result dihapus setelah migrasi berhasil.
     */
    private void migrateLegacyFields(
            DocumentSnapshot document,
            ChordHistory history
    ) {
        Map<String, Object> updates =
                new HashMap<>();

        migrateLegacyAudioPath(
                document,
                history,
                updates
        );

        migrateLegacyResult(
                document,
                history,
                updates
        );

        if (updates.isEmpty()) {
            return;
        }

        document.getReference()
                .update(updates)
                .addOnFailureListener(error ->
                        Log.e(
                                TAG,
                                "Failed to migrate history "
                                        + document.getId(),
                                error
                        )
                );
    }

    private void migrateLegacyAudioPath(
            DocumentSnapshot document,
            ChordHistory history,
            Map<String, Object> updates
    ) {
        if (!document.contains("filePath")) {
            return;
        }

        String legacyPath =
                document.getString("filePath");

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

            updates.put(
                    "audioFileName",
                    safeFileName
            );
        }

        updates.put(
                "filePath",
                FieldValue.delete()
        );
    }

    private void migrateLegacyResult(
            DocumentSnapshot document,
            ChordHistory history,
            Map<String, Object> updates
    ) {
        List<ChordTimestamp> structuredChords =
                history.getChords();

        Integer structuredKeyIndex =
                history.getKeyIndex();

        boolean originallyHadChords =
                structuredChords != null;

        boolean originallyHadKeyIndex =
                structuredKeyIndex != null;

        String legacyResult =
                document.getString("result");

        HistoryLegacyParser.ParsedHistory parsed =
                null;

        if (
                legacyResult != null
                        && (
                        !originallyHadChords
                                || !originallyHadKeyIndex
                )
        ) {
            parsed =
                    HistoryLegacyParser.parse(
                            legacyResult
                    );
        }

        if (!originallyHadChords) {
            structuredChords =
                    parsed != null
                            ? parsed.getChords()
                            : new ArrayList<>();

            history.setChords(
                    structuredChords
            );

            updates.put(
                    "chords",
                    new ArrayList<>(
                            structuredChords
                    )
            );
        }

        if (!originallyHadKeyIndex) {
            structuredKeyIndex =
                    parsed != null
                            ? parsed.getKeyIndex()
                            : KeyDetector.UNKNOWN_KEY_INDEX;

            history.setKeyIndex(
                    structuredKeyIndex
            );

            updates.put(
                    "keyIndex",
                    structuredKeyIndex
            );
        }

        if (legacyResult == null) {
            return;
        }

        boolean alreadyStructured =
                originallyHadChords
                        && originallyHadKeyIndex;

        boolean migrationWasSafe =
                parsed != null
                        && parsed.isSafeToDeleteSource();

        if (
                alreadyStructured
                        || migrationWasSafe
        ) {
            updates.put(
                    "result",
                    FieldValue.delete()
            );
        } else {
            Log.w(
                    TAG,
                    "Legacy result retained because "
                            + "some lines could not be parsed: "
                            + document.getId()
            );
        }
    }

    public void saveOrUpdateHistory(
            String historyId,
            String title,
            String audioFileName,
            List<ChordTimestamp> chords,
            int keyIndex,
            OnSaveListener listener
    ) {
        String uid =
                FirebaseAuth
                        .getInstance()
                        .getUid();

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
                                && !title.trim().isEmpty()
                                ? title
                                : "Audio",
                        audioFileName,
                        chords != null
                                ? new ArrayList<>(chords)
                                : new ArrayList<>(),
                        KeyDetector.isValidKeyIndex(
                                keyIndex
                        )
                                ? keyIndex
                                : KeyDetector.UNKNOWN_KEY_INDEX,
                        Timestamp.now()
                );

        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snapshot =
                            transaction.get(
                                    historyDocument
                            );

                    boolean isUpdate =
                            snapshot.exists();

                    /*
                     * set() tanpa merge mengganti dokumen lama.
                     * Karena ChordHistory tidak lagi memiliki
                     * field result, field tersebut ikut hilang.
                     */
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
                        listener.onSuccess(
                                isUpdate
                        );
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
            notifyDeleteError(
                    listener,
                    new IllegalArgumentException(
                            "User ID is required"
                    )
            );
            return;
        }

        if (
                !HistoryIdentity.isUsable(
                        historyId
                )
        ) {
            notifyDeleteError(
                    listener,
                    new IllegalArgumentException(
                            "History ID is invalid"
                    )
            );
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

                    notifyDeleteError(
                            listener,
                            error
                    );
                });
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