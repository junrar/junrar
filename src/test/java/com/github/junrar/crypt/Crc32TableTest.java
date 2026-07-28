package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks {@link Crc32Table} against {@code java.util.zip.CRC32} (P3 brief: "verify the
 * existing table matches unrar's InitCRC32 before relying on it"). {@code java.util.zip.CRC32}
 * exposes no per-byte table, only a whole-buffer digest, so this drives {@link
 * Crc32Table#runningCrc32} the same way unrar's own {@code CryptData::CRC32} is driven --
 * starting from {@code 0xffffffff}, no final complement -- and compares against the algebraic
 * identity that relationship has to the JDK's standard (zlib-compatible) CRC-32: {@code
 * runningCrc32(0xffffffff, data) == ~CRC32.getValue()} for the same bytes, since {@code
 * java.util.zip.CRC32} starts at the same {@code 0xffffffff} internal state and applies the same
 * per-byte step, differing only in the public API's implicit final complement.
 */
class Crc32TableTest {

    @Test
    void tableAgreesWithJdkCrc32OnRandomBuffers() {
        final Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            final byte[] data = new byte[random.nextInt(300)];
            random.nextBytes(data);

            final CRC32 jdk = new CRC32();
            jdk.update(data);
            final int jdkNoFinalComplement = ~((int) jdk.getValue());

            final int ours = Crc32Table.runningCrc32(0xffffffff, data);

            assertThat(ours)
                    .as("trial %d, %d bytes", trial, data.length)
                    .isEqualTo(jdkNoFinalComplement);
        }
    }

    @Test
    void tableFirstEntryIsZeroAndIsTheStandardEdb88320Table() {
        // unrar InitCRC32 (crc.cpp:36-42): C=I; 8 rounds of (C&1)?(C>>1)^0xEDB88320:(C>>1);
        // table[0]'s bit 0 is always 0, so it never touches the polynomial -- always 0.
        assertThat(Crc32Table.TABLE[0]).isZero();
        // Well-known standard CRC-32 (poly 0xEDB88320) table entries, independent source.
        assertThat(Crc32Table.TABLE[1]).isEqualTo(0x77073096);
        assertThat(Crc32Table.TABLE[255]).isEqualTo(0x2d02ef8d);
    }
}
