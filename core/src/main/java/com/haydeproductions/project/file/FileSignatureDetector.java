package com.haydeproductions.project.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

public final class FileSignatureDetector {

    private static final int HEADER_SIZE = 512;

    public FileSignature detect(Path path) throws IOException {
        Path normalized = Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();

        byte[] header = new byte[HEADER_SIZE];
        int length = 0;

        try (InputStream input = Files.newInputStream(
                normalized,
                StandardOpenOption.READ
        )) {
            while (length < header.length) {
                int read = input.read(
                        header,
                        length,
                        header.length - length
                );

                if (read == -1) {
                    break;
                }

                length += read;
            }
        }

        return detect(header, length);
    }

    FileSignature detect(byte[] header, int length) {
        if (startsWith(header, length,
                0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return FileSignature.PNG;
        }

        if (startsWith(header, length, 0xFF, 0xD8, 0xFF)) {
            return FileSignature.JPEG;
        }

        if (startsWithAscii(header, length, "GIF87a")
                || startsWithAscii(header, length, "GIF89a")) {
            return FileSignature.GIF;
        }

        if (startsWithAscii(header, length, "%PDF-")) {
            return FileSignature.PDF;
        }

        if (startsWith(header, length, 0x50, 0x4B, 0x03, 0x04)
                || startsWith(header, length, 0x50, 0x4B, 0x05, 0x06)
                || startsWith(header, length, 0x50, 0x4B, 0x07, 0x08)) {
            return FileSignature.ZIP;
        }

        if (startsWith(header, length, 0x1F, 0x8B)) {
            return FileSignature.GZIP;
        }

        if (startsWithAscii(header, length, "MZ")) {
            return FileSignature.PE;
        }

        if (startsWith(header, length, 0x7F, 0x45, 0x4C, 0x46)) {
            return FileSignature.ELF;
        }

        if (startsWith(header, length, 0xFE, 0xED, 0xFA, 0xCE)
                || startsWith(header, length, 0xCE, 0xFA, 0xED, 0xFE)
                || startsWith(header, length, 0xFE, 0xED, 0xFA, 0xCF)
                || startsWith(header, length, 0xCF, 0xFA, 0xED, 0xFE)) {
            return FileSignature.MACH_O;
        }

        if (startsWith(header, length, 0xCA, 0xFE, 0xBA, 0xBE)) {
            return FileSignature.JAVA_CLASS;
        }

        if (length >= 263) {
            byte[] marker = Arrays.copyOfRange(header, 257, 263);
            String value = new String(marker, java.nio.charset.StandardCharsets.US_ASCII);
            if (value.equals("ustar\0") || value.equals("ustar ")) {
                return FileSignature.TAR;
            }
        }

        return FileSignature.UNKNOWN;
    }

    private static boolean startsWithAscii(
            byte[] bytes,
            int length,
            String text
    ) {
        byte[] expected = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (length < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean startsWith(
            byte[] bytes,
            int length,
            int... expected
    ) {
        if (length < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }

        return true;
    }
}
