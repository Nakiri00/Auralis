package com.nakiri00.auralis;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class HistoryAudioStorage {

    private static final String TAG =
            "HistoryAudioStorage";

    private static final String FILE_PREFIX =
            "history_";

    private static final String[] SUPPORTED_EXTENSIONS = {
            ".mp3",
            ".wav",
            ".flac",
            ".m4a",
            ".aac",
            ".ogg",
            ".mp4"
    };

    private HistoryAudioStorage() {
    }

    public static File createDestination(
            Context context,
            String historyId,
            String originalFileName
    ) {
        if (
                !HistoryIdentity.isUsable(
                        historyId
                )
        ) {
            throw new IllegalArgumentException(
                    "History ID is invalid"
            );
        }

        String extension =
                resolveExtension(
                        originalFileName
                );

        String fileName =
                FILE_PREFIX
                        + historyId
                        + extension;

        return new File(
                context.getFilesDir(),
                fileName
        );
    }

    /**
     * Mengembalikan file lokal jika benar-benar tersedia
     * di internal storage aplikasi.
     */
    public static File resolveExisting(
            Context context,
            ChordHistory history
    ) {
        if (
                context == null
                        || history == null
                        || !isSafeFileName(
                        history.getAudioFileName()
                )
        ) {
            return null;
        }

        File candidate =
                new File(
                        context.getFilesDir(),
                        history.getAudioFileName()
                );

        if (
                !isInsideFilesDirectory(
                        context,
                        candidate
                )
                        || !candidate.isFile()
        ) {
            return null;
        }

        return candidate;
    }

    /**
     * Hanya mengembalikan basename apabila path mengarah
     * ke internal files directory aplikasi.
     */
    public static String getManagedFileName(
            Context context,
            String absolutePath
    ) {
        if (
                context == null
                        || absolutePath == null
                        || absolutePath.trim().isEmpty()
        ) {
            return null;
        }

        File file = new File(absolutePath);

        if (
                !isInsideFilesDirectory(
                        context,
                        file
                )
                        || !isSafeFileName(
                        file.getName()
                )
        ) {
            return null;
        }

        return file.getName();
    }

    /**
     * Digunakan satu kali untuk dokumen Firestore lama yang
     * masih memiliki field filePath.
     *
     * Absolute path dibuang dan hanya basename aman yang diambil.
     */
    public static String sanitizeLegacyFileName(
            String legacyPath
    ) {
        if (
                legacyPath == null
                        || legacyPath.trim().isEmpty()
        ) {
            return null;
        }

        String fileName =
                new File(legacyPath)
                        .getName();

        return isSafeFileName(fileName)
                ? fileName
                : null;
    }

    public static boolean delete(
            File file
    ) {
        if (file == null || !file.exists()) {
            return true;
        }

        boolean deleted = file.delete();

        if (!deleted) {
            Log.w(
                    TAG,
                    "Failed to delete audio: "
                            + file.getAbsolutePath()
            );
        }

        return deleted;
    }

    private static String resolveExtension(
            String fileName
    ) {
        if (fileName == null) {
            return ".mp3";
        }

        String lower =
                fileName.toLowerCase(
                        Locale.US
                );

        for (String extension
                : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return extension;
            }
        }

        return ".mp3";
    }

    private static boolean isSafeFileName(
            String fileName
    ) {
        if (
                fileName == null
                        || fileName.trim().isEmpty()
                        || fileName.contains("/")
                        || fileName.contains("\\")
                        || !fileName.startsWith(
                        FILE_PREFIX
                )
        ) {
            return false;
        }

        String lower =
                fileName.toLowerCase(
                        Locale.US
                );

        for (String extension
                : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInsideFilesDirectory(
            Context context,
            File candidate
    ) {
        try {
            String filesDirectory =
                    context
                            .getFilesDir()
                            .getCanonicalPath()
                            + File.separator;

            String candidatePath =
                    candidate.getCanonicalPath();

            return candidatePath.startsWith(
                    filesDirectory
            );

        } catch (IOException error) {
            Log.e(
                    TAG,
                    "Failed to validate audio path",
                    error
            );

            return false;
        }
    }
}