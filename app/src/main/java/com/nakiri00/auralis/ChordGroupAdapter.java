package com.nakiri00.auralis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChordGroupAdapter
    extends RecyclerView.Adapter<ChordGroupAdapter.GroupViewHolder>
{


    public interface OnPlayListener {
        void onPlay(ChordGroup group);
    }

    private final List<ChordGroup> groups = new ArrayList<>();
    private OnPlayListener playListener;

    public void setOnPlayListener(OnPlayListener l) {
        this.playListener = l;
    }

    public void updateData(List<ChordGroup> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent,
        int viewType
    ) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
            R.layout.item_chord_group,
            parent,
            false
        );
        return new GroupViewHolder(v);
    }

    @Override
    public void onBindViewHolder(
        @NonNull GroupViewHolder holder,
        int position
    ) {
        ChordGroup group = groups.get(position);

        // Header
        holder.tvChordName.setText(group.getChordName());
        holder.btnPlay.setOnClickListener(v -> {
            if (playListener != null) playListener.onPlay(group);
        });

        holder.btnPlay.setVisibility(
            group.getAudioResId() != 0 ? View.VISIBLE : View.GONE
        );

        PositionAdapter positionAdapter = new PositionAdapter(
                group.getPositions(),
                group.getBaseFrets()
        );
        holder.rvPositions.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        holder.rvPositions.setAdapter(positionAdapter);
        holder.rvPositions.setNestedScrollingEnabled(false);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }


    static class GroupViewHolder extends RecyclerView.ViewHolder {

        TextView tvChordName;
        ImageButton btnPlay;
        RecyclerView rvPositions;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvChordName = itemView.findViewById(R.id.tv_chord_group_name);
            btnPlay = itemView.findViewById(R.id.btn_play_chord);
            rvPositions = itemView.findViewById(R.id.rv_chord_positions);
        }
    }

    static class PositionAdapter extends RecyclerView.Adapter<PositionAdapter.PosViewHolder> {

        private final List<String> fretStrings;
        private final List<Integer> baseFrets;

        PositionAdapter(List<String> fretStrings, List<Integer> baseFrets) {
            this.fretStrings = fretStrings;
            this.baseFrets = baseFrets;
        }

        @NonNull
        @Override
        public PosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chord_position, parent, false);
            return new PosViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PosViewHolder holder, int pos) {
            String fretStr = fretStrings.get(pos);
            int currentBaseFret = baseFrets.get(pos);

            holder.tvLabel.setText("Fret " + currentBaseFret);

            holder.chordView.setChordPositions(fretStr);
            holder.tvFretString.setText(fretStr.replace("-1", "X"));
        }

        @Override
        public int getItemCount() {
            return fretStrings.size();
        }

        static class PosViewHolder extends RecyclerView.ViewHolder {

            TextView tvLabel;
            ChordView chordView;
            TextView tvFretString;

            PosViewHolder(@NonNull View itemView) {
                super(itemView);
                tvLabel = itemView.findViewById(R.id.tv_position_label);
                chordView = itemView.findViewById(R.id.cv_position_diagram);
                tvFretString = itemView.findViewById(R.id.tv_position_frets);
            }
        }
    }
}
