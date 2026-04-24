package com.github.junrar.rar5.header;

import com.github.junrar.rar5.Rar5Constants;
import com.github.junrar.rarfile.BaseBlock;

/**
 * RAR5 end-of-archive header (HEAD_ENDARC).
 */
public class RAR5EndArcHeader extends BaseBlock {

    private final int arcFlags;
    private final boolean nextVolume;

    public RAR5EndArcHeader(final long headerCrc32, final long headerSize,
                            final long commonFlags, final int arcFlags) {
        this.headCRC = (short) headerCrc32;
        this.headerSize = (short) headerSize;
        this.flags = (short) commonFlags;
        this.arcFlags = arcFlags;
        this.nextVolume = (arcFlags & Rar5Constants.EHFL_NEXTVOLUME) != 0;
    }

    /**
     * RAR5 end-of-archive specific flags (EHFL_* constants).
     */
    public int getArcFlags() {
        return arcFlags;
    }

    /**
     * Returns true if another volume follows this one.
     */
    public boolean isNextVolume() {
        return nextVolume;
    }
}
