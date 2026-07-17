package com.github.junrar.unpack.vm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link RarVM#setLowEndianValue(Vector, int, int)} — RarVM's only directly-reachable
 * pure shift site — against negative {@code int} values (high bit set). VM_SHR/VM_SAR
 * (RarVM.java opcode switch) and the Itanium filter's private bit-composition helpers are
 * reachable only through full {@code execute(VMPreparedProgram)} — see signedness-audit.md
 * for the justified deferral rather than an invented bytecode-construction harness.
 */
class RarVMSignednessTest {

    @Test
    void givenIntWithOnlyTopByteSet_whenSetLowEndianValue_thenDecomposesUnsignedPerByte() {
        RarVM vm = new RarVM();
        Vector<Byte> mem = new Vector<>(Arrays.asList((byte) 0, (byte) 0, (byte) 0, (byte) 0));

        vm.setLowEndianValue(mem, 0, 0x80000000); // Integer.MIN_VALUE

        assertThat(mem).containsExactly((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x80);
    }

    @Test
    void givenAllBitsSetInt_whenSetLowEndianValue_thenEveryByteIsFullyUnsignedSet() {
        RarVM vm = new RarVM();
        Vector<Byte> mem = new Vector<>(Arrays.asList((byte) 0, (byte) 0, (byte) 0, (byte) 0));

        vm.setLowEndianValue(mem, 0, -1); // 0xFFFFFFFF

        assertThat(mem).containsExactly((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    // The two cases above (only-top-bit-set, all-bits-set) are degenerate for a wrong
    // shift-AMOUNT: every byte is 0x00 or 0xFF regardless of which 8-bit window is read, so
    // e.g. >>> 8 mutated to >>> 9 stays green. This asymmetric value has a distinct nonzero
    // byte at every position, so a wrong shift amount changes the extracted byte.
    @Test
    void givenAsymmetricInt_whenSetLowEndianValue_thenEachByteMatchesItsOwnShiftWindow() {
        RarVM vm = new RarVM();
        Vector<Byte> mem = new Vector<>(Arrays.asList((byte) 0, (byte) 0, (byte) 0, (byte) 0));

        vm.setLowEndianValue(mem, 0, 0x12345678);

        // 0x12345678 & 0xff = 0x78; >>> 8 & 0xff = 0x56; >>> 16 & 0xff = 0x34; >>> 24 & 0xff = 0x12.
        assertThat(mem).containsExactly((byte) 0x78, (byte) 0x56, (byte) 0x34, (byte) 0x12);
    }

}
