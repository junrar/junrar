package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class FileNameDecoderTest {

    @Test
    public void testDecodeCase0() {
        // Case 0: Normal 8-bit characters
        // flags >>> 6 == 0 means flags are 0b00xxxxxx
        // Let's use flags = 0.
        // name[0] = highByte (not used in case 0)
        // name[1] = flags = 0
        // name[2] = 'a'
        byte[] name = {(byte) 0, (byte) 0, (byte) 'a'};
        String result = FileNameDecoder.decode(name, 0);
        assertThat(result).isEqualTo("a");
    }

    @Test
    public void testDecodeCase1() {
        // Case 1: (highByte << 8) + nextByte
        // flags >>> 6 == 1 means flags are 0b01xxxxxx (0x40)
        // name[0] = 0x12 (highByte)
        // name[1] = 0x40 (flags)
        // name[2] = 0x34
        // Expected char: (0x12 << 8) + 0x34 = 0x1234
        byte[] name = {(byte) 0x12, (byte) 0x40, (byte) 0x34};
        String result = FileNameDecoder.decode(name, 0);
        assertThat(result).isEqualTo("\u1234");
    }

    @Test
    public void testDecodeCase2() {
        // Case 2: (nextByte2 << 8) + nextByte1
        // flags >>> 6 == 2 means flags are 0b10xxxxxx (0x80)
        // name[0] = 0 (highByte)
        // name[1] = 0x80 (flags)
        // name[2] = 0x34 (low)
        // name[3] = 0x12 (high)
        // Expected char: 0x1234
        byte[] name = {(byte) 0, (byte) 0x80, (byte) 0x34, (byte) 0x12};
        String result = FileNameDecoder.decode(name, 0);
        assertThat(result).isEqualTo("\u1234");
    }

    @Test
    public void testSimpleSequence() {
        // highByte = 0
        // char 1: Case 0, 'a'
        // char 2: Case 0, 'b'
        // char 3: Case 1, 0x01 -> 0x0001
        byte[] name = {
            0,          // highByte
            0b01000000, // flags: Case 0, Case 1, Case 0, Case 0 (00 01 00 00) -> 0x40
            'a',        // Case 0
            0x01        // Case 1 -> (0 << 8) + 1 = 1
        };
        // encPos 0: highByte=0, encPos=1
        // encPos 1: flags=0x40, flagBits=8, encPos=2
        //   flags>>>6 = 1. Case 1. buf.append(name[2]+0)=97 ('a'). decPos=1, encPos=3
        //   flags = 0, flagBits=6
        //   flags>>>6 = 0. Case 0. buf.append(name[3])=1. decPos=2, encPos=4
        // Result: "a\u0001"
        String result = FileNameDecoder.decode(name, 0);
        assertThat(result).isEqualTo("a\u0001");
    }
}
