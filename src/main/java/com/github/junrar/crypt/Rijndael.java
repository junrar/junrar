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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Rijndael {

    /**
     * RAR4 key-derivation cache -- unrar {@code CryptData::KDF3Cache[4]} ({@code crypt.hpp:85-86},
     * {@code crypt3.cpp} {@code SetKey30}). The RAR3.x derivation is the expensive step (262,144
     * SHA-1 rounds), and every encrypted block of an archive reuses one password and -- for header
     * encryption -- one salt, so the derived AES key/IV are cached; only the cheap per-block
     * {@link Cipher} is rebuilt.
     *
     * <p>Deliberately an <em>instance</em>, exactly as unrar's is a private non-static member of
     * {@code CryptData}: it holds the password bytes and the AES key, so its lifetime must not
     * exceed the archive that derived them. {@link com.github.junrar.Archive} owns one and
     * {@link #wipe()}s it on close, alongside the password copy it already clears -- a JVM-global
     * cache would keep plaintext passwords reachable in a heap or core dump for the life of the
     * process, and leave one caller's secrets readable while another's request is served.
     *
     * <p>Cached arrays are cloned on the way in and on the way out, so no caller can mutate an
     * entry and no entry aliases a key that a caller may later zero (the {@code Arrays.fill}
     * hygiene used in {@code ComprDataIO} and {@code Rar5Crypt}).
     *
     * <p>Four slots, replaced round-robin as unrar does -- a hit does not refresh position. Since
     * every encrypted RAR3 entry carries its own salt, an archive with more than four of them
     * evicts continuously, so an evicted entry is zeroed as it is dropped rather than left to the
     * GC with its secrets intact.
     */
    public static final class Kdf3Cache {

        private final CacheItem[] cache = new CacheItem[4];
        private int cachePos = 0;

        /**
         * @param password archive password; {@code null} is rejected
         * @param salt the 8-byte RAR3 salt
         * @return an AES-128/CBC decipherer for this (password, salt), deriving the key only on a
         *     cache miss
         */
        public synchronized Cipher buildDecipherer(final String password, final byte[] salt)
                throws NoSuchAlgorithmException,
                        InvalidKeyException,
                        InvalidAlgorithmParameterException,
                        NoSuchPaddingException {
            final byte[] pwd = passwordBytes(password);
            for (final CacheItem item : cache) {
                if (item != null
                        && Arrays.equals(item.pwd, pwd)
                        && Arrays.equals(item.salt, salt)) {
                    Arrays.fill(pwd, (byte) 0);
                    return Rijndael.buildDecipherer(item.key.clone(), item.iv.clone());
                }
            }

            final byte[][] keyAndIv = deriveRar4KeyAndIv(pwd, salt);
            // unrar's KDF3Cache is an array of fixed-size structs, so storing over the slot
            // (crypt3.cpp SetKey30) overwrites the evicted entry's secrets in place. Replacing a
            // Java object does not, so zero the evicted entry before dropping it to the GC. After
            // the derivation, not before: zeroing first would destroy a valid entry if it threw.
            if (cache[cachePos] != null) {
                cache[cachePos].wipe();
            }
            // pwd is handed to the entry rather than zeroed; wipe() clears it.
            cache[cachePos] = new CacheItem(pwd, salt.clone(), keyAndIv[0], keyAndIv[1]);
            cachePos = (cachePos + 1) % cache.length;
            return Rijndael.buildDecipherer(keyAndIv[0].clone(), keyAndIv[1].clone());
        }

        /**
         * Zero every cached password, key and IV and drop the entries (unrar
         * {@code KDF3CacheItem::Clean}, called from its destructor). Eviction on a cache miss zeroes
         * the entry it replaces by the same route, so this is not the only zeroing path.
         */
        public synchronized void wipe() {
            for (int i = 0; i < cache.length; i++) {
                if (cache[i] != null) {
                    cache[i].wipe();
                    cache[i] = null;
                }
            }
            cachePos = 0;
        }
    }

    private static final class CacheItem {
        final byte[] pwd;
        final byte[] salt;
        final byte[] key;
        final byte[] iv;

        CacheItem(final byte[] pwd, final byte[] salt, final byte[] key, final byte[] iv) {
            this.pwd = pwd;
            this.salt = salt;
            this.key = key;
            this.iv = iv;
        }

        /**
         * unrar {@code KDF3CacheItem::Clean} ({@code crypt.hpp:59-65}). Safe on an entry being
         * evicted as well as on {@code Kdf3Cache.wipe()}: entries are never published -- a hit
         * returns clones of {@code key} and {@code iv} -- so no live caller can observe the zeroing.
         * Callers must hold the {@code Kdf3Cache} monitor.
         */
        void wipe() {
            Arrays.fill(pwd, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(key, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    /**
     * Derive and build a decipherer without caching -- retains no key material past the call. Use
     * {@link Kdf3Cache#buildDecipherer(String, byte[])} on a repeated (password, salt), which is
     * the case for every encrypted archive.
     */
    public static Cipher buildDecipherer(final String password, byte[] salt)
            throws IOException,
                    NoSuchAlgorithmException,
                    InvalidKeyException,
                    InvalidAlgorithmParameterException,
                    NoSuchPaddingException {
        final byte[] pwd = passwordBytes(password);
        try {
            final byte[][] keyAndIv = deriveRar4KeyAndIv(pwd, salt);
            return buildDecipherer(keyAndIv[0], keyAndIv[1]);
        } finally {
            Arrays.fill(pwd, (byte) 0);
        }
    }

    private static byte[] passwordBytes(final String password)
            throws InvalidAlgorithmParameterException {
        if (password == null) {
            throw new InvalidAlgorithmParameterException("password should be specified");
        }
        // unrar 3.7.3 crypt.cpp:240-249: CharToWide+WideToRaw serialize the password as
        // UTF-16LE, not the platform charset.
        return password.getBytes(StandardCharsets.UTF_16LE);
    }

    /**
     * RAR4 (RAR 3.x) SHA-1 based key derivation, ported from unrar {@code CryptData::SetKey30}
     * (crypt3.cpp). Returns {@code {AES-128 key (16 bytes), IV (16 bytes)}}.
     *
     * <p>Known divergence, pre-dating this implementation: unrar feeds the password through
     * {@code sha1_process_rar29} ({@code sha1.cpp}), which writes the SHA-1 message schedule back
     * over its own input buffer, so the password bytes mutate between rounds. That branch is only
     * reached once the (password + salt) buffer spans a full extra 64-byte block -- a password of
     * 29 characters or more with the usual 8-byte salt -- and {@link MessageDigest} cannot express
     * it. Such archives therefore derive a different key here than in unrar and fail to decrypt.
     * Shorter passwords, which is everything the test corpus and the wild have shown, are
     * bit-identical to unrar.
     */
    private static byte[][] deriveRar4KeyAndIv(final byte[] pwd, byte[] salt)
            throws NoSuchAlgorithmException {
        final byte[] rawpsw = new byte[pwd.length + salt.length];
        System.arraycopy(pwd, 0, rawpsw, 0, pwd.length);
        System.arraycopy(salt, 0, rawpsw, pwd.length, salt.length);

        final byte[] AESInit = new byte[16];
        final byte[] AESKey = new byte[16];

        final MessageDigest sha = MessageDigest.getInstance("sha-1");

        final int HashRounds = 0x40000;
        final int xh = HashRounds / 16;

        // Feed (rawpsw || little-endian 3-byte round counter) into a single SHA-1 context for
        // every round. At the 16 evenly spaced sample points, clone the running context and
        // finalise the copy to extract one IV byte without disturbing the hash that ultimately
        // yields the AES key. Matches unrar SetKey30; the earlier junrar code re-hashed an
        // ever-growing buffer at each sample point, doing roughly 10x the SHA-1 work.
        final byte[] counter = new byte[3];
        try {
            for (int i = 0; i < HashRounds; i++) {
                sha.update(rawpsw);
                counter[0] = (byte) i;
                counter[1] = (byte) (i >>> 8);
                counter[2] = (byte) (i >>> 16);
                sha.update(counter);

                if (i % xh == 0) {
                    AESInit[i / xh] = ((MessageDigest) sha.clone()).digest()[19];
                }
            }
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("SHA-1 MessageDigest is not cloneable", e);
        } finally {
            // unrar cleandata(RawPsw,...), crypt3.cpp: the password+salt buffer is secret material
            // and must not outlive the derivation.
            Arrays.fill(rawpsw, (byte) 0);
        }

        final byte[] digest = sha.digest();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                AESKey[i * 4 + j] =
                        (byte)
                                (((digest[i * 4] * 0x1000000) & 0xff000000
                                                | ((digest[i * 4 + 1] * 0x10000) & 0xff0000)
                                                | ((digest[i * 4 + 2] * 0x100) & 0xff00)
                                                | digest[i * 4 + 3] & 0xff)
                                        >>> (j * 8));
            }
        }

        return new byte[][] {AESKey, AESInit};
    }

    private static Cipher buildDecipherer(final byte[] key, final byte[] iv)
            throws NoSuchAlgorithmException,
                    NoSuchPaddingException,
                    InvalidKeyException,
                    InvalidAlgorithmParameterException {
        final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher;
    }
}
