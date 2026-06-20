package com.example.musicchords;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.util.fft.FFT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AudioAnalysisRepository {

    private static final String TAG = "AudioAnalysisRepo";

    public interface AnalysisCallback {
        void onComplete(List<ChordTimestamp> results, int keyIndex);
        void onError(Exception e);
    }

    public void analyzeChords(String audioPath, AnalysisCallback callback) {
        new Thread(() -> {
            try {
                if (!new File(audioPath).exists()) {
                    callback.onError(new Exception("File not found: " + audioPath));
                    return;
                }
                File file = new File(audioPath);
                Log.d("DEBUG", "Exists: " + file.exists());
                Log.d("DEBUG", "Size: " + file.length());
                Log.d("DEBUG", "Can Read: " + file.canRead());
                AudioData decoded = decodeAudio(audioPath);
                if (decoded == null) {
                    callback.onError(new Exception("Failed to decode audio"));

                    return;
                }

                byte[] pcmData = (decoded.channels > 1) ? convertToMono(decoded.bytes) : decoded.bytes;
                int sampleRate = decoded.sampleRate;

                int bufferSize = 8192;
                // Testing 4096 ke 6144
                int bufferOverlap = 6144;

                TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
                UniversalAudioInputStream inputStream = new UniversalAudioInputStream(
                        new ByteArrayInputStream(pcmData), format);
                AudioDispatcher dispatcher = new AudioDispatcher(inputStream, bufferSize, bufferOverlap);

                final FFT fft = new FFT(bufferSize);
                final float[] spectrum = new float[bufferSize / 2];

                final float[] hannWindow = new float[bufferSize];
                for (int i = 0; i < bufferSize; i++) {
                    hannWindow[i] = (float) (0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (bufferSize - 1))));
                }

                // Testing dari 5 ke 3
                final int VOTE_WINDOW_SIZE = 7;
                final ArrayDeque<String> chordWindow = new ArrayDeque<>();
                final ArrayDeque<Double> timeWindow = new ArrayDeque<>();
                final String[] lastSavedChord = {""};
                final List<ChordTimestamp> detectedChords = new ArrayList<>();
                final float[] globalChroma = new float[12];
                final ArrayDeque<float[]> chromaTemporalWindow = new ArrayDeque<>();
                final int CHROMA_SMOOTHING_FRAMES = 4;

                AudioProcessor chordProcessor = new AudioProcessor() {
                    @Override
                    public boolean process(AudioEvent audioEvent) {
                        float[] audioBuffer = audioEvent.getFloatBuffer();
                        double timestamp = audioEvent.getTimeStamp();

                        // A. SILENCE DETECTION
                        double rms = 0;
                        for (float s : audioBuffer) rms += s * s;
                        rms = Math.sqrt(rms / audioBuffer.length);
                        // 0.008 --> 0.003
                        if (rms < 0.003) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        // B. APPLY HANN WINDOW
                        float[] transformBuffer = new float[audioBuffer.length];
                        for (int i = 0; i < audioBuffer.length; i++) {
                            transformBuffer[i] = audioBuffer[i] * hannWindow[i];
                        }

                        // C. FFT
                        fft.forwardTransform(transformBuffer);
                        fft.modulus(transformBuffer, spectrum);
//                        float top1Amp = 0, top2Amp = 0, top3Amp = 0;
//                        double f1 = 0, f2 = 0, f3 = 0;
//
//                        for (int i = 1; i < spectrum.length - 1; i++) {
//                            double freq = fft.binToHz(i, sampleRate);
//                            if (freq >= 80.0 && freq <= 1000.0) {
//                                // Syarat "Puncak": suaranya harus lebih keras dari frekuensi tetangganya
//                                if (spectrum[i] > spectrum[i-1] && spectrum[i] > spectrum[i+1]) {
//                                    float amp = spectrum[i];
//                                    if (amp > top1Amp) {
//                                        top3Amp = top2Amp; f3 = f2;
//                                        top2Amp = top1Amp; f2 = f1;
//                                        top1Amp = amp; f1 = freq;
//                                    } else if (amp > top2Amp) {
//                                        top3Amp = top2Amp; f3 = f2;
//                                        top2Amp = amp; f2 = freq;
//                                    } else if (amp > top3Amp) {
//                                        top3Amp = amp; f3 = freq;
//                                    }
//                                }
//                            }
//                        }

                        // D. DYNAMIC NOISE FLOOR
                        float sumAmp = 0;
                        int count = 0;
                        for (int i = 0; i < spectrum.length; i++) {
                            double f = fft.binToHz(i, sampleRate);
                            if (f >= 80.0 && f <= 1200.0) {
                                sumAmp += spectrum[i];
                                count++;
                            }
                        }
                        float meanAmp = count > 0 ? (sumAmp / count) : 0;
                        float noiseFloor = meanAmp * 1.2f;

                        // E. ENERGY-WEIGHTED CHROMA
                        float[] currentFrameChroma = new float[12];
                        final double MIN_FREQ = 80.0;
                        final double MAX_FREQ = 1200.0;

                        for (int i = 1; i < spectrum.length - 1; i++) {
                            if (spectrum[i] > spectrum[i - 1] && spectrum[i] > spectrum[i + 1]) {
                                if (spectrum[i] < noiseFloor) continue;

                                double freq = fft.binToHz(i, sampleRate);
                                if (freq < MIN_FREQ || freq > MAX_FREQ) continue;

                                float logAmp = (float) Math.log10(1.0 + spectrum[i]);
                                double midiExact = 69.0 + (12.0 * Math.log(freq / 440.0)) / Math.log(2);
                                int pitchClass = ((int) Math.round(midiExact)) % 12;
                                if (pitchClass < 0) pitchClass += 12;

                                currentFrameChroma[pitchClass] += logAmp;
                                currentFrameChroma[(pitchClass + 11) % 12] += logAmp * 0.1f;
                                currentFrameChroma[(pitchClass + 1) % 12] += logAmp * 0.1f;
                            }
                        }

                        chromaTemporalWindow.addLast(currentFrameChroma);
                        if (chromaTemporalWindow.size() > CHROMA_SMOOTHING_FRAMES) {
                            chromaTemporalWindow.pollFirst();
                        }

                        float[] smoothChroma = new float[12];
                        for (float[] frameC : chromaTemporalWindow) {
                            for (int i = 0; i < 12; i++) {
                                smoothChroma[i] += frameC[i]; // Akumulasi energi
                            }
                        }

                        // F. NORMALIZE CHROMA
                        float maxChroma = 0;
                        for (float v : smoothChroma) maxChroma = Math.max(maxChroma, v);
                        if (maxChroma <= 0) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }
                        for (int i = 0; i < 12; i++) smoothChroma[i] /= maxChroma;

                        // H. HARMONIC CONTENT FILTER
                        float top1 = 0, top2 = 0, top3 = 0;
                        for (float v : smoothChroma) {
                            if (v > top1) { top3 = top2; top2 = top1; top1 = v; }
                            else if (v > top2) { top3 = top2; top2 = v; }
                            else if (v > top3) { top3 = v; }
                        }
                        float totalEnergy = 0;
                        for (float v : smoothChroma) totalEnergy += v;
                        float concentration = (top1 + top2 + top3) / (totalEnergy + 1e-10f);

                        if (concentration < 0.20f) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }

                        int noisyNotes = 0;
                        for (float v : smoothChroma) {
                            if (v > 0.30f) noisyNotes++;
                        }
                        if (noisyNotes > 7) {
                            updateVoteWindow("-", timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                            return true;
                        }
                        // G2. ACCUMULATE GLOBAL CHROMA for key detection
                        for (int i = 0; i < 12; i++) globalChroma[i] += chroma[i];


                        // H. CHORD MATCHING
                        String currentChord = ChordTemplates.findBestMatchingChord(smoothChroma);
                        if ("N/A".equals(currentChord)) currentChord = "-";

                        if (!currentChord.equals("-")) {
                            String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
                            double[] freqs = {261.63, 277.18, 293.66, 311.13, 329.63, 349.23, 369.99, 392.00, 415.30, 440.00, 466.16, 493.88};

                            // 2. Ambil Nada Dasar (Root) dari nama chord dan normalkan (misal Ab jadi G#)
                            String rootStr = currentChord.split(" ")[0]
                                    .replace("Ab", "G#").replace("Eb", "D#")
                                    .replace("Bb", "A#").replace("Db", "C#").replace("Gb", "F#");
                            boolean isMinor = currentChord.contains("Minor");

                            // 3. Cari posisi indeks Nada Dasar
                            int rootIndex = -1;
                            for (int i = 0; i < notes.length; i++) {
                                if (notes[i].equals(rootStr)) { rootIndex = i; break; }
                            }

                            if (rootIndex != -1) {
                                // 4. Hitung kaidah jarak seminada (semitones)
                                // Major = Root + 4 semitones | Minor = Root + 3 semitones
                                int thirdIndex = (rootIndex + (isMinor ? 3 : 4)) % 12;
                                // Perfect 5th selalu = Root + 7 semitones
                                int fifthIndex = (rootIndex + 7) % 12;

                                double rootFreq = freqs[rootIndex];
                                double thirdFreq = freqs[thirdIndex];
                                double fifthFreq = freqs[fifthIndex];

                                // 5. Jika nada 3rd atau 5th melompat ke oktaf berikutnya (melewati B), kalikan 2
                                if (thirdIndex < rootIndex) thirdFreq *= 2;
                                if (fifthIndex < rootIndex) fifthFreq *= 2;

                                Log.d("ChordAnalysis", String.format("Waktu: %.2fs | %s | Kaidah: Root(%.2f Hz), 3rd(%.2f Hz), 5th(%.2f Hz)",
                                        timestamp, currentChord, rootFreq, thirdFreq, fifthFreq));
                            }
                        }

                        // I. VOTE WINDOW STABILIZATION
                        updateVoteWindow(currentChord, timestamp, chordWindow, timeWindow, lastSavedChord, VOTE_WINDOW_SIZE, detectedChords);
                        return true;
                    }

                    @Override
                    public void processingFinished() {
                        float maxGlobal = 0;
                        for (float v : globalChroma) maxGlobal = Math.max(maxGlobal, v);
                        if (maxGlobal > 0) {
                            for (int i = 0; i < 12; i++) globalChroma[i] /= maxGlobal;
                        }

                        int keyIndex = KeyDetector.detectKey(globalChroma);
                        List<ChordTimestamp> afterKeyFix   = applyKeyConsistency(detectedChords, globalChroma, keyIndex);
                        List<ChordTimestamp> finalChords   = smoothTransitions(afterKeyFix);
                        callback.onComplete(finalChords, keyIndex);
                    }

                };

                dispatcher.addAudioProcessor(chordProcessor);
                dispatcher.run();

            } catch (Exception e) {
                Log.e(TAG, "Analysis error", e);
                callback.onError(e);
            }
        }).start();
    }

    /**
     * Post-processes detected chords for musical key consistency.
     *
     * Rules per chord type:
     *  - Power chord (C5)   : key-neutral (no 3rd) → keep as-is always
     *  - Sus chord (sus2/4) : tonally ambiguous → keep as-is always
     *  - Dominant 7th (C7)  : if not diatonic, try stripping to root Major triad
     *  - Minor 7th (Cm7)    : if not diatonic, try stripping to root Minor triad
     *  - Major/Minor triad  : if not diatonic, try opposite quality
     */
    private List<ChordTimestamp> applyKeyConsistency(
            List<ChordTimestamp> rawChords, float[] globalChroma, int keyIndex) {

        if (rawChords.isEmpty()) return rawChords;

        // Gunakan keyIndex yang sudah dihitung — tidak perlu detectKey() lagi
        Set<String> diatonic = KeyDetector.getDiatonicChords(keyIndex);
        Log.d(TAG, "Detected key: " + KeyDetector.getKeyName(keyIndex));
        Log.d(TAG, "Diatonic chords: " + diatonic);

        List<ChordTimestamp> result = new ArrayList<>();
        for (ChordTimestamp ct : rawChords) {
            String chord = ct.getChordName();

            if (chord.equals("-") || diatonic.contains(chord)) {
                result.add(ct);
                continue;
            }

            String corrected = chord;

            if (chord.endsWith("5") || chord.endsWith("sus2") || chord.endsWith("sus4")) {
                corrected = chord;
            } else if (chord.endsWith("m7")) {
                String rootName = chord.substring(0, chord.length() - 2);
                String candidate = rootName + " Minor";
                if (diatonic.contains(candidate)) corrected = candidate;
                if (corrected.equals(chord)) {
                    candidate = rootName + " Major";
                    if (diatonic.contains(candidate)) corrected = candidate;
                }
            } else if (chord.endsWith("7")) {
                String rootName = chord.substring(0, chord.length() - 1);
                String candidate = rootName + " Major";
                if (diatonic.contains(candidate)) corrected = candidate;
                if (corrected.equals(chord)) {
                    candidate = rootName + " Minor";
                    if (diatonic.contains(candidate)) corrected = candidate;
                }
            } else if (chord.endsWith(" Major")) {
                String candidate = chord.replace(" Major", " Minor");
                if (diatonic.contains(candidate)) corrected = candidate;
            } else if (chord.endsWith(" Minor")) {
                String candidate = chord.replace(" Minor", " Major");
                if (diatonic.contains(candidate)) corrected = candidate;
            }

            if (!corrected.equals(chord)) {
                Log.d(TAG, "Key correction: " + chord + " -> " + corrected);
            }
            result.add(new ChordTimestamp(ct.getTimeSeconds(), corrected));
        }
        return result;
    }


    // Tambahkan di AudioAnalysisRepository setelah applyKeyConsistency()
    private List<ChordTimestamp> smoothTransitions(List<ChordTimestamp> chords) {
        if (chords.size() < 3) return chords;

        List<ChordTimestamp> result = new ArrayList<>(chords);
        // Hapus "isolated chord" — muncul 1x di antara 2 chord yang sama
        // Contoh: C, C, G, C, C → kemungkinan G adalah noise → C, C, C, C, C
        for (int i = 1; i < result.size() - 1; i++) {
            String prev = result.get(i - 1).getChordName();
            String curr = result.get(i).getChordName();
            String next = result.get(i + 1).getChordName();
            if (prev.equals(next) && !curr.equals(prev) && !curr.equals("-")) {
                Log.d(TAG, "Transition smooth: " + curr + " → " + prev + " (isolated)");
                result.set(i, new ChordTimestamp(result.get(i).getTimeSeconds(), prev));
            }
        }
        return result;
    }



    public void analyze(String audioPath, boolean isPremiumMode, AnalysisCallback callback) {
        // 1. Ekstrak audio menjadi PCM (berlaku untuk kedua mode)
        AudioData decoded = decodeAudio(audioPath);
        if (decoded == null) {
            callback.onError(new Exception("Failed to decode")); return;
        }

        // 2. Pilih "Senjata" (Strategy) berdasarkan mode
        ChordAnalyzerStrategy analyzer;
        if (isPremiumMode) {
            analyzer = new TFLiteMLAnalyzer();
        } else {
            analyzer = new TarsosDSPAnalyzer(); // Pakai TarsosDSP dengan Vocal Suppression
        }

        // 3. Eksekusi analisis
        analyzer.analyzeChords(audioPath, decoded.sampleRate, callback);
    }

    private void updateVoteWindow(String chord, double timestamp,
                                  ArrayDeque<String> chordWindow, ArrayDeque<Double> timeWindow,
                                  String[] lastSaved, int windowSize, List<ChordTimestamp> detectedChords) {
        chordWindow.addLast(chord);
        timeWindow.addLast(timestamp);
        if (chordWindow.size() > windowSize) {
            chordWindow.pollFirst();
            timeWindow.pollFirst();
        }
        if (chordWindow.size() < windowSize) return;

        HashMap<String, Integer> votes = new HashMap<>();
        for (String c : chordWindow) votes.merge(c, 1, Integer::sum);

        String winner = "-";
        int maxVotes = 0;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            if (e.getValue() > maxVotes) {
                maxVotes = e.getValue();
                winner = e.getKey();
            }
        }

        int majority = windowSize / 2 + 1;
        if (maxVotes < majority || winner.equals(lastSaved[0])) return;

        double onsetTime = timestamp;
        String[] windowChords = chordWindow.toArray(new String[0]);
        Double[] windowTimes = timeWindow.toArray(new Double[0]);
        for (int i = 0; i < windowChords.length; i++) {
            if (windowChords[i].equals(winner)) { onsetTime = windowTimes[i]; break; }
        }
        onsetTime = Math.max(0.0, onsetTime - 0.15);

        synchronized (detectedChords) {
            detectedChords.add(new ChordTimestamp(onsetTime, winner));
        }
        lastSaved[0] = winner;
    }

        private AudioData decodeAudio(String path) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(path);

            int audioTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    format = f;
                    break;
                }
            }
            if (audioTrack < 0) return null;

            extractor.selectTrack(audioTrack);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null) return null;

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            final int TIMEOUT_US = 10000;

            while (!outputDone) {
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inIndex);
                        if (inputBuffer != null) {
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outIndex >= 0) {
                    ByteBuffer outBuffer = codec.getOutputBuffer(outIndex);
                    if (outBuffer != null && bufferInfo.size > 0) {
                        byte[] chunk = new byte[bufferInfo.size];
                        outBuffer.get(chunk);
                        pcmOutput.write(chunk);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }

            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            return new AudioData(pcmOutput.toByteArray(), sampleRate, channels);

        } catch (Exception e) {
            Log.e(TAG, "Decode error", e);
            return null;
        } finally {
            // Selalu dibebaskan, bahkan jika exception terjadi di tengah jalan
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) {} codec.release(); }
            if (extractor != null) extractor.release();
        }
    }


    private byte[] convertToMono(byte[] stereoData) {
        if (stereoData == null || stereoData.length == 0) return stereoData;
        int totalFrames = stereoData.length / 4;
        ByteBuffer bb = ByteBuffer.wrap(stereoData).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        java.nio.ShortBuffer sb = bb.asShortBuffer();
        short[] monoShorts = new short[totalFrames];
        for (int i = 0; i < totalFrames; i++) {
            monoShorts[i] = (short) ((sb.get(i * 2) + sb.get(i * 2 + 1)) / 2);
        }
        ByteBuffer outBb = ByteBuffer.allocate(monoShorts.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        outBb.asShortBuffer().put(monoShorts);
        return outBb.array();
    }
}
