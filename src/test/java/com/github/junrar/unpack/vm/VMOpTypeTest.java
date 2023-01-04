package com.github.junrar.unpack.vm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class VMOpTypeTest {

    @ParameterizedTest
    @EnumSource(VMOpType.class)
    void findOpType(final VMOpType vmOpType) {
        assertThat(VMOpType.findOpType(vmOpType.getOpType())).isEqualTo(vmOpType);
    }

    @Test
    void unknownVMOpType() {
        assertThat(VMOpType.findOpType(4)).isNull();
    }

}
