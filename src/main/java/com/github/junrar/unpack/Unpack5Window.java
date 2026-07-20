/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import java.util.Arrays;

/**
 * RAR5 sliding-dictionary window (issue #27, M3.6). The RAR3 chain's window is a compile-time
 * constant ({@code Compress.MAXWINSIZE}); RAR5 windows are per-archive and dynamic (128 KB up to
 * the engine capability ceiling), so this is a sibling abstraction, not the RAR3 {@code window}
 * field.
 *
 * <p><b>Growth-capped, never header-eager allocation (plan &sect;6 R1, review B-S3).</b> The
 * declared window {@link #size} is only the addressing domain (a power of two) and the growth
 * cap. The backing array starts at the {@code 0x40000} floor and doubles (via {@code arraycopy})
 * as positions are actually written, capped at {@link #size}. A tiny archive that claims a 1 GB
 * dictionary therefore allocates kilobytes, not a gigabyte — strictly better than upstream, which
 * eagerly allocates up to its limit. Amortized copy cost is {@code <= 2x} bytes written.
 *
 * <p>Legitimacy of never pre-allocating the full window: reaching the first wrap means
 * {@link #size} real bytes were written (a legit archive), and pre-wrap reads beyond the written
 * region are rejected by the day-one FirstWinDone semantics landing with the M3.7 decode loop.
 */
public class Unpack5Window {

    /**
     * Minimum backing allocation (unrar {@code MinAllocSize}, {@code 8f437ab:unpack.cpp:85}): twice
     * the maximum filter block size for extra safety. Also the effective floor for the addressing
     * domain, so a sub-floor declared dictionary is bumped up to it.
     */
    public static final int MIN_ALLOC = 0x40000;

    private final int size;
    private final int mask;
    private byte[] buffer;

    /**
     * @param size the addressing domain and growth cap in bytes; must be a power of two that is
     *             {@code >= }{@link #MIN_ALLOC} and fits a Java array (the caller enforces the
     *             engine capability ceiling before constructing the window).
     */
    public Unpack5Window(final int size) {
        this.size = size;
        this.mask = size - 1;
        this.buffer = new byte[Math.min(size, MIN_ALLOC)];
    }

    /** @return the declared window size (addressing domain / growth cap), in bytes. */
    public int size() {
        return size;
    }

    /** @return the mask {@code size - 1} for wrapping window positions. */
    public int mask() {
        return mask;
    }

    /** @return the current backing-array length, i.e. the bytes actually allocated so far. */
    public int capacity() {
        return buffer.length;
    }

    /**
     * Store a byte at an in-window position, growing the backing array (doubling, capped at
     * {@link #size}) if the position is beyond the current capacity.
     *
     * @param pos an already-masked window position in {@code [0, size)}
     */
    public void put(final int pos, final byte value) {
        if (pos >= buffer.length) {
            grow(pos);
        }
        buffer[pos] = value;
    }

    /**
     * @param pos an already-written, already-masked window position
     * @return the byte previously stored at {@code pos}
     */
    public byte get(final int pos) {
        return buffer[pos];
    }

    private void grow(final int pos) {
        int cap = buffer.length;
        // Double until pos fits, but never past the cap (and never overflow int).
        while (cap < size && cap <= pos) {
            cap <<= 1;
        }
        if (cap > size) {
            cap = size;
        }
        buffer = Arrays.copyOf(buffer, cap);
    }
}
