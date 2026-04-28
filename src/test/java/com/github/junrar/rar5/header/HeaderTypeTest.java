package com.github.junrar.rar5.header;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderTypeTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void fromValueKnownTypes(final int value) {
        assertThat(HeaderType.fromValue(value)).isNotNull();
    }

    @Test
    void fromValueUnknown() {
        assertThat(HeaderType.fromValue(99)).isNull();
    }

    @Test
    void markType() {
        assertThat(HeaderType.MARK.getValue()).isZero();
    }

    @Test
    void archiveType() {
        assertThat(HeaderType.ARCHIVE.getValue()).isEqualTo(1);
    }

    @Test
    void fileType() {
        assertThat(HeaderType.FILE.getValue()).isEqualTo(2);
    }

    @Test
    void serviceType() {
        assertThat(HeaderType.SERVICE.getValue()).isEqualTo(3);
    }

    @Test
    void cryptType() {
        assertThat(HeaderType.CRYPT.getValue()).isEqualTo(4);
    }

    @Test
    void endarcType() {
        assertThat(HeaderType.ENDARC.getValue()).isEqualTo(5);
    }
}
