package com.github.junrar.crypt;

import com.github.junrar.exception.CorruptHeaderException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * RAR5 crypto primitives (M3.4, issue #25). Ports the format math of unrar
 * {@code d861246:crypt5.cpp} ({@code pbkdf2} :85-128, {@code SetKey50} :131-190,
 * {@code ConvertHashToMAC} :193-212) while taking every primitive from the JDK (manual
 * &sect;4.11): {@code Mac("HmacSHA256")}, {@code Cipher("AES/CBC/NoPadding")},
 * {@code MessageDigest("SHA-256")}.
 * <p>
 * The KDF is a hand-rolled single-pass PBKDF2 over one reused {@code Mac} (its cached inner
 * key schedule is the Java analog of upstream's {@code ICtxOpt}/{@code RCtxOpt}); it is NOT
 * {@code SecretKeyFactory("PBKDF2WithHmacSHA256")}, which Java 8 does not require any provider
 * to ship (manual &sect;2.1). One pass yields the AES key (at {@code Count} iterations), the
 * HMAC hash key (at {@code Count+16}) and the password-check source (at {@code Count+32}) --
 * the {@code {Count-1,16,16}} segment loop of {@code crypt5.cpp:104-122}.
 * <p>
 * A 4-entry KDF cache ({@code d861246:crypt.hpp:88}, {@code KDF5Cache[4]}) avoids re-running
 * the ~2^15 HMAC iterations for every header/file of a same-password archive.
 */
public final class Rar5Crypt {

    public static final int SIZE_SALT50 = 16;
    public static final int SIZE_INITV = 16;
    public static final int SIZE_PSWCHECK = 8;
    public static final int SIZE_PSWCHECK_CSUM = 4;
    public static final int CRYPT5_KDF_LG2_COUNT_MAX = 24;

    private static final int SHA256_DIGEST_SIZE = 32;
    private static final int AES_KEY_SIZE = 32;

    private Rar5Crypt() {
    }

    // ---- KDF cache (unrar KDF5Cache[4], d861246:crypt.hpp:88) ---------------------------

    private static final class CacheItem {
        final byte[] pwd;
        final byte[] salt;
        final int lg2Count;
        final Kdf kdf;

        CacheItem(final byte[] pwd, final byte[] salt, final int lg2Count, final Kdf kdf) {
            this.pwd = pwd;
            this.salt = salt;
            this.lg2Count = lg2Count;
            this.kdf = kdf;
        }
    }

    private static final CacheItem[] CACHE = new CacheItem[4];
    private static int cachePos = 0;

    /** The three derived KDF values for one (password, salt, iteration count). */
    public static final class Kdf {
        /** 32-byte AES-256 key (PBKDF2 output at {@code Count} iterations). */
        public final byte[] aesKey;
        /** 32-byte HMAC key for {@code ConvertHashToMAC} (at {@code Count+16}). */
        public final byte[] hashKey;
        /** 8-byte password check value, folded from the {@code Count+32} output. */
        public final byte[] pswCheck;

        Kdf(final byte[] aesKey, final byte[] hashKey, final byte[] pswCheck) {
            this.aesKey = aesKey;
            this.hashKey = hashKey;
            this.pswCheck = pswCheck;
        }
    }

    /**
     * Derives the AES key, HMAC hash key and password-check value for a RAR5 password
     * (unrar {@code SetKey50}, {@code d861246:crypt5.cpp:131-190}). Result-cached across calls.
     *
     * @param pwdUtf8  the password as UTF-8 bytes (see {@link #passwordToUtf8})
     * @param salt16   the 16-byte KDF salt
     * @param lg2Count the KDF iteration count log2 (iterations = {@code 1 << lg2Count})
     * @return the derived key material
     * @throws CorruptHeaderException if {@code lg2Count} exceeds {@link #CRYPT5_KDF_LG2_COUNT_MAX}
     */
    public static synchronized Kdf deriveKey(final byte[] pwdUtf8, final byte[] salt16, final int lg2Count)
        throws CorruptHeaderException {
        if (lg2Count > CRYPT5_KDF_LG2_COUNT_MAX) {
            throw new CorruptHeaderException("RAR5 KDF Lg2Count exceeds " + CRYPT5_KDF_LG2_COUNT_MAX);
        }
        for (final CacheItem item : CACHE) {
            if (item != null && item.lg2Count == lg2Count
                && Arrays.equals(item.pwd, pwdUtf8) && Arrays.equals(item.salt, salt16)) {
                return item.kdf;
            }
        }
        final byte[][] out = pbkdf2(pwdUtf8, salt16, 1 << lg2Count);
        final byte[] pswCheck = new byte[SIZE_PSWCHECK];
        // SetKey50: PswCheck[i % SIZE_PSWCHECK] ^= V2[i] (crypt5.cpp:177-179).
        for (int i = 0; i < SHA256_DIGEST_SIZE; i++) {
            pswCheck[i % SIZE_PSWCHECK] ^= out[2][i];
        }
        final Kdf kdf = new Kdf(out[0], out[1], pswCheck);
        CACHE[cachePos++ % CACHE.length] = new CacheItem(pwdUtf8.clone(), salt16.clone(), lg2Count, kdf);
        return kdf;
    }

    /**
     * Single-pass PBKDF2-HMAC-SHA256 producing the RAR5 output triple (unrar {@code pbkdf2},
     * {@code d861246:crypt5.cpp:85-128}): {@code [0]} = key at {@code count} iterations,
     * {@code [1]} = value at {@code count+16}, {@code [2]} = value at {@code count+32}, each 32
     * bytes. The block index is always 1 (RAR5 never requests more than one HMAC width).
     * Package-private: exposed for the mandated KDF known-answer tests.
     */
    static byte[][] pbkdf2(final byte[] pwd, final byte[] salt, final int count) {
        final Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pwd.length == 0 ? new byte[1] : pwd, "HmacSHA256"));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }

        final byte[] saltData = new byte[salt.length + 4];
        System.arraycopy(salt, 0, saltData, 0, salt.length);
        saltData[salt.length + 3] = 1; // Block index 1, big-endian, appended to salt.

        // U1 = PRF(pwd, salt || 1); Fn = U1.
        byte[] u1 = mac.doFinal(saltData);
        final byte[] fn = u1.clone();

        final int[] curCount = {count - 1, 16, 16};
        final byte[][] result = new byte[3][];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < curCount[i]; j++) {
                u1 = mac.doFinal(u1); // U = PRF(pwd, U); reuses the cached key schedule.
                for (int k = 0; k < SHA256_DIGEST_SIZE; k++) {
                    fn[k] ^= u1[k];
                }
            }
            result[i] = fn.clone();
        }
        return result;
    }

    /**
     * Serializes a password to UTF-8 (unrar {@code WideToUtf}, {@code crypt5.cpp:158-160}) --
     * NOT the platform charset (the RAR3 T4 wart, manual &sect;6). Works from the {@code char[]}
     * directly so the caller's password never becomes an unwipeable {@code String}.
     */
    public static byte[] passwordToUtf8(final char[] password) {
        final ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        final byte[] out = new byte[bb.remaining()];
        bb.get(out);
        Arrays.fill(bb.array(), (byte) 0);
        return out;
    }

    /**
     * AES-256/CBC decryption cipher for a per-header or per-file 16-byte IV (manual &sect;4.11:
     * primitives from the JDK).
     */
    public static Cipher buildDecipherer(final byte[] aesKey32, final byte[] iv16) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey32, "AES"), new IvParameterSpec(iv16));
        return cipher;
    }

    /**
     * Validates a stored password-check checksum: the low {@link #SIZE_PSWCHECK_CSUM} bytes of
     * {@code SHA-256(pswCheck)} (unrar {@code arcread.cpp:752-755}). unrar treats a mismatch as
     * "no usable pswcheck" rather than a hard error, so parsing keeps the flag off.
     */
    public static boolean pswCheckCsumValid(final byte[] pswCheck8, final byte[] csum4) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(pswCheck8);
            for (int i = 0; i < SIZE_PSWCHECK_CSUM; i++) {
                if (digest[i] != csum4[i]) {
                    return false;
                }
            }
            return true;
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * {@code ConvertHashToMAC} for a CRC32 checksum (unrar {@code crypt5.cpp:193-205}): the
     * stored CRC is HMAC-masked with the hash key so it leaks no plaintext information. The
     * Blake2 variant is deferred to M3.5 (Blake2sp).
     *
     * @param crc32     the raw little-endian CRC32 value
     * @param hashKey32 the 32-byte HMAC hash key from {@link Kdf#hashKey}
     * @return the MAC-converted 32-bit checksum
     */
    public static int convertCrc32ToMac(final int crc32, final byte[] hashKey32) {
        final byte[] rawCrc = {
            (byte) crc32, (byte) (crc32 >>> 8), (byte) (crc32 >>> 16), (byte) (crc32 >>> 24)
        };
        final byte[] digest;
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey32, "HmacSHA256"));
            digest = mac.doFinal(rawCrc);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
        int result = 0;
        for (int i = 0; i < SHA256_DIGEST_SIZE; i++) {
            result ^= (digest[i] & 0xff) << ((i & 3) * 8);
        }
        return result;
    }
}
