package com.nakiri00.auralis;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.util.fft.FFT;

public class TarsosDSPAnalyzer implements ChordAnalyzerStrategy {

    private static final String TAG = "TarsosDSPAnalyzer";

    private static final class DecodedPcmFile {
        final File file;
        final int sampleRate;

        DecodedPcmFile(File file, int sampleRate) {
            this.file = file;
            this.sampleRate = sampleRate;
        }
    }
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean callbackDelivered = new AtomicBoolean(false);

    private volatile Thread workerThread;
    private volatile AudioDispatcher activeDispatcher;

    @Override
    public void analyzeChords(
            String audioPath,
            int inputSampleRate,
            AudioAnalysisRepository.AnalysisCallback callback
    ) {
        if (!started.compareAndSet(false, true)) {
            callback.onError(
                    new IllegalStateException(
                            "TarsosDSPAnalyzer instances are single-use"
                    )
            );
            return;
        }
        Thread worker = new Thread(() -> {
            DecodedPcmFile decoded = null;

            try {
                throwIfCancellationRequested();

                File audioFile = new File(audioPath);

                if (!audioFile.exists()) {
                    throw new IOException(
                            "Audio file not found: " + audioPath
                    );
                }

                decoded = decodeAudioToMonoPcmFile(audioPath);

                throwIfCancellationRequested();

                if (decoded == null || !decoded.file.exists()) {
                    throw new IOException("Failed to decode audio");
                }

                final int sampleRate = decoded.sampleRate;
                final int bufferSize = 8192;
                final int bufferOverlap = 6144;

                TarsosDSPAudioFormat format =
                        new TarsosDSPAudioFormat(
                                sampleRate,
                                16,
                                1,
                                true,
                                false
                        );

                try (FileInputStream pcmInput =
                             new FileInputStream(decoded.file)) {

                    UniversalAudioInputStream inputStream =
                            new UniversalAudioInputStream(
                                    pcmInput,
                                    format
                            );

                    AudioDispatcher dispatcher =
                            new AudioDispatcher(
                                    inputStream,
                                    bufferSize,
                                    bufferOverlap
                            );
                    activeDispatcher = dispatcher;
                    throwIfCancellationRequested();
                    final FFT fft = new FFT(bufferSize);
                    final float[] spectrum =
                            new float[bufferSize / 2];

                    final float[] transformBuffer =
                            new float[bufferSize];

                    final float[] hannWindow =
                            new float[bufferSize];

                    for (int i = 0; i < bufferSize; i++) {
                        hannWindow[i] =
                                (float) (
                                        0.5
                                                * (
                                                1.0
                                                        - Math.cos(
                                                        (2.0 * Math.PI * i)
                                                                / (bufferSize - 1)
                                                )
                                        )
                                );
                    }

                    final int voteWindowSize = 7;
                    final int chromaSmoothingFrames = 4;

                    final ArrayDeque<String> chordWindow =
                            new ArrayDeque<>();

                    final ArrayDeque<Double> timeWindow =
                            new ArrayDeque<>();

                    final ArrayDeque<float[]> chromaTemporalWindow =
                            new ArrayDeque<>();

                    final String[] lastSavedChord = {""};

                    final List<ChordTimestamp> detectedChords =
                            new ArrayList<>();

                    final float[] globalChroma = new float[12];

                    AudioProcessor chordProcessor =
                            new AudioProcessor() {
                                @Override
                                public boolean process(
                                        AudioEvent audioEvent
                                ) {
                                    if (isCancellationRequested()) {
                                        return false;
                                    }
                                    float[] audioBuffer =
                                            audioEvent.getFloatBuffer();

                                    double timestamp =
                                            audioEvent.getTimeStamp();

                                    /*
                                     * A. Silence detection
                                     */
                                    double rms = 0;

                                    for (float sample : audioBuffer) {
                                        rms += sample * sample;
                                    }

                                    rms = Math.sqrt(
                                            rms / audioBuffer.length
                                    );

                                    if (rms < 0.003) {
                                        updateVoteWindow(
                                                "-",
                                                timestamp,
                                                chordWindow,
                                                timeWindow,
                                                lastSavedChord,
                                                voteWindowSize,
                                                detectedChords
                                        );

                                        return !isCancellationRequested();
                                    }

                                    /*
                                     * B. Apply Hann window
                                     */
                                    for (int i = 0;
                                         i < audioBuffer.length;
                                         i++) {

                                        transformBuffer[i] =
                                                audioBuffer[i]
                                                        * hannWindow[i];
                                    }

                                    /*
                                     * C. FFT
                                     */
                                    fft.forwardTransform(
                                            transformBuffer
                                    );

                                    fft.modulus(
                                            transformBuffer,
                                            spectrum
                                    );

                                    /*
                                     * D. Dynamic noise floor
                                     */
                                    float sumAmplitude = 0;
                                    int frequencyBinCount = 0;

                                    for (int i = 0;
                                         i < spectrum.length;
                                         i++) {

                                        double frequency =
                                                fft.binToHz(
                                                        i,
                                                        sampleRate
                                                );

                                        if (frequency >= 80.0
                                                && frequency <= 1200.0) {

                                            sumAmplitude += spectrum[i];
                                            frequencyBinCount++;
                                        }
                                    }

                                    float meanAmplitude =
                                            frequencyBinCount > 0
                                                    ? sumAmplitude
                                                    / frequencyBinCount
                                                    : 0;

                                    float noiseFloor =
                                            meanAmplitude * 1.2f;

                                    /*
                                     * E. Energy-weighted chroma
                                     */
                                    float[] currentFrameChroma =
                                            new float[12];

                                    final double minimumFrequency = 80.0;
                                    final double maximumFrequency = 1200.0;

                                    for (int i = 1;
                                         i < spectrum.length - 1;
                                         i++) {

                                        boolean isPeak =
                                                spectrum[i]
                                                        > spectrum[i - 1]
                                                        && spectrum[i]
                                                        > spectrum[i + 1];

                                        if (!isPeak) {
                                            continue;
                                        }

                                        if (spectrum[i] < noiseFloor) {
                                            continue;
                                        }

                                        double frequency =
                                                fft.binToHz(
                                                        i,
                                                        sampleRate
                                                );

                                        if (frequency < minimumFrequency
                                                || frequency
                                                > maximumFrequency) {
                                            continue;
                                        }

                                        float logarithmicAmplitude =
                                                (float) Math.log10(
                                                        1.0
                                                                + spectrum[i]
                                                );

                                        double midiExact =
                                                69.0
                                                        + (
                                                        12.0
                                                                * Math.log(
                                                                frequency
                                                                        / 440.0
                                                        )
                                                )
                                                        / Math.log(2);

                                        int pitchClass =
                                                ((int) Math.round(
                                                        midiExact
                                                )) % 12;

                                        if (pitchClass < 0) {
                                            pitchClass += 12;
                                        }

                                        currentFrameChroma[pitchClass]
                                                += logarithmicAmplitude;

                                        currentFrameChroma[
                                                (pitchClass + 11) % 12
                                                ] += logarithmicAmplitude
                                                * 0.1f;

                                        currentFrameChroma[
                                                (pitchClass + 1) % 12
                                                ] += logarithmicAmplitude
                                                * 0.1f;
                                    }

                                    chromaTemporalWindow.addLast(
                                            currentFrameChroma
                                    );

                                    if (chromaTemporalWindow.size()
                                            > chromaSmoothingFrames) {

                                        chromaTemporalWindow.pollFirst();
                                    }

                                    float[] smoothChroma =
                                            new float[12];

                                    for (float[] frameChroma
                                            : chromaTemporalWindow) {

                                        for (int i = 0; i < 12; i++) {
                                            smoothChroma[i]
                                                    += frameChroma[i];
                                        }
                                    }

                                    /*
                                     * F. Normalize chroma
                                     */
                                    float maximumChroma = 0;

                                    for (float value : smoothChroma) {
                                        maximumChroma =
                                                Math.max(
                                                        maximumChroma,
                                                        value
                                                );
                                    }

                                    if (maximumChroma <= 0) {
                                        updateVoteWindow(
                                                "-",
                                                timestamp,
                                                chordWindow,
                                                timeWindow,
                                                lastSavedChord,
                                                voteWindowSize,
                                                detectedChords
                                        );

                                        return !isCancellationRequested();
                                    }

                                    for (int i = 0; i < 12; i++) {
                                        smoothChroma[i] /= maximumChroma;
                                    }

                                    /*
                                     * G. Harmonic-content filter
                                     */
                                    float top1 = 0;
                                    float top2 = 0;
                                    float top3 = 0;

                                    for (float value : smoothChroma) {
                                        if (value > top1) {
                                            top3 = top2;
                                            top2 = top1;
                                            top1 = value;
                                        } else if (value > top2) {
                                            top3 = top2;
                                            top2 = value;
                                        } else if (value > top3) {
                                            top3 = value;
                                        }
                                    }

                                    float totalEnergy = 0;

                                    for (float value : smoothChroma) {
                                        totalEnergy += value;
                                    }

                                    float concentration =
                                            (top1 + top2 + top3)
                                                    / (
                                                    totalEnergy
                                                            + 1e-10f
                                            );

                                    if (concentration < 0.20f) {
                                        updateVoteWindow(
                                                "-",
                                                timestamp,
                                                chordWindow,
                                                timeWindow,
                                                lastSavedChord,
                                                voteWindowSize,
                                                detectedChords
                                        );

                                        return !isCancellationRequested();
                                    }

                                    int noisyNotes = 0;

                                    for (float value : smoothChroma) {
                                        if (value > 0.30f) {
                                            noisyNotes++;
                                        }
                                    }

                                    if (noisyNotes > 7) {
                                        updateVoteWindow(
                                                "-",
                                                timestamp,
                                                chordWindow,
                                                timeWindow,
                                                lastSavedChord,
                                                voteWindowSize,
                                                detectedChords
                                        );

                                        return !isCancellationRequested();
                                    }

                                    /*
                                     * H. Accumulate global chroma
                                     * for key detection
                                     */
                                    for (int i = 0; i < 12; i++) {
                                        globalChroma[i]
                                                += smoothChroma[i];
                                    }

                                    /*
                                     * I. Chord matching
                                     */
                                    String currentChord =
                                            ChordTemplates
                                                    .findBestMatchingChord(
                                                            smoothChroma
                                                    );

                                    if ("N/A".equals(currentChord)) {
                                        currentChord = "-";
                                    }

                                    if (!"-".equals(currentChord)) {
                                        logChordFrequencies(
                                                currentChord,
                                                timestamp
                                        );
                                    }

                                    /*
                                     * J. Vote-window stabilization
                                     */
                                    updateVoteWindow(
                                            currentChord,
                                            timestamp,
                                            chordWindow,
                                            timeWindow,
                                            lastSavedChord,
                                            voteWindowSize,
                                            detectedChords
                                    );

                                    return !isCancellationRequested();
                                }

                                @Override
                                public void processingFinished() {
                                    if (isCancellationRequested()) {
                                        return;
                                    }
                                    float maximumGlobalChroma = 0;

                                    for (float value : globalChroma) {
                                        maximumGlobalChroma =
                                                Math.max(
                                                        maximumGlobalChroma,
                                                        value
                                                );
                                    }

                                    int keyIndex =
                                            KeyDetector.UNKNOWN_KEY_INDEX;

                                    if (
                                            maximumGlobalChroma
                                                    > 1e-6f
                                    ) {
                                        for (int i = 0; i < 12; i++) {
                                            globalChroma[i] /=
                                                    maximumGlobalChroma;
                                        }

                                        keyIndex =
                                                KeyDetector.detectKey(
                                                        globalChroma
                                                );
                                    }

                                    List<ChordTimestamp> chordsForSmoothing;

                                    if (
                                            KeyDetector.isValidKeyIndex(
                                                    keyIndex
                                            )
                                    ) {
                                        chordsForSmoothing =
                                                applyKeyConsistency(
                                                        detectedChords,
                                                        keyIndex
                                                );
                                    } else {
                                        chordsForSmoothing =
                                                new ArrayList<>(
                                                        detectedChords
                                                );
                                    }

                                    List<ChordTimestamp> finalChords =
                                            smoothTransitions(
                                                    chordsForSmoothing
                                            );
                                    if (isCancellationRequested()) {
                                        return;
                                    }

                                    deliverComplete(
                                            callback,
                                            finalChords,
                                            keyIndex
                                    );

                                }
                            };

                    dispatcher.addAudioProcessor(chordProcessor);
                    dispatcher.run();
                }

            } catch (CancellationException exception) {
                Log.d(TAG, "TarsosDSP chord analysis cancelled");
            } catch (Exception exception) {
                if (!isCancellationRequested()) {
                    Log.e(TAG, "Chord analysis failed", exception);
                    deliverError(callback, exception);
                }
            } finally {
                activeDispatcher = null;

                if (decoded != null
                        && decoded.file != null
                        && decoded.file.exists()
                        && !decoded.file.delete()) {
                    Log.w(
                            TAG,
                            "Unable to delete decoded PCM file: "
                                    + decoded.file
                    );
                }

                workerThread = null;
            }
        }, "auralis-chord-analysis");
        workerThread = worker;
        if (cancelled.get()) {
            workerThread = null;
            return;
        }

        worker.start();
    }

