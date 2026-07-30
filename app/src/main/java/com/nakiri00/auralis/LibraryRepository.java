package com.nakiri00.auralis;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibraryRepository {

    private static final String TAG = "LibraryRepository";

    // ─── DTOs untuk parsing JSON (hanya dipakai di sini) ─────────────────────

    private static class GuitarData {

        Map<String, List<ChordInfo>> chords;
    }

    private static class ChordInfo {

        String key;
        String suffix;
        List<Position> positions;
    }

    private static class Position {

        List<Integer> frets;
        int baseFret; // fret ke-berapa diagram ini mulai (1 = nut)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Membaca guitar.json, lalu mengelompokkan SEMUA posisi per chord menjadi
     * List<ChordGroup>. Fret dikoversi dari relatif ke absolut agar ChordView
     * bisa menampilkan posisi yang benar (termasuk "5fr", "7fr", dll.).
     *
     * Harus dipanggil dari background thread!
     */
    public List<ChordGroup> loadChordGroupsFromAssets(Context context) {
        List<ChordGroup> result = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open("guitar.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            GuitarData guitarData = new Gson().fromJson(
                new String(buffer, StandardCharsets.UTF_8),
                GuitarData.class
            );

            if (guitarData == null || guitarData.chords == null) return result;

            String[] targetKeys = {
                "C",
                "C#",
                "D",
                "Eb",
                "E",
                "F",
                "F#",
                "G",
                "Ab",
                "A",
                "Bb",
                "B",
            };

            for (String key : targetKeys) {
                List<ChordInfo> chordInfoList = guitarData.chords.get(key);
                if (chordInfoList == null) continue;

                for (ChordInfo info : chordInfoList) {
                    if (
                        !info.suffix.equals("major") &&
                        !info.suffix.equals("minor")
                    ) continue;
                    if (
                        info.positions == null || info.positions.isEmpty()
                    ) continue;

                    String chordName = info.key + " " + info.suffix;


                    List<String> positionFretStrings = new ArrayList<>();
                    List<Integer> baseFretsList = new ArrayList<>();
                    for (Position pos : info.positions) {
                        if (pos.frets == null || pos.frets.isEmpty()) continue;
                        positionFretStrings.add(buildAbsoluteFretString(pos));
                        baseFretsList.add(pos.baseFret);
                    }
                    if (positionFretStrings.isEmpty()) continue;

                    String keyClean = info.key
                        .replace("#", "sharp")
                        .toLowerCase();
                    String audioFileName =
                        "chord_" + keyClean + "_" + info.suffix.toLowerCase();
                    int audioResId = context
                        .getResources()
                        .getIdentifier(
                            audioFileName,
                            "raw",
                            context.getPackageName()
                        );

                    result.add(
                        new ChordGroup(
                            chordName,
                            positionFretStrings,
                            audioResId,
                                baseFretsList

                        )
                    );
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read guitar.json", e);
        }
        return result;
    }

    /**
     * Konversi fret RELATIF (dalam diagram) ke ABSOLUT (posisi jari nyata).
     * Rumus: absoluteFret = relativeFret + baseFret - 1  (untuk fret > 0)
     *
     * Contoh: baseFret=5, frets=[-1,-1,1,1,1,4] → "X X 5 5 5 8"
     * ChordView akan otomatis menampilkan label "5fr" di samping diagram.
     */
    private String buildAbsoluteFretString(Position pos) {
        StringBuilder sb = new StringBuilder();
        for (int f : pos.frets) {
            if (f == -1) {
                sb.append("X");
            } else if (f == 0) {
                sb.append("0");
            } else {
                // fret relatif → absolut
                sb.append(f + pos.baseFret - 1);
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}
