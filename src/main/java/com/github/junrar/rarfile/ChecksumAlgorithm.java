package com.github.junrar.rarfile;

/**
 * Checksum algorithm used for file data integrity verification.
 */
public enum ChecksumAlgorithm {
    /** 32-bit CRC. */
    CRC32,
    /** BLAKE2sp hash. */
    BLAKE2SP,
    /** No checksum available. */
    NONE
}
