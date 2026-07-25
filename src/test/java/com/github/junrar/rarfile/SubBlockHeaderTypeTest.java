package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubBlockHeaderTypeTest {

    @ParameterizedTest
    @EnumSource(SubBlockHeaderType.class)
    void findSubblockHeaderType(final SubBlockHeaderType subBlockHeaderType) {
        assertThat(SubBlockHeaderType.findSubblockHeaderType(subBlockHeaderType.getSubblocktype()))
                .isEqualTo(subBlockHeaderType);
    }

    @Test
    void unknownSubblockHeaderType() {
        assertThat(SubBlockHeaderType.findSubblockHeaderType((short) 0)).isNull();
    }
}
