package com.nakiri00.auralis;

import java.util.List;

public class HistoryUiState {

    public final List<ChordHistory> items;

    public final boolean isEmpty;
    public final String emptyMessage;

    public final boolean showPagination;
    public final int currentPage;
    public final int totalPages;

    public final boolean dateChipActive;
    public final String dateChipLabel;
    public final String titleChipLabel;

    public HistoryUiState(
            List<ChordHistory> items,
            boolean isEmpty,
            String emptyMessage,
            boolean showPagination,
            int currentPage,
            int totalPages,
            boolean dateChipActive,
            String dateChipLabel,
            String titleChipLabel
    ) {
        this.items = items;
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