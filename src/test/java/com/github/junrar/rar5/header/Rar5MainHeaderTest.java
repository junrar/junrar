package com.github.junrar.rar5.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rar5MainHeaderTest {

    @Test
    void parseBasicFlags() {
        // flags = 0 (no volume, not solid, not locked, no recovery)
        final byte[] data = {0x00};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.getFlags()).isZero();
        assertThat(header.isVolume()).isFalse();
        assertThat(header.isSolid()).isFalse();
        assertThat(header.isLocked()).isFalse();
        assertThat(header.hasRecovery()).isFalse();
        assertThat(header.getVolumeNumber()).isZero();
        assertThat(header.isFirstVolume()).isFalse();
    }

    @Test
    void parseSolidArchive() {
        // flags = MHFL_SOLID (0x04)
        final byte[] data = {0x04};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.isSolid()).isTrue();
        assertThat(header.isVolume()).isFalse();
    }

    @Test
    void parseVolumeWithoutNumber() {
        // flags = MHFL_VOLUME (0x01), no volume number field
        final byte[] data = {0x01};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.isVolume()).isTrue();
        assertThat(header.isFirstVolume()).isTrue();
        assertThat(header.getVolumeNumber()).isZero();
    }

    @Test
    void parseVolumeWithNumber() {
        // flags = MHFL_VOLUME | MHFL_VOLNUMBER (0x03), volume number = 2
        final byte[] data = {0x03, 0x02};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.isVolume()).isTrue();
        assertThat(header.getVolumeNumber()).isEqualTo(2);
        assertThat(header.isFirstVolume()).isFalse();
    }

    @Test
    void parseLockedArchive() {
        // flags = MHFL_LOCK (0x10)
        final byte[] data = {0x10};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.isLocked()).isTrue();
    }

    @Test
    void parseRecoveryRecord() {
        // flags = MHFL_PROTECT (0x08)
        final byte[] data = {0x08};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.hasRecovery()).isTrue();
    }

    @Test
    void parseAllFlagsSet() {
        // flags = VOLUME | VOLNUMBER | SOLID | PROTECT | LOCKED = 0x1F
        // volume number = 5
        final byte[] data = {0x1F, 0x05};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.isVolume()).isTrue();
        assertThat(header.isSolid()).isTrue();
        assertThat(header.isLocked()).isTrue();
        assertThat(header.hasRecovery()).isTrue();
        assertThat(header.getVolumeNumber()).isEqualTo(5);
        assertThat(header.isFirstVolume()).isFalse();
    }

    @Test
    void parseLargeVolumeNumber() {
        // flags = MHFL_VOLUME | MHFL_VOLNUMBER (0x03)
        // volume number = 10000 (vint: 0x90 0x4E)
        final byte[] data = {0x03, (byte) 0x90, 0x4E};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 0, data.length);

        assertThat(header.getVolumeNumber()).isEqualTo(10000);
    }

    @Test
    void parseAtOffset() {
        final byte[] data = {0x00, 0x00, 0x04, 0x00};
        final Rar5MainHeader header = Rar5MainHeader.parse(data, 2, data.length);

        assertThat(header.isSolid()).isTrue();
    }

    @Test
    void parseNullData() {
        assertThatThrownBy(() -> Rar5MainHeader.parse(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void parseInvalidOffsetRange() {
        assertThatThrownBy(() -> Rar5MainHeader.parse(new byte[10], 5, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid offset range");
    }

    @Test
    void parseNegativeOffset() {
        assertThatThrownBy(() -> Rar5MainHeader.parse(new byte[10], -1, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid offset range");
    }
}
