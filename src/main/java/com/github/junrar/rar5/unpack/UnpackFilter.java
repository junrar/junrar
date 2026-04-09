package com.github.junrar.rar5.unpack;

/**
 * RAR5 post-processing filter descriptor.
 */
public final class UnpackFilter {

    /** Filter type: DELTA */
    public static final int TYPE_DELTA = 0;

    /** Filter type: E8 (x86 CALL) */
    public static final int TYPE_E8 = 1;

    /** Filter type: E8E9 (x86 CALL + JMP) */
    public static final int TYPE_E8E9 = 2;

    /** Filter type: ARM (ARM BL) */
    public static final int TYPE_ARM = 3;

    private int type;
    private int channels;
    private long blockStart;
    private int blockLength;
    private boolean nextWindow;

    /** @return filter type */
    public int getType() {
        return type;
    }

    /** @param type the filter type */
    public void setType(final int type) {
        this.type = type;
    }

    /** @return number of channels (DELTA filter only) */
    public int getChannels() {
        return channels;
    }

    /** @param channels the number of channels */
    public void setChannels(final int channels) {
        this.channels = channels;
    }

    /** @return start position in the window */
    public long getBlockStart() {
        return blockStart;
    }

    /** @param blockStart the start position */
    public void setBlockStart(final long blockStart) {
        this.blockStart = blockStart;
    }

    /** @return length of data to filter */
    public int getBlockLength() {
        return blockLength;
    }

    /** @param blockLength the block length */
    public void setBlockLength(final int blockLength) {
        this.blockLength = blockLength;
    }

    /** @return true if this filter belongs to the next window cycle */
    public boolean isNextWindow() {
        return nextWindow;
    }

    /** @param nextWindow whether this filter belongs to the next window */
    public void setNextWindow(final boolean nextWindow) {
        this.nextWindow = nextWindow;
    }
}
