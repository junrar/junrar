package com.github.junrar.unpack.vm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Migrated for M2.1 (recognition model): the DELTA filter is now selected via
 * {@link VMPreparedProgram#setType(VMStandardFilters)} and invoked directly by
 * {@link RarVM#execute(VMPreparedProgram)} — there is no more VM_STANDARD
 * command/AltCmd/CmdCount scaffolding to build. The native transform itself
 * (ExecuteStandardFilter) is unchanged, so the expected byte assertions below
 * are byte-identical to the pre-M2.1 versions of this test.
 */
class RarVMDeltaChannelsTest {

    @Test
    void deltaFilterRejectsUnsignedHighBitDataSize() {
        RarVM vm = new RarVM();
        vm.init();
        Arrays.fill(vm.getMem(), 0, 16, (byte) 0x55);

        VMPreparedProgram program = new VMPreparedProgram();
        program.getInitR()[0] = 1;
        program.getInitR()[4] = Integer.MIN_VALUE;
        program.setType(VMStandardFilters.VMSF_DELTA);

        vm.execute(program);

        assertThat(Arrays.copyOfRange(vm.getMem(), 0, 16))
                .containsExactly(
                        bytes(
                                0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55,
                                0x55, 0x55, 0x55, 0x55, 0x55));
    }

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
        program.setType(VMStandardFilters.VMSF_DELTA);

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