    @Override
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }

        AudioDispatcher dispatcher = activeDispatcher;

        if (dispatcher != null) {
            try {
                dispatcher.stop();
            } catch (RuntimeException exception) {
                Log.w(
                        TAG,
                        "Unable to stop AudioDispatcher",
                        exception
                );
            }
        }

        Thread worker = workerThread;

        if (worker != null) {
            worker.interrupt();
        }
    }

    private boolean isCancellationRequested() {
        return cancelled.get()
                || Thread.currentThread().isInterrupted();
    }

    private void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException(
                    "TarsosDSP chord analysis was cancelled"
            );
        }
    }

    private void deliverComplete(
            AudioAnalysisRepository.AnalysisCallback callback,
            List<ChordTimestamp> chordData,
            int keyIndex
    ) {
        if (isCancellationRequested()) {
            return;
        }

        if (callbackDelivered.compareAndSet(false, true)) {
            callback.onComplete(chordData, keyIndex);
        }
    }

    private void deliverError(
            AudioAnalysisRepository.AnalysisCallback callback,
            Exception exception
    ) {
        if (isCancellationRequested()) {
            return;
        }

        if (callbackDelivered.compareAndSet(false, true)) {
            callback.onError(exception);
        }
    }

    private void logChordFrequencies(
            String currentChord,
            double timestamp
    ) {
        String[] notes = {
                "C", "C#", "D", "D#", "E", "F",
                "F#", "G", "G#", "A", "A#", "B"
        };

        double[] frequencies = {
                261.63, 277.18, 293.66, 311.13,
                329.63, 349.23, 369.99, 392.00,
                415.30, 440.00, 466.16, 493.88
        };

        String root =
                currentChord
                        .split(" ")[0]
                        .replace("Ab", "G#")
                        .replace("Eb", "D#")
                        .replace("Bb", "A#")
                        .replace("Db", "C#")
                        .replace("Gb", "F#");

        boolean isMinor =
                currentChord.contains("Minor");

        int rootIndex = -1;

        for (int i = 0; i < notes.length; i++) {
            if (notes[i].equals(root)) {
                rootIndex = i;
                break;
            }
        }

        if (rootIndex < 0) {
            return;
        }

        int thirdIndex =
                (rootIndex + (isMinor ? 3 : 4)) % 12;

        int fifthIndex =
                (rootIndex + 7) % 12;

        double rootFrequency =
                frequencies[rootIndex];

        double thirdFrequency =
                frequencies[thirdIndex];

        double fifthFrequency =
                frequencies[fifthIndex];

        if (thirdIndex < rootIndex) {
            thirdFrequency *= 2;
        }

        if (fifthIndex < rootIndex) {
            fifthFrequency *= 2;
        }

        Log.d(
                "ChordAnalysis",
                String.format(
                        "Time: %.2fs | %s | "
                                + "Root(%.2f Hz), "
                                + "3rd(%.2f Hz), "
                                + "5th(%.2f Hz)",
                        timestamp,
                        currentChord,
                        rootFrequency,
                        thirdFrequency,
                        fifthFrequency
                )
        );
    }

    private List<ChordTimestamp> applyKeyConsistency(List<ChordTimestamp> rawChords, int keyIndex) {
        if (rawChords == null || rawChords.isEmpty() || !KeyDetector.isValidKeyIndex(keyIndex)) {
            return rawChords != null ? rawChords : new ArrayList<>();
        }
        Set<String> diatonicChords =
                KeyDetector.getDiatonicChords(keyIndex);

        Log.d(TAG, "Detected key: " + KeyDetector.getKeyName(keyIndex));

        Log.d(TAG, "Diatonic chords: " + diatonicChords);

        List<ChordTimestamp> result = new ArrayList<>();

        for (ChordTimestamp chordTimestamp : rawChords) {
            String chord =
                    chordTimestamp.getChordName();

            if ("-".equals(chord)
                    || diatonicChords.contains(chord)) {

                result.add(chordTimestamp);
                continue;
            }

            String correctedChord = chord;

            if (chord.endsWith(" Major")) {
                String minorCandidate =
                        chord.replace(
                                " Major",
                                " Minor"
                        );

                if (diatonicChords.contains(minorCandidate)) {
                    correctedChord = minorCandidate;
                }

            } else if (chord.endsWith(" Minor")) {
                String majorCandidate =
                        chord.replace(
                                " Minor",
                                " Major"
                        );

                if (diatonicChords.contains(majorCandidate)) {
                    correctedChord = majorCandidate;
                }
            }

            if (!correctedChord.equals(chord)) {
                Log.d(TAG, "Key correction: " + chord + " -> " + correctedChord);
            }

            result.add(
                    new ChordTimestamp(
                            chordTimestamp.getTimeSeconds(),
                            correctedChord
                    )
            );
        }

        return result;
    }

    private List<ChordTimestamp> smoothTransitions(
            List<ChordTimestamp> chords
    ) {
        if (chords.size() < 3) {
            return chords;
        }

        List<ChordTimestamp> result =
                new ArrayList<>(chords);

        for (int i = 1; i < result.size() - 1; i++) {
            String previousChord =
                    result.get(i - 1).getChordName();

            String currentChord =
                    result.get(i).getChordName();

            String nextChord =
                    result.get(i + 1).getChordName();

            boolean isIsolatedChord =
                    previousChord.equals(nextChord)
                            && !currentChord.equals(previousChord)
                            && !"-".equals(currentChord);

            if (isIsolatedChord) {
                Log.d(
                        TAG,
                        "Transition smoothing: "
                                + currentChord
                                + " -> "
                                + previousChord
                );

                result.set(
                        i,
                        new ChordTimestamp(
                                result.get(i).getTimeSeconds(),
                                previousChord
                        )
                );
            }
        }

        return result;
    }

    private void updateVoteWindow(
            String chord,
            double timestamp,
            ArrayDeque<String> chordWindow,
            ArrayDeque<Double> timeWindow,
            String[] lastSavedChord,
            int windowSize,
            List<ChordTimestamp> detectedChords
    ) {
        chordWindow.addLast(chord);
        timeWindow.addLast(timestamp);

        if (chordWindow.size() > windowSize) {
            chordWindow.pollFirst();
            timeWindow.pollFirst();
        }

        if (chordWindow.size() < windowSize) {
            return;
        }

        HashMap<String, Integer> votes =
                new HashMap<>();

        for (String currentChord : chordWindow) {
            votes.merge(
                    currentChord,
                    1,
                    Integer::sum
            );
        }

        String winner = "-";
        int maximumVotes = 0;

        for (Map.Entry<String, Integer> entry
                : votes.entrySet()) {

            if (entry.getValue() > maximumVotes) {
                maximumVotes = entry.getValue();
                winner = entry.getKey();
            }
        }

        int majority = (windowSize / 2) + 1;

        if (maximumVotes < majority
                || winner.equals(lastSavedChord[0])) {
            return;
        }

        double onsetTime = timestamp;

        String[] windowChords =
                chordWindow.toArray(new String[0]);

        Double[] windowTimes =
                timeWindow.toArray(new Double[0]);

        for (int i = 0; i < windowChords.length; i++) {
            if (windowChords[i].equals(winner)) {
                onsetTime = windowTimes[i];
                break;
            }
        }

        onsetTime = Math.max(
                0.0,
                onsetTime - 0.35
        );

        synchronized (detectedChords) {
            detectedChords.add(
                    new ChordTimestamp(
                            onsetTime,
                            winner
                    )
            );
        }

        lastSavedChord[0] = winner;
    }

    private DecodedPcmFile decodeAudioToMonoPcmFile(
            String path
    ) {
        MediaExtractor extractor = null;
        MediaCodec codec = null;
        File pcmFile = null;

        try {
            File sourceFile = new File(path);
            File outputDirectory =
                    sourceFile.getParentFile();

            if (outputDirectory == null
                    || !outputDirectory.exists()
                    || !outputDirectory.canWrite()) {

                throw new IOException(
                        "Audio directory is not writable: "
                                + path
                );
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(path);

            int audioTrackIndex = -1;
            MediaFormat inputFormat = null;

            for (int i = 0;
                 i < extractor.getTrackCount();
                 i++) {

                MediaFormat candidateFormat =
                        extractor.getTrackFormat(i);

                String candidateMime =
                        candidateFormat.getString(
                                MediaFormat.KEY_MIME
                        );

                if (candidateMime != null
                        && candidateMime.startsWith("audio/")) {

                    audioTrackIndex = i;
                    inputFormat = candidateFormat;
                    break;
                }
            }

            if (audioTrackIndex < 0
                    || inputFormat == null) {

                throw new IOException(
                        "No audio track found"
                );
            }

            extractor.selectTrack(audioTrackIndex);

            String mime =
                    inputFormat.getString(
                            MediaFormat.KEY_MIME
                    );

            if (mime == null) {
                throw new IOException(
                        "Audio MIME type is unavailable"
                );
            }

            int outputChannels =
                    inputFormat.containsKey(
                            MediaFormat.KEY_CHANNEL_COUNT
                    )
                            ? inputFormat.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT
                    )
                            : 1;

            int outputSampleRate =
                    inputFormat.containsKey(
                            MediaFormat.KEY_SAMPLE_RATE
                    )
                            ? inputFormat.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE
                    )
                            : 44100;

            int pcmEncoding =
                    AudioFormat.ENCODING_PCM_16BIT;

            codec =
                    MediaCodec.createDecoderByType(mime);

            codec.configure(
                    inputFormat,
                    null,
                    null,
                    0
            );

            codec.start();

            pcmFile =
                    File.createTempFile(
                            "auralis_pcm_",
                            ".raw",
                            outputDirectory
                    );

            MediaCodec.BufferInfo bufferInfo =
                    new MediaCodec.BufferInfo();

            boolean inputDone = false;
            boolean outputDone = false;

            final int timeoutMicroseconds = 10_000;

            try (FileOutputStream pcmOutput =
                         new FileOutputStream(pcmFile)) {

                while (!outputDone) {
                    throwIfCancellationRequested();
                    if (!inputDone) {
                        int inputIndex =
                                codec.dequeueInputBuffer(
                                        timeoutMicroseconds
                                );

                        if (inputIndex >= 0) {
                            ByteBuffer inputBuffer =
                                    codec.getInputBuffer(
                                            inputIndex
                                    );

                            if (inputBuffer == null) {
                                throw new IOException(
                                        "Decoder input buffer is null"
                                );
                            }

                            inputBuffer.clear();

                            int sampleSize =
                                    extractor.readSampleData(
                                            inputBuffer,
                                            0
                                    );

                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec
                                                .BUFFER_FLAG_END_OF_STREAM
                                );

                                inputDone = true;

                            } else {
                                codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.getSampleTime(),
                                        0
                                );

                                extractor.advance();
                            }
                        }
                    }

                    throwIfCancellationRequested();

                    int outputIndex =
                            codec.dequeueOutputBuffer(
                                    bufferInfo,
                                    timeoutMicroseconds
                            );

                    throwIfCancellationRequested();

                    if (outputIndex >= 0) {
                        try {
                            ByteBuffer outputBuffer =
                                    codec.getOutputBuffer(
                                            outputIndex
                                    );

                            if (outputBuffer != null
                                    && bufferInfo.size > 0) {

                                outputBuffer.position(
                                        bufferInfo.offset
                                );

                                outputBuffer.limit(
                                        bufferInfo.offset
                                                + bufferInfo.size
                                );

                                writeMonoPcm16(
                                        outputBuffer,
                                        outputChannels,
                                        pcmEncoding,
                                        pcmOutput
                                );
                            }

                        } finally {
                            codec.releaseOutputBuffer(
                                    outputIndex,
                                    false
                            );
                        }

                        if ((bufferInfo.flags
                                & MediaCodec
                                .BUFFER_FLAG_END_OF_STREAM) != 0) {

                            outputDone = true;
                        }

                    } else if (
                            outputIndex
                                    == MediaCodec
                                    .INFO_OUTPUT_FORMAT_CHANGED
                    ) {
                        MediaFormat outputFormat =
                                codec.getOutputFormat();

                        if (outputFormat.containsKey(
                                MediaFormat.KEY_CHANNEL_COUNT
                        )) {
                            outputChannels =
                                    outputFormat.getInteger(
                                            MediaFormat
                                                    .KEY_CHANNEL_COUNT
                                    );
                        }

                        if (outputFormat.containsKey(
                                MediaFormat.KEY_SAMPLE_RATE
                        )) {
                            outputSampleRate =
                                    outputFormat.getInteger(
                                            MediaFormat.KEY_SAMPLE_RATE
                                    );
                        }

                        if (outputFormat.containsKey(
                                MediaFormat.KEY_PCM_ENCODING
                        )) {
                            pcmEncoding =
                                    outputFormat.getInteger(
                                            MediaFormat.KEY_PCM_ENCODING
                                    );
                        }
                    }
                }

                pcmOutput.flush();
            }

            if (pcmFile.length() == 0) {
                throw new IOException(
                        "Decoded PCM file is empty"
                );
            }

            return new DecodedPcmFile(
                    pcmFile,
                    outputSampleRate
            );

        } catch (Exception exception) {
            if (pcmFile != null
                    && pcmFile.exists()
                    && !pcmFile.delete()) {

                Log.w(
                        TAG,
                        "Failed to delete incomplete PCM file: "
                                + pcmFile.getAbsolutePath()
                );
            }

            if (exception instanceof CancellationException) {
                throw (CancellationException) exception;
            }

            Log.e(TAG, "Audio decoding failed", exception);
            return null;

        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                    // Codec may not have started successfully.
                }

                try {
                    codec.release();
                } catch (Exception ignored) {
                    // Nothing else can be done during cleanup.
                }
            }

            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception ignored) {
                    // Nothing else can be done during cleanup.
                }
            }
        }
    }

    private void writeMonoPcm16(
            ByteBuffer source,
            int channels,
            int pcmEncoding,
            FileOutputStream destination
    ) throws IOException {
        if (pcmEncoding
                != AudioFormat.ENCODING_PCM_16BIT) {

            throw new IOException(
                    "Unsupported decoded PCM encoding: "
                            + pcmEncoding
            );
        }

        if (channels <= 0) {
            throw new IOException(
                    "Invalid decoded channel count: "
                            + channels
            );
        }

        source.order(ByteOrder.LITTLE_ENDIAN);

        if (channels == 1) {
            byte[] monoData =
                    new byte[source.remaining()];

            source.get(monoData);
            destination.write(monoData);
            return;
        }

        int frameSize =
                channels * Short.BYTES;

        if (source.remaining() % frameSize != 0) {
            throw new IOException(
                    "Decoded PCM buffer is not frame-aligned"
            );
        }

        int frameCount =
                source.remaining() / frameSize;

        byte[] monoData =
                new byte[frameCount * Short.BYTES];

        ByteBuffer monoBuffer =
                ByteBuffer.wrap(monoData)
                        .order(ByteOrder.LITTLE_ENDIAN);

        for (int frame = 0;
             frame < frameCount;
             frame++) {

            long sampleSum = 0;

            for (int channel = 0;
                 channel < channels;
                 channel++) {

                sampleSum += source.getShort();
            }

            monoBuffer.putShort(
                    (short) (sampleSum / channels)
            );
        }

        destination.write(monoData);
    }
}