package com.haydeproductions.project.rule;

import com.haydeproductions.project.file.FileFingerprint;
import com.haydeproductions.project.file.FileFingerprintService;
import com.haydeproductions.project.file.FileSignature;
import com.haydeproductions.project.file.FileSignatureDetector;
import com.haydeproductions.project.file.MimeTypeDetector;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FileContext {

    private final Path path;
    private final Path normalizedPath;
    private final FileFingerprintService fingerprintService;
    private final FileSignatureDetector signatureDetector;
    private final MimeTypeDetector mimeTypeDetector;

    private BasicFileAttributes attributes;
    private FileFingerprint fingerprint;
    private FileSignature signature;
    private String mimeType;
    private List<String> extensions;

    public FileContext(Path path) {
        this(
                path,
                new FileFingerprintService(),
                new FileSignatureDetector(),
                new MimeTypeDetector()
        );
    }

    public FileContext(
            Path path,
            FileFingerprintService fingerprintService,
            FileSignatureDetector signatureDetector,
            MimeTypeDetector mimeTypeDetector
    ) {
        this.path = Objects.requireNonNull(path);
        this.normalizedPath = path.toAbsolutePath().normalize();
        this.fingerprintService = Objects.requireNonNull(fingerprintService);
        this.signatureDetector = Objects.requireNonNull(signatureDetector);
        this.mimeTypeDetector = Objects.requireNonNull(mimeTypeDetector);
    }

    public Path getPath() {
        return path;
    }

    public Path getNormalizedPath() {
        return normalizedPath;
    }

    public String getFileName() {
        Path fileName = normalizedPath.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    public List<String> getExtensions() {
        if (extensions == null) {
            extensions = calculateExtensions(getFileName());
        }

        return extensions;
    }

    public long getSize() throws IOException {
        if (fingerprint != null) {
            return fingerprint.size();
        }

        return getAttributes().size();
    }

    public FileTime getLastModifiedTime() throws IOException {
        return getAttributes().lastModifiedTime();
    }

    public String getSha256() throws IOException {
        return getFingerprint().sha256();
    }

    public FileSignature getFileSignature() throws IOException {
        if (signature == null) {
            signature = signatureDetector.detect(normalizedPath);
        }

        return signature;
    }

    public String getMimeType() throws IOException {
        if (mimeType == null) {
            mimeType = mimeTypeDetector.detect(
                    normalizedPath,
                    getFileSignature()
            );
        }

        return mimeType;
    }

    private BasicFileAttributes getAttributes() throws IOException {
        if (attributes == null) {
            attributes = Files.readAttributes(
                    normalizedPath,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
        }

        return attributes;
    }

    private FileFingerprint getFingerprint() throws IOException {
        if (fingerprint == null) {
            fingerprint = fingerprintService.fingerprint(normalizedPath);
        }

        return fingerprint;
    }

    private static List<String> calculateExtensions(String fileName) {
        int firstDot = fileName.indexOf('.');

        if (firstDot <= 0 || firstDot == fileName.length() - 1) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for (int dot = firstDot;
             dot >= 0 && dot < fileName.length() - 1;
             dot = fileName.indexOf('.', dot + 1)) {

            if (dot == 0) {
                continue;
            }

            String extension = fileName
                    .substring(dot + 1);

            if (!extension.isBlank()) {
                result.add(extension);
            }
        }

        return List.copyOf(result);
    }
}
