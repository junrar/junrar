package com.github.junrar.impl;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.RarException;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.BaseBlock;

import java.io.IOException;
import java.util.List;

/**
 * Interface for reading RAR archive headers.
 * Implementations handle different RAR versions (RAR4, RAR5).
 */
public interface HeaderReader {

    /**
     * Maximum allowed header size to prevent memory exhaustion attacks (20MB).
     */
    int MAX_HEADER_SIZE = 20971520;

    /**
     * Reads all headers from the RAR archive.
     *
     * @param channel    the seekable byte channel to read from
     * @param fileLength the total length of the archive file
     * @param password   the password for encrypted archives (may be null)
     * @throws IOException  if an I/O error occurs
     * @throws RarException if a RAR-specific error occurs
     */
    void readHeaders(SeekableReadOnlyByteChannel channel, long fileLength, String password)
            throws IOException, RarException;

    /**
     * Returns the list of headers read from the archive.
     *
     * @return list of BaseBlock headers
     */
    List<BaseBlock> getHeaders();

    /**
     * Safely allocates a byte array with bounds checking.
     * Prevents allocating excessively large buffers that could cause
     * OutOfMemoryError or heap exhaustion attacks.
     *
     * @param len the requested length in bytes
     * @return a new byte array of the requested size
     * @throws BadRarArchiveException if len is negative or exceeds MAX_HEADER_SIZE
     */
    default byte[] safelyAllocate(final long len) throws RarException {
        if (len < 0 || len > MAX_HEADER_SIZE) {
            throw new BadRarArchiveException();
        }
        return new byte[(int) len];
    }
}
