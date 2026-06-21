package com.github.junrar.crc;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    public void testCheckOldCrcOverflow() {
        // Test rotating bit from 15 to 0
        // Initial crc = 0x7FFF (32767)
        // Data = 0x00
        // crc = 0x7FFF + 0 = 0x7FFF
        // crc = (0x7FFF << 1) | (0x7FFF >>> 15)
        // 0x7FFF << 1 = 0xFFFE (-2)
        // 0x7FFF >>> 15 = 0
        // result = 0xFFFE (-2)
        byte[] data = {0x00};
        short crc = RarCRC.checkOldCrc((short) 0x7FFF, data, 1);
        assertThat(crc).isEqualTo((short) 0xFFFE);

        // Next step with 0xFFFE (-2)
        // crc = 0xFFFE + 0 = 0xFFFE
        // crc = (short) (((0xFFFE << 1) | (0xFFFE >>> 15)) & 0xFFFF)
        // 0xFFFE << 1 in 32-bit int = 0xFFFF FFFC
        // (short) 0xFFFF FFFC = 0xFFFC (-4)
        // 0xFFFE >>> 15 in 32-bit int = 0x0001 FFFF (unsigned shift)
        // WAIT: 0xFFFE as short is -2. When converted to int for >>>, it is 0xFFFFFFFE.
        // 0xFFFFFFFE >>> 15 = 0x0001FFFF
        // (0xFFFF FFFC) | (0x0001 FFFF) = 0xFFFF FFFF (-1)
        crc = RarCRC.checkOldCrc(crc, data, 1);
        assertThat(crc).isEqualTo((short) -1);
    }
}
