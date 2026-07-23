package com.github.junrar.unpack.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M2.1 (recognition model) exercises {@link RarVM#execute(VMPreparedProgram)}'s
 * dispatch to the native standard filters exclusively through {@code VMSF_DELTA}
 * elsewhere (RarVMDeltaChannelsTest) and {@code VMSF_AUDIO} via the archive-level
 * ArchiveTest audio fixtures -- both take the {@code FilteredDataOffset=blockSize}
 * branch. This pins the other branch: E8/E8E9/ITANIUM always report
 * {@code FilteredDataOffset=0} (the transform runs in place at the start of
 * {@code Mem}, unlike DELTA/RGB/AUDIO which write past {@code Mem+blockSize}).
 * The native E8 transform itself (ExecuteStandardFilter) is unchanged by M2.1.
 */
class RarVME8DispatchTest {

    @Test
    void e8FilterReportsZeroOffsetAndRewritesRelativeCall() {
        RarVM vm = new RarVM();
        vm.init();
        // 0xE8 (CALL opcode) followed by a little-endian 32-bit relative address of 0,
        // then an untouched sentinel byte just past the filtered region.
        byte[] seed = {(byte) 0xe8, 0x00, 0x00, 0x00, 0x00, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa};
        System.arraycopy(seed, 0, vm.getMem(), 0, seed.length);

        VMPreparedProgram program = new VMPreparedProgram();
        program.getInitR()[4] = 8; // dataSize
        program.getInitR()[6] = 0; // fileOffset
        program.setType(VMStandardFilters.VMSF_E8);

        vm.execute(program);

        assertThat(program.getFilteredDataSize()).isEqualTo(8);
        assertThat(program.getFilteredDataOffset()).isZero();
        // Addr(0) - (CurPos(1)+FileOffset(0)) == -1, written little-endian as 4 bytes of 0xFF.
        assertThat(vm.getMem()[0]).isEqualTo((byte) 0xe8);
        assertThat(vm.getMem()[1]).isEqualTo((byte) 0xff);
        assertThat(vm.getMem()[2]).isEqualTo((byte) 0xff);
        assertThat(vm.getMem()[3]).isEqualTo((byte) 0xff);
        assertThat(vm.getMem()[4]).isEqualTo((byte) 0xff);
        assertThat(vm.getMem()[5]).isEqualTo((byte) 0xaa);
    }
}
