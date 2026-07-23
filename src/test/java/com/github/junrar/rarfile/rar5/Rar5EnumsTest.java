package com.github.junrar.rarfile.rar5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * M3.3 (issue #24) wire-value enum round-trip tests: {@code @EnumSource} round-trip +
 * {@code findX(unknown) == null} for every new RAR5 enum (the C11 lesson, manual &sect;4.6).
 */
class Rar5EnumsTest {

    @ParameterizedTest
    @EnumSource(Rar5HostOS.class)
    void hostOSRoundTrips(Rar5HostOS hostOS) {
        assertThat(Rar5HostOS.findHostOS(hostOS.getValue())).isEqualTo(hostOS);
    }

    @Test
    void hostOSUnknownIsNull() {
        assertThat(Rar5HostOS.findHostOS(9999)).isNull();
        assertThat(Rar5HostOS.findHostOS(-1)).isNull();
    }

    @ParameterizedTest
    @EnumSource(Rar5FileExtraType.class)
    void fileExtraTypeRoundTrips(Rar5FileExtraType type) {
        assertThat(Rar5FileExtraType.findType(type.getValue())).isEqualTo(type);
    }

    @Test
    void fileExtraTypeUnknownIsNull() {
        assertThat(Rar5FileExtraType.findType(9999)).isNull();
        assertThat(Rar5FileExtraType.findType(-1)).isNull();
    }

    @ParameterizedTest
    @EnumSource(Rar5MainExtraType.class)
    void mainExtraTypeRoundTrips(Rar5MainExtraType type) {
        assertThat(Rar5MainExtraType.findType(type.getValue())).isEqualTo(type);
    }

    @Test
    void mainExtraTypeUnknownIsNull() {
        assertThat(Rar5MainExtraType.findType(9999)).isNull();
        assertThat(Rar5MainExtraType.findType(-1)).isNull();
    }

    @ParameterizedTest
    @EnumSource(Rar5RedirType.class)
    void redirTypeRoundTrips(Rar5RedirType type) {
        assertThat(Rar5RedirType.findType(type.getValue())).isEqualTo(type);
    }

    @Test
    void redirTypeUnknownIsNull() {
        assertThat(Rar5RedirType.findType(9999)).isNull();
        assertThat(Rar5RedirType.findType(-1)).isNull();
    }

    @ParameterizedTest
    @EnumSource(Rar5HashType.class)
    void hashTypeRoundTrips(Rar5HashType type) {
        assertThat(Rar5HashType.findType(type.getValue())).isEqualTo(type);
    }

    @Test
    void hashTypeUnknownIsNull() {
        assertThat(Rar5HashType.findType(9999)).isNull();
        assertThat(Rar5HashType.findType(-1)).isNull();
    }
}
