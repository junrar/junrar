package com.github.junrar.rar5.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rar5EndArcHeaderTest {

    @Test
    void parseNoFlags() {
        // flags = 0 (last volume)
        final byte[] data = {0x00};
        final Rar5EndArcHeader header = Rar5EndArcHeader.parse(data, 0);

        assertThat(header.getFlags()).isZero();
        assertThat(header.isNotLastVolume()).isFalse();
    }

    @Test
    void parseNextVolumeFlag() {
        // flags = EHFL_NEXTVOLUME (0x01)
        final byte[] data = {0x01};
        final Rar5EndArcHeader header = Rar5EndArcHeader.parse(data, 0);

        assertThat(header.getFlags()).isEqualTo(0x01);
        assertThat(header.isNotLastVolume()).isTrue();
    }

    @Test
    void parseAtOffset() {
        final byte[] data = {0x00, 0x00, 0x01, 0x00};
        final Rar5EndArcHeader header = Rar5EndArcHeader.parse(data, 2);

        assertThat(header.isNotLastVolume()).isTrue();
    }

    @Test
    void parseNullData() {
        assertThatThrownBy(() -> Rar5EndArcHeader.parse(null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void parseNegativeOffset() {
        assertThatThrownBy(() -> Rar5EndArcHeader.parse(new byte[10], -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }
}
