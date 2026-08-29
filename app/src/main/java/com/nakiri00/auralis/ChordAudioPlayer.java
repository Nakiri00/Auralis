package com.nakiri00.auralis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Generates a short, guitar-like preview directly from a six-string fret position. */
public class ChordAudioPlayer {

    public interface PlaybackCallback {
        void onStarted();
        void onError(Exception error);
    }

    private final Object trackLock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private final AudioAttributes audioAttributes;
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener;

    private AudioTrack audioTrack;
    private AudioFocusRequest audioFocusRequest;
    private int playbackGeneration = 0;
    private boolean released = false;

    public ChordAudioPlayer(Context context) {
        audioManager = (AudioManager) context
                .getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stop();
            }
        };
    }

    public void play(String fretPosition, PlaybackCallback callback) {
        final int generation;
        synchronized (trackLock) {
            if (released) {
                callback.onError(new IllegalStateException("Audio player has been released"));
                return;
            }
            playbackGeneration++;
            generation = playbackGeneration;
            releaseTrackLocked();
        }

        abandonAudioFocus();
        if (!requestAudioFocus()) {
            abandonAudioFocus();
            callback.onError(new IllegalStateException("Audio focus is unavailable"));
            return;
        }

        executor.execute(() -> {
            try {
                short[] samples = ChordAudioSynthesizer.synthesize(fretPosition);
                AudioTrack newTrack = buildAudioTrack(samples.length);
                writeAllSamples(newTrack, samples);
                newTrack.setNotificationMarkerPosition(samples.length - 1);
                newTrack.setPlaybackPositionUpdateListener(
                        new AudioTrack.OnPlaybackPositionUpdateListener() {
                            @Override
                            public void onMarkerReached(AudioTrack finishedTrack) {
                                releaseCompletedTrack(finishedTrack, generation);
                            }

                            @Override
                            public void onPeriodicNotification(AudioTrack ignored) {}
                        },
                        mainHandler
                );

                synchronized (trackLock) {
                    if (released || generation != playbackGeneration) {
                        newTrack.release();
                        return;
                    }
                    audioTrack = newTrack;
                    newTrack.play();
                }

                if (isCurrentGeneration(generation)) callback.onStarted();
            } catch (Exception error) {
                if (isCurrentGeneration(generation)) {
                    stopGeneration(generation);
                    callback.onError(error);
                }
            }
        });
    }

    public void stop() {
        synchronized (trackLock) {
            playbackGeneration++;
            releaseTrackLocked();
        }
        abandonAudioFocus();
    }

    public void release() {
        synchronized (trackLock) {
            if (released) return;
            released = true;
            playbackGeneration++;
            releaseTrackLocked();
        }
        abandonAudioFocus();
        executor.shutdownNow();
    }

    private AudioTrack buildAudioTrack(int sampleCount) {
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(ChordAudioSynthesizer.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(sampleCount * Short.BYTES)
                .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException("Unable to initialize chord audio output");
        }
        return track;
    }

    private void writeAllSamples(AudioTrack track, short[] samples) {
        int offset = 0;
        while (offset < samples.length) {
            int written = track.write(
                    samples,
                    offset,
                    samples.length - offset,
                    AudioTrack.WRITE_BLOCKING
            );
            if (written <= 0) {
                throw new IllegalStateException("Unable to write chord audio: " + written);
            }
            offset += written;
        }
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            }
        } else {
            audioManager.abandonAudioFocus(focusChangeListener);
        }
    }

    private void releaseCompletedTrack(AudioTrack finishedTrack, int generation) {
        synchronized (trackLock) {
            if (generation != playbackGeneration || audioTrack != finishedTrack) return;
            audioTrack = null;
        }
        finishedTrack.release();
        abandonAudioFocus();
    }

    private void stopGeneration(int generation) {
        synchronized (trackLock) {
            if (generation != playbackGeneration) return;
            playbackGeneration++;
            releaseTrackLocked();
        }
        abandonAudioFocus();
    }

    private boolean isCurrentGeneration(int generation) {
        synchronized (trackLock) {
            return !released && generation == playbackGeneration;
        }
    }

    private void releaseTrackLocked() {
        if (audioTrack == null) return;
        try {
            audioTrack.stop();
        } catch (IllegalStateException ignored) {
            // The track may not have reached PLAYSTATE_PLAYING yet.
        }
        audioTrack.release();
        audioTrack = null;
    }
}
