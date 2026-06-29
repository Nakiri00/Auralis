package com.example.musicchords;

import static androidx.core.content.ContextCompat.startActivity;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;

    // Views
    private Button buttonConvert;
    private Button buttonDetectPitch;
    private TextView tvResultChord;
    private EditText editText;
    private TextView resultTextView;
    private ProgressBar loadingIndicator;
    private Button buttonDownload;
    private TextView tvPlayingTitle;
    private ImageButton btnPlay, btnPause;
    private SeekBar seekBarAudio;
    private TextView tvDuration;
    private Button buttonPickFile;
    private LinearLayout layoutAudioPlayer;
    private ProgressBar progressBarChord;
    private UpcomingChordAdapter upcomingAdapter;
    private RecyclerView rvUpcomingChords;
    private Button exportchord;
    private TextView tvDetectedKey;
    private TextView tvCapoSuggestion;


    private long downloadID = -1;
    private boolean receiverRegistered = false;

    private final ActivityResultLauncher<String> pickAudioLauncher =
        registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && viewModel != null) {
                    viewModel.processAudioFile(uri, null);
                }
            }
        );

    private final BroadcastReceiver onDownloadComplete =
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (viewModel == null) return;
                long id = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1
                );
                if (downloadID != id) return;

                DownloadManager dm = (DownloadManager) context.getSystemService(
                    Context.DOWNLOAD_SERVICE
                );
                Cursor cursor = dm.query(
                    new DownloadManager.Query().setFilterById(id)
                );
                if (cursor != null && cursor.moveToFirst()) {
                    @SuppressLint("Range") int status = cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    );
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        @SuppressLint("Range") String uriString = cursor.getString(
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_LOCAL_URI
                            )
                        );
                        if (uriString != null) {
                            viewModel.processAudioFile(
                                Uri.parse(uriString),
                                viewModel.getAudioTitle()
                            );
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Unduhan Gagal / Belum Selesai",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                    cursor.close();
                }
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                requireContext(),
                onDownloadComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            );
            receiverRegistered = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        progressBarChord = view.findViewById(R.id.chordProgressBar);

        return view;
    }

    @Override
    public void onViewCreated(
        @NonNull View view,
        @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        buttonConvert = view.findViewById(R.id.button_convert);
        resultTextView = view.findViewById(R.id.textview_result);
        loadingIndicator = view.findViewById(R.id.progress_bar);
        editText = view.findViewById(R.id.url_input);
        buttonDownload = view.findViewById(R.id.button_download);
        buttonDetectPitch = view.findViewById(R.id.button_detect_pitch);
        tvResultChord = view.findViewById(R.id.tv_result_chord);
        btnPlay = view.findViewById(R.id.btn_play);
        btnPause = view.findViewById(R.id.btn_pause);
        seekBarAudio = view.findViewById(R.id.seekbar_audio);
        tvDuration = view.findViewById(R.id.tv_duration);
        buttonPickFile = view.findViewById(R.id.btn_pick_file);
        layoutAudioPlayer = view.findViewById(R.id.layout_audio_player);
        tvPlayingTitle = view.findViewById(R.id.tv_playing_title);
        rvUpcomingChords = view.findViewById(R.id.rvUpcomingChords);
        exportchord = view.findViewById(R.id.exportChord);
        rvUpcomingChords.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        upcomingAdapter = new UpcomingChordAdapter();
        rvUpcomingChords.setAdapter(upcomingAdapter);
        tvDetectedKey     = view.findViewById(R.id.tv_detected_key);
        tvCapoSuggestion  = view.findViewById(R.id.tv_capo_suggestion);


        // Initial state
        buttonDetectPitch.setEnabled(false);
        buttonDownload.setVisibility(View.GONE);
        layoutAudioPlayer.setVisibility(View.GONE);
        btnPause.setVisibility(View.GONE);

        setupClickListeners();
        setupObservers();

        if (getArguments() != null) {
            loadHistoryData(getArguments());
        }
    }

    private void setupClickListeners() {
        buttonPickFile.setOnClickListener(v ->
            pickAudioLauncher.launch("audio/*")
        );

        buttonConvert.setOnClickListener(v -> {
            String url = editText.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "Silakan masukkan URL",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }
            buttonDownload.setVisibility(View.GONE);
            layoutAudioPlayer.setVisibility(View.GONE);
            viewModel.releaseMediaPlayer();
            viewModel.convertYoutubeUrl(url);
        });

        buttonDownload.setOnClickListener(v -> {
            String link = viewModel.getDownloadLink().getValue();
            String title = viewModel.getAudioTitle();
            if (link != null && !link.isEmpty()) {
                Toast.makeText(
                    getContext(),
                    "Mulai mengunduh: " + title,
                    Toast.LENGTH_SHORT
                ).show();
                downloadID = viewModel.downloadAudio(link, title);
                buttonDownload.setVisibility(View.GONE);
            } else {
                Toast.makeText(
                    getContext(),
                    "Link download tidak valid.",
                    Toast.LENGTH_SHORT
                ).show();
            }
        });

        buttonDetectPitch.setOnClickListener(v -> {
            String path = viewModel.getAudioFilePath();
            if (path != null && !path.isEmpty()) {
                String[] options = {
                        "Mode Standar (TarsosDSP)",
                        "Mode Premium (Librosa)"
                };

                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Pilih Mode Deteksi Chord")
                        .setItems(options, (dialog, which) -> {
                            boolean isPremium = (which == 1);

                            if (isPremium) {
                                // TODO: Cek apakah user sudah bayar/berlangganan
                                // Jika belum berlangganan:
                                // Toast.makeText(getContext(), "Silakan upgrade ke Premium untuk akses AI!", Toast.LENGTH_SHORT).show();
                                // return;
                            }

                            // Jika gratis atau sudah bayar premium, jalankan analisis
                            viewModel.analyzeChords(path, viewModel.getAudioTitle(), isPremium);
                        })
                        .show();

            } else {
                Toast.makeText(getContext(), "Pilih atau unduh file audio terlebih dahulu.", Toast.LENGTH_SHORT).show();
            }
        });

        btnPlay.setOnClickListener(v -> viewModel.playAudio());
        btnPause.setOnClickListener(v -> viewModel.pauseAudio());

        seekBarAudio.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
                ) {
                    if (fromUser) {
                        viewModel.seekTo(progress);
                        updateDurationText(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            }
        );

        exportchord.setOnClickListener(v -> {
            List<ChordTimestamp> currentChords = viewModel.getDetectedChords().getValue();

            if (currentChords != null && !currentChords.isEmpty()) {
                // Tambahkan opsi MIDI di sini
                String[] options = {"Export sebagai PDF", "Export sebagai TXT", "Export sebagai MIDI"};

                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Pilih Format")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                exportToFile(currentChords, viewModel.getAudioTitle(), true); // PDF
                            } else if (which == 1) {
                                exportToFile(currentChords, viewModel.getAudioTitle(), false); // TXT
                            } else if (which == 2) {
                                // Panggil MidiExportHelper untuk MIDI
                                File midiFile = MidiExportHelper.exportChordsToMidi(requireContext(), currentChords, "MIDI_" + viewModel.getAudioTitle().replaceAll("[\\\\/:*?\"<>|]", "_"));

                                if (midiFile != null) {
                                    Toast.makeText(getContext(), "Berhasil! File MIDI tersimpan di folder Music", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(getContext(), "Gagal membuat file MIDI", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .show();
            } else {
                Toast.makeText(requireContext(), "Belum ada chord yang terdeteksi!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupObservers() {
        viewModel
            .getIsLoading()
            .observe(getViewLifecycleOwner(), loading -> {
                loadingIndicator.setVisibility(
                    loading ? View.VISIBLE : View.GONE
                );
                buttonConvert.setEnabled(!loading);
            });

        viewModel
            .getStatusText()
            .observe(getViewLifecycleOwner(), text -> {
                if (text != null) {
                    resultTextView.setText(text);
                    if (text.equals("Analisis selesai.") || text.equals("Data dimuat dari History.")) {
                        buttonDetectPitch.setVisibility(View.GONE);
                    }
                }
            });

        viewModel
            .getDownloadLink()
            .observe(getViewLifecycleOwner(), link ->
                buttonDownload.setVisibility(
                    link != null ? View.VISIBLE : View.GONE
                )
            );

        viewModel
            .getToastMessage()
            .observe(getViewLifecycleOwner(), msg -> {
                if (msg != null && !msg.isEmpty() && isAdded()) {
                    Toast.makeText(
                        requireContext(),
                        msg,
                        Toast.LENGTH_SHORT
                    ).show();
                    viewModel.clearToastMessage();
                }
            });

        viewModel
            .getIsAnalyzing()
            .observe(getViewLifecycleOwner(), analyzing ->
                buttonDetectPitch.setEnabled(!analyzing)
            );

        viewModel
            .getPlayerTitle()
            .observe(getViewLifecycleOwner(), title -> {
                if (tvPlayingTitle != null) tvPlayingTitle.setText(title);
            });

        viewModel
            .getPlayerReady()
            .observe(getViewLifecycleOwner(), ready -> {
                setAudioControlsEnabled(ready);
                layoutAudioPlayer.setVisibility(
                    ready ? View.VISIBLE : View.GONE
                );
                if (ready) {
                    Integer dur = viewModel.getPlayerDuration().getValue();
                    seekBarAudio.setMax(dur != null ? dur : 0);
                    updateDurationText(0);
                }
            });

        viewModel
            .getIsPlaying()
            .observe(getViewLifecycleOwner(), playing -> {
                btnPlay.setVisibility(playing ? View.GONE : View.VISIBLE);
                btnPause.setVisibility(playing ? View.VISIBLE : View.GONE);
            });

        viewModel
            .getPlayerPosition()
            .observe(getViewLifecycleOwner(), pos -> {
                if (pos != null) {
                    seekBarAudio.setProgress(pos);
                    updateDurationText(pos);
                }
            });

        viewModel
            .getCurrentChordDisplay()
            .observe(getViewLifecycleOwner(), chord -> {
                if (chord != null) tvResultChord.setText(chord);
            });

        viewModel
            .getFileLoaded()
            .observe(getViewLifecycleOwner(), loaded -> {
                if (Boolean.TRUE.equals(loaded)) {
                    String currentStatus = viewModel.getStatusText().getValue();
                    if (currentStatus != null && (currentStatus.equals("Data dimuat dari History.") || currentStatus.equals("Analisis selesai."))) {
                        buttonDetectPitch.setVisibility(View.GONE);
                    } else {
                        buttonDetectPitch.setVisibility(View.VISIBLE);
                        buttonDetectPitch.setEnabled(true);
                    }
                    buttonDownload.setVisibility(View.GONE);
                }
            });
        viewModel.getDetectedKeyText().observe(getViewLifecycleOwner(), keyText -> {
            if (keyText != null && !keyText.isEmpty()) {
                tvDetectedKey.setText(keyText);
                tvDetectedKey.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getCapoSuggestionText().observe(getViewLifecycleOwner(), capo -> {
            if (capo != null && !capo.isEmpty()) {
                tvCapoSuggestion.setText(capo);
                tvCapoSuggestion.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getChordProgress().observe(getViewLifecycleOwner(), progress -> {
            progressBarChord.setProgress(progress);
            if (progress > 85) {
                progressBarChord.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
            } else {
                progressBarChord.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            }
        });
        viewModel.getUpcomingChords().observe(getViewLifecycleOwner(), nextChords -> {
            if (upcomingAdapter != null) {
                upcomingAdapter.updateChords(nextChords);

                // Agar posisi list selalu berada di awal saat data berubah
                rvUpcomingChords.scrollToPosition(0);
            }
        });

    }

    private void updateDurationText(int currentMs) {
        Integer durationMs = viewModel.getPlayerDuration().getValue();
        int tot = (durationMs != null) ? durationMs / 1000 : 0;
        int cur = currentMs / 1000;
        tvDuration.setText(
            String.format(
                "%02d:%02d / %02d:%02d",
                cur / 60,
                cur % 60,
                tot / 60,
                tot % 60
            )
        );
    }

    private void setAudioControlsEnabled(boolean enabled) {
        btnPlay.setEnabled(enabled);
        btnPause.setEnabled(enabled);
        seekBarAudio.setEnabled(enabled);
        btnPlay.setAlpha(enabled ? 1.0f : 0.5f);
        if (!enabled) seekBarAudio.setProgress(0);
    }

    // Dipanggil dari MainActivity saat user memilih item di HistoryFragment
    public void loadHistoryData(Bundle bundle) {
        if (bundle == null || viewModel == null) return;
        String audioPath = bundle.getString("audioPath");
        String title = bundle.getString("songTitle");
        String savedChordData = bundle.getString("chordData");

        if (audioPath != null) {
            if (new File(audioPath).exists()) {
                viewModel.loadHistoryData(audioPath, title, savedChordData);
                buttonDetectPitch.setVisibility(View.GONE);
                buttonDetectPitch.setEnabled(true);
                buttonDownload.setVisibility(View.GONE);
            } else {
                Toast.makeText(
                    getContext(),
                    "File audio tidak ditemukan (mungkin sudah dihapus)",
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private String formatChordsForExport(List<ChordTimestamp> chords, String songTitle) {
        StringBuilder sb = new StringBuilder();

        // Header
        String title = (songTitle != null && !songTitle.isEmpty()) ? songTitle : "Audio";
        sb.append("🎵 Hasil Deteksi Chord: ").append(title).append(" 🎵\n");
        sb.append("=========================================\n\n");

        if (chords == null || chords.isEmpty()) {
            sb.append("Tidak ada chord yang terdeteksi.\n");
            return sb.toString();
        }

        // Iterasi setiap chord untuk menghitung rentang waktu
        for (int i = 0; i < chords.size(); i++) {
            ChordTimestamp current = chords.get(i);

            // Konversi detik ke format mm:ss untuk waktu mulai
            int startSeconds = (int) current.getTimeSeconds(); // Sesuaikan dengan nama getter Anda
            String startTime = String.format("%02d:%02d", startSeconds / 60, startSeconds % 60);

            if (i < chords.size() - 1) {
                // Ambil waktu dari chord berikutnya sebagai batas akhir rentang waktu ini
                ChordTimestamp next = chords.get(i + 1);
                int endSeconds = (int) next.getTimeSeconds();
                String endTime = String.format("%02d:%02d", endSeconds / 60, endSeconds % 60);

                // Output: [00:00 - 00:04]  :  C Major
                sb.append(String.format("[%s - %s]  :  %s\n", startTime, endTime, current.getChordName()));
            } else {
                // Chord terakhir di lagu
                sb.append(String.format("[%s - END]    :  %s\n", startTime, current.getChordName()));
            }
        }

        // Footer
        sb.append("\n=========================================\n");
        sb.append("Dihasilkan oleh Music Chords App");

        return sb.toString();
    }

    private void exportToFile(List<ChordTimestamp> chords, String songTitle, boolean isPdf) {
        String formattedText = formatChordsForExport(chords, songTitle);
        String fileName = "Chord_" + songTitle.replaceAll("[^a-zA-Z0-9]", "_") + (isPdf ? ".pdf" : ".txt");
        File file = new File(requireContext().getCacheDir(), fileName);

        try {
            if (isPdf) {
                generatePdfFile(file, formattedText);
            } else {
                generateTxtFile(file, formattedText);
            }
            shareFile(file);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Gagal membuat file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void generateTxtFile(File file, String text) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(text.getBytes());
        fos.close();
    }

    private void generatePdfFile(File file, String text) throws Exception {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        TextPaint paint = new TextPaint();
        paint.setTextSize(12f);

        StaticLayout staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 500)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build();

        canvas.save();
        canvas.translate(40, 40); // Margin kiri dan atas
        staticLayout.draw(canvas);
        canvas.restore();

        document.finishPage(page);
        document.writeTo(new FileOutputStream(file));
        document.close();
    }

    private void shareFile(File file) {
        // Hardcode nama package yang sama persis dengan di Manifest
        String authority = "com.example.musicchords.fileprovider";

        Uri uri = FileProvider.getUriForFile(requireContext(), authority, file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(file.getName().endsWith(".pdf") ? "application/pdf" : "text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Simpan/Bagikan File"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // MediaPlayer dirilis oleh ViewModel.onCleared() secara otomatis
        if (receiverRegistered) {
            requireActivity().unregisterReceiver(onDownloadComplete);
            receiverRegistered = false;
        }
    }

    // Panggil metode ini dari MainActivity saat bahasa diganti
    public void updateTexts() {
        if (getView() == null) return;

        // Update semua elemen UI yang punya teks berdasarkan strings.xml

        // Contoh untuk EditText (Hint)
        if (editText != null) editText.setHint(getString(R.string.hint_youtube_url));

        // Contoh untuk Button
        if (buttonConvert != null) buttonConvert.setText(getString(R.string.btn_convert_url));
        if (buttonPickFile != null) buttonPickFile.setText(getString(R.string.btn_pick_local_file));
        if (buttonDownload != null) buttonDownload.setText(getString(R.string.btn_download_audio));
        if (buttonDetectPitch != null) buttonDetectPitch.setText(getString(R.string.btn_analyze_detect));
        if (exportchord != null) exportchord.setText(getString(R.string.exportChord));

        // Contoh untuk TextView biasa
        TextView tvTitle = getView().findViewById(R.id.tv_playing_title); // atau referensi variabel jika ada
        // Jika judul lagunya sedang kosong (belum ada lagu), update placeholder-nya
        if (tvTitle != null && tvTitle.getText().toString().equals("Judul Lagu") || tvTitle.getText().toString().equals("Song Title")) {
            tvTitle.setText(getString(R.string.placeholder_song_title));
        }
    }
}
