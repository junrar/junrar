package com.github.junrar.regression;

import com.github.junrar.Archive;
import com.github.junrar.RarFormat;
import com.github.junrar.exception.RarException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
    /** The 8-byte RAR 5.0 marker (52 61 72 21 1a 07 01 00). */
    private static final byte[] RAR5_SIGNATURE = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00};

    static ArchiveRecord fromArchive(Archive from) throws RarException {
        return new ArchiveRecord(
            from.getFormat() == RarFormat.RAR50,
            from.isEncrypted(),
            from.isPasswordProtected(),
            from.isOldFormat(),
            from.getFileHeaders().stream().map(FileHeaderRecord::fromFileHeader).collect(Collectors.toList()),
            null
        );
    }

    static ArchiveRecord fromException(RarException from, Path filePath) {
        // The archive failed to open, so getFormat() is unavailable; derive isRarV5 from the
        // file's leading signature bytes instead (B-S2). Pre-flip this equals the old
        // "instanceof UnsupportedRarV5Exception" result for every corpus record.
        return new ArchiveRecord(
            hasRar5Signature(filePath),
            false,
            false,
            false,
            Collections.emptyList(),
            from.getClass().getSimpleName()
        );
    }

    private static boolean hasRar5Signature(Path filePath) {
        byte[] head = new byte[RAR5_SIGNATURE.length];
        try (InputStream in = Files.newInputStream(filePath)) {
            int off = 0;
            int n;
            while (off < head.length && (n = in.read(head, off, head.length - off)) > 0) {
                off += n;
            }
            return off == head.length && Arrays.equals(head, RAR5_SIGNATURE);
        } catch (IOException e) {
            return false;
        }
    }
}
