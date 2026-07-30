package com.nakiri00.auralis;

import java.util.List;

/**
 * Satu objek yang mendeskripsikan seluruh tampilan HistoryFragment.
 * HistoryViewModel mempost objek ini setiap kali state berubah,
 * dan Fragment cukup mem-bind nilai-nilainya ke View.
 */
public class HistoryUiState {
    public final List<ChordHistory> items;
    public final List<String> docIds;

    // Empty state
    public final boolean isEmpty;
    public final String emptyMessage;

    // Pagination
    public final boolean showPagination;
    public final int currentPage;   // 0-based
    public final int totalPages;

    // Chips
    public final boolean dateChipActive;
    public final String dateChipLabel;
    public final String titleChipLabel;

    public HistoryUiState(List<ChordHistory> items, List<String> docIds,
                          boolean isEmpty, String emptyMessage,
                          boolean showPagination, int currentPage, int totalPages,
                          boolean dateChipActive, String dateChipLabel, String titleChipLabel) {
        this.items = items;
        this.docIds = docIds;
        this.isEmpty = isEmpty;
        this.emptyMessage = emptyMessage;
        this.showPagination = showPagination;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.dateChipActive = dateChipActive;
        this.dateChipLabel = dateChipLabel;
        this.titleChipLabel = titleChipLabel;
    }
}
