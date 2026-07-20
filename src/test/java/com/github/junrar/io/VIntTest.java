package com.github.junrar.io;

import com.github.junrar.exception.CorruptHeaderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VIntTest {

    /** Base-128 little-endian, unsigned; continuation bit 0x80 on every non-final byte. */
    private static byte[] encode(long value) {
        byte[] tmp = new byte[VInt.MAX_BYTES];
        int n = 0;
        long v = value;
        do {
            int b = (int) (v & 0x7f);
            v >>>= 7;
            if (v != 0) {
                b |= 0x80;
            }
            tmp[n++] = (byte) b;
        } while (v != 0);
        return Arrays.copyOf(tmp, n);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 127, 128, 300, 16383, 16384, 0x7fffffffL, 0xffffffffL,
        0x123456789abcdefL, Long.MAX_VALUE})
    void decodesRoundTripAndAdvancesCursor(long value) throws Exception {
        byte[] encoded = encode(value);
        VInt vint = new VInt(encoded, 0);

        assertThat(vint.read()).isEqualTo(value);
        assertThat(vint.position()).isEqualTo(encoded.length);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void encodingWidthMatchesByteCount(int bytes) throws Exception {
        // Smallest value that requires exactly `bytes` continuation bytes: 2^(7*(bytes-1)).
        long value = bytes == 10 ? Long.MIN_VALUE : (1L << (7 * (bytes - 1)));
        byte[] encoded = encode(value);
        assertThat(encoded).hasSize(bytes);
        assertThat(new VInt(encoded, 0).read()).isEqualTo(value);
    }

    @Test
    void decodesTwoToThe63Boundary() throws Exception {
        // 2^63 needs all 10 bytes and, as a signed long, is Long.MIN_VALUE.
        byte[] encoded = encode(Long.MIN_VALUE);
        assertThat(encoded).hasSize(10);
        VInt vint = new VInt(encoded, 0);
        assertThat(vint.read()).isEqualTo(Long.MIN_VALUE);
        assertThat(vint.position()).isEqualTo(10);
    }

    @Test
    void readsSequentialVintsFromSharedCursor() throws Exception {
        byte[] a = encode(300);
        byte[] b = encode(0x7fffffffL);
        byte[] buf = new byte[a.length + b.length];
        System.arraycopy(a, 0, buf, 0, a.length);
        System.arraycopy(b, 0, buf, a.length, b.length);

        VInt vint = new VInt(buf, 0);
        assertThat(vint.read()).isEqualTo(300L);
        assertThat(vint.read()).isEqualTo(0x7fffffffL);
        assertThat(vint.position()).isEqualTo(buf.length);
    }

    @Test
    void overflowFromContinuationBitFloodThrows() {
        byte[] flood = new byte[VInt.MAX_BYTES]; // 10 bytes, continuation bit always set
        Arrays.fill(flood, (byte) 0x80);
        assertThatThrownBy(() -> new VInt(flood, 0).read())
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void overLongFloodBeyondMaxBytesThrows() {
        byte[] flood = new byte[64];
        Arrays.fill(flood, (byte) 0x80);
        assertThatThrownBy(() -> new VInt(flood, 0).read())
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void truncatedVintThrows() {
        // Continuation bit set but the buffer ends before the terminating byte.
        assertThatThrownBy(() -> new VInt(new byte[]{(byte) 0x80}, 0).read())
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }
}
