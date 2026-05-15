package com.github.junrar.rarfile;

import java.util.Locale;

/**
 * CRC32 implementation of {@link FileChecksum}.
 * <p>
 * The underlying 32-bit CRC value is stored as a signed {@code int}
 * but is interpreted as unsigned when formatted (see {@link #getChecksum()}).
 */
public class CrcChecksum implements FileChecksum {

    /**
     * The raw CRC32 checksum value (unsigned 32-bit, stored as signed int).
     */
    public final int checksum;

    /**
     * Constructs a CRC32 checksum from the raw 32-bit value.
     *
     * @param checksum the raw CRC32 integer (little-endian)
     */
    public CrcChecksum(final int checksum) {
        this.checksum = checksum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChecksumAlgorithm getAlgorithm() {
        return ChecksumAlgorithm.CRC32;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getChecksum() {
        return Long.toHexString(checksum & 0xFFFFFFFFL).toUpperCase(Locale.ROOT);
    }
}
