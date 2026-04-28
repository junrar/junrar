package com.github.junrar.rar5.header.extra;

/**
 * File encryption extra record (FHEXTRA_CRYPT = 0x01).
 *
 * <p>Fields:
 * <ul>
 *   <li>Encryption version (vint): 0 = AES-256</li>
 *   <li>Flags (vint): PswCheck (0x01), HashMAC (0x02)</li>
 *   <li>KDF count (1 byte): log2 of PBKDF2 iterations</li>
 *   <li>Salt (16 bytes)</li>
 *   <li>IV (16 bytes)</li>
 *   <li>Check value (12 bytes, optional)</li>
 *   <li>Check value checksum (4 bytes, optional)</li>
 * </ul>
 */
public final class Rar5FileCryptRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x01;

    /** Password check data present flag. */
    public static final int FHEXTRA_CRYPT_PSWCHECK = 0x01;

    /** Use MAC for unpacked data checksums flag. */
    public static final int FHEXTRA_CRYPT_HASHMAC = 0x02;

    /** Size of the salt in bytes. */
    public static final int SALT_SIZE = 16;

    /** Size of the IV in bytes. */
    public static final int IV_SIZE = 16;

    /** Size of the password check value in bytes. */
    public static final int PSWCHECK_SIZE = 8;

    /** Size of the password check checksum in bytes. */
    public static final int PSWCHECK_CSUM_SIZE = 4;

    private final long version;
    private final long flags;
    private final int kdfCount;
    private final byte[] salt;
    private final byte[] iv;
    private final byte[] pswCheck;
    private final boolean hasPswCheck;
    private final boolean hasHashMac;

    Rar5FileCryptRecord(final long version, final long flags,
                                final int kdfCount, final byte[] salt,
                                final byte[] iv, final byte[] pswCheck,
                                final boolean hasPswCheck, final boolean hasHashMac) {
        this.version = version;
        this.flags = flags;
        this.kdfCount = kdfCount;
        this.salt = salt;
        this.iv = iv;
        this.pswCheck = pswCheck;
        this.hasPswCheck = hasPswCheck;
        this.hasHashMac = hasHashMac;
    }

    @Override
    public long getRecordType() {
        return TYPE;
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
     * @return the 16-byte initialization vector
     */
    public byte[] getIv() {
        return iv.clone();
    }

    /**
     * @return the 12-byte password check value, or null if not present
     */
    public byte[] getPswCheck() {
        return pswCheck != null ? pswCheck.clone() : null;
    }

    /**
     * @return true if password check data is present
     */
    public boolean hasPswCheck() {
        return hasPswCheck;
    }

    /**
     * @return true if MAC is used for unpacked data checksums
     */
    public boolean hasHashMac() {
        return hasHashMac;
    }
}
