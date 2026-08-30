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
import java.util.Collections;
import java.util.List;

public class ChordGroupAdapter
        extends RecyclerView.Adapter<
        ChordGroupAdapter.GroupViewHolder> {

    public interface OnPlayListener {
        void onPlay(ChordGroup group);
    }

    private final List<ChordGroup> groups =
            new ArrayList<>();

    private OnPlayListener playListener;

    public void setOnPlayListener(
            OnPlayListener listener
    ) {
        this.playListener = listener;
    }

    public void updateData(
            List<ChordGroup> newGroups
    ) {
        groups.clear();

        if (newGroups != null) {
            groups.addAll(newGroups);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_chord_group,
                                parent,
                                false
                        );

        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull GroupViewHolder holder,
            int position
    ) {
        ChordGroup group = groups.get(position);

        holder.tvChordName.setText(
                group.getChordName()
        );

        List<String> positions =
                group.getPositions() != null
                        ? group.getPositions()
                        : Collections.emptyList();

        List<Integer> baseFrets =
                group.getBaseFrets() != null
                        ? group.getBaseFrets()
                        : Collections.emptyList();

        /*
         * Tombol sekarang ditentukan berdasarkan ketersediaan
         * posisi fingering, bukan berdasarkan resource res/raw.
         */
        boolean canPlay = !positions.isEmpty();

        holder.btnPlay.setVisibility(
                canPlay ? View.VISIBLE : View.GONE
        );

        holder.btnPlay.setEnabled(canPlay);

        if (canPlay) {
            holder.btnPlay.setOnClickListener(view -> {
                if (playListener != null) {
                    playListener.onPlay(group);
                }
            });
        } else {
            holder.btnPlay.setOnClickListener(null);
        }

        PositionAdapter positionAdapter =
                new PositionAdapter(
                        positions,
                        baseFrets
                );

        holder.rvPositions.setLayoutManager(
                new LinearLayoutManager(
                        holder.itemView.getContext(),
                        LinearLayoutManager.HORIZONTAL,
                        false
                )
        );

        holder.rvPositions.setAdapter(
                positionAdapter
        );

        holder.rvPositions.setNestedScrollingEnabled(
                false
        );
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class GroupViewHolder
            extends RecyclerView.ViewHolder {

        final TextView tvChordName;
        final ImageButton btnPlay;
        final RecyclerView rvPositions;

        GroupViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            tvChordName =
                    itemView.findViewById(
                            R.id.tv_chord_group_name
                    );

            btnPlay =
                    itemView.findViewById(
                            R.id.btn_play_chord
                    );

            rvPositions =
                    itemView.findViewById(
                            R.id.rv_chord_positions
                    );
        }
    }

    static class PositionAdapter
            extends RecyclerView.Adapter<
            PositionAdapter.PositionViewHolder> {

        private final List<String> fretStrings;
        private final List<Integer> baseFrets;

        PositionAdapter(
                List<String> fretStrings,
                List<Integer> baseFrets
        ) {
            this.fretStrings = fretStrings;
            this.baseFrets = baseFrets;
        }

        @NonNull
        @Override
        public PositionViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            View view =
                    LayoutInflater
                            .from(parent.getContext())
                            .inflate(
                                    R.layout.item_chord_position,
                                    parent,
                                    false
                            );

            return new PositionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull PositionViewHolder holder,
                int position
        ) {
            String fretString =
                    fretStrings.get(position);

            int currentBaseFret =
                    position < baseFrets.size()
                            ? baseFrets.get(position)
                            : 1;

            holder.tvLabel.setText(
                    "Fret " + currentBaseFret
            );

            holder.chordView.setChordPositions(
                    fretString
            );

            holder.tvFretString.setText(
                    fretString.replace("-1", "X")
            );
        }

        @Override
        public int getItemCount() {
            return fretStrings.size();
        }

        static class PositionViewHolder
                extends RecyclerView.ViewHolder {

            final TextView tvLabel;
            final ChordView chordView;
            final TextView tvFretString;

            PositionViewHolder(
                    @NonNull View itemView
            ) {
                super(itemView);

                tvLabel =
                        itemView.findViewById(
                                R.id.tv_position_label
                        );

                chordView =
                        itemView.findViewById(
                                R.id.cv_position_diagram
                        );

                tvFretString =
                        itemView.findViewById(
                                R.id.tv_position_frets
                        );
            }
        }
    }
}