package com.github.junrar.rar5.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rar5BlockHeaderTest {

    @Test
    void parseMinimalBlock() {
        // CRC32(0x00, 0x00) = 0x41D912FF, LE: FF 12 D9 41
        // Header data: type=0(MARK), flags=0
        final byte[] data = {
            (byte) 0xFF, 0x12, (byte) 0xD9, 0x41, // CRC32
            0x02,                                    // header size = 2
            0x00,                                    // type = MARK
            0x00                                     // flags = 0
        };

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeadCrc()).isEqualTo(0x41D912FFL);
        assertThat(header.getHeaderSize()).isEqualTo(2);
        assertThat(header.getHeaderType()).isEqualTo(HeaderType.MARK);
        assertThat(header.getFlags()).isZero();
        assertThat(header.hasExtra()).isFalse();
        assertThat(header.hasData()).isFalse();
        assertThat(header.getExtraSize()).isZero();
        assertThat(header.getDataSize()).isZero();
    }

    @Test
    void parseBlockWithExtra() {
        // CRC32(0x01, 0x01, 0x00) = 0xE7988264, LE: 64 82 98 E7
        // Header data: type=1(ARCHIVE), flags=0x01(HFL_EXTRA), extraSize=0
        final byte[] data = {
            0x64, (byte) 0x82, (byte) 0x98, (byte) 0xE7, // CRC32
            0x03,                                          // header size = 3
            0x01,                                          // type = ARCHIVE
            0x01,                                          // flags = HFL_EXTRA
            0x00                                           // extraSize = 0
        };

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeadCrc()).isEqualTo(0xE7988264L);
        assertThat(header.getHeaderSize()).isEqualTo(3);
        assertThat(header.getHeaderType()).isEqualTo(HeaderType.ARCHIVE);
        assertThat(header.getFlags()).isEqualTo(0x01);
        assertThat(header.hasExtra()).isTrue();
        assertThat(header.hasData()).isFalse();
        assertThat(header.getExtraSize()).isZero();
    }

    @Test
    void parseBlockWithData() {
        // CRC32(0x01, 0x03, 0x00, 0x64) = 0xD161A361, LE: 61 A3 61 D1
        // Header data: type=1(ARCHIVE), flags=0x03(HFL_EXTRA|HFL_DATA), extraSize=0, dataSize=100
        final byte[] data = {
            0x61, (byte) 0xA3, 0x61, (byte) 0xD1, // CRC32
            0x04,                                    // header size = 4
            0x01,                                    // type = ARCHIVE
            0x03,                                    // flags = HFL_EXTRA | HFL_DATA
            0x00,                                    // extraSize = 0
            0x64                                     // dataSize = 100
        };

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeadCrc()).isEqualTo(0xD161A361L);
        assertThat(header.getHeaderSize()).isEqualTo(4);
        assertThat(header.getHeaderType()).isEqualTo(HeaderType.ARCHIVE);
        assertThat(header.getFlags()).isEqualTo(0x03);
        assertThat(header.hasExtra()).isTrue();
        assertThat(header.hasData()).isTrue();
        assertThat(header.getExtraSize()).isZero();
        assertThat(header.getDataSize()).isEqualTo(100);
    }

    @Test
    void parseEndArcBlock() {
        // CRC32(0x05, 0x00) = 0x3CAEE6BA, LE: BA E6 AE 3C
        // Header data: type=5(ENDARC), flags=0
        final byte[] data = {
            (byte) 0xBA, (byte) 0xE6, (byte) 0xAE, 0x3C, // CRC32
            0x02,                                           // header size = 2
            0x05,                                           // type = ENDARC
            0x00                                            // flags = 0
        };

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeaderType()).isEqualTo(HeaderType.ENDARC);
        assertThat(header.getFlags()).isZero();
    }

    @Test
    void parseBlockWithSplitFlags() {
        // CRC32(0x02, 0x18) — type=2(FILE), flags=0x18(SPLITBEFORE|SPLITAFTER)
        final byte[] headerData = {0x02, 0x18};
        final long crc = computeCrc32(headerData);

        final byte[] data = new byte[7];
        data[0] = (byte) (crc & 0xFF);
        data[1] = (byte) ((crc >> 8) & 0xFF);
        data[2] = (byte) ((crc >> 16) & 0xFF);
        data[3] = (byte) ((crc >> 24) & 0xFF);
        data[4] = 0x02; // header size
        System.arraycopy(headerData, 0, data, 5, 2);

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeaderType()).isEqualTo(HeaderType.FILE);
        assertThat(header.isSplitBefore()).isTrue();
        assertThat(header.isSplitAfter()).isTrue();
    }

    @Test
    void parseBlockWithLargeHeaderSize() {
        // Header size = 200 (multi-byte vint: 0xC8 0x01)
        // CRC32 of {0x00, 0x00}
        final byte[] headerData = {0x00, 0x00};
        final long crc = computeCrc32(headerData);

        final byte[] data = new byte[9];
        data[0] = (byte) (crc & 0xFF);
        data[1] = (byte) ((crc >> 8) & 0xFF);
        data[2] = (byte) ((crc >> 16) & 0xFF);
        data[3] = (byte) ((crc >> 24) & 0xFF);
        data[4] = (byte) 0xC8; // header size low byte (200 & 0x7F | 0x80)
        data[5] = 0x01;        // header size high byte
        data[6] = 0x00;        // type = MARK
        data[7] = 0x00;        // flags
        // Note: actual header data would be 200 bytes, but we only test parsing

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);

        assertThat(header.getHeaderSize()).isEqualTo(200);
        assertThat(header.getHeaderType()).isEqualTo(HeaderType.MARK);
    }

    @Test
    void parseAllHeaderTypes() {
        for (final HeaderType type : HeaderType.values()) {
            final byte[] headerData = {(byte) type.getValue(), 0x00};
            final long crc = computeCrc32(headerData);

            final byte[] data = new byte[8];
            data[0] = (byte) (crc & 0xFF);
            data[1] = (byte) ((crc >> 8) & 0xFF);
            data[2] = (byte) ((crc >> 16) & 0xFF);
            data[3] = (byte) ((crc >> 24) & 0xFF);
            data[4] = 0x02;
            data[5] = (byte) type.getValue();
            data[6] = 0x00;

            final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 0);
            assertThat(header.getHeaderType())
                .as("header type for value %d", type.getValue())
                .isEqualTo(type);
        }
    }

    @Test
    void parseAtOffset() {
        final byte[] headerData = {0x00, 0x00};
        final long crc = computeCrc32(headerData);

        final byte[] data = new byte[14];
        data[0] = 0x00; data[1] = 0x00; data[2] = 0x00; data[3] = 0x00;
        data[4] = (byte) (crc & 0xFF);
        data[5] = (byte) ((crc >> 8) & 0xFF);
        data[6] = (byte) ((crc >> 16) & 0xFF);
        data[7] = (byte) ((crc >> 24) & 0xFF);
        data[8] = 0x02;
        data[9] = 0x00;
        data[10] = 0x00;

        final Rar5BlockHeader header = Rar5BlockHeader.parse(data, 4);
        assertThat(header.getHeaderType()).isEqualTo(HeaderType.MARK);
    }

    @Test
    void parseUnknownHeaderType() {
        final byte[] headerData = {0x06, 0x00};
        final long crc = computeCrc32(headerData);

        final byte[] data = new byte[8];
        data[0] = (byte) (crc & 0xFF);
        data[1] = (byte) ((crc >> 8) & 0xFF);
        data[2] = (byte) ((crc >> 16) & 0xFF);
        data[3] = (byte) ((crc >> 24) & 0xFF);
        data[4] = 0x02;
        data[5] = 0x06;
        data[6] = 0x00;

        assertThatThrownBy(() -> Rar5BlockHeader.parse(data, 0))
            .isInstanceOf(Rar5HeaderException.class)
            .hasMessageContaining("unknown header type");
    }

    @Test
    void parseZeroHeaderSize() {
        final byte[] data = {
            0x00, 0x00, 0x00, 0x00, // CRC32 (dummy)
            0x00                     // header size = 0 (invalid)
        };

        assertThatThrownBy(() -> Rar5BlockHeader.parse(data, 0))
            .isInstanceOf(Rar5HeaderException.class)
            .hasMessageContaining("header size must not be zero");
    }

    @Test
    void parseNullData() {
        assertThatThrownBy(() -> Rar5BlockHeader.parse(null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void parseNegativeOffset() {
        assertThatThrownBy(() -> Rar5BlockHeader.parse(new byte[10], -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    private static long computeCrc32(final byte[] data) {
        final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }
}
