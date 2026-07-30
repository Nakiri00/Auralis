package com.nakiri00.auralis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChordAdapter extends RecyclerView.Adapter<ChordAdapter.ChordViewHolder> {

    private final List<Chord> chordList = new ArrayList<>();

    // Callback ke ViewModel — Adapter tidak tahu soal MediaPlayer sama sekali
    private OnPlayAudioListener playListener;

    public interface OnPlayAudioListener {
        void onPlay(Chord chord);
    }

    public void setOnPlayAudioListener(OnPlayAudioListener l) {
        this.playListener = l;
    }

    /** Dipanggil dari Fragment saat LiveData chords berubah */
    public void updateData(List<Chord> newChords) {
        chordList.clear();
        chordList.addAll(newChords);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chord, parent, false);
        return new ChordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChordViewHolder holder, int position) {
        Chord chord = chordList.get(position);

        holder.tvChordName.setText(chord.getChordName());
        holder.tvChordStrings.setText("Senar: " + chord.getStrings());
        holder.chordView.setChordPositions(chord.getStrings());

        holder.btnPlayAudio.setOnClickListener(v -> {
            if (playListener != null) playListener.onPlay(chord);
        });
    }

    @Override
    public int getItemCount() { return chordList.size(); }

    public static class ChordViewHolder extends RecyclerView.ViewHolder {
        ChordView chordView;
        TextView tvChordName;
        TextView tvChordStrings;
        ImageButton btnPlayAudio;

        public ChordViewHolder(@NonNull View itemView) {
            super(itemView);
            chordView     = itemView.findViewById(R.id.cv_chord_diagram);
            tvChordName   = itemView.findViewById(R.id.tv_chord_name);
            tvChordStrings= itemView.findViewById(R.id.tv_chord_strings);
            btnPlayAudio  = itemView.findViewById(R.id.btn_play_audio);
        }
    }
}
