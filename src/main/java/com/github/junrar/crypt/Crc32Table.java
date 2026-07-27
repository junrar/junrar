package com.github.junrar.crypt;

/**
 * The standard reflected CRC-32 lookup table (polynomial {@code 0xEDB88320}), byte-indexed --
 * unrar {@code InitCRC32}, {@code d861246:crc.cpp:32-42}, ported verbatim (the loop, not just the
 * final digest). {@link com.github.junrar.crc.RarCRC} wraps {@code java.util.zip.CRC32} instead,
 * which computes the same standard CRC-32 but exposes no per-byte table access -- the legacy RAR
 * 1.5/2.0 cipher key schedules ({@link Rar15Cipher}, {@link Rar20Cipher}; unrar {@code
 * crypt1.cpp:15-28,52-66}, {@code crypt2.cpp:22-59,116-125}) need the individual table entries,
 * not just a whole-buffer digest, so this table exists as data this package needs, not as a
 * competing CRC implementation. {@link
 * com.github.junrar.crypt.Crc32TableTest#tableAgreesWithJdkCrc32OnRandomBuffers()} cross-checks
 * every entry against {@code java.util.zip.CRC32} to prove the two are the same algorithm.
 */
final class Crc32Table {

    static final int[] TABLE = build();

    private Crc32Table() {}

    private static int[] build() {
        final int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int j = 0; j < 8; j++) {
                c = ((c & 1) != 0) ? (c >>> 1) ^ 0xEDB88320 : (c >>> 1);
            }
            table[i] = c;
        }
        return table;
    }

    /**
     * The running (no final complement) CRC-32 used to seed {@link Rar15Cipher}'s key schedule
     * (unrar {@code CryptData::CRC32}, {@code d861246:crc.cpp:79-...}: {@code StartCRC =
     * crc_tables[0][(byte)(StartCRC^Data[i])]^(StartCRC>>8)}, no initial/final XOR beyond the
     * caller-supplied {@code StartCRC}).
     */
    static int runningCrc32(final int startCrc, final byte[] data) {
        int crc = startCrc;
        for (final byte b : data) {
            crc = TABLE[(crc ^ b) & 0xff] ^ (crc >>> 8);
        }
        return crc;
    }
}
