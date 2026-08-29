package com.nakiri00.auralis;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.NoteOff;
import com.leff.midi.event.NoteOn;
import com.leff.midi.event.meta.Tempo;
import com.leff.midi.event.meta.TimeSignature;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class MidiExportHelper {

    private static final String TAG = "MidiExportHelper";
    private static final Pattern DISPLAY_ANNOTATION = Pattern.compile(
            "\\s+\\(.*\\)\\s*$"
    );

    // Method utama untuk memanggil export
    public static File exportChordsToMidi(Context context, List<ChordTimestamp> detectedChords, String fileName) {
        try {
            // 1. Inisialisasi Track MIDI
            MidiTrack tempoTrack = new MidiTrack();
            MidiTrack noteTrack = new MidiTrack();

            // Set Time Signature (4/4) dan Tempo (120 BPM)
            TimeSignature ts = new TimeSignature();
            ts.setTimeSignature(4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION);
            tempoTrack.insertEvent(ts);

            Tempo tempo = new Tempo();
            tempo.setBpm(120);
            tempoTrack.insertEvent(tempo);

            // Resolusi standar MIDI (Ticks per Quarter Note)
            final int RESOLUTION = MidiFile.DEFAULT_RESOLUTION; // Biasanya 480
            final int TICKS_PER_SECOND = 960;

            // Kasih delay super kecil (sekitar 0.1 detik) di awal lagu biar aman dari reset player
            final long TICK_OFFSET = 100;

            // 2. Masukkan nada dari list chord
            boolean isGuitarSet = false; // Flag biar alat musik diganti sekali aja

            for (int i = 0; i < detectedChords.size(); i++) {
                ChordTimestamp current = detectedChords.get(i);

                // Lewati jika tidak ada chord yang terdeteksi
                if (current.getChordName().equals("-") || current.getChordName().equals("N/A")) continue;

                // Hitung kapan chord dimulai dan berakhir (dalam Ticks) ditambah Offset
                long startTick = TICK_OFFSET + (long) (current.getTimeSeconds() * TICKS_PER_SECOND);
                long endTick;

                // Durasi chord adalah sampai chord berikutnya berbunyi
                if (i < detectedChords.size() - 1) {
                    endTick = TICK_OFFSET + (long) (detectedChords.get(i + 1).getTimeSeconds() * TICKS_PER_SECOND);
                } else {
                    // Untuk chord terakhir, beri durasi default 2 detik
                    endTick = startTick + (2 * TICKS_PER_SECOND);
                }


                if (!isGuitarSet) {
                    long pcTick = startTick - 10;
                    com.leff.midi.event.ProgramChange changeInstrument = new com.leff.midi.event.ProgramChange(pcTick, 0, 25);
                    noteTrack.insertEvent(changeInstrument);
                    isGuitarSet = true;
                }

                // Dapatkan angka MIDI (Root, 3rd, 5th)
                int[] midiNotes = getMidiNotesForChord(current.getChordName());

                for (int note : midiNotes) {
                    NoteOn noteOn = new NoteOn(startTick, 0, note, 100);
                    NoteOff noteOff = new NoteOff(endTick, 0, note, 0);

                    noteTrack.insertEvent(noteOn);
                    noteTrack.insertEvent(noteOff);
                }
            }

            // 3. Gabungkan Track dan Simpan ke File
            MidiFile midiFile = new MidiFile(RESOLUTION);
            midiFile.addTrack(tempoTrack);
            midiFile.addTrack(noteTrack);

            File cacheFile = new File(context.getCacheDir(), fileName + ".mid");
            midiFile.writeToFile(cacheFile);

            File outputFile = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName + ".mid");
                values.put(MediaStore.Downloads.MIME_TYPE, "audio/midi");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (InputStream is = new FileInputStream(cacheFile);
                         OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    Log.d(TAG, "Berhasil mengekspor MIDI ke folder Download publik via MediaStore");
                    outputFile = cacheFile;
                }
            } else {
                // Untuk Android 9 (API 28) ke bawah menggunakan akses File biasa
                File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!outputDir.exists()) outputDir.mkdirs();

                outputFile = new File(outputDir, fileName + ".mid");
                try (InputStream is = new FileInputStream(cacheFile);
                     OutputStream os = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                Log.d(TAG, "Berhasil mengekspor MIDI ke folder Download legacy: " + outputFile.getAbsolutePath());
            }

            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Gagal mengekspor MIDI", e);
            return null;
        }
    }

    // Method pemetaan Chord String -> MIDI Notes (C4 = 60)
    static int[] getMidiNotesForChord(String chordName) {
        if (chordName == null) return new int[0];

        // Roman numerals are display metadata, not part of the chord quality.
        // Example: "A Minor (vi)" must still be parsed as an A minor triad.
        String musicalChordName = DISPLAY_ANNOTATION
                .matcher(chordName.trim())
                .replaceFirst("");

        // Normalisasi enharmonic (Ab→G#, Eb→D#, dll)
        String normalized = musicalChordName
                .replace("Ab", "G#").replace("Eb", "D#")
                .replace("Bb", "A#").replace("Db", "C#").replace("Gb", "F#");

        String[] allNotes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

        // Tentukan root (bisa 1 atau 2 karakter: C, C#, G#, dll)
        String rootStr = "";
        int rootMidi = 60;
        for (String note : allNotes) {
            if (normalized.startsWith(note)) {
                if (note.length() > rootStr.length()) rootStr = note; // ambil yang lebih spesifik (C# > C)
            }
        }
        for (int i = 0; i < allNotes.length; i++) {
            if (allNotes[i].equals(rootStr)) { rootMidi = 60 + i; break; }
        }

        // Tentukan kualitas chord dari suffix setelah root
        String suffix = normalized.substring(rootStr.length()).trim();

        if (suffix.equals("5")) {
            // Power chord: Root + 5th only
            return new int[]{rootMidi, rootMidi + 7};

        } else if (suffix.equals("7")) {
            // Dominant 7th: Root + Major 3rd + 5th + Minor 7th
            return new int[]{rootMidi, rootMidi + 4, rootMidi + 7, rootMidi + 10};

        } else if (suffix.equals("m7")) {
            // Minor 7th: Root + Minor 3rd + 5th + Minor 7th
            return new int[]{rootMidi, rootMidi + 3, rootMidi + 7, rootMidi + 10};

        } else if (suffix.equals("sus4")) {
            // Sus4: Root + Perfect 4th + 5th
            return new int[]{rootMidi, rootMidi + 5, rootMidi + 7};

        } else if (suffix.equals("sus2")) {
            // Sus2: Root + Major 2nd + 5th
            return new int[]{rootMidi, rootMidi + 2, rootMidi + 7};

        } else if (suffix.equals("Minor")) {
            // Minor triad
            return new int[]{rootMidi, rootMidi + 3, rootMidi + 7};

        } else {
            // Default: Major triad (termasuk "Major" dan kasus tidak dikenal)
            return new int[]{rootMidi, rootMidi + 4, rootMidi + 7};
        }
    }

}
