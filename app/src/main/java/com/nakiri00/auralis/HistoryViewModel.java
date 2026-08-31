package com.nakiri00.auralis;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.io.File;

public class HistoryViewModel extends AndroidViewModel {

    private static final String TAG = "HistoryViewModel";
    private static final int PAGE_SIZE = 5;
    private static final int SORT_DATE = 0;
    private static final int SORT_TITLE = 1;


    // Repository
    private final HistoryRepository historyRepository;

    public HistoryViewModel(
            @NonNull Application application
    ) {
        super(application);

        historyRepository =
                new HistoryRepository(
                        application.getApplicationContext()
                );

        firebaseAuth =
                FirebaseAuth.getInstance();
    }
    private ListenerRegistration listenerRegistration;

    // Internal datasets
    private final List<ChordHistory> masterList =
            new ArrayList<>();

    private final List<ChordHistory> filteredList =
            new ArrayList<>();

    // Filter / sort / pagination state
    private int currentPage = 0;
    private int totalPages = 1;
    private String searchQuery = "";
    private int sortType = SORT_DATE;
    private boolean sortAscending = false;

    // LiveData
    private final MutableLiveData<HistoryUiState> uiState =
        new MutableLiveData<>();
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>(
        null
    );

    public LiveData<HistoryUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    // ─── Listening ─────────────────────────────────────────────────────────
    private final FirebaseAuth firebaseAuth;

    private boolean authListenerRegistered;
    private String listeningUid;

    private void startFirestoreListener(
            String uid
    ) {
        if (
                uid == null
                        || uid.trim().isEmpty()
        ) {
            return;
        }

        if (
                listenerRegistration != null
                        && uid.equals(listeningUid)
        ) {
            return;
        }

        stopFirestoreListener();

        listeningUid = uid;

        listenerRegistration =
                historyRepository.listenToHistory(
                        uid,
                        new HistoryRepository.HistoryLoadCallback() {
                            @Override
                            public void onUpdate(
                                    List<ChordHistory> items
                            ) {
                                masterList.clear();

                                if (items != null) {
                                    masterList.addAll(
                                            items
                                    );
                                }

                                applyFiltersAndPaginate(
                                        false
                                );
                            }

                            @Override
                            public void onError(
                                    Exception error
                            ) {
                                Log.e(
                                        TAG,
                                        "Firestore snapshot error",
                                        error
                                );

                                toastMessage.postValue(
                                        "History cloud sementara "
                                                + "tidak tersedia"
                                );
                            }
                        }
                );
    }

