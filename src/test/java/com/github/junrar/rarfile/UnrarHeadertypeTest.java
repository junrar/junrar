package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class UnrarHeadertypeTest {

    @ParameterizedTest
    @EnumSource(UnrarHeadertype.class)
    void findType(final UnrarHeadertype unrarHeadertype) {
        assertThat(UnrarHeadertype.findType(unrarHeadertype.getHeaderByte())).isEqualTo(unrarHeadertype);
    }

    @Test
    void unknownUnrarHeadertype() {
        assertThat(UnrarHeadertype.findType((byte) 0)).isNull();
    }

}
