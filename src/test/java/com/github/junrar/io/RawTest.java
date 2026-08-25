package com.github.junrar.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class RawTest {

    @Test
    public void testShortBigEndian() {
        byte[] array = new byte[2];
        Raw.writeShortBigEndian(array, 0, (short) 0x1234);
        assertThat(array).isEqualTo(new byte[] {0x12, 0x34});
        assertThat(Raw.readShortBigEndian(array, 0)).isEqualTo((short) 0x1234);
    }

    @Test
    public void testIntBigEndian() {
        byte[] array = new byte[4];
        Raw.writeIntBigEndian(array, 0, 0x12345678);
        assertThat(array).isEqualTo(new byte[] {0x12, 0x34, 0x56, 0x78});
        assertThat(Raw.readIntBigEndian(array, 0)).isEqualTo(0x12345678);
    }

    @Test
    public void testLongBigEndian() {
        byte[] array = new byte[8];
        long value = 0x1234567890ABCDEFL;
        Raw.writeLongBigEndian(array, 0, value);
        assertThat(array)
                .isEqualTo(
                        new byte[] {
                            0x12,
                            0x34,
                            0x56,
                            0x78,
                            (byte) 0x90,
                            (byte) 0xAB,
                            (byte) 0xCD,
                            (byte) 0xEF
                        });
        assertThat(Raw.readLongBigEndian(array, 0)).isEqualTo(value);
    }

    @Test
    public void testReadLongBigEndianRandomInputs() {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int offset = random.nextInt(64);
            int totalLength = offset + 8 + random.nextInt(64);
            byte[] array = new byte[totalLength];
            random.nextBytes(array);

            long expected = ByteBuffer.wrap(array).getLong(offset);
            long actual = Raw.readLongBigEndian(array, offset);

            assertThat(actual)
                    .as("trial %d with offset %d in buffer length %d", i, offset, totalLength)
                    .isEqualTo(expected);
        }
    }

    @Test
    public void testReadLongBigEndianRandomExpectedOutputs() {
        Random random = new Random(1337);
        long[] edgeCases = {
            0L,
            -1L,
            1L,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            0x0102030405060708L,
            0x8080808080808080L,
            0x7F80000000000000L,
            0x00000000FFFFFFFFL,
            0xFFFFFFFF00000000L,
            0x5555555555555555L,
            0xAAAAAAAAAAAAAAAAL
        };

        for (long expected : edgeCases) {
            for (int offset = 0; offset <= 16; offset++) {
                byte[] array = new byte[offset + 8 + 16];
                ByteBuffer.wrap(array).putLong(offset, expected);
                assertThat(Raw.readLongBigEndian(array, offset))
                        .as("edge case %d with offset %d", expected, offset)
                        .isEqualTo(expected);
            }
        }

        for (int i = 0; i < 1000; i++) {
            long expected = random.nextLong();
            int offset = random.nextInt(64);
            int totalLength = offset + 8 + random.nextInt(64);
            byte[] array = new byte[totalLength];
            ByteBuffer.wrap(array).putLong(offset, expected);

            long actual = Raw.readLongBigEndian(array, offset);
            assertThat(actual)
                    .as("trial %d with expected output %d at offset %d", i, expected, offset)
                    .isEqualTo(expected);
        }
    }

    @Test
    public void testShortLittleEndian() {
        byte[] array = new byte[2];
        Raw.writeShortLittleEndian(array, 0, (short) 0x1234);
        assertThat(array).isEqualTo(new byte[] {0x34, 0x12});
        assertThat(Raw.readShortLittleEndian(array, 0)).isEqualTo((short) 0x1234);
    }

    @Test
    public void testIntLittleEndian() {
        byte[] array = new byte[4];
        Raw.writeIntLittleEndian(array, 0, 0x12345678);
        assertThat(array).isEqualTo(new byte[] {0x78, 0x56, 0x34, 0x12});
        assertThat(Raw.readIntLittleEndian(array, 0)).isEqualTo(0x12345678);
        assertThat(Raw.readIntLittleEndianAsLong(array, 0)).isEqualTo(0x12345678L);
    }

    @Test
    public void testLongLittleEndian() {
        byte[] array = new byte[8];
        long value = 0x1234567890ABCDEFL;
        Raw.writeLongLittleEndian(array, 0, value);
        assertThat(array)
                .isEqualTo(
                        new byte[] {
                            (byte) 0xEF,
                            (byte) 0xCD,
                            (byte) 0xAB,
                            (byte) 0x90,
                            0x78,
                            0x56,
                            0x34,
                            0x12
                        });
        assertThat(Raw.readLongLittleEndian(array, 0)).isEqualTo(value);
    }

    @Test
    public void testIncShortLittleEndian() {
        byte[] array = new byte[2];
        Raw.writeShortLittleEndian(array, 0, (short) 0x1234);
        Raw.incShortLittleEndian(array, 0, 1);
        assertThat(Raw.readShortLittleEndian(array, 0)).isEqualTo((short) 0x1235);
        Raw.incShortLittleEndian(array, 0, 0xFF);
        assertThat(Raw.readShortLittleEndian(array, 0)).isEqualTo((short) 0x1334);
    }
}
