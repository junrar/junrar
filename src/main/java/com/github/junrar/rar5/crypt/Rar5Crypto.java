package com.github.junrar.rar5.crypt;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * RAR5 encryption utilities.
 *
 * <p>Handles PBKDF2-HMAC-SHA256 key derivation, password validation,
 * and AES-256-CBC cipher creation for RAR5 archives.
 *
 * <p>The key derivation produces three values from PBKDF2:
 * <ul>
 *   <li>Encryption key (32 bytes) — at iteration count N</li>
 *   <li>Hash key (32 bytes) — at iteration count N+16</li>
 *   <li>Check value (12 bytes) — at iteration count N+32, XOR-folded</li>
 * </ul>
 */
public final class Rar5Crypto {

    /** AES-256 key size in bytes */
    public static final int KEY_SIZE = 32;

    /** HMAC-SHA256 output size in bytes */
    private static final int HMAC_SIZE = 32;

    /** Password check value size in bytes */
    public static final int CHECK_VALUE_SIZE = 8;

    /** Number of extra PBKDF2 iterations for hash key (V1) */
    private static final int HASH_KEY_EXTRA_ITERATIONS = 16;

    /** Number of extra PBKDF2 iterations for check value (V2) */
    private static final int CHECK_VALUE_EXTRA_ITERATIONS = 32;

    private Rar5Crypto() {
    }

    /**
     * Derived key material from PBKDF2 key derivation.
     */
    public static final class DerivedKeys {
        private final byte[] encryptionKey;
        private final byte[] hashKey;
        private final byte[] checkValue;

        private DerivedKeys(final byte[] encryptionKey, final byte[] hashKey,
                            final byte[] checkValue) {
            this.encryptionKey = encryptionKey;
            this.hashKey = hashKey;
            this.checkValue = checkValue;
        }

        /** @return 32-byte AES-256 encryption key */
        public byte[] getEncryptionKey() {
            return encryptionKey;
        }

        /** @return 32-byte HMAC key for MAC-transformed checksums */
        public byte[] getHashKey() {
            return hashKey;
        }

        /** @return 12-byte password check value */
        public byte[] getCheckValue() {
            return checkValue;
        }
    }

