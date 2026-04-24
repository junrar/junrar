package com.github.junrar.rar5.io;

import java.util.Objects;

/**
 * Variable-length integer (vint) encoder/decoder for RAR5 archives.
 *
 * <p>Each byte contains 7 bits of data and 1 continuation flag (bit 7).
 * If the continuation flag is 1, more bytes follow. If 0, this is the last byte.
 * The first byte contains the least significant 7 bits (little-endian order).
 * Maximum encoded length is 10 bytes for a full 64-bit value.
 *
 * <p>Vints are always read from byte-aligned positions. The C++ unrar implementation
 * explicitly aligns to a byte boundary before reading any vint.
 *
 * <p>When space is pre-allocated before the exact value is known, leading bytes
 * may be filled with 0x80 (value 0 with continuation flag set).
 */
public final class VInt {

    /**
     * Maximum number of bytes a vint can occupy (10 bytes for 64-bit values).
     */
    public static final int MAX_BYTES = 10;

    /**
     * Mask for the data bits in a vint byte.
     */
    private static final int DATA_MASK = 0x7F;

    /**
     * Mask for the continuation flag bit.
     */
    private static final int CONTINUATION_FLAG = 0x80;

    private VInt() {
    }

    /**
     * Result of reading a vint, containing both the decoded value and the
     * number of bytes consumed from the source.
     */
    public static final class Result {

        private final long value;
        private final int bytesConsumed;

        private Result(final long value, final int bytesConsumed) {
            this.value = value;
            this.bytesConsumed = bytesConsumed;
        }

        /**
         * @return the decoded long value
         */
        public long getValue() {
            return value;
        }

        /**
         * @return the number of bytes consumed from the source
         */
        public int getBytesConsumed() {
            return bytesConsumed;
        }

        @Override
        public String toString() {
            return "Result{"
                    + "value=" + value
                    + ", bytesConsumed=" + bytesConsumed
                    + '}';
        }
    }

    /**
     * Reads a vint from a byte array starting at the given offset.
     *
     * @param data   the byte array to read from
     * @param offset the starting offset within the array
     * @return a Result containing the decoded value and bytes consumed
     * @throws IllegalArgumentException if offset is negative or data is null
     * @throws VIntOverflowException    if the vint exceeds 10 bytes or overflows a long
     */
    public static Result read(final byte[] data, final int offset) {
        Objects.requireNonNull(data, "data must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (offset >= data.length) {
            throw new VIntOverflowException("offset " + offset + " is beyond data length " + data.length);
        }

        long value = 0;
        int shift = 0;

        for (int bytesRead = 0; bytesRead < MAX_BYTES; bytesRead++) {
            if (offset + bytesRead >= data.length) {
                throw new VIntOverflowException("unexpected end of data after " + bytesRead + " bytes");
            }

            final int b = Byte.toUnsignedInt(data[offset + bytesRead]); // equal to & 0xFF
            value |= (long) (b & DATA_MASK) << shift;

            if ((b & CONTINUATION_FLAG) == 0) {
                return new Result(value, bytesRead + 1);
            }

            shift += 7;
        }

        throw new VIntOverflowException("vint exceeds maximum length of " + MAX_BYTES + " bytes");
    }

    /**
     * Encodes a non-negative long value as a vint into the given byte array.
     *
     * @param value  the value to encode (must be non-negative)
     * @param data   the byte array to write into
     * @param offset the starting offset within the array
     * @return the number of bytes written
     * @throws IllegalArgumentException if value is negative, data is null, or offset is invalid
     */
    public static int write(final long value, final byte[] data, final int offset) {
        Objects.requireNonNull(data, "data must not be null");
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, was " + value);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (offset >= data.length) {
            throw new IllegalArgumentException("insufficient space in data array");
        }

        long remaining = value;
        int bytesWritten = 0;

        // do-while is required here to correctly handle value == 0 (which must write 1 byte)
        do {
            if (offset + bytesWritten >= data.length) {
                throw new IllegalArgumentException("insufficient space in data array");
            }

            // OPTIMIZATION: Always set the continuation flag inside the loop.
            // This removes the "if (remaining != 0)" branch from the hot path.
            data[offset + bytesWritten] = (byte) ((remaining & DATA_MASK) | CONTINUATION_FLAG);

            remaining >>>= 7;
            bytesWritten++;
        } while (remaining != 0);

        // OPTIMIZATION: Clear the continuation flag on the final byte after the loop finishes.
        // (Assuming CONTINUATION_FLAG is 0x80, ~CONTINUATION_FLAG becomes 0x7F)
        data[offset + bytesWritten - 1] &= ~CONTINUATION_FLAG;

        return bytesWritten;
    }

    /**
     * Returns the number of bytes required to encode the given value as a vint.
     *
     * @param value the value to measure (must be non-negative)
     * @return the number of bytes needed (1 to 10)
     * @throws IllegalArgumentException if value is negative
     */
    public static int encodedLength(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, was " + value);
        }

        // The '| 1' trick safely handles value == 0 without an explicit if-statement.
        // Long.numberOfLeadingZeros is a JVM intrinsic that compiles to a single CPU instruction (like LZCNT).
        int bits = 64 - Long.numberOfLeadingZeros(value | 1);

        return (bits + 6) / 7;
    }
}
