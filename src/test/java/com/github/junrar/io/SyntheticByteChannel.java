package com.github.junrar.io;

import java.io.InputStream;

/**
 * Deterministic byte generator with no real backing store: {@code read()} yields
 * {@link #valueAt(long)} for a {@code long} cursor, so it can serve content far past
 * {@code Integer.MAX_VALUE} bytes without ever allocating that much memory itself.
 * Implements both {@link InputStream} (to feed {@code RandomAccessInputStream}/
 * {@code SeekableReadOnlyInputStream}) and {@link SeekableReadOnlyByteChannel} directly.
 */
public final class SyntheticByteChannel extends InputStream implements SeekableReadOnlyByteChannel {

    private long position;

    /**
     * Deterministic byte for a given absolute stream position, so reads can be verified.
     * Multiplicative (golden-ratio) hash rather than a raw {@code (byte) pos} truncation:
     * the latter repeats every 256 positions, which would hide an off-by-N offset bug.
     */
    public static byte valueAt(long pos) {
        return (byte) ((pos * 0x9E3779B97F4A7C15L) >>> 56);
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void setPosition(long pos) {
        position = pos;
    }

    @Override
    public int read() {
        return valueAt(position++) & 0xff;
    }

    @Override
    public int read(byte[] buffer, int off, int count) {
        for (int i = 0; i < count; i++) {
            buffer[off + i] = valueAt(position++);
        }
        return count;
    }

    @Override
    public int readFully(byte[] buffer, int count) {
        return read(buffer, 0, count);
    }

    @Override
    public void close() {
        // no-op: no real resource to release
    }
}
