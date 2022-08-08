package com.github.junrar.regression;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public record ArchiveRecord(
    boolean isRarV5,
    boolean isEncrypted,
    boolean isPasswordProtected,
    boolean isOldFormat,
    List<FileHeaderRecord> fileHeaders,

    String exception
) {
    static ArchiveRecord fromArchive(Archive from) throws RarException {
        return new ArchiveRecord(
            false,
            from.isEncrypted(),
            from.isPasswordProtected(),
            from.isOldFormat(),
            from.getFileHeaders().stream().map(FileHeaderRecord::fromFileHeader).collect(Collectors.toList()),
            null
        );
    }

    static ArchiveRecord fromException(RarException from) {
        return new ArchiveRecord(
            from instanceof UnsupportedRarV5Exception,
            false,
            false,
            false,
            Collections.emptyList(),
            from.getClass().getSimpleName()
        );
    }
}
