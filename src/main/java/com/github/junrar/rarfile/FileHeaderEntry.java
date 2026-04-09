package com.github.junrar.rarfile;

import java.nio.file.attribute.FileTime;
import java.util.Date;

/**
 * Common interface for file headers across RAR versions.
 *
 * <p>This interface provides the methods needed by the extraction engine
 * to work with both RAR4 ({@link FileHeader}) and RAR5 ({@code Rar5FileHeader})
 * file headers transparently.
 */
public interface FileHeaderEntry {

    /**
     * @return the file name as a String
     */
    String getFileName();

    /**
     * @return the unpacked file size
     */
    long getFullUnpackSize();

    /**
     * @return the packed (compressed) file size
     */
    long getFullPackSize();

    /**
     * @return true if this entry is a directory
     */
    boolean isDirectory();

    /**
     * @return true if this entry is encrypted
     */
    boolean isEncrypted();

    /**
     * @return true if this entry is part of a solid archive
     */
    boolean isSolid();

    /**
     * @return true if this entry continues in the next volume
     */
    boolean isSplitAfter();

    /**
     * @return true if this entry continues from the previous volume
     */
    boolean isSplitBefore();

    /**
     * @return the CRC32 of the unpacked data
     */
    int getFileCRC();

    /**
     * @return the compression method version
     */
    byte getUnpVersion();

    /**
     * @return the compression method (0=stored, 1-5 for RAR5, 15-36 for RAR4)
     */
    byte getUnpMethod();

    /**
     * @return the host OS byte value
     */
    byte getHostOSByte();

    /**
     * @return the modification time, or null if not available
     */
    Date getMTime();

    /**
     * @return the creation time, or null if not available
     */
    Date getCTime();

    /**
     * @return the last access time, or null if not available
     */
    Date getATime();

    /**
     * @return the archival time, or null if not available
     */
    FileTime getArchivalTime();

    /**
     * @return the file position where this entry's data starts
     */
    long getPositionInFile();

    /**
     * @return the size of this header in bytes (not encrypted-padded)
     */
    short getHeaderSize();

    /**
     * @return the encryption salt bytes, or empty array if not encrypted
     */
    byte[] getSalt();
}
