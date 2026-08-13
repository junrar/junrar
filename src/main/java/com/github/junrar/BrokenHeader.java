package com.github.junrar;

import com.github.junrar.rarfile.UnrarHeadertype;

/**
 * One header the archive could not use, as reported by {@link Archive#getHeaderFailures()}.
 *
 * <p>Deliberately not a {@link Throwable}. The number of these is decided by the archive being
 * read, so a crafted file of minimal blocks yields one per block; retaining a stack trace each
 * would turn a hostile archive into an allocation lever, which is the shape of problem this
 * package is fixing rather than one to add. Everything a caller can act on -- where it was, what
 * it claimed to be, how big it said it was, and why it was unusable -- is here, and the stack of
 * the parser that read it is not something a caller can act on.
 */
public final class BrokenHeader {

    private final long position;
    private final UnrarHeadertype headerType;
    private final int declaredSize;
    private final String reason;
    private final boolean terminal;

    BrokenHeader(
            final long position,
            final UnrarHeadertype headerType,
            final int declaredSize,
            final String reason,
            final boolean terminal) {
        this.position = position;
        this.headerType = headerType;
        this.declaredSize = declaredSize;
        this.reason = reason;
        this.terminal = terminal;
    }

    /**
     * Offset in the archive of the block this header began at, or {@code -1} for a failure that
     * belongs to the read rather than to one identifiable block.
     */
    public long getPosition() {
        return position;
    }

    /**
     * The type the block claimed to be; {@code null} either because that byte was not a known
     * type, or because the failure belongs to the read and no type byte was ever read.
     */
    public UnrarHeadertype getHeaderType() {
        return headerType;
    }

    /**
     * The size the header declared for itself, unpadded. Enumeration advances by this plus
     * whatever the block type adds -- packed data for a file header, and encryption padding when
     * the headers are encrypted -- so it is what the header claimed to be, not the distance to
     * the next one. Zero for a failure that belongs to the read rather than to one block.
     */
    public int getDeclaredSize() {
        return declaredSize;
    }

    /** Why the header could not be used. */
    public String getReason() {
        return reason;
    }

    /**
     * Whether this ended the read. A non-terminal failure cost only its own entry -- the headers
     * before and after it were still read. A terminal one cost every header after it as well,
     * because enumeration could not get past this block to reach them.
     */
    public boolean isTerminal() {
        return terminal;
    }

    @Override
    public String toString() {
        return "BrokenHeader{position="
                + position
                + ", headerType="
                + headerType
                + ", declaredSize="
                + declaredSize
                + ", terminal="
                + terminal
                + ", reason='"
                + reason
                + "'}";
    }
}
