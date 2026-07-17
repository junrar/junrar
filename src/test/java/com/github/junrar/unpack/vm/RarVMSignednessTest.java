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

}
