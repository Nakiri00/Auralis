package com.example.musicchords;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class ChordView extends View {
    private Paint linePaint, dotPaint, textPaint;
    private String[] frets = new String[6];
    private int maxFret = 4;
    private int startingFret = 1;
    private int themeColor;

    public ChordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnBackground, typedValue, true);
        themeColor = typedValue.data;

        linePaint = new Paint();
        linePaint.setColor(themeColor); // Gunakan warna tema, bukan Color.BLACK
        linePaint.setAntiAlias(true);

        dotPaint = new Paint();
        dotPaint.setColor(themeColor); // Gunakan warna tema
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        textPaint = new Paint();
        // Anda bisa menggunakan themeColor, atau memberikan sedikit transparansi jika ingin DKGRAY di light mode
        textPaint.setColor(themeColor);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        for (int i = 0; i < 6; i++) frets[i] = "X";
    }

    public void setChordPositions(String fretPositions) {
        if (fretPositions != null && !fretPositions.isEmpty()) {
            this.frets = fretPositions.split(" ");

            // Hitung jangkauan fret (fret ke berapa yang ditekankan)
            maxFret = 4;
            startingFret = 1;
            int maxFound = 0;
            int minFound = 99;

            for (String f : frets) {
                if (!f.equals("X") && !f.equals("0")) {
                    try {
                        int fNum = Integer.parseInt(f);
                        if (fNum > maxFound) maxFound = fNum;
                        if (fNum < minFound) minFound = fNum;
                    } catch (Exception ignored) {}
                }
            }
            // Jika fret lebih dari 4, geser diagram ke bawah
            if (maxFound > 4) {
                startingFret = minFound;
                maxFret = Math.max(4, maxFound - minFound + 1);
            }
        }
        invalidate(); // Perintahkan Android untuk menggambar ulang
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float topPadding = 50f;
        float leftPadding = 20f;
        float rightPadding = 20f;
        float bottomPadding = 20f;

        float drawWidth = width - leftPadding - rightPadding;
        float drawHeight = height - topPadding - bottomPadding;

        // 6 senar berarti ada 5 ruang jarak
        float stringSpacing = drawWidth / 5f;
        float fretSpacing = drawHeight / maxFret;

        // Menggambar 6 Garis Senar (Vertikal)
        linePaint.setStrokeWidth(3f);
        for (int i = 0; i < 6; i++) {
            float x = leftPadding + (i * stringSpacing);
            canvas.drawLine(x, topPadding, x, topPadding + drawHeight, linePaint);
        }

        // Menggambar Garis Fret (Horizontal)
        // Jika dimulai dari Fret 1, gambar garis pembatas atas (Nut) lebih tebal
        linePaint.setStrokeWidth(startingFret == 1 ? 12f : 3f);
        canvas.drawLine(leftPadding, topPadding, leftPadding + drawWidth, topPadding, linePaint);

        linePaint.setStrokeWidth(3f);
        for (int i = 1; i <= maxFret; i++) {
            float y = topPadding + (i * fretSpacing);
            canvas.drawLine(leftPadding, y, leftPadding + drawWidth, y, linePaint);
        }

        // Jika tidak dimulai dari fret 1, tulis angka fretnya di sebelah kiri
        if (startingFret > 1) {
            textPaint.setTextSize(24f);
            canvas.drawText(startingFret + "fr", leftPadding / 2f, topPadding + (fretSpacing / 2f) + 8f, textPaint);
            textPaint.setTextSize(30f); // Kembalikan ukuran teks
        }

        // Menggambar titik jari (dot) dan status X / O
        if (frets.length >= 6) {
            for (int i = 0; i < 6; i++) {
                float x = leftPadding + (i * stringSpacing);
                String fret = frets[i];

                if (fret.equals("X")) {
                    canvas.drawText("X", x, topPadding - 15f, textPaint);
                } else if (fret.equals("0")) {
                    canvas.drawText("O", x, topPadding - 15f, textPaint);
                } else {
                    try {
                        int fNum = Integer.parseInt(fret);
                        int relativeFret = fNum - startingFret + 1;
                        float y = topPadding + (relativeFret * fretSpacing) - (fretSpacing / 2f);
                        canvas.drawCircle(x, y, stringSpacing / 2.5f, dotPaint);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}