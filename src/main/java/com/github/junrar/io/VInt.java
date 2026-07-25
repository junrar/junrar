package com.github.junrar.io;

import com.github.junrar.exception.CorruptHeaderException;

/**
 * RAR5 variable-length integer reader (unrar {@code RawRead::GetV},
 * {@code d861246:rawread.cpp:125-138}): base-128, little-endian, high bit
 * ({@code 0x80}) of each byte is the continuation flag. Unlike {@link Raw}'s
 * stateless static-offset helpers, a vint has no fixed width, so this reader keeps
 * a position cursor into the backing {@code byte[]} and advances it per read.
 * <p>
 * Divergence from unrar (manual &sect;4.7): unrar's reader silently returns {@code 0}
 * on buffer underrun or an over-long value; junrar sizes buffers exactly and throws
 * {@link CorruptHeaderException} instead of zero-filling. The general reader is capped
 * at {@link #MAX_BYTES} bytes (unrar's {@code Shift<64} guard), which bounds a hostile
 * continuation-bit flood; callers that need a tighter bound (e.g. the RAR5 header-size
 * field, capped at 3 vint bytes by M3.2's contract) enforce it separately.
 */
public final class VInt {

    /**
     * Maximum bytes a single vint may occupy. 10 &times; 7 bits = 70 bits covers a full
     * 64-bit value (a value needing bit 63 uses all 10 bytes); mirrors unrar's
     * {@code Shift<64} loop bound in {@code GetV}.
     */
    public static final int MAX_BYTES = 10;

    private final byte[] data;
    private int pos;

    public VInt(final byte[] data, final int pos) {
        this.data = data;
        this.pos = pos;
    }

    /**
     * @return the cursor position immediately after the last byte read.
     */
    public int position() {
        return pos;
    }

    /**
     * Reads one vint, advancing the cursor past it. Bits 64 and above are discarded
     * (unrar truncates the same way; a value can occupy the full {@link #MAX_BYTES}).
     *
     * @return the decoded value, interpreted as an unsigned 64-bit integer held in a
     *         signed {@code long} (so 2<sup>63</sup> and above are negative).
     * @throws CorruptHeaderException if the buffer ends mid-vint, or the continuation
     *                                bit is still set after {@link #MAX_BYTES} bytes.
     */
    public long read() throws CorruptHeaderException {
        long result = 0;
        for (int i = 0; i < MAX_BYTES; i++) {
            if (pos >= data.length) {
                throw new CorruptHeaderException("Truncated variable-length integer");
            }
            final int b = data[pos++] & 0xff;
            result |= (long) (b & 0x7f) << (i * 7);
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new CorruptHeaderException("Variable-length integer exceeds " + MAX_BYTES + " bytes");
    }
}
