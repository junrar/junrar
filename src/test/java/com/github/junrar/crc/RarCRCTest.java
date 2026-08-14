package com.github.junrar.crc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class RarCRCTest {

    @Test
    public void testCheckOldCrcEmpty() {
        byte[] data = new byte[0];
        short crc = RarCRC.checkOldCrc((short) 0, data, 0);
        assertThat(crc).isEqualTo((short) 0);
    }

    @Test
    public void testCheckOldCrcSingleByte() {
        // byte 0x01
        // crc = 0 + 1 = 1
        // crc = (1 << 1) | (1 >>> 15) = 2
        byte[] data = {0x01};
        short crc = RarCRC.checkOldCrc((short) 0, data, 1);
        assertThat(crc).isEqualTo((short) 2);
    }

    @Test
    public void testCheckOldCrcMultipleBytes() {
        // byte 0x01, 0x02
        // Step 1 (0x01): crc = 2
        // Step 2 (0x02):
        // crc = 2 + 2 = 4
        // crc = (4 << 1) | (4 >>> 15) = 8
        byte[] data = {0x01, 0x02};
        short crc = RarCRC.checkOldCrc((short) 0, data, 2);
        assertThat(crc).isEqualTo((short) 8);
    }

    @Test
    public void testCheckOldCrcWithInitialValue() {
        byte[] data = {0x01};
        short crc = RarCRC.checkOldCrc((short) 2, data, 1);
        // Step 1: crc = 2 + 1 = 3
        // crc = (3 << 1) | (3 >>> 15) = 6
        assertThat(crc).isEqualTo((short) 6);
    }
}
