package com.nakiri00.auralis;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HistoryAudioStorage {

    private static final String TAG =
            "HistoryAudioStorage";
    private static final String DRAFT_DIRECTORY =
            "history_audio_drafts";

    private static final Set<String> ACTIVE_DRAFT_NAMES =
            ConcurrentHashMap.newKeySet();
    private static final String AUDIO_DIRECTORY =
            "history_audio";

    private static final String FILE_PREFIX =
            "history_";

    private static final int COPY_BUFFER_SIZE =
            16 * 1024;

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

    /**
     * Membuat lokasi audio History yang tidak mengikuti
     * Auto Backup maupun device-to-device transfer.
     */
    public static File createDraft(
            Context context,
            String historyId,
            String originalFileName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Context is required"
            );
        }

        if (
                !HistoryIdentity.isUsable(
                        historyId
                )
        ) {
            throw new IllegalArgumentException(
                    "History ID is invalid"
            );
        }

        String fileName =
                FILE_PREFIX
                        + historyId
                        + resolveExtension(
                        originalFileName
                );

        File draft =
                new File(
                        requireDraftDirectory(context),
                        fileName
                );

        ACTIVE_DRAFT_NAMES.add(
                fileName
        );

        return draft;
    }

    public static boolean isDraftFile(
            Context context,
            File file
    ) {
        return context != null
                && file != null
                && isSafeFileName(file.getName())
                && isInsideDirectory(
                requireDraftDirectory(context),
                file
        );
    }

    public static synchronized File commitDraft(
            Context context,
            File draft
    ) {
        if (
                context == null
                        || draft == null
                        || !isSafeFileName(
                        draft.getName()
                )
        ) {
            return null;
        }

        File destination =
                new File(
                        requireAudioDirectory(context),
                        draft.getName()
                );

        /*
         * Draft mungkin sudah dipromosikan oleh proses
         * rekonsiliasi snapshot Firestore.
         */
        if (
                !draft.isFile()
                        && destination.isFile()
        ) {
            ACTIVE_DRAFT_NAMES.remove(
                    draft.getName()
            );

            return destination;
        }

        if (
                !draft.isFile()
                        || !isInsideDirectory(
                        requireDraftDirectory(context),
                        draft
                )
        ) {
            return null;
        }

        File committed =
                moveOrCopy(
                        draft,
                        destination
                );

        if (committed != null) {
            ACTIVE_DRAFT_NAMES.remove(
                    draft.getName()
            );
        }

        return committed;
    }

    public static synchronized boolean discardDraft(
            Context context,
            File draft
    ) {
        if (
                context == null
                        || draft == null
                        || !isSafeFileName(
                        draft.getName()
                )
                        || !isInsideDirectory(
                        requireDraftDirectory(context),
                        draft
                )
        ) {
            return false;
        }

        ACTIVE_DRAFT_NAMES.remove(
                draft.getName()
        );

        return !draft.exists()
                || draft.delete();
    }

    /**
     * Dipanggil hanya setelah menerima snapshot Firestore
     * yang authoritative, bukan snapshot cache.
     */
    public static synchronized void reconcileWithHistory(
            Context context,
            Set<String> referencedFileNames
    ) {
        if (context == null) {
            return;
        }

        Set<String> safeReferences =
                new HashSet<>();

        if (referencedFileNames != null) {
            for (String fileName : referencedFileNames) {
                if (isSafeFileName(fileName)) {
                    safeReferences.add(
                            fileName
                    );
                }
            }
        }

        reconcileDraftFiles(
                context,
                safeReferences
        );

        reconcilePermanentFiles(
                context,
                safeReferences
        );

        reconcileLegacyFiles(
                context,
                safeReferences
        );
    }

    private static void reconcileDraftFiles(
            Context context,
            Set<String> references
    ) {
        File[] drafts =
                requireDraftDirectory(context)
                        .listFiles();

        if (drafts == null) {
            return;
        }

        for (File draft : drafts) {
            String fileName =
                    draft.getName();

            if (!isSafeFileName(fileName)) {
                continue;
            }

            if (references.contains(fileName)) {
                /*
                 * Firestore sudah memiliki dokumen.
                 * Ini juga memulihkan kasus aplikasi crash
                 * setelah save tetapi sebelum commitDraft().
                 */
                commitDraft(
                        context,
                        draft
                );

                continue;
            }

            /*
             * Jangan hapus file yang sedang dipakai
             * oleh proses import/analisis aktif.
             */
            if (
                    !ACTIVE_DRAFT_NAMES.contains(
                            fileName
                    )
                            && draft.exists()
                            && !draft.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to delete orphan draft: "
                                + fileName
                );
            }
        }
    }

    private static void reconcilePermanentFiles(
            Context context,
            Set<String> references
    ) {
        File[] files =
                requireAudioDirectory(context)
                        .listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            String fileName =
                    file.getName();

            if (
                    isSafeFileName(fileName)
                            && !references.contains(
                            fileName
                    )
                            && file.exists()
                            && !file.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to delete orphan audio: "
                                + fileName
                );
            }
        }
    }

    private static void reconcileLegacyFiles(
            Context context,
            Set<String> references
    ) {
        File[] legacyFiles =
                context.getFilesDir()
                        .listFiles();

        if (legacyFiles == null) {
            return;
        }

        for (File legacyFile : legacyFiles) {
            String fileName =
                    legacyFile.getName();

            if (!isSafeFileName(fileName)) {
                continue;
            }

            if (references.contains(fileName)) {
                moveOrCopy(
                        legacyFile,
                        new File(
                                requireAudioDirectory(context),
                                fileName
                        )
                );

                continue;
            }

            if (
                    legacyFile.exists()
                            && !legacyFile.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to delete legacy orphan: "
                                + fileName
                );
            }
        }
    }

    private static File requireDraftDirectory(
            Context context
    ) {
        File directory =
                new File(
                        context.getNoBackupFilesDir(),
                        DRAFT_DIRECTORY
                );

        if (
                !directory.isDirectory()
                        && !directory.mkdirs()
                        && !directory.isDirectory()
        ) {
            throw new IllegalStateException(
                    "Unable to create History "
                            + "draft directory"
            );
        }

        return directory;
    }

    private static File moveOrCopy(
            File source,
            File destination
    ) {
        if (
                source == null
                        || destination == null
                        || !source.isFile()
        ) {
            return null;
        }

        File parent =
                destination.getParentFile();

        if (
                parent == null
                        || (
                        !parent.isDirectory()
                                && !parent.mkdirs()
                )
        ) {
            return null;
        }

        if (destination.isFile()) {
            if (
                    source.exists()
                            && !source.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to remove duplicate source: "
                                + source.getName()
                );
            }

            return destination;
        }

        if (source.renameTo(destination)) {
            return destination;
        }

        File temporary =
                new File(
                        parent,
                        destination.getName()
                                + ".migrating"
                );

        if (
                temporary.exists()
                        && !temporary.delete()
        ) {
            return null;
        }

        try (
                FileInputStream input =
                        new FileInputStream(source);

                FileOutputStream output =
                        new FileOutputStream(temporary)
        ) {
            byte[] buffer =
                    new byte[COPY_BUFFER_SIZE];

            int read;

            while (
                    (read = input.read(buffer)) != -1
            ) {
                output.write(
                        buffer,
                        0,
                        read
                );
            }

            output.flush();
            output.getFD().sync();

        } catch (IOException error) {
            Log.e(
                    TAG,
                    "Failed to copy audio",
                    error
            );

            if (temporary.exists()) {
                temporary.delete();
            }

            return null;
        }

        if (!temporary.renameTo(destination)) {
            temporary.delete();
            return null;
        }

        if (
                source.exists()
                        && !source.delete()
        ) {
            Log.w(
                    TAG,
                    "Failed to remove source after copy"
            );
        }

        return destination;
    }

    /**
     * Mengembalikan audio lokal apabila tersedia.
     *
     * Jika masih ditemukan di filesDir lama, file akan
     * dipindahkan secara otomatis ke noBackupFilesDir.
     */
    public static synchronized File resolveExisting(
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

        String fileName =
                history.getAudioFileName();

        File audioDirectory;

        try {
            audioDirectory =
                    requireAudioDirectory(
                            context
                    );
        } catch (RuntimeException error) {
            Log.e(
                    TAG,
                    "Failed to access no-backup "
                            + "audio directory",
                    error
            );

            return null;
        }

        File currentFile =
                new File(
                        audioDirectory,
                        fileName
                );

        if (
                isInsideDirectory(
                        audioDirectory,
                        currentFile
                )
                        && currentFile.isFile()
        ) {
            return currentFile;
        }

        /*
         * Lokasi yang digunakan Auralis sebelum
         * strategi no-backup diterapkan.
         */
        File legacyFile =
                new File(
                        context.getFilesDir(),
                        fileName
                );

        if (
                !isInsideDirectory(
                        context.getFilesDir(),
                        legacyFile
                )
                        || !legacyFile.isFile()
        ) {
            return null;
        }

        File draft =
                new File(
                        requireDraftDirectory(context),
                        fileName
                );

        if (
                isInsideDirectory(
                        requireDraftDirectory(context),
                        draft
                )
                        && draft.isFile()
        ) {
            return commitDraft(
                    context,
                    draft
            );
        }

        return migrateLegacyAudio(
                legacyFile,
                currentFile
        );
    }

    /**
     * Mengembalikan basename aman untuk disimpan di Firestore.
     *
     * Lokasi lama masih diterima sementara agar pembaruan
     * aplikasi tidak memutus proses analisis yang sedang berjalan.
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

        File file =
                new File(
                        absolutePath
                );

        if (!isSafeFileName(file.getName())) {
            return null;
        }

        boolean insidePermanentDirectory =
                isInsideDirectory(
                        requireAudioDirectory(context),
                        file
                );

        boolean insideDraftDirectory =
                isInsideDirectory(
                        requireDraftDirectory(context),
                        file
                );

        boolean insideLegacyDirectory =
                isInsideDirectory(
                        context.getFilesDir(),
                        file
                );

        return insidePermanentDirectory
                || insideDraftDirectory
                || insideLegacyDirectory
                ? file.getName()
                : null;
    }

    /**
     * Digunakan untuk dokumen Firestore lama yang
     * masih memiliki absolute filePath.
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
                new File(
                        legacyPath
                ).getName();

        return isSafeFileName(fileName)
                ? fileName
                : null;
    }

    public static boolean delete(
            File file
    ) {
        if (
                file == null
                        || !file.exists()
        ) {
            return true;
        }

        boolean deleted =
                file.delete();

        if (!deleted) {
            Log.w(
                    TAG,
                    "Failed to delete audio: "
                            + file.getAbsolutePath()
            );
        }

        return deleted;
    }

    private static File migrateLegacyAudio(
            File legacyFile,
            File destinationFile
    ) {
        File parent =
                destinationFile.getParentFile();

        if (
                parent == null
                        || (
                        !parent.isDirectory()
                                && !parent.mkdirs()
                )
        ) {
            Log.e(
                    TAG,
                    "Failed to create destination "
                            + "directory"
            );

            return legacyFile;
        }

        /*
         * renameTo() biasanya berhasil karena filesDir dan
         * noBackupFilesDir masih berada pada storage aplikasi
         * yang sama.
         */
        if (legacyFile.renameTo(destinationFile)) {
            Log.i(
                    TAG,
                    "Legacy audio moved to no-backup "
                            + "storage: "
                            + destinationFile.getName()
            );

            return destinationFile;
        }

        File temporaryFile =
                new File(
                        parent,
                        destinationFile.getName()
                                + ".migrating"
                );

        if (
                temporaryFile.exists()
                        && !temporaryFile.delete()
        ) {
            Log.w(
                    TAG,
                    "Failed to remove stale migration file"
            );

            return legacyFile;
        }

        try (
                FileInputStream input =
                        new FileInputStream(
                                legacyFile
                        );

                FileOutputStream output =
                        new FileOutputStream(
                                temporaryFile
                        )
        ) {
            byte[] buffer =
                    new byte[COPY_BUFFER_SIZE];

            int read;

            while (
                    (read = input.read(buffer)) != -1
            ) {
                output.write(
                        buffer,
                        0,
                        read
                );
            }

            output.flush();
            output.getFD().sync();

        } catch (IOException error) {
            Log.e(
                    TAG,
                    "Failed to copy legacy audio",
                    error
            );

            if (
                    temporaryFile.exists()
                            && !temporaryFile.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to remove incomplete "
                                + "migration file"
                );
            }

            return legacyFile;
        }

        if (!temporaryFile.renameTo(destinationFile)) {
            Log.e(
                    TAG,
                    "Failed to finalize audio migration"
            );

            if (
                    temporaryFile.exists()
                            && !temporaryFile.delete()
            ) {
                Log.w(
                        TAG,
                        "Failed to clean migration file"
                );
            }

            return legacyFile;
        }

        if (!legacyFile.delete()) {
            Log.w(
                    TAG,
                    "Audio migration succeeded, but the "
                            + "legacy copy could not be deleted"
            );
        }

        Log.i(
                TAG,
                "Legacy audio copied to no-backup storage: "
                        + destinationFile.getName()
        );

        return destinationFile;
    }

    private static File requireAudioDirectory(
            Context context
    ) {
        File directory =
                new File(
                        context.getNoBackupFilesDir(),
                        AUDIO_DIRECTORY
                );

        if (
                !directory.isDirectory()
                        && !directory.mkdirs()
                        && !directory.isDirectory()
        ) {
            throw new IllegalStateException(
                    "Unable to create History "
                            + "audio directory"
            );
        }

        return directory;
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

        for (
                String extension
                : SUPPORTED_EXTENSIONS
        ) {
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

        for (
                String extension
                : SUPPORTED_EXTENSIONS
        ) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInsideDirectory(
            File directory,
            File candidate
    ) {
        if (
                directory == null
                        || candidate == null
        ) {
            return false;
        }

        try {
            String directoryPath =
                    directory.getCanonicalPath()
                            + File.separator;

            String candidatePath =
                    candidate.getCanonicalPath();

            return candidatePath.startsWith(
                    directoryPath
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