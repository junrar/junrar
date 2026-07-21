/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import java.util.Arrays;

/**
 * RAR5/RAR7 sliding-dictionary window (issue #27, M3.6; segmented at M4.3, issue #35). The RAR3
 * chain's window is a compile-time constant ({@code Compress.MAXWINSIZE}); RAR5 windows are
 * per-archive and dynamic (128 KB up to the engine capability ceiling), so this is a sibling
 * abstraction, not the RAR3 {@code window} field.
 *
 * <p><b>Segmentation (plan &sect;6 R1, manual &sect;5.2).</b> A Java array caps at 2^31 bytes while
 * a RAR7 dictionary reaches 64 GB, so above 1 GB the window is a {@code byte[][]} of
 * {@link #SEGMENT_SHIFT}-sized (256 MB) power-of-two segments addressed by shift and mask. At
 * 1 GB and below there is exactly one segment spanning the whole window, so those archives keep
 * the M3.6 flat layout byte for byte. This is junrar's own design, not a port of unrar's
 * {@code unpack50frag.cpp} — that allocator solves a 32-bit-C++ fragmentation problem, not an
 * array-index-width problem.
 *
 * <p>Note that {@link #size} is <b>not</b> necessarily a power of two: a RAR7 dictionary carries a
 * 5-bit fraction ({@code winSize += winSize/32*frac}), so the last segment is short whenever the
 * size is not a whole multiple of the segment size, and window positions wrap through
 * {@code WrapUp}/{@code WrapDown} in {@link Unpack5} rather than through a {@code size - 1} mask.
 *
 * <p><b>Growth-capped, never header-eager allocation (review B-S3).</b> The declared {@link #size}
 * is only the addressing domain and the growth cap. Only the first segment exists up front, at the
 * {@code 0x40000} floor, and it doubles as positions are actually written; every later segment is
 * allocated on first touch. A tiny archive that claims a 64 GB dictionary therefore allocates
 * 256 KB, not 64 GB — strictly better than upstream, which eagerly allocates up to its limit.
 * Amortized copy cost is {@code <= 2x} bytes written.
 *
 * <p>Legitimacy of never pre-allocating the full window: reaching the first wrap means
 * {@link #size} real bytes were written (a legit archive), and pre-wrap reads beyond the written
 * region are rejected by the FirstWinDone semantics in {@link Unpack5#copyString}.
 */
public class Unpack5Window {

    /**
     * Minimum backing allocation (unrar {@code MinAllocSize}, {@code 8f437ab:unpack.cpp:85}): twice
     * the maximum filter block size for extra safety. Also the effective floor for the addressing
     * domain, so a sub-floor declared dictionary is bumped up to it.
     */
    public static final int MIN_ALLOC = 0x40000;

    /** Largest window still held in a single segment, i.e. the M3.6 flat layout unchanged. */
    static final long FLAT_MAX_SIZE = 1L << 30;

    /** Segment size above {@link #FLAT_MAX_SIZE}: 256 MB, so 64 GB needs 256 segments. */
    static final int SEGMENT_SHIFT = 28;

    /** Shift for a flat window: covers {@link #FLAT_MAX_SIZE}, so the segment index is always 0. */
    private static final int FLAT_SHIFT = 30;

    private final long size;
    private final int shift;
    private final long offsetMask;
    private final byte[][] segments;

    /**
     * @param size the addressing domain and growth cap in bytes; at least {@link #MIN_ALLOC} and
     *             at most the engine capability, which the caller enforces before constructing the
     *             window. Need not be a power of two.
     */
    public Unpack5Window(final long size) {
        this(size, size <= FLAT_MAX_SIZE ? FLAT_SHIFT : SEGMENT_SHIFT);
    }

    /**
     * Test seam: an explicit segment shift, so segment-boundary behaviour is reachable without
     * allocating 256 MB per segment.
     */
    Unpack5Window(final long size, final int segmentShift) {
        this.size = size;
        this.shift = segmentShift;
        this.offsetMask = (1L << segmentShift) - 1;
        this.segments = new byte[(int) ((size + offsetMask) >>> segmentShift)][];
        this.segments[0] = new byte[Math.min(segmentLength(0), MIN_ALLOC)];
    }

