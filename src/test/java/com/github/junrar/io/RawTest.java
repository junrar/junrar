package com.github.junrar.io;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class RawTest {

    @Test
    public void testShortBigEndian() {
        byte[] array = new byte[2];
        Raw.writeShortBigEndian(array, 0, (short) 0x1234);
        assertThat(array).isEqualTo(new byte[]{0x12, 0x34});
        assertThat(Raw.readShortBigEndian(array, 0)).isEqualTo((short) 0x1234);
    }

    @Test
    public void testIntBigEndian() {
        byte[] array = new byte[4];
        Raw.writeIntBigEndian(array, 0, 0x12345678);
        assertThat(array).isEqualTo(new byte[]{0x12, 0x34, 0x56, 0x78});
        assertThat(Raw.readIntBigEndian(array, 0)).isEqualTo(0x12345678);
    }

    @Test
    public void testLongBigEndian() {
        byte[] array = new byte[8];
        long value = 0x1234567890ABCDEFL;
        Raw.writeLongBigEndian(array, 0, value);
        assertThat(array).isEqualTo(new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x90, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF});
        assertThat(Raw.readLongBigEndian(array, 0)).isEqualTo(value);
    }

    @Test
    public void testShortLittleEndian() {
        byte[] array = new byte[2];
        Raw.writeShortLittleEndian(array, 0, (short) 0x1234);
        assertThat(array).isEqualTo(new byte[]{0x34, 0x12});
        assertThat(Raw.readShortLittleEndian(array, 0)).isEqualTo((short) 0x1234);
    }

    @Test
    public void testIntLittleEndian() {
        byte[] array = new byte[4];
        Raw.writeIntLittleEndian(array, 0, 0x12345678);
        assertThat(array).isEqualTo(new byte[]{0x78, 0x56, 0x34, 0x12});
        assertThat(Raw.readIntLittleEndian(array, 0)).isEqualTo(0x12345678);
        assertThat(Raw.readIntLittleEndianAsLong(array, 0)).isEqualTo(0x12345678L);
    }

    @Test
    public void testLongLittleEndian() {
        byte[] array = new byte[8];
        long value = 0x1234567890ABCDEFL;
        Raw.writeLongLittleEndian(array, 0, value);
        assertThat(array).isEqualTo(new byte[]{(byte) 0xEF, (byte) 0xCD, (byte) 0xAB, (byte) 0x90, 0x78, 0x56, 0x34, 0x12});
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
