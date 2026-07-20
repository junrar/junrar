package com.github.junrar.crypt.blake2;

/**
 * A RAR5 file-checksum accumulator (M3.5, issue #26). Mirrors unrar {@code hash.cpp}'s
 * {@code DataHash} abstraction, which unifies the two RAR5 checksum kinds: CRC32 and BLAKE2sp.
 * <p>
 * Only the BLAKE2sp arm ({@link Blake2sp}) implements this interface. The CRC32 arm stays inline
 * in {@code ComprDataIO} (it already owns {@code unpCrc32}/{@code unpFileCRC} and the
 * complement-at-observation trick, manual &sect;5.5, no-go row D2) rather than being wrapped here --
 * that trick has exactly one caller and moving it behind this seam would be a needless
 * indirection for a single implementation.
 * <p>
 * // ponytail: no Crc32-backed DataHash today; ComprDataIO branches on Rar5HashType inline
 * // instead. Add one if/when the M3.6/M3.7 decode routes CRC verification through this same
 * // seam as BLAKE2sp -- until then a second implementation would be speculative.
 */
public interface DataHash {

    /**
     * Feeds {@code count} bytes starting at {@code offset} into the running hash.
     */
    void update(byte[] data, int offset, int count);

    /**
     * Finalizes and returns the digest (32 bytes for {@link Blake2sp}). One-shot: this mutates
     * internal state, so calling it twice does not reproduce the same value.
     */
    byte[] digest();
}
