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

public class HistoryAdapter
    extends RecyclerView.Adapter<HistoryAdapter.ViewHolder>
{

    private final List<ChordHistory> list = new ArrayList<>();
    private final List<String> documentIds = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat(
        "dd MMM yyyy, HH:mm",
        Locale.getDefault()
    );

    private OnItemClickListener listener;
    private OnDeleteListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(ChordHistory item);
    }

    public interface OnDeleteListener {
        void onDeleteClick(
            String documentId,
            String filePath,
            ChordHistory item
        );
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void setOnDeleteListener(OnDeleteListener l) {
        this.deleteListener = l;
    }

    /** Dipanggil dari Fragment setiap kali LiveData uiState berubah */
    public void updateData(
        List<ChordHistory> newItems,
        List<String> newDocIds
    ) {
        final List<ChordHistory> oldList = new ArrayList<>(list);
        final List<String> oldIds = new ArrayList<>(documentIds);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
            new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldList.size();
                }

                @Override
                public int getNewListSize() {
                    return newItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    return oldIds.get(oldPos).equals(newDocIds.get(newPos));
                }

                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    ChordHistory o = oldList.get(oldPos);
                    ChordHistory n = newItems.get(newPos);
                    return (
                        o.getTitle() != null &&
                        o.getTitle().equals(n.getTitle()) &&
                        o.getTimestamp() != null &&
                        o.getTimestamp().equals(n.getTimestamp())
                    );
                }
            }
        );

        list.clear();
        list.addAll(newItems);
        documentIds.clear();
        documentIds.addAll(newDocIds);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent,
        int viewType
    ) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
            R.layout.item_history_card,
            parent,
            false
        );
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChordHistory item = list.get(position);
        String docId = documentIds.get(position);
        String textShowResult = holder.itemView.getContext().getString(R.string.show_result);
        holder.tvShowResult.setText(textShowResult);

        holder.tvTitle.setText(
            item.getTitle() != null ? item.getTitle() : "Tanpa Judul"
        );
        holder.tvDate.setText(
            item.getTimestamp() != null
                ? sdf.format(item.getTimestamp().toDate())
                : "-"
        );

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(
                docId,
                item.getFilePath(),
                item
            );
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvTitle, tvDate, tvShowResult;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_history_title);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            btnDelete = itemView.findViewById(R.id.btn_delete_history);
            tvShowResult = itemView.findViewById(R.id.tv_show_result);
        }
    }
}
