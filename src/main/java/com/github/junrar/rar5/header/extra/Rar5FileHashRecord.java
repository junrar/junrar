package com.github.junrar.rar5.header.extra;

/**
 * File hash extra record (FHEXTRA_HASH = 0x02).
 *
 * <p>Fields:
 * <ul>
 *   <li>Hash type (vint): 0 = BLAKE2sp</li>
 *   <li>Hash data (32 bytes for BLAKE2sp)</li>
 * </ul>
 */
public final class Rar5FileHashRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x02;

    /** BLAKE2sp hash type. */
    public static final long HASH_BLAKE2 = 0x00;

    /** Size of BLAKE2sp digest in bytes. */
    public static final int BLAKE2_DIGEST_SIZE = 32;

    private final long hashType;
    private final byte[] digest;

    Rar5FileHashRecord(final long hashType, final byte[] digest) {
        this.hashType = hashType;
        this.digest = digest;
    }

    @Override
    public long getRecordType() {
        return TYPE;
    }

    /**
     * @return hash type (0 = BLAKE2sp)
     */
    public long getHashType() {
        return hashType;
    }

    /**
     * @return the hash digest bytes
     */
    public byte[] getDigest() {
        return digest.clone();
    }

    /**
     * @return true if this is a BLAKE2sp hash
     */
    public boolean isBlake2() {
        return hashType == HASH_BLAKE2;
    }
}
