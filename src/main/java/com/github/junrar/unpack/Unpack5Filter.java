/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.unpack.decode.Compress;

/**
 * One RAR5 filter queue entry (M3.8, issue #29; unrar {@code 8f437ab:unpack.hpp} struct
 * {@code UnpackFilter}). Mirrors the RAR3 {@link UnpackFilter} shape minus the VM fields —
 * RAR5 dropped the VM entirely, filters are just a type tag plus a window range (manual
 * &sect;5.4). {@code blockLength} is a C++ {@code uint} and stays {@code int}; {@code blockStart}
 * is zero-extended to {@code long} on read, because {@code AddFilter} folds it into a window
 * position ({@code size_t}, up to 64 GB) — {@code d861246:unpack.hpp:150}, where the field was
 * widened from {@code uint} to {@code size_t} for exactly that reason (no-go C15).
 */
class Unpack5Filter {

    private int type = Compress.FILTER_NONE;

    private long blockStart;

    private int blockLength;

    private int channels;

    private boolean nextWindow;

    public int getType() {
        return type;
    }

    public void setType(final int type) {
        this.type = type;
    }

    public long getBlockStart() {
        return blockStart;
    }

    public void setBlockStart(final long blockStart) {
        this.blockStart = blockStart;
    }

    public int getBlockLength() {
        return blockLength;
    }

    public void setBlockLength(final int blockLength) {
        this.blockLength = blockLength;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(final int channels) {
        this.channels = channels;
    }

    public boolean isNextWindow() {
        return nextWindow;
    }

    public void setNextWindow(final boolean nextWindow) {
        this.nextWindow = nextWindow;
    }
}