    private void stopFirestoreListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }

        listeningUid = null;
    }

    private final FirebaseAuth.AuthStateListener authStateListener =
            auth -> {
                FirebaseUser user =
                        auth.getCurrentUser();

                if (user == null) {
                    stopFirestoreListener();

                    /*
                     * Jangan tampilkan data dari akun anonim lama.
                     */
                    masterList.clear();
                    applyFiltersAndPaginate(true);
                    return;
                }

                startFirestoreListener(
                        user.getUid()
                );
            };

    /** Dipanggil dari Fragment di onViewCreated. Guard agar tidak double-register. */
    public void startListening() {
        if (!authListenerRegistered) {
            authListenerRegistered = true;

            /*
             * Listener langsung menerima kondisi auth saat ini,
             * termasuk null ketika aplikasi sedang offline.
             */
            firebaseAuth.addAuthStateListener(
                    authStateListener
            );
        }

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            startFirestoreListener(
                    currentUser.getUid()
            );
        } else {
            /*
             * History cloud kosong sementara, tetapi halaman
             * Home dan Library tetap dapat digunakan.
             */
            applyFiltersAndPaginate(false);
        }
    }

    // ─── User Actions ───────────────────────────────────────────────────────

    public void setSearchQuery(String query) {
        searchQuery = (query != null) ? query.trim() : "";
        applyFiltersAndPaginate(true);
    }

    public void setSortDate() {
        if (sortType == SORT_DATE) sortAscending = !sortAscending;
        else {
            sortType = SORT_DATE;
            sortAscending = false;
        }
        applyFiltersAndPaginate(true);
    }

    public void setSortTitle() {
        if (sortType == SORT_TITLE) sortAscending = !sortAscending;
        else {
            sortType = SORT_TITLE;
            sortAscending = true;
        }
        applyFiltersAndPaginate(true);
    }

    public void goToNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            buildUiState();
        }
    }

    public void goToPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            buildUiState();
        }
    }

    public void deleteItem(
            String historyId,
            File localAudioFile
    ) {
        String uid =
                FirebaseAuth.getInstance()
                        .getUid();

        if (uid == null) {
            toastMessage.postValue(
                    "User not authenticated"
            );
            return;
        }

        historyRepository.deleteHistory(
                uid,
                historyId,
                localAudioFile,
                new HistoryRepository.OnDeleteListener() {
                    @Override
                    public void onSuccess() {
                        toastMessage.postValue(
                                "Item Removed"
                        );
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        Log.e(
                                TAG,
                                "Failed to remove history",
                                error
                        );

                        toastMessage.postValue(
                                "Failed to remove item"
                        );
                    }
                }
        );
    }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    // ─── Filter + Sort + Paginate ───────────────────────────────────────────

    private void applyFiltersAndPaginate(
            boolean resetPage
    ) {
        filteredList.clear();

        String query =
                searchQuery.toLowerCase(
                        Locale.getDefault()
                );

        for (ChordHistory history : masterList) {
            String title =
                    history.getTitle() != null
                            ? history
                            .getTitle()
                            .toLowerCase(
                                    Locale.getDefault()
                            )
                            : "";

            if (
                    query.isEmpty()
                            || title.contains(query)
            ) {
                filteredList.add(history);
            }
        }

        if (sortType == SORT_DATE) {
            if (sortAscending) {
                Collections.reverse(
                        filteredList
                );
            }
        } else {
            final boolean ascending =
                    sortAscending;

            filteredList.sort(
                    (first, second) -> {
                        String firstTitle =
                                first.getTitle() != null
                                        ? first.getTitle()
                                        : "";

                        String secondTitle =
                                second.getTitle() != null
                                        ? second.getTitle()
                                        : "";

                        return ascending
                                ? firstTitle.compareToIgnoreCase(
                                secondTitle
                        )
                                : secondTitle.compareToIgnoreCase(
                                firstTitle
                        );
                    }
            );
        }

        totalPages = Math.max(
                1,
                (int) Math.ceil(
                        (double) filteredList.size()
                                / PAGE_SIZE
                )
        );

        currentPage = resetPage
                ? 0
                : Math.max(
                0,
                Math.min(
                        currentPage,
                        totalPages - 1
                )
        );

        buildUiState();
    }

    private void buildUiState() {
        int start =
                currentPage * PAGE_SIZE;

        int end =
                Math.min(
                        start + PAGE_SIZE,
                        filteredList.size()
                );

        List<ChordHistory> pageItems =
                new ArrayList<>(
                        filteredList.subList(
                                start,
                                end
                        )
                );

        String emptyMessage =
                filteredList.isEmpty()
                        ? (
                        searchQuery.isEmpty()
                                ? "Squeaky clean!"
                                : "There are no matches "
                                + "in the history for \""
                                + searchQuery
                                + "\""
                )
                        : "";

        boolean dateActive =
                sortType == SORT_DATE;

        String dateLabel =
                dateActive
                        ? (
                        sortAscending
                                ? "Oldest"
                                : "Newest"
                )
                        : "Newest";

        String titleLabel =
                !dateActive
                        ? (
                        sortAscending
                                ? "Title A-Z"
                                : "Title Z-A"
                )
                        : "Title A-Z";

        uiState.setValue(
                new HistoryUiState(
                        pageItems,
                        filteredList.isEmpty(),
                        emptyMessage,
                        totalPages > 1,
                        currentPage,
                        totalPages,
                        dateActive,
                        dateLabel,
                        titleLabel
                )
        );
    }

    @Override
    protected void onCleared() {
        stopFirestoreListener();

        if (authListenerRegistered) {
            firebaseAuth.removeAuthStateListener(
                    authStateListener
            );

            authListenerRegistered = false;
        }

        super.onCleared();
    }
}
