package com.github.junrar.unpack.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VMCommandsTest {

    @ParameterizedTest
    @EnumSource(VMCommands.class)
    void findVMCommand(final VMCommands vmCommands) {
        assertThat(VMCommands.findVMCommand(vmCommands.getVMCommand())).isEqualTo(vmCommands);
    }

    @Test
    void unknownVMCommand() {
        assertThat(VMCommands.findVMCommand(55)).isNull();
    }
}
