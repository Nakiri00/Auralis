package com.nakiri00.auralis;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AudioFingerprint {

    private static final int BUFFER_SIZE =
            64 * 1024;

    private static final char[] HEX =
            "0123456789abcdef".toCharArray();

    private AudioFingerprint() {
    }

    public static String sha256(
            File audioFile
    ) throws IOException {

        if (
                audioFile == null
                        || !audioFile.isFile()
                        || audioFile.length() <= 0
        ) {
            throw new IOException(
                    "Audio file is invalid or empty"
            );
        }

        final MessageDigest digest;

        try {
            digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    error
            );
        }

        try (
                InputStream input =
                        new BufferedInputStream(
                                new FileInputStream(
                                        audioFile
                                )
                        )
        ) {
            byte[] buffer =
                    new byte[BUFFER_SIZE];

            int bytesRead;

            while (
                    (bytesRead =
                            input.read(buffer)) != -1
            ) {
                digest.update(
                        buffer,
                        0,
                        bytesRead
                );
            }
        }

        byte[] hash =
                digest.digest();

        char[] output =
                new char[hash.length * 2];

        for (int index = 0;
             index < hash.length;
             index++) {

            int value =
                    hash[index] & 0xff;

            output[index * 2] =
                    HEX[value >>> 4];

            output[index * 2 + 1] =
                    HEX[value & 0x0f];
        }

        return new String(output);
    }
}