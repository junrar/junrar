package com.github.junrar.rar5.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VIntTest {

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 42, 64, 100, 126, 127})
    void readSingleByteVInt(final long value) {
        final byte[] data = new byte[1];
        final int written = VInt.write(value, data, 0);

        assertThat(written).isEqualTo(1);
        assertThat(data[0] & 0x80).isEqualTo(0);

        final VInt.Result result = VInt.read(data, 0);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.getBytesConsumed()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {128, 129, 200, 255, 256, 1000, 16383})
    void readTwoByteVInt(final long value) {
        final byte[] data = new byte[2];
        final int written = VInt.write(value, data, 0);

        assertThat(written).isEqualTo(2);
        assertThat(data[0] & 0x80).isNotZero();
        assertThat(data[1] & 0x80).isEqualTo(0);

        final VInt.Result result = VInt.read(data, 0);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.getBytesConsumed()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {16384, 16385, 100000, 2097151})
    void readThreeByteVInt(final long value) {
        final byte[] data = new byte[3];
        VInt.write(value, data, 0);

        final VInt.Result result = VInt.read(data, 0);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.getBytesConsumed()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(longs = {2097152, 10000000, 0x7FFFFFFFL})
    void readFourByteVInt(final long value) {
        final byte[] data = new byte[10];
        final int written = VInt.write(value, data, 0);

        final VInt.Result result = VInt.read(data, 0);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.getBytesConsumed()).isEqualTo(written);
    }

    @ParameterizedTest
    @ValueSource(longs = {0x80000000L, 0xFFFFFFFFL, 0x100000000L, 0x123456789ABCDEFL, Long.MAX_VALUE})
    void readLargeVInt(final long value) {
        final byte[] data = new byte[10];
        final int written = VInt.write(value, data, 0);

        final VInt.Result result = VInt.read(data, 0);
        assertThat(result.getValue()).isEqualTo(value);
        assertThat(result.getBytesConsumed()).isEqualTo(written);
    }

    @Test
    void readKnownByteSequences() {
        assertThat(readVInt((byte) 0x00)).isEqualTo(0);
        assertThat(readVInt((byte) 0x7F)).isEqualTo(127);
        assertThat(readVInt((byte) 0x80, (byte) 0x01)).isEqualTo(128);
        assertThat(readVInt((byte) 0x81, (byte) 0x01)).isEqualTo(129);
        assertThat(readVInt((byte) 0xFF, (byte) 0x01)).isEqualTo(255);
        assertThat(readVInt((byte) 0xFF, (byte) 0x7F)).isEqualTo(16383);
        assertThat(readVInt((byte) 0x80, (byte) 0x80, (byte) 0x01)).isEqualTo(16384);
        assertThat(readVInt((byte) 0xFF, (byte) 0xFF, (byte) 0x7F)).isEqualTo(2097151);
    }

    @Test
    void readPreAllocatedPadding() {
        assertThat(readVInt((byte) 0x80, (byte) 0x01)).isEqualTo(128);
        assertThat(readVInt((byte) 0x80, (byte) 0x80, (byte) 0x01)).isEqualTo(16384);
    }

    @Test
    void readMultipleVInts() {
        final byte[] data = new byte[20];
        int pos = 0;
        pos += VInt.write(42, data, pos);
        pos += VInt.write(1000, data, pos);
        pos += VInt.write(0, data, pos);
        pos += VInt.write(127, data, pos);

        int offset = 0;
        VInt.Result r1 = VInt.read(data, offset);
        assertThat(r1.getValue()).isEqualTo(42);
        offset += r1.getBytesConsumed();

        VInt.Result r2 = VInt.read(data, offset);
        assertThat(r2.getValue()).isEqualTo(1000);
        offset += r2.getBytesConsumed();

        VInt.Result r3 = VInt.read(data, offset);
        assertThat(r3.getValue()).isZero();
        offset += r3.getBytesConsumed();

        VInt.Result r4 = VInt.read(data, offset);
        assertThat(r4.getValue()).isEqualTo(127);
    }

    @Test
    void readFromOffset() {
        final byte[] data = {0x00, 0x00, (byte) 0x80, 0x01, 0x00};
        final VInt.Result result = VInt.read(data, 2);
        assertThat(result.getValue()).isEqualTo(128);
        assertThat(result.getBytesConsumed()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 42, 127})
    void writeSingleByte(final long value) {
        final byte[] data = new byte[1];
        final int written = VInt.write(value, data, 0);
        assertThat(written).isEqualTo(1);
        assertThat(data[0] & 0x80).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(longs = {128, 255, 16383})
    void writeTwoByte(final long value) {
        final byte[] data = new byte[2];
        final int written = VInt.write(value, data, 0);
        assertThat(written).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {16384, 2097151})
    void writeThreeByte(final long value) {
        final byte[] data = new byte[3];
        final int written = VInt.write(value, data, 0);
        assertThat(written).isEqualTo(3);
    }

    @Test
    void writeKnownByteSequences() {
        assertThat(writeVInt(0)).containsExactly(0x00);
        assertThat(writeVInt(127)).containsExactly(0x7F);
        assertThat(writeVInt(128)).containsExactly((byte) 0x80, 0x01);
        assertThat(writeVInt(16383)).containsExactly((byte) 0xFF, (byte) 0x7F);
        assertThat(writeVInt(16384)).containsExactly((byte) 0x80, (byte) 0x80, 0x01);
    }

    @Test
    void writeRoundTrip() {
        final long[] values = {0, 1, 42, 127, 128, 255, 256, 1000, 16383, 16384,
            0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0x100000000L,
            0x7FFFFFFFFFFFFFFFL};

        final byte[] buffer = new byte[20];
        for (final long value : values) {
            final int written = VInt.write(value, buffer, 0);
            final VInt.Result result = VInt.read(buffer, 0);
            assertThat(result.getValue())
                .as("round-trip failed for value %d (wrote %d bytes)", value, written)
                .isEqualTo(value);
            assertThat(result.getBytesConsumed()).isEqualTo(written);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 127})
    void encodedLengthOneByte(final long value) {
        assertThat(VInt.encodedLength(value)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {128, 255, 16383})
    void encodedLengthTwoBytes(final long value) {
        assertThat(VInt.encodedLength(value)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {16384, 2097151})
    void encodedLengthThreeBytes(final long value) {
        assertThat(VInt.encodedLength(value)).isEqualTo(3);
    }

    @Test
    void encodedLengthBoundaryValues() {
        assertThat(VInt.encodedLength(0xFFFFFFFFL)).isEqualTo(5);
        assertThat(VInt.encodedLength(0x100000000L)).isEqualTo(5);
        assertThat(VInt.encodedLength(Long.MAX_VALUE)).isEqualTo(9);
    }

    @Test
    void encodedLengthMatchesWritten() {
        final long[] values = {0, 1, 127, 128, 255, 16383, 16384,
            0x7FFFFFFFL, 0xFFFFFFFFL, 0x100000000L, Long.MAX_VALUE};

        final byte[] buffer = new byte[20];
        for (final long value : values) {
            final int actual = VInt.write(value, buffer, 0);
            final int predicted = VInt.encodedLength(value);
            assertThat(predicted).as("encodedLength for %d", value).isEqualTo(actual);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullData", "negativeOffset"})
    void readInvalidArguments(final String scenario) {
        if ("nullData".equals(scenario)) {
            assertThatThrownBy(() -> VInt.read(null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        } else {
            assertThatThrownBy(() -> VInt.read(new byte[10], -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        }
    }

    @Test
    void readTruncatedData() {
        final byte[] data = {(byte) 0x80};
        assertThatThrownBy(() -> VInt.read(data, 0))
            .isInstanceOf(VIntOverflowException.class)
            .hasMessageContaining("unexpected end of data");
    }

    @Test
    void readExcessiveContinuation() {
        final byte[] data = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
            (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
            (byte) 0x80, (byte) 0x80};
        assertThatThrownBy(() -> VInt.read(data, 0))
            .isInstanceOf(VIntOverflowException.class)
            .hasMessageContaining("exceeds maximum length");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, -100, Long.MIN_VALUE})
    void writeNegativeValue(final long value) {
        assertThatThrownBy(() -> VInt.write(value, new byte[10], 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative");
    }

    @Test
    void writeNullData() {
        assertThatThrownBy(() -> VInt.write(42, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void writeInsufficientSpace() {
        final byte[] data = new byte[1];
        assertThatThrownBy(() -> VInt.write(128, data, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("insufficient space");
    }

    @Test
    void writeNegativeOffset() {
        assertThatThrownBy(() -> VInt.write(42, new byte[10], -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    void encodedLengthNegative() {
        assertThatThrownBy(() -> VInt.encodedLength(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static long readVInt(final byte... data) {
        return VInt.read(data, 0).getValue();
    }

    private static byte[] writeVInt(final long value) {
        final byte[] buffer = new byte[10];
        final int written = VInt.write(value, buffer, 0);
        final byte[] result = new byte[written];
        System.arraycopy(buffer, 0, result, 0, written);
        return result;
    }
}