    /**
     * Derives encryption keys from password and salt using PBKDF2-HMAC-SHA256.
     *
     * <p>Matches the C++ implementation exactly: a single PBKDF2 loop is run,
     * and three values are extracted at different points:
     * <ul>
     *   <li>Encryption key: XOR of U1 through U_N (at iteration N)</li>
     *   <li>Hash key: XOR of U_(N+1) through U_(N+16)</li>
     *   <li>Check value: XOR of U_(N+17) through U_(N+32), then XOR-folded to 12 bytes</li>
     * </ul>
     *
     * @param password the archive password (UTF-8 encoded)
     * @param salt     the 16-byte salt from the encryption header
     * @param kdfCount log2 of the iteration count (e.g., 16 = 65536 iterations)
     * @return derived keys (encryption key, hash key, check value)
     * @throws GeneralSecurityException if the crypto operations fail
     */
    public static DerivedKeys deriveKeys(final String password, final byte[] salt,
                                         final int kdfCount) throws GeneralSecurityException {
        final byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        final int baseIterations = 1 << kdfCount;

        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));

        // Build salt || blockIndex (big-endian 0x00000001)
        final byte[] saltWithIndex = new byte[salt.length + 4];
        System.arraycopy(salt, 0, saltWithIndex, 0, salt.length);
        saltWithIndex[salt.length] = 0;
        saltWithIndex[salt.length + 1] = 0;
        saltWithIndex[salt.length + 2] = 0;
        saltWithIndex[salt.length + 3] = 1;

        // U1 = HMAC(password, salt || 1)
        byte[] u = mac.doFinal(saltWithIndex);

        // Accumulator starts as U1
        final byte[] fn = u.clone();

        // Encryption key: XOR of U1 through U_N (Count-1 more iterations)
        for (int i = 1; i < baseIterations; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < HMAC_SIZE; j++) {
                fn[j] ^= u[j];
            }
        }
        final byte[] encryptionKey = fn.clone();

        // Hash key: XOR of U1 through U_(N+16) (16 more iterations)
        for (int i = 0; i < HASH_KEY_EXTRA_ITERATIONS; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < HMAC_SIZE; j++) {
                fn[j] ^= u[j];
            }
        }
        final byte[] hashKey = fn.clone();

        // Check value: XOR of U1 through U_(N+32) (16 more iterations), then XOR-folded to 12 bytes
        for (int i = 0; i < HASH_KEY_EXTRA_ITERATIONS; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < HMAC_SIZE; j++) {
                fn[j] ^= u[j];
            }
        }
        final byte[] checkValue = xorFold(fn);

        return new DerivedKeys(encryptionKey, hashKey, checkValue);
    }

    /**
     * Validates a password by comparing the computed check value against
     * the stored check value from the archive header.
     *
     * @param computedCheck  the 12-byte check value derived from the password
     * @param storedCheck    the 12-byte check value stored in the archive
     * @return true if the password is correct
     */
    public static boolean validatePassword(final byte[] computedCheck, final byte[] storedCheck) {
        if (computedCheck == null || storedCheck == null) {
            return false;
        }
        if (computedCheck.length != CHECK_VALUE_SIZE || storedCheck.length != CHECK_VALUE_SIZE) {
            return false;
        }
        // Constant-time comparison to prevent timing attacks
        int result = 0;
        for (int i = 0; i < CHECK_VALUE_SIZE; i++) {
            result |= computedCheck[i] ^ storedCheck[i];
        }
        return result == 0;
    }

    /**
     * Creates an AES-256-CBC decipherer for RAR5 file data.
     *
     * @param key the 32-byte encryption key
     * @param iv  the 16-byte initialization vector
     * @return a Cipher configured for AES-256-CBC decryption
     * @throws GeneralSecurityException if the cipher cannot be created
     */
    public static Cipher createDecipherer(final byte[] key, final byte[] iv)
            throws GeneralSecurityException {
        final javax.crypto.SecretKey keySpec = new SecretKeySpec(key, "AES");
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher;
    }

    /**
     * Converts a CRC32 value to a MAC-transformed CRC32 using the hash key.
     *
     * <p>When the FHEXTRA_CRYPT_HASHMAC flag is set, CRC32 checksums are
     * converted to HMAC values to prevent tampering.
     *
     * @param crc32   the original CRC32 value
     * @param hashKey the 32-byte hash key from key derivation
     * @return the MAC-transformed CRC32 value
     * @throws GeneralSecurityException if HMAC computation fails
     */
    public static int convertCrc32ToMac(final int crc32, final byte[] hashKey)
            throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));

        // Feed CRC32 as 4 bytes (little-endian) into HMAC
        final byte[] crcBytes = new byte[4];
        crcBytes[0] = (byte) (crc32 & 0xFF);
        crcBytes[1] = (byte) ((crc32 >>> 8) & 0xFF);
        crcBytes[2] = (byte) ((crc32 >>> 16) & 0xFF);
        crcBytes[3] = (byte) ((crc32 >>> 24) & 0xFF);
        final byte[] hmac = mac.doFinal(crcBytes);

        // XOR-fold 32-byte HMAC back to 4 bytes
        int result = 0;
        for (int i = 0; i < HMAC_SIZE; i++) {
            result ^= (hmac[i] & 0xFF) << ((i & 3) * 8);
        }
        return result;
    }

    /**
     * XOR-folds a 32-byte SHA-256 digest down to a 12-byte check value.
     *
     * <p>Matches the C++ logic:
     * <pre>
     * for (uint I = 0; I &lt; 32; I++)
     *     PswCheck[I % 12] ^= digest[I];
     * </pre>
     *
     * @param digest the 32-byte digest
     * @return the 12-byte XOR-folded check value
     */
    private static byte[] xorFold(final byte[] digest) {
        final byte[] result = new byte[CHECK_VALUE_SIZE];
        for (int i = 0; i < HMAC_SIZE; i++) {
            result[i % CHECK_VALUE_SIZE] ^= digest[i];
        }
        return result;
    }
}
