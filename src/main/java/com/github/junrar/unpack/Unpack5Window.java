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

    private final long size;
    private byte[] buffer;

    /**
     * @param size the addressing domain and growth cap in bytes; at least {@link #MIN_ALLOC} and
     *             within a Java array (the caller enforces the engine capability ceiling before
     *             constructing the window). <b>Not necessarily a power of two</b> — a RAR7
     *             dictionary carries a 5-bit fraction ({@code winSize += winSize/32*frac}), so
     *             positions wrap through {@code WrapUp}/{@code WrapDown} arithmetic in
     *             {@link Unpack5}, never through a {@code size - 1} mask.
     */
    public Unpack5Window(final long size) {
        this.size = size;
        this.buffer = new byte[(int) Math.min(size, MIN_ALLOC)];
    }

    /** @return the declared window size (addressing domain / growth cap), in bytes. */
    public long size() {
        return size;
    }

    /** @return the current backing-array length, i.e. the bytes actually allocated so far. */
    public int capacity() {
        return buffer.length;
    }

    /**
     * Store a byte at an in-window position, growing the backing array (doubling, capped at
     * {@link #size}) if the position is beyond the current capacity.
     *
     * @param pos an already-wrapped window position in {@code [0, size)}
     */
    public void put(final long pos, final byte value) {
        if (pos >= buffer.length) {
            grow(pos);
        }
        buffer[(int) pos] = value;
    }

    /**
     * @param pos an already-written, already-wrapped window position
     * @return the byte previously stored at {@code pos}
     */
    public byte get(final long pos) {
        return buffer[(int) pos];
    }

    /**
     * The number of bytes readable contiguously from one backing array starting at {@code pos},
     * capped at {@code required} (unrar {@code FragmentedWindow::GetBlockSize}). The write-out
     * and filter-copy paths loop on this instead of assuming the whole window is one array.
     *
     * @param pos      an already-written, already-wrapped window position
     * @param required the number of bytes still wanted; never crosses the window end
     */
    public int run(final long pos, final long required) {
        return (int) Math.min(required, size - pos);
    }

    /** @return the backing array holding {@code pos} — pair with {@link #offsetAt}. */
    public byte[] bufferAt(final long pos) {
        return buffer;
    }

    /** @return the index of {@code pos} within {@link #bufferAt(long)}. */
    public int offsetAt(final long pos) {
        return (int) pos;
    }

    /**
     * Copy {@code len} written window bytes starting at {@code pos} into {@code dst}. The range
     * must not cross the window end — callers split a wrap themselves, the way unrar's filter
     * sweep does.
     */
    public void copyOut(final long pos, final byte[] dst, final int dstOff, final int len) {
        System.arraycopy(buffer, (int) pos, dst, dstOff, len);
    }

    private void grow(final long pos) {
        int cap = buffer.length;
        // Double until pos fits, but never past the cap (and never overflow int).
        while (cap < size && cap <= pos) {
            cap <<= 1;
        }
        if (cap > size) {
            cap = (int) size;
        }
        buffer = Arrays.copyOf(buffer, cap);
    }
}
