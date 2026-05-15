package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrcChecksumTest {

    @Test
    void initialValue() {
        CrcChecksum checksum = new CrcChecksum(-1);
        assertThat(checksum.getChecksum()).isEqualTo("FFFFFFFF");
    }

    @Test
    void maxValue() {
        CrcChecksum checksum = new CrcChecksum(Integer.MAX_VALUE);
        assertThat(checksum.getChecksum()).isEqualTo("7FFFFFFF");
    }

    @Test
    void minValue() {
        CrcChecksum checksum = new CrcChecksum(Integer.MIN_VALUE);
        assertThat(checksum.getChecksum()).isEqualTo("80000000");
    }

}
