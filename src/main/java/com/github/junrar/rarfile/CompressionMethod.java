package com.github.junrar.rarfile;

/**
 * Represents the compression method used in a RAR archive.
 * <p>
 * This enum abstracts away version-specific differences between RAR4 and RAR5 formats:
 * <ul>
 *   <li>RAR4: Method {@code 0x30} means stored (no compression)</li>
 *   <li>RAR5: Method {@code 0} means stored (algorithm v0)</li>
 * </ul>
 */
public enum CompressionMethod {
    /**
     * The file is stored without compression.
     */
    STORED,

    /**
     * The file is compressed using a compression algorithm.
     */
    COMPRESSED
}
