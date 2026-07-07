package com.example.musicchords;

import android.util.Log;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryRepository {

    private static final String TAG = "HistoryRepository";

    // ─── Interfaces ─────────────────────────────────────────────────────────

    public interface OnSaveListener {
        void onSuccess(boolean isUpdate);
        void onError(Exception e);
    }

    public interface HistoryLoadCallback {
        void onUpdate(List<ChordHistory> items, List<String> docIds);
        void onError(Exception e);
    }

    public interface OnDeleteListener {
        void onSuccess();
        void onError(Exception e);
    }

    // ─── Listen (real-time) ─────────────────────────────────────────────────

    /**
     * Mendaftarkan Firestore snapshot listener.
     * Kembalikan ListenerRegistration agar ViewModel bisa menghentikannya di onCleared().
     */
    public ListenerRegistration listenToHistory(String uid, HistoryLoadCallback callback) {
        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Snapshot error", error);
                        callback.onError(error);
                        return;
                    }
                    List<ChordHistory> items = new ArrayList<>();
                    List<String> docIds = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            ChordHistory h = doc.toObject(ChordHistory.class);
                            if (h != null) {
                                items.add(h);
                                docIds.add(doc.getId());
                            }
                        }
                    }
                    callback.onUpdate(items, docIds);
                });
    }

    // ─── Delete ─────────────────────────────────────────────────────────────

    public void deleteHistory(String uid, String docId, String filePath, OnDeleteListener listener) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("history")
                .document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (filePath != null && !filePath.isEmpty()) {
                        File f = new File(filePath);
                        if (f.exists()) f.delete();
                    }
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete history", e);
                    if (listener != null) listener.onError(e);
                });
    }

    // ─── Save / Update (dipakai oleh HomeViewModel) ─────────────────────────

    public void saveOrUpdateHistory(String title, String path, String resultText, OnSaveListener listener) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not authenticated"));
            return;
        }
        CollectionReference historyRef = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("history");

        historyRef.whereEqualTo("title", title).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception e = task.getException() != null
                        ? task.getException()
                        : new Exception("Unknown Firestore error");
                Log.e(TAG, "Error checking history", e);
                if (listener != null) listener.onError(e);
                return;
            }
            if (!task.getResult().isEmpty()) {
                DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                historyRef.document(doc.getId())
                        .update("timestamp", new Timestamp(new Date()), "result", resultText, "filePath", path)
                        .addOnSuccessListener(aVoid -> { if (listener != null) listener.onSuccess(true); })
                        .addOnFailureListener(e -> { if (listener != null) listener.onError(e); });
            } else {
                historyRef.add(new ChordHistory(title, path, resultText, new Timestamp(new Date())))
                        .addOnSuccessListener(ref -> { if (listener != null) listener.onSuccess(false); })
                        .addOnFailureListener(e -> { if (listener != null) listener.onError(e); });
            }
        });
    }
}
