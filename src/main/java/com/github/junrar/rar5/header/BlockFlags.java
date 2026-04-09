package com.github.junrar.rar5.header;

/**
 * Common RAR5 block header flags.
 */
public final class BlockFlags {

    /** Additional extra area is present in the end of block header. */
    public static final int HFL_EXTRA = 0x0001;

    /** Additional data area is present in the end of block header. */
    public static final int HFL_DATA = 0x0002;

    /** Unknown blocks with this flag must be skipped when updating an archive. */
    public static final int HFL_SKIPIFUNKNOWN = 0x0004;

    /** Data area of this block is continuing from previous volume. */
    public static final int HFL_SPLITBEFORE = 0x0008;

    /** Data area of this block is continuing in next volume. */
    public static final int HFL_SPLITAFTER = 0x0010;

    /** Block depends on preceding file block. */
    public static final int HFL_CHILD = 0x0020;

    /** Preserve a child block if host is modified. */
    public static final int HFL_INHERITED = 0x0040;

    private BlockFlags() {
    }
}
