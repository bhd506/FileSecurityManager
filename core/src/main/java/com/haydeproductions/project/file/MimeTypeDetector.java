package com.haydeproductions.project.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class MimeTypeDetector {

    public String detect(
            Path path,
            FileSignature signature
    ) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(signature);

        String signatureMimeType = mimeTypeForSignature(signature);
        if (signatureMimeType != null) {
            return signatureMimeType;
        }

        String probed = Files.probeContentType(path);
        return probed == null
                ? "application/octet-stream"
                : probed.toLowerCase(java.util.Locale.ROOT);
    }

    private String mimeTypeForSignature(FileSignature signature) {
        return switch (signature) {
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case GIF -> "image/gif";
            case PDF -> "application/pdf";
            case ZIP -> "application/zip";
            case GZIP -> "application/gzip";
            case PE -> "application/vnd.microsoft.portable-executable";
            case ELF -> "application/x-elf";
            case MACH_O -> "application/x-mach-binary";
            case JAVA_CLASS -> "application/java-vm";
            case TAR -> "application/x-tar";
            case UNKNOWN -> null;
        };
    }
}
