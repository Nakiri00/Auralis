package com.nakiri00.auralis;

public class AudioData {
    public final byte[] bytes;
    public final int sampleRate;
    public final int channels;

    public AudioData(byte[] bytes, int sampleRate, int channels) {
        this.bytes = bytes;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }
}
