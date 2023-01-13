package com.github.junrar.unpack.ppm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BlockTypesTest {

    @ParameterizedTest
    @EnumSource(BlockTypes.class)
    void findBlockType(final BlockTypes blockTypes) {
        assertThat(BlockTypes.findBlockType(blockTypes.getBlockType())).isEqualTo(blockTypes);
    }

    @Test
    void unknownBlockType() {
        assertThat(BlockTypes.findBlockType(2)).isNull();
    }

}
