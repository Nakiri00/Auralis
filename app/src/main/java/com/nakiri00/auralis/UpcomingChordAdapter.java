package com.nakiri00.auralis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class UpcomingChordAdapter extends RecyclerView.Adapter<UpcomingChordAdapter.ViewHolder> {

    private final List<String> chords = new ArrayList<>();

    public void updateChords(List<String> newChords) {
        chords.clear();
        if (newChords != null) {
            chords.addAll(newChords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_upcoming_chord, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvChordName.setText(chords.get(position));

        // (Opsional) Jika ini adalah chord yang paling dekat (index 0), buat warnanya agak menonjol
        if (position == 0) {
            holder.tvChordName.setTextColor(android.graphics.Color.parseColor("#FF9800")); // Warna Oranye
        } else {
            holder.tvChordName.setTextColor(android.graphics.Color.parseColor("#757575")); // Abu-abu
        }
    }

    @Override
    public int getItemCount() {
        return chords.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvChordName;

        ViewHolder(View itemView) {
            super(itemView);
            tvChordName = itemView.findViewById(R.id.tvUpcomingChordName);
        }
    }
}