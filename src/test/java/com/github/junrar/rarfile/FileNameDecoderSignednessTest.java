package com.github.junrar.rarfile;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FileNameDecoder#decode(byte[], int)} against names whose bytes are &gt;=0x80 in
 * every field the algorithm widens: {@code highByte}, the flags selector byte (drives
 * {@code flags >>> 6}), and each switch case's data bytes. Expected chars are hand-derived
 * from the algorithm (derivation in each case's comment), not read back from the decoder.
 */
class FileNameDecoderSignednessTest {

    static Stream<Arguments> highBitCases() {
        return Stream.of(
            // case 0 (flags top 2 bits == 00): unshifted data byte -> char. 0x9F pins
            // getChar's & 0xff mask directly: an unmasked byte sign-extends into the char's
            // low 16 bits (0xFF9F), not just bits a shift would discard.
            Arguments.of(new byte[] {(byte) 0x85, (byte) 0x00, (byte) 0x9F}, 0,
                "case0 unshifted data byte", String.valueOf((char) 0x9F)),
            // case 1 (flags top 2 bits == 01): dataByte + (highByte<<8); dataByte=0x9A is
            // unshifted (mask-sensitive), highByte=0x85 is shifted first.
            // 0x9A + (0x85<<8) = 0x009A + 0x8500 = 0x859A.
            Arguments.of(new byte[] {(byte) 0x85, (byte) 0x40, (byte) 0x9A}, 0,
                "case1 highByte<<8 plus unshifted data byte", String.valueOf((char) 0x859A)),
            // case 2 (flags top 2 bits == 10, flags itself >= 0x80): (high<<8)+low.
            // flags=0x80 exercises "flags >>> 6" selecting the branch from a high-bit-set
            // selector byte. (0x85<<8)+0x9C = 0x8500+0x009C = 0x859C.
            Arguments.of(new byte[] {(byte) 0x00, (byte) 0x80, (byte) 0x9C, (byte) 0x85}, 0,
                "case2 (high<<8)+low with high-bit selector", String.valueOf((char) 0x859C)),
            // case 3 plain (flags top 2 bits == 11, length&0x80==0): copies decPos-indexed
            // bytes back out unshifted; flags=0xC0 again drives "flags >>> 6" from a
            // high-bit-set selector. Bound by name.length=3, so 3 chars are copied back:
            // name[0]=0x85, name[1]=0xC0, name[2]=0x05.
            Arguments.of(new byte[] {(byte) 0x85, (byte) 0xC0, (byte) 0x05}, 0,
                "case3 plain decPos back-copy",
                new String(new char[] {(char) 0x85, (char) 0xC0, (char) 0x05})),
            // case 3 correction (length&0x80!=0): low=(name[decPos]+correction)&0xff,
            // char=(highByte<<8)+low. highByte=0x85, correction=0x03; decPos reads
            // name[0..2]=0x85,0xC0,0x81 -> lows 0x88,0xC3,0x84 -> chars 0x8588,0x85C3,0x8584.
            Arguments.of(new byte[] {(byte) 0x85, (byte) 0xC0, (byte) 0x81, (byte) 0x03}, 0,
                "case3 correction branch",
                new String(new char[] {(char) 0x8588, (char) 0x85C3, (char) 0x8584}))
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("highBitCases")
    void givenHighBitSetBytes_whenDecode_thenMatchesHandDerivedOutput(byte[] name, int encPos, String description, String expected) {
        assertThat(FileNameDecoder.decode(name, encPos)).as(description).isEqualTo(expected);
    }

}
