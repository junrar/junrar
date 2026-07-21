package com.github.junrar.unpack.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VMFlagsTest {

    @ParameterizedTest
    @EnumSource(VMFlags.class)
    void findFlag(final VMFlags vmFlags) {
        assertThat(VMFlags.findFlag(vmFlags.getFlag())).isEqualTo(vmFlags);
    }

    @Test
    void unknownVMFlags() {
        assertThat(VMFlags.findFlag(3)).isNull();
    }
}
