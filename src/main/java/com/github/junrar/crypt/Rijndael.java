/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.crypt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Rijndael {
    public static Cipher buildDecipherer(final String password, byte[] salt) throws IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (password == null) {
            throw new InvalidAlgorithmParameterException("password should be specified");
        }
        byte[] AESInit = new byte[16];
        byte[] AESKey = new byte[16];
        int rawLength = 2 * password.length();
        byte[] rawpsw = new byte[rawLength + 8];
        byte[] pwd = password.getBytes();
        for (int i = 0; i < password.length(); i++) {
            rawpsw[i * 2] = pwd[i];
            rawpsw[i * 2 + 1] = 0;
        }
        System.arraycopy(salt, 0, rawpsw, rawLength, salt.length);

        MessageDigest sha = MessageDigest.getInstance("sha-1");

        final int HashRounds = 0x40000;
        final int xh = HashRounds / 16;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] digest = null;

        for (int i = 0; i < HashRounds; i++) {
            bout.write(rawpsw);
            bout.write((byte) i);
            bout.write((byte) (i >>> 8));
            bout.write((byte) (i >>> 16));

            if (i % xh == 0) {
                byte[] input = bout.toByteArray();
                sha.update(input);
                digest = sha.digest();
                AESInit[i / xh] = digest[19];
            }
        }

        sha.update(bout.toByteArray());
        digest = sha.digest();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                AESKey[i * 4 + j] = (byte) (((digest[i * 4] * 0x1000000) & 0xff000000
                        | ((digest[i * 4 + 1] * 0x10000) & 0xff0000) | ((digest[i * 4 + 2] * 0x100) & 0xff00)
                        | digest[i * 4 + 3] & 0xff) >>> (j * 8));
            }
        }

        return buildDecipherer(AESKey, AESInit);
    }

    /**
     * Builds an AES-CBC (no padding) decryption cipher from an already-derived key and IV.
     * Shared by both RAR formats: a 16-byte key selects AES-128 (RAR4) and a 32-byte key
     * AES-256 (RAR5), the variant being inferred from the key length.
     *
     * @param key the AES key (16 or 32 bytes)
     * @param iv  the 16-byte initialization vector
     * @return a {@link Cipher} ready to decrypt
     */
    public static Cipher buildDecipherer(final byte[] key, final byte[] iv)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher;
    }

    // ---------------------------------------------------------------------
    // RAR5 (AES-256) crypto. Key derivation matches the official unrar C++
    // reference crypt5.cpp (SetKey50): PBKDF2-HMAC-SHA256 with the AES key at
    // the stored iteration count and the HashMAC key 16 iterations further on.
    // PBKDF2 is delegated to the JDK; only the HashMAC conversion
    // (ConvertHashToMAC) is reproduced here.
    // ---------------------------------------------------------------------

    /** RAR5 AES-256 key length in bits. */
    private static final int RAR5_KEY_BITS = 256;

    /**
     * Extra PBKDF2 iterations, beyond the key, at which RAR5 derives the HMAC
     * key used for HashMAC checksums (the unrar V1 supplementary value).
     */
    private static final int RAR5_HASH_KEY_EXTRA_ITERATIONS = 16;

    /** Maximum allowed log2 of the PBKDF2 iteration count (CRYPT5_KDF_LG2_COUNT_MAX). */
    private static final int CRYPT5_KDF_LG2_COUNT_MAX = 24;

    /**
     * Derives the RAR5 AES-256 key from a password and salt using
     * PBKDF2-HMAC-SHA256.
     *
     * @param password the archive password
     * @param salt     the 16-byte salt from the encryption record
     * @param lg2count log2 of the PBKDF2 iteration count
     * @return the 32-byte AES-256 key
     */
    public static byte[] deriveRar5Key(final String password, final byte[] salt, final int lg2count)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
        return pbkdf2(password, salt, iterationCount(lg2count));
    }

    /**
     * Derives the RAR5 HMAC key used for HashMAC checksum conversion. This is the
     * PBKDF2 value 16 iterations beyond the AES key (unrar's V1 supplementary
     * value).
     *
     * @param password the archive password
     * @param salt     the 16-byte salt from the encryption record
     * @param lg2count log2 of the PBKDF2 iteration count
     * @return the 32-byte HMAC key
     */
    public static byte[] deriveRar5HashKey(final String password, final byte[] salt, final int lg2count)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
        return pbkdf2(password, salt, iterationCount(lg2count) + RAR5_HASH_KEY_EXTRA_ITERATIONS);
    }

    private static int iterationCount(final int lg2count) throws InvalidAlgorithmParameterException {
        if (lg2count > CRYPT5_KDF_LG2_COUNT_MAX) {
            throw new InvalidAlgorithmParameterException("KDF iteration count too large: " + lg2count);
        }
        return 1 << lg2count;
    }

    /**
     * PBKDF2-HMAC-SHA256 producing a 32-byte output. The JDK's
     * {@code PBKDF2WithHmacSHA256} encodes the password char[] as UTF-8, which
     * matches the encoding RAR5 uses (WideToUtf in the reference).
     */
    private static byte[] pbkdf2(final String password, final byte[] salt, final int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
        if (password == null) {
            throw new InvalidAlgorithmParameterException("password should be specified");
        }
        final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, RAR5_KEY_BITS);
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Converts a CRC32 value into its HashMAC form, as done by unrar's
     * ConvertHashToMAC when the FHEXTRA_CRYPT_HASHMAC flag is set: HMAC-SHA256
     * over the 4 little-endian CRC bytes keyed by {@code hashKey}, with the
     * 32-byte digest folded into a 32-bit value.
     *
     * @param hashKey the 32-byte HMAC key from {@link #deriveRar5HashKey}
     * @param crc32   the computed CRC32 value
     * @return the MAC-transformed CRC32 value (unsigned, in the low 32 bits)
     */
    public static long convertRar5Crc32ToMac(final byte[] hashKey, final long crc32) {
        final byte[] rawCrc = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt((int) crc32).array();
        final ByteBuffer digest = ByteBuffer.wrap(hmacSha256(hashKey, rawCrc))
                .order(ByteOrder.LITTLE_ENDIAN);

        // Fold the 32-byte digest into a u32 by XOR-ing its eight little-endian words.
        long mac = 0;
        while (digest.hasRemaining()) {
            mac ^= digest.getInt() & 0xFFFFFFFFL;
        }
        return mac & 0xFFFFFFFFL;
    }

    /**
     * Converts a BLAKE2sp digest into its HashMAC form, as done by unrar's
     * ConvertHashToMAC when the FHEXTRA_CRYPT_HASHMAC flag is set: HMAC-SHA256
     * over the 32-byte digest keyed by {@code hashKey}.
     *
     * @param hashKey the 32-byte HMAC key from {@link #deriveRar5HashKey}
     * @param digest  the computed 32-byte BLAKE2sp digest
     * @return the 32-byte MAC-transformed digest
     */
    public static byte[] convertRar5Blake2ToMac(final byte[] hashKey, final byte[] digest) {
        return hmacSha256(hashKey, digest);
    }

    private static byte[] hmacSha256(final byte[] key, final byte[] data) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is guaranteed present on every JRE and the key is always a
            // valid 32-byte derived key, so neither exception can occur in practice.
            throw new IllegalStateException(e);
        }
    }
}
