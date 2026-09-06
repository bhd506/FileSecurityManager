package com.haydeproductions.project.config;

import java.util.Set;

public final class ConfigSyntax {

    public static final int CURRENT_VERSION = 1;

    public static final Set<String> ACTIONS = Set.of(
            "flag",
            "quarantine",
            "delete"
    );

    public static final Set<String> CUSTOM_CONDITIONS = Set.of(
            "and",
            "or",
            "not",
            "extension",
            "fileName",
            "path",
            "size",
            "modifiedTime",
            "hash",
            "mimeType",
            "fileSignature"
    );

    public static final Set<String> PREDEFINED_RULES = Set.of(
            "blockExtensions",
            "allowExtensions",
            "blockFileNames",
            "blockPaths",
            "maxFileSize",
            "minFileSize",
            "blockHashes",
            "allowHashes",
            "blockMimeTypes",
            "allowMimeTypes",
            "blockSignatures",
            "allowSignatures",
            "olderThan",
            "newerThan"
    );

    private ConfigSyntax() {
    }
}
