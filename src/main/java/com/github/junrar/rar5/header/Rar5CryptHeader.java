package com.github.junrar.rar5.header;

/**
 * RAR5 archive encryption header (HEAD_CRYPT).
 *
 * <p>Present only in archives with encrypted headers. When present,
 * all subsequent headers are AES-256 encrypted.
 *
 * <p>Fields:
 * <ul>
 *   <li>Encryption version (vint): 0 = AES-256</li>
 *   <li>Encryption flags (vint): 0x0001 = Password check data present</li>
 *   <li>KDF count (1 byte): log2 of PBKDF2 iterations</li>
 *   <li>Salt (16 bytes)</li>
 *   <li>Check value (12 bytes, optional)</li>
 * </ul>
 */
public final class Rar5CryptHeader {

    /** Size of the salt in bytes. */
    public static final int SALT_SIZE = 16;

    /** Size of the password check value in bytes. */
    public static final int CHECK_VALUE_SIZE = 12;

    private final long version;
    private final long flags;
    private final int kdfCount;
    private final byte[] salt;
    private final byte[] checkValue;

    /**
     * Creates a new RAR5 crypt header.
     */
    public Rar5CryptHeader(final long version, final long flags, final int kdfCount,
                    final byte[] salt, final byte[] checkValue) {
        this.version = version;
        this.flags = flags;
        this.kdfCount = kdfCount;
        this.salt = salt.clone();
        this.checkValue = checkValue != null ? checkValue.clone() : null;
    }

    /**
     * @return encryption version (0 = AES-256)
     */
    public long getVersion() {
        return version;
    }

    /**
     * @return encryption flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return log2 of PBKDF2 iteration count
     */
    public int getKdfCount() {
        return kdfCount;
    }

    /**
     * @return the 16-byte salt
     */
    public byte[] getSalt() {
        return salt.clone();
    }

    /**
     * @return the 12-byte password check value, or null if not present
     */
    public byte[] getCheckValue() {
        return checkValue != null ? checkValue.clone() : null;
    }

    /**
     * @return true if password check data is present
     */
    public boolean hasCheckValue() {
        return checkValue != null;
    }
}
