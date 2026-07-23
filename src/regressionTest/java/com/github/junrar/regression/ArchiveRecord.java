package com.github.junrar.regression;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Was a record until the test sources moved to release 8 (PR #289 review). JSON property
 * names are pinned explicitly to the record-era component names the 12,475 reference
 * files use verbatim ({@code isRarV5}, not the bean-convention {@code rarV5}); only the
 * annotated accessors are exposed.
 */
@JsonPropertyOrder({
    "isRarV5",
    "isEncrypted",
    "isPasswordProtected",
    "isOldFormat",
    "fileHeaders",
    "exception"
})
public final class ArchiveRecord {
    /** The 8-byte RAR 5.0 marker (52 61 72 21 1a 07 01 00). */
    private static final byte[] RAR5_SIGNATURE = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00};

    private final boolean isRarV5;
    private final boolean isEncrypted;
    private final boolean isPasswordProtected;
    private final boolean isOldFormat;
    private final List<FileHeaderRecord> fileHeaders;
    private final String exception;

    @JsonCreator
    public ArchiveRecord(
            @JsonProperty("isRarV5") boolean isRarV5,
            @JsonProperty("isEncrypted") boolean isEncrypted,
            @JsonProperty("isPasswordProtected") boolean isPasswordProtected,
            @JsonProperty("isOldFormat") boolean isOldFormat,
            @JsonProperty("fileHeaders") List<FileHeaderRecord> fileHeaders,
            @JsonProperty("exception") String exception) {
        this.isRarV5 = isRarV5;
        this.isEncrypted = isEncrypted;
        this.isPasswordProtected = isPasswordProtected;
        this.isOldFormat = isOldFormat;
        this.fileHeaders = fileHeaders;
        this.exception = exception;
    }

    static ArchiveRecord fromArchive(Archive from) throws RarException {
        return new ArchiveRecord(
                from.getFormat() == RarFormat.RAR50,
                from.isEncrypted(),
                from.isPasswordProtected(),
                from.isOldFormat(),
                from.getFileHeaders().stream()
                        .map(FileHeaderRecord::fromFileHeader)
                        .collect(Collectors.toList()),
                null);
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
                Collections.<FileHeaderRecord>emptyList(),
                from.getClass().getSimpleName());
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

    @JsonProperty("isRarV5")
    public boolean isRarV5() {
        return isRarV5;
    }

    @JsonProperty("isEncrypted")
    public boolean isEncrypted() {
        return isEncrypted;
    }

    @JsonProperty("isPasswordProtected")
    public boolean isPasswordProtected() {
        return isPasswordProtected;
    }

    @JsonProperty("isOldFormat")
    public boolean isOldFormat() {
        return isOldFormat;
    }

    @JsonProperty("fileHeaders")
    public List<FileHeaderRecord> fileHeaders() {
        return fileHeaders;
    }

    @JsonProperty("exception")
    public String exception() {
        return exception;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArchiveRecord)) {
            return false;
        }
        ArchiveRecord that = (ArchiveRecord) o;
        return isRarV5 == that.isRarV5
                && isEncrypted == that.isEncrypted
                && isPasswordProtected == that.isPasswordProtected
                && isOldFormat == that.isOldFormat
                && Objects.equals(fileHeaders, that.fileHeaders)
                && Objects.equals(exception, that.exception);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isRarV5, isEncrypted, isPasswordProtected, isOldFormat, fileHeaders, exception);
    }

    @Override
    public String toString() {
        return "ArchiveRecord[isRarV5="
                + isRarV5
                + ", isEncrypted="
                + isEncrypted
                + ", isPasswordProtected="
                + isPasswordProtected
                + ", isOldFormat="
                + isOldFormat
                + ", fileHeaders="
                + fileHeaders
                + ", exception="
                + exception
                + "]";
    }
}
