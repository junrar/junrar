package com.github.junrar.crypt;

/**
 * The per-algorithm decrypt operation shared by {@link Rar13Cipher}, {@link Rar15Cipher} and
 * {@link Rar20Cipher}, so {@link LegacyCipherSpi} can hold whichever one {@link
 * RarLegacyCrypt#select} chose without a method-specific switch at update time.
 */
interface LegacyCipherEngine {

    /**
     * Decrypts {@code buf[off, off+len)} in place, advancing this engine's running key state.
     * Stream ciphers ({@link Rar13Cipher}, {@link Rar15Cipher}) accept any {@code len}; the block
     * cipher ({@link Rar20Cipher}) processes {@code len} rounded down to a 16-byte multiple (unrar
     * {@code crypt.cpp:33-36}) -- callers always hand this whole 16-byte-aligned chunks in
     * practice ({@link com.github.junrar.io.RawDataIo} already rounds every read up to
     * {@code CRYPT_BLOCK_SIZE} for the AES path).
     */
    void decrypt(byte[] buf, int off, int len);
}
