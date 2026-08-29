package com.nakiri00.auralis;

import java.util.Random;

/** Converts a six-string guitar fret position into a short PCM chord preview. */
final class ChordAudioSynthesizer {

    static final int SAMPLE_RATE = 44_100;
    private static final double NOTE_DURATION_SECONDS = 2.2;
    private static final int STRUM_DELAY_SAMPLES = (int) (SAMPLE_RATE * 0.035);
    private static final int[] OPEN_STRING_MIDI = {40, 45, 50, 55, 59, 64};

    private ChordAudioSynthesizer() {}

    static short[] synthesize(String fretPosition) {
        int[] frets = parseFrets(fretPosition);
        int activeStrings = 0;
        for (int fret : frets) {
            if (fret >= 0) activeStrings++;
        }
        if (activeStrings == 0) {
            throw new IllegalArgumentException("Chord position contains no playable strings");
        }

        int noteSamples = (int) (SAMPLE_RATE * NOTE_DURATION_SECONDS);
        int totalSamples = noteSamples + STRUM_DELAY_SAMPLES * (frets.length - 1);
        double[] mix = new double[totalSamples];
        double stringGain = 0.85 / Math.sqrt(activeStrings);

        for (int stringIndex = 0; stringIndex < frets.length; stringIndex++) {
            int fret = frets[stringIndex];
            if (fret < 0) continue;

            double frequency = midiToFrequency(OPEN_STRING_MIDI[stringIndex] + fret);
            int delayLength = Math.max(2, (int) Math.round(SAMPLE_RATE / frequency));
            double[] delayLine = new double[delayLength];
            Random random = new Random(31L * (stringIndex + 1) + fret);
            for (int i = 0; i < delayLine.length; i++) {
                delayLine[i] = random.nextDouble() * 2.0 - 1.0;
            }

            int onset = stringIndex * STRUM_DELAY_SAMPLES;
            int delayIndex = 0;
            for (int sample = 0; sample < noteSamples; sample++) {
                double current = delayLine[delayIndex];
                int nextIndex = (delayIndex + 1) % delayLength;
                delayLine[delayIndex] = 0.996 * 0.5
                        * (current + delayLine[nextIndex]);
                delayIndex = nextIndex;

                double fadeOut = 1.0 - (sample / (double) noteSamples);
                mix[onset + sample] += current * stringGain * fadeOut;
            }
        }

        short[] pcm = new short[mix.length];
        for (int i = 0; i < mix.length; i++) {
            double limited = Math.tanh(mix[i]);
            pcm[i] = (short) Math.round(limited * Short.MAX_VALUE * 0.9);
        }
        return pcm;
    }

    static int[] parseFrets(String fretPosition) {
        if (fretPosition == null || fretPosition.trim().isEmpty()) {
            throw new IllegalArgumentException("Chord position is empty");
        }

        String[] tokens = fretPosition.trim().split("\\s+");
        if (tokens.length != OPEN_STRING_MIDI.length) {
            throw new IllegalArgumentException("Chord position must contain six strings");
        }

        int[] frets = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if ("X".equalsIgnoreCase(token) || "-1".equals(token)) {
                frets[i] = -1;
            } else {
                frets[i] = Integer.parseInt(token);
                if (frets[i] < 0) {
                    throw new IllegalArgumentException("Invalid fret: " + token);
                }
            }
        }
        return frets;
    }

    private static double midiToFrequency(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }
}
