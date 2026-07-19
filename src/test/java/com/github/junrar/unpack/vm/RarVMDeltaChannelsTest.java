package com.github.junrar.unpack.vm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RarVMDeltaChannelsTest {

    @ParameterizedTest
    @MethodSource("deltaChannels")
    void deltaFilterHonorsChannelCount(final int channels, final byte[] expected) {
        RarVM vm = new RarVM();
        vm.init();
        Arrays.fill(vm.getMem(), 0, 16, (byte) 0x55);
        for (int i = 0; i < 8; i++) {
            vm.getMem()[i] = (byte) (i + 1);
        }

        VMPreparedProgram program = new VMPreparedProgram();
        program.getInitR()[0] = channels;
        program.getInitR()[4] = 8;
        VMPreparedCommand filter = new VMPreparedCommand();
        filter.setOpCode(VMCommands.VM_STANDARD);
        filter.getOp1().setData(VMStandardFilters.VMSF_DELTA.getFilter());
        filter.getOp1().setType(VMOpType.VM_OPNONE);
        filter.getOp2().setType(VMOpType.VM_OPNONE);
        program.getCmd().add(filter);
        VMPreparedCommand ret = new VMPreparedCommand();
        ret.setOpCode(VMCommands.VM_RET);
        program.getCmd().add(ret);
        program.setCmdCount(2);
        program.setAltCmd(Arrays.asList());

        vm.execute(program);

        assertThat(Arrays.copyOfRange(vm.getMem(), 8, 16)).containsExactly(expected);
    }

    private static Stream<Arguments> deltaChannels() {
        return Stream.of(
                Arguments.of(0, bytes(0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55)),
                Arguments.of(1, bytes(0xff, 0xfd, 0xfa, 0xf6, 0xf1, 0xeb, 0xe4, 0xdc)),
                Arguments.of(1024, bytes(0xff, 0xfe, 0xfd, 0xfc, 0xfb, 0xfa, 0xf9, 0xf8)),
                Arguments.of(1025, bytes(0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55)),
                Arguments.of(-1, bytes(0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55)));
    }

    private static byte[] bytes(final int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
