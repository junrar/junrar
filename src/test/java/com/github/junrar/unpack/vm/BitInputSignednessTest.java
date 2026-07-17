package com.github.junrar.unpack.vm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BitInput#getbits()}/{@link BitInput#fgetbits()} — the class's only
 * buffer-widening bit-reading methods (docs/porting/reports/signedness-audit.md enumerates
 * all public members) — against buffers whose bytes all have the high bit set. Expected
 * values are hand-derived from the {@code getbits()} formula, not read back from it.
 */
class BitInputSignednessTest {

    @Test
    void givenHighBitSetBufferAndZeroBitOffset_whenGetbits_thenReturnsHandDerivedField() {
        BitInput input = new BitInput();
        byte[] buf = input.getInBuf();
        buf[0] = (byte) 0x80;
        buf[1] = (byte) 0x81;
        buf[2] = (byte) 0x82;
        input.inAddr = 0;
        input.inBit = 0;

        // composed = (0x80<<16)+(0x81<<8)+0x82 = 0x808182; shift = 8-0 = 8;
        // 0x808182 >>> 8 = 0x8081; & 0xffff = 0x8081 (32897).
        assertThat(input.getbits()).isEqualTo(0x8081);
    }

    @Test
    void givenHighBitSetBufferAndNonZeroBitOffset_whenGetbits_thenReturnsHandDerivedField() {
        BitInput input = new BitInput();
        byte[] buf = input.getInBuf();
        buf[0] = (byte) 0x80;
        buf[1] = (byte) 0x81;
        buf[2] = (byte) 0x82;
        input.inAddr = 0;
        input.inBit = 5;

        // composed = 0x808182; shift = 8-5 = 3; 0x808182 >>> 3 = 1052720 (0x101030);
        // & 0xffff = 0x1030 (4144).
        assertThat(input.getbits()).isEqualTo(0x1030);
    }

    @Test
    void givenHighBitSetBuffer_whenFgetbits_thenDelegatesToGetbitsExactly() {
        BitInput input = new BitInput();
        byte[] buf = input.getInBuf();
        buf[0] = (byte) 0x80;
        buf[1] = (byte) 0x81;
        buf[2] = (byte) 0x82;
        input.inAddr = 0;
        input.inBit = 0;

        assertThat(input.fgetbits()).isEqualTo(0x8081);
    }

}
