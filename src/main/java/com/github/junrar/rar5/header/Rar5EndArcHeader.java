package com.github.junrar.rar5.header;

/**
 * RAR5 end of archive header.
 *
 * <p>Fields:
 * <ul>
 *   <li>End of archive flags (vint): NEXTVOLUME (0x0001) = not last volume</li>
 * </ul>
 */
public final class Rar5EndArcHeader {

    /** Not last volume flag. */
    public static final int EHFL_NEXTVOLUME = 0x0001;

    private final long flags;
    private final boolean isNotLastVolume;

    private Rar5EndArcHeader(final long flags, final boolean isNotLastVolume) {
        this.flags = flags;
        this.isNotLastVolume = isNotLastVolume;
    }

    /**
     * @return end of archive flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return true if this is not the last volume of a multi-volume archive
     */
    public boolean isNotLastVolume() {
        return isNotLastVolume;
    }

    /**
     * Parses the end of archive header fields from the given byte array.
     *
     * @param data   the byte array containing the end arc header fields
     * @param offset the starting offset (after the common block header)
     * @return the parsed end arc header
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static Rar5EndArcHeader parse(final byte[] data, final int offset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }

        final com.github.junrar.rar5.io.VInt.Result flagsResult =
            com.github.junrar.rar5.io.VInt.read(data, offset);
        final long flags = flagsResult.getValue();

        final boolean isNotLastVolume = (flags & EHFL_NEXTVOLUME) != 0;

        return new Rar5EndArcHeader(flags, isNotLastVolume);
    }
}
