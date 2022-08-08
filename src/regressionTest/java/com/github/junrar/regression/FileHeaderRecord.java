package com.github.junrar.regression;

import com.github.junrar.rarfile.FileHeader;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

public record FileHeaderRecord(
    Instant archivalTime,
    Instant lastAccessTime,
    Instant lastModifiedTime,
    Instant creationTime,
    int fileCRC,
    String fileName,
    String hostOS,
    long fullPackSize,
    long fullUnpackSize,
    boolean isSolid,
    boolean isEncrypted,
    boolean isUnicode,
    boolean hasExtTime,
    boolean isLargeBlock,
    boolean isDirectory
) {
    static FileHeaderRecord fromFileHeader(FileHeader from) {
        return new FileHeaderRecord(
            Optional.ofNullable(from.getArchivalTime()).map(FileTime::toInstant).orElse(null),
            Optional.ofNullable(from.getLastAccessTime()).map(FileTime::toInstant).orElse(null),
            Optional.ofNullable(from.getLastModifiedTime()).map(FileTime::toInstant).orElse(null),
            Optional.ofNullable(from.getCreationTime()).map(FileTime::toInstant).orElse(null),
            from.getFileCRC(),
            from.getFileName(),
            from.getHostOS().name(),
            from.getFullPackSize(),
            from.getFullUnpackSize(),
            from.isSolid(),
            from.isEncrypted(),
            from.isUnicode(),
            from.hasExtTime(),
            from.isLargeBlock(),
            from.isDirectory()
        );
    }
}
