package com.github.junrar.rarfile;

/**
 * Checksum of a file entry in a RAR archive.
 */
public interface FileChecksum {

    /**
     * @return the algorithm used for this checksum
     */
    ChecksumAlgorithm getAlgorithm();

    /**
     * @return the checksum value as a string
     */
    String getChecksum();
}
