package com.nakiri00.auralis;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryViewModel extends ViewModel {

    private static final String TAG = "HistoryViewModel";
    private static final int PAGE_SIZE = 5;
    private static final int SORT_DATE = 0;
    private static final int SORT_TITLE = 1;

    // Repository
    private final HistoryRepository historyRepository = new HistoryRepository();
    private ListenerRegistration listenerRegistration;

    // Internal datasets
    private final List<ChordHistory> masterList = new ArrayList<>();
    private final List<String> masterDocIds = new ArrayList<>();
    private final List<ChordHistory> filteredList = new ArrayList<>();
    private final List<String> filteredDocIds = new ArrayList<>();

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

    /** Dipanggil dari Fragment di onViewCreated. Guard agar tidak double-register. */
    public void startListening() {
        if (listenerRegistration != null) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        listenerRegistration = historyRepository.listenToHistory(
            uid,
            new HistoryRepository.HistoryLoadCallback() {
                @Override
                public void onUpdate(
                    List<ChordHistory> items,
                    List<String> docIds
                ) {
                    masterList.clear();
                    masterList.addAll(items);
                    masterDocIds.clear();
                    masterDocIds.addAll(docIds);
                    // false = jaga posisi halaman saat ini saat Firestore auto-refresh
                    applyFiltersAndPaginate(false);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Firestore snapshot error", e);
                }
            }
        );
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

    public void deleteItem(String docId, String filePath) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        historyRepository.deleteHistory(
            uid,
            docId,
            filePath,
            new HistoryRepository.OnDeleteListener() {
                @Override
                public void onSuccess() {
                    toastMessage.postValue("Item Removed");
                }

                @Override
                public void onError(Exception e) {
                    toastMessage.postValue("Failed to remove item");
                }
            }
        );
    }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    // ─── Filter + Sort + Paginate ───────────────────────────────────────────

    private void applyFiltersAndPaginate(boolean resetPage) {
        filteredList.clear();
        filteredDocIds.clear();

        // 1. Search
        String query = searchQuery.toLowerCase(Locale.getDefault());
        for (int i = 0; i < masterList.size(); i++) {
            String title =
                masterList.get(i).getTitle() != null
                    ? masterList
                          .get(i)
                          .getTitle()
                          .toLowerCase(Locale.getDefault())
                    : "";
            if (query.isEmpty() || title.contains(query)) {
                filteredList.add(masterList.get(i));
                filteredDocIds.add(masterDocIds.get(i));
            }
        }

        // 2. Sort
        if (sortType == SORT_DATE) {
            // Firestore sudah DESCENDING (newest first); sortAscending=true → balik ke oldest first
            if (sortAscending) {
                Collections.reverse(filteredList);
                Collections.reverse(filteredDocIds);
            }
        } else {
            // Sort by title, sinkronisasi kedua list via index
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < filteredList.size(); i++) indices.add(i);
            final boolean asc = sortAscending;
            indices.sort((a, b) -> {
                String tA =
                    filteredList.get(a).getTitle() != null
                        ? filteredList.get(a).getTitle()
                        : "";
                String tB =
                    filteredList.get(b).getTitle() != null
                        ? filteredList.get(b).getTitle()
                        : "";
                return asc
                    ? tA.compareToIgnoreCase(tB)
                    : tB.compareToIgnoreCase(tA);
            });
            List<ChordHistory> sortedH = new ArrayList<>();
            List<String> sortedIds = new ArrayList<>();
            for (int idx : indices) {
                sortedH.add(filteredList.get(idx));
                sortedIds.add(filteredDocIds.get(idx));
            }
            filteredList.clear();
            filteredList.addAll(sortedH);
            filteredDocIds.clear();
            filteredDocIds.addAll(sortedIds);
        }

        // 3. Pagination
        totalPages = Math.max(
            1,
            (int) Math.ceil((double) filteredList.size() / PAGE_SIZE)
        );
        currentPage = resetPage
            ? 0
            : Math.max(0, Math.min(currentPage, totalPages - 1));
        buildUiState();
    }

    private void buildUiState() {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filteredList.size());

        List<ChordHistory> pageItems = new ArrayList<>(
            filteredList.subList(start, end)
        );
        List<String> pageDocIds = new ArrayList<>(
            filteredDocIds.subList(start, end)
        );

        String emptyMsg = filteredList.isEmpty()
            ? (searchQuery.isEmpty()
                  ? "Squeaky clean!"
                  : "There are no matches in the history for \"" + searchQuery + "\"")
            : "";

        boolean dateActive = (sortType == SORT_DATE);
        String dateLabel = dateActive
            ? (sortAscending ? "Oldest" : "Newest")
            : "Newest";
        String titleLabel = !dateActive
            ? (sortAscending ? "Title A-Z" : "Title Z-A")
            : "Title A-Z";

        uiState.setValue(
            new HistoryUiState(
                pageItems,
                pageDocIds,
                filteredList.isEmpty(),
                emptyMsg,
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
        super.onCleared();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
