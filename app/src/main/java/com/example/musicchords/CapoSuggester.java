package com.example.musicchords;

public class CapoSuggester {

    // Key-key yang "mudah" di gitar (banyak open chord)
    private static final int[] EASY_KEYS = {0, 2, 4, 5, 7, 9}; // C, D, E, F, G, A

    /**
     * Memberikan saran capo berdasarkan key terdeteksi.
     * Return: "Capo X → mainkan di key Y" atau "Tidak perlu capo"
     *
     * @param keyIndex key terdeteksi dari KeyDetector (0-23)
     */
    public static String suggest(int keyIndex) {
        boolean isMinor = keyIndex >= 12;
        int detectedRoot = isMinor ? keyIndex - 12 : keyIndex;

        // Cek apakah sudah di easy key
        for (int easyKey : EASY_KEYS) {
            if (detectedRoot == easyKey) {
                return "Tidak perlu capo — sudah di key " + KeyDetector.getKeyName(keyIndex);
            }
        }

        // Cari capo paling rendah yang menghasilkan easy key
        for (int capo = 1; capo <= 7; capo++) {
            int effectiveRoot = (detectedRoot - capo + 12) % 12;
            for (int easyKey : EASY_KEYS) {
                if (effectiveRoot == easyKey) {
                    String easyKeyName = ChordTemplates.NOTES[effectiveRoot] + (isMinor ? " Minor" : " Major");
                    return "Capo fret " + capo + " → mainkan chord " + easyKeyName;
                }
            }
        }

        return "Tidak ada saran capo yang sesuai";
    }
}