    /** @return the declared window size (addressing domain / growth cap), in bytes. */
    public long size() {
        return size;
    }

    /** @return whether the window needed more than one segment (i.e. it is past 1 GB). */
    public boolean isSegmented() {
        return segments.length > 1;
    }

    /** @return the number of segments the addressing domain is split into. */
    public int segmentCount() {
        return segments.length;
    }

    /** @return the bytes actually allocated so far, across every segment. */
    public long capacity() {
        long total = 0;
        for (final byte[] segment : segments) {
            if (segment != null) {
                total += segment.length;
            }
        }
        return total;
    }

    /**
     * Store a byte at an in-window position, allocating or growing its segment if the position is
     * beyond what has been written so far.
     *
     * @param pos an already-wrapped window position in {@code [0, size)}
     */
    public void put(final long pos, final byte value) {
        final int index = (int) (pos >>> shift);
        final int offset = (int) (pos & offsetMask);
        byte[] segment = segments[index];
        if (segment == null || offset >= segment.length) {
            segment = grow(index, offset);
        }
        segment[offset] = value;
    }

    /**
     * @param pos an already-written, already-wrapped window position
     * @return the byte previously stored at {@code pos}
     */
    public byte get(final long pos) {
        return segments[(int) (pos >>> shift)][(int) (pos & offsetMask)];
    }

    /**
     * The number of bytes readable contiguously from one backing array starting at {@code pos},
     * capped at {@code required} (the role of unrar's {@code FragmentedWindow::GetBlockSize}). The
     * write-out and filter-copy paths loop on this instead of assuming the window is one array.
     *
     * @param pos      an already-written, already-wrapped window position
     * @param required the number of bytes still wanted; the caller keeps the range inside the
     *                 window, so this is always positive
     */
    public int run(final long pos, final long required) {
        return (int) Math.min(required, segmentLength((int) (pos >>> shift)) - (pos & offsetMask));
    }

    /** @return the backing array holding {@code pos} — pair with {@link #offsetAt}. */
    public byte[] bufferAt(final long pos) {
        return segments[(int) (pos >>> shift)];
    }

    /** @return the index of {@code pos} within {@link #bufferAt(long)}. */
    public int offsetAt(final long pos) {
        return (int) (pos & offsetMask);
    }

    /**
     * Copy {@code len} written window bytes starting at {@code pos} into {@code dst}, crossing
     * segment boundaries as needed. The range must not cross the window <em>end</em> — callers
     * split that wrap themselves, the way unrar's filter sweep does.
     */
    public void copyOut(final long pos, final byte[] dst, final int dstOff, final int len) {
        int done = 0;
        while (done < len) {
            final long p = pos + done;
            final int n = run(p, len - done);
            System.arraycopy(bufferAt(p), offsetAt(p), dst, dstOff + done, n);
            done += n;
        }
    }

    /** @return the full length of segment {@code index}; the last one is short for a fractional size. */
    private int segmentLength(final int index) {
        return (int) Math.min(1L << shift, size - ((long) index << shift));
    }

    private byte[] grow(final int index, final int offset) {
        final byte[] old = segments[index];
        final int len = segmentLength(index);
        int cap;
        if (old != null) {
            cap = old.length;
        } else if (index == 0) {
            // The first segment doubles from the min-alloc floor, so a dict-bomb header that
            // claims 64 GB and then writes 100 bytes still allocates only 256 KB.
            cap = Math.min(len, MIN_ALLOC);
        } else {
            // Positions are written strictly in order, so touching a later segment at all proves
            // every earlier one filled: the window is genuinely filling and doubling this one
            // would only buy another full-segment copy.
            cap = len;
        }
        while (cap < len && cap <= offset) {
            cap <<= 1;
        }
        if (cap > len) {
            cap = len;
        }
        final byte[] segment = old == null ? new byte[cap] : Arrays.copyOf(old, cap);
        segments[index] = segment;
        return segment;
    }
}
