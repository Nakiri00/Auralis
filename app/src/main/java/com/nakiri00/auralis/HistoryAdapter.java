package com.nakiri00.auralis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class HistoryAdapter
        extends RecyclerView.Adapter<
        HistoryAdapter.ViewHolder
        > {

    private final List<ChordHistory> items =
            new ArrayList<>();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat(
                    "dd MMM yyyy, HH:mm",
                    Locale.getDefault()
            );

    private OnItemClickListener itemClickListener;
    private OnDeleteListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(ChordHistory item);
    }

    public interface OnDeleteListener {
        void onDeleteClick(ChordHistory item);
    }

    public void setOnItemClickListener(
            OnItemClickListener listener
    ) {
        this.itemClickListener = listener;
    }

    public void setOnDeleteListener(
            OnDeleteListener listener
    ) {
        this.deleteListener = listener;
    }

    public void updateData(
            List<ChordHistory> newItems
    ) {
        List<ChordHistory> safeNewItems =
                newItems != null
                        ? new ArrayList<>(newItems)
                        : new ArrayList<>();

        List<ChordHistory> oldItems =
                new ArrayList<>(items);

        DiffUtil.DiffResult difference =
                DiffUtil.calculateDiff(
                        new DiffUtil.Callback() {
                            @Override
                            public int getOldListSize() {
                                return oldItems.size();
                            }

                            @Override
                            public int getNewListSize() {
                                return safeNewItems.size();
                            }

                            @Override
                            public boolean areItemsTheSame(
                                    int oldPosition,
                                    int newPosition
                            ) {
                                return Objects.equals(
                                        oldItems
                                                .get(oldPosition)
                                                .getHistoryId(),
                                        safeNewItems
                                                .get(newPosition)
                                                .getHistoryId()
                                );
                            }

                            @Override
                            public boolean areContentsTheSame(
                                    int oldPosition,
                                    int newPosition
                            ) {
                                ChordHistory oldItem =
                                        oldItems.get(
                                                oldPosition
                                        );

                                ChordHistory newItem =
                                        safeNewItems.get(
                                                newPosition
                                        );

                                return Objects.equals(
                                        oldItem.getTitle(),
                                        newItem.getTitle()
                                ) && Objects.equals(
                                        oldItem.getAudioFileName(),
                                        newItem.getAudioFileName()
                                ) && Objects.equals(
                                        oldItem.getChords(),
                                        newItem.getChords()
                                ) && Objects.equals(
                                        oldItem.getKeyIndex(),
                                        newItem.getKeyIndex()
                                ) && Objects.equals(
                                        oldItem.getTimestamp(),
                                        newItem.getTimestamp()
                                );
                            }
                        }
                );

        items.clear();
        items.addAll(safeNewItems);

        difference.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_history_card,
                                parent,
                                false
                        );

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        ChordHistory item =
                items.get(position);

        holder.showResult.setText(
                holder.itemView
                        .getContext()
                        .getString(
                                R.string.show_result
                        )
        );

        holder.title.setText(
                item.getTitle() != null
                        && !item.getTitle().trim().isEmpty()
                        ? item.getTitle()
                        : "Tanpa Judul"
        );

        holder.date.setText(
                item.getTimestamp() != null
                        ? dateFormat.format(
                        item.getTimestamp()
                                .toDate()
                )
                        : "-"
        );

        holder.itemView.setOnClickListener(view -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(item);
            }
        });

        holder.deleteButton.setOnClickListener(view -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView date;
        final TextView showResult;
        final ImageButton deleteButton;

        ViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            title = itemView.findViewById(
                    R.id.tv_history_title
            );

            date = itemView.findViewById(
                    R.id.tv_history_date
            );

            showResult = itemView.findViewById(
                    R.id.tv_show_result
            );

            deleteButton = itemView.findViewById(
                    R.id.btn_delete_history
            );
        }
    }
}