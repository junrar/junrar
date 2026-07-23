package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.exception.CorruptHeaderException;
import org.junit.jupiter.api.Test;

/**
 * Unit-level pins for the M1.5 (issue #19) {@link FileNameDecoder#decode(byte[], int, int)}
 * bounds rewrite: every read of the encoded stream is bounds-checked before the access
 * (ports unrar 7.2.7 encname.cpp EncodeFileName::Decode), and a truncated stream throws a
 * typed {@link CorruptHeaderException} rather than a raw ArrayIndexOutOfBoundsException
 * (MIGRATION_MANUAL &sect;4.7).
 */
class FileNameDecoderBoundsTest {

    @Test
    void case0MissingDataByteThrows() {
        // highByte=0x00 (encPos 0->1), flags=0x00 (encPos 1->2, case 0), no 3rd byte for
        // the literal char -> encPos(2) >= name.length(2).
        byte[] name = {0x00, 0x00};
        assertThatThrownBy(() -> FileNameDecoder.decode(name, 0, 0))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void case1MissingDataByteThrows() {
        // highByte=0x00 (encPos 0->1), flags=0x40 (encPos 1->2, case 1: flags>>>6==1), no
        // 3rd byte for the data byte -> encPos(2) >= name.length(2).
        byte[] name = {0x00, 0x40};
        assertThatThrownBy(() -> FileNameDecoder.decode(name, 0, 0))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void case2OnlyOneOfTwoDataBytesThrows() {
        // highByte=0x00 (encPos 0->1), flags=0x80 (encPos 1->2, case 2: flags>>>6==2), needs
        // 2 more bytes (low, high) but only 1 remains -> encPos+1(3) >= name.length(3).
        byte[] name = {0x00, (byte) 0x80, 0x11};
        assertThatThrownBy(() -> FileNameDecoder.decode(name, 0, 0))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void case3MissingLengthByteThrows() {
        // highByte=0x00 (encPos 0->1), flags=0xC0 (encPos 1->2, case 3: flags>>>6==3), no
        // length byte -> encPos(2) >= name.length(2).
        byte[] name = {0x00, (byte) 0xC0};
        assertThatThrownBy(() -> FileNameDecoder.decode(name, 0, 0))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void case3MissingCorrectionByteThrows() {
        // highByte=0x00 (encPos 0->1), flags=0xC0 (encPos 1->2, case 3), length byte 0x80
        // (encPos 2->3, high bit set -> needs a correction byte) but none remains ->
        // encPos(3) >= name.length(3).
        byte[] name = {0x00, (byte) 0xC0, (byte) 0x80};
        assertThatThrownBy(() -> FileNameDecoder.decode(name, 0, 0))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void emptyEncodedStreamReturnsEmptyStringWithoutThrowing() throws CorruptHeaderException {
        // encPos == name.length: no highByte read attempted (guarded), loop never enters.
        byte[] name = {};
        assertThat(FileNameDecoder.decode(name, 0, 0)).isEmpty();
    }
}
