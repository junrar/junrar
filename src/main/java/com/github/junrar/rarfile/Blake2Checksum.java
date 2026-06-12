package com.github.junrar.rarfile;

/**
 * BLAKE2sp implementation of {@link FileChecksum}.
 * <p>
 * Stores the 32-byte BLAKE2sp digest and presents it as a lowercase hex string.
 */
public class Blake2Checksum implements FileChecksum {

    private final byte[] digest;

    /**
     * Constructs a BLAKE2sp checksum from the 32-byte digest.
     *
     * @param digest the BLAKE2sp digest (32 bytes)
     */
    public Blake2Checksum(final byte[] digest) {
        this.digest = digest.clone();
    }

    @Override
    public ChecksumAlgorithm getAlgorithm() {
        return ChecksumAlgorithm.BLAKE2SP;
    }

    /**
     * Returns the 32-byte BLAKE2sp digest as a lowercase hex string (64 characters).
     */
    @Override
    public String getChecksum() {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Returns a copy of the raw 32-byte BLAKE2sp digest.
     *
     * @return cloned digest bytes
     */
    public byte[] getDigest() {
        return digest.clone();
    }
}
