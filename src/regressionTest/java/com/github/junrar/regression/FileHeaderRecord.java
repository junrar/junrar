package com.github.junrar.regression;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.junrar.rarfile.FileHeader;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Was a record until the test sources moved to release 8 (PR #289 review). Every JSON
 * property is pinned explicitly because the 12,475 reference files use the record-era
 * component names verbatim ({@code isSolid}, not the bean-convention {@code solid});
 * only the annotated accessors are exposed, so bean auto-detection cannot rename them.
 */
@JsonPropertyOrder({
    "archivalTime", "lastAccessTime", "lastModifiedTime", "creationTime", "fileCRC",
    "fileName", "hostOS", "fullPackSize", "fullUnpackSize", "isSolid", "isEncrypted",
    "isUnicode", "hasExtTime", "isLargeBlock", "isDirectory"
})
public final class FileHeaderRecord {
    private final Instant archivalTime;
    private final Instant lastAccessTime;
    private final Instant lastModifiedTime;
    private final Instant creationTime;
    private final int fileCRC;
    private final String fileName;
    private final String hostOS;
    private final long fullPackSize;
    private final long fullUnpackSize;
    private final boolean isSolid;
    private final boolean isEncrypted;
    private final boolean isUnicode;
    private final boolean hasExtTime;
    private final boolean isLargeBlock;
    private final boolean isDirectory;

    @JsonCreator
    public FileHeaderRecord(
        @JsonProperty("archivalTime") Instant archivalTime,
        @JsonProperty("lastAccessTime") Instant lastAccessTime,
        @JsonProperty("lastModifiedTime") Instant lastModifiedTime,
        @JsonProperty("creationTime") Instant creationTime,
        @JsonProperty("fileCRC") int fileCRC,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("hostOS") String hostOS,
        @JsonProperty("fullPackSize") long fullPackSize,
        @JsonProperty("fullUnpackSize") long fullUnpackSize,
        @JsonProperty("isSolid") boolean isSolid,
        @JsonProperty("isEncrypted") boolean isEncrypted,
        @JsonProperty("isUnicode") boolean isUnicode,
        @JsonProperty("hasExtTime") boolean hasExtTime,
        @JsonProperty("isLargeBlock") boolean isLargeBlock,
        @JsonProperty("isDirectory") boolean isDirectory
    ) {
        this.archivalTime = archivalTime;
        this.lastAccessTime = lastAccessTime;
        this.lastModifiedTime = lastModifiedTime;
        this.creationTime = creationTime;
        this.fileCRC = fileCRC;
        this.fileName = fileName;
        this.hostOS = hostOS;
        this.fullPackSize = fullPackSize;
        this.fullUnpackSize = fullUnpackSize;
        this.isSolid = isSolid;
        this.isEncrypted = isEncrypted;
        this.isUnicode = isUnicode;
        this.hasExtTime = hasExtTime;
        this.isLargeBlock = isLargeBlock;
        this.isDirectory = isDirectory;
    }

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

    @JsonProperty("archivalTime")
    public Instant archivalTime() {
        return archivalTime;
    }

    @JsonProperty("lastAccessTime")
    public Instant lastAccessTime() {
        return lastAccessTime;
    }

    @JsonProperty("lastModifiedTime")
    public Instant lastModifiedTime() {
        return lastModifiedTime;
    }

    @JsonProperty("creationTime")
    public Instant creationTime() {
        return creationTime;
    }

    @JsonProperty("fileCRC")
    public int fileCRC() {
        return fileCRC;
    }

    @JsonProperty("fileName")
    public String fileName() {
        return fileName;
    }

    @JsonProperty("hostOS")
    public String hostOS() {
        return hostOS;
    }

    @JsonProperty("fullPackSize")
    public long fullPackSize() {
        return fullPackSize;
    }

    @JsonProperty("fullUnpackSize")
    public long fullUnpackSize() {
        return fullUnpackSize;
    }

    @JsonProperty("isSolid")
    public boolean isSolid() {
        return isSolid;
    }

    @JsonProperty("isEncrypted")
    public boolean isEncrypted() {
        return isEncrypted;
    }

    @JsonProperty("isUnicode")
    public boolean isUnicode() {
        return isUnicode;
    }

    @JsonProperty("hasExtTime")
    public boolean hasExtTime() {
        return hasExtTime;
    }

    @JsonProperty("isLargeBlock")
    public boolean isLargeBlock() {
        return isLargeBlock;
    }

    @JsonProperty("isDirectory")
    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileHeaderRecord)) {
            return false;
        }
        FileHeaderRecord that = (FileHeaderRecord) o;
        return fileCRC == that.fileCRC
            && fullPackSize == that.fullPackSize
            && fullUnpackSize == that.fullUnpackSize
            && isSolid == that.isSolid
            && isEncrypted == that.isEncrypted
            && isUnicode == that.isUnicode
            && hasExtTime == that.hasExtTime
            && isLargeBlock == that.isLargeBlock
            && isDirectory == that.isDirectory
            && Objects.equals(archivalTime, that.archivalTime)
            && Objects.equals(lastAccessTime, that.lastAccessTime)
            && Objects.equals(lastModifiedTime, that.lastModifiedTime)
            && Objects.equals(creationTime, that.creationTime)
            && Objects.equals(fileName, that.fileName)
            && Objects.equals(hostOS, that.hostOS);
    }

    @Override
    public int hashCode() {
        return Objects.hash(archivalTime, lastAccessTime, lastModifiedTime, creationTime, fileCRC,
            fileName, hostOS, fullPackSize, fullUnpackSize, isSolid, isEncrypted, isUnicode,
            hasExtTime, isLargeBlock, isDirectory);
    }

    @Override
    public String toString() {
        return "FileHeaderRecord[fileName=" + fileName + ", fileCRC=" + fileCRC
            + ", fullPackSize=" + fullPackSize + ", fullUnpackSize=" + fullUnpackSize + "]";
    }
}
