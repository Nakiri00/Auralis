package com.example.musicchords;

import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Centralized helper for showing consistent, theme-adaptive dialogs
 * across the entire app. Uses MaterialAlertDialogBuilder so dialogs
 * automatically follow the light/dark theme.
 *
 * Usage examples:
 *   DialogHelper.showDestructiveDialog(context, "Hapus?", "...", "Hapus", () -> delete());
 *   DialogHelper.showConfirmDialog(context, "Simpan?", "...", "Simpan", () -> save());
 *   DialogHelper.showInfoDialog(context, "Info", "Berhasil disimpan.");
 */
public class DialogHelper {

    /**
     * Confirmation dialog for safe/neutral actions.
     * Positive button uses the app's primary color.
     *
     * @param positiveLabel Label for the confirm button (e.g. "Simpan", "Ya")
     * @param onConfirm     Runs when the user taps the positive button
     */
    public static void showConfirmDialog(Context context,
                                         String title,
                                         String message,
                                         String positiveLabel,
                                         Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context, R.style.App_AlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> onConfirm.run())
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Confirmation dialog for dangerous/irreversible actions (e.g. delete).
     * Positive button is shown in red (?attr/colorError) to signal danger.
     *
     * @param positiveLabel Label for the destructive button (e.g. "Hapus")
     * @param onConfirm     Runs when the user confirms the action
     */
    public static void showDestructiveDialog(Context context,
                                             String title,
                                             String message,
                                             String positiveLabel,
                                             Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context, R.style.App_AlertDialog_Destructive)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel, (d, w) -> onConfirm.run())
                .setNegativeButton("Batal", null)
                .show();
    }

    public static void showSelectionDialog(Context context,
                                           String title,
                                           String[] options,
                                           android.content.DialogInterface.OnClickListener onOptionSelected) {
        new MaterialAlertDialogBuilder(context, R.style.App_AlertDialog)
                .setTitle(title)
                .setItems(options, onOptionSelected)
                .setNegativeButton("Batal", null) // Tombol batal standar agar seragam
                .show();
    }

    /**
     * Simple info dialog with a single dismiss button.
     * Use this for success messages, warnings, or non-critical errors.
     */
    public static void showInfoDialog(Context context,
                                      String title,
                                      String message) {
        new MaterialAlertDialogBuilder(context, R.style.App_AlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
