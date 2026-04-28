package com.github.junrar.rar5.header;

/**
 * RAR5 main archive header.
 *
 * <p>Contains archive-level flags and optional fields.
 *
 * <p>Fields:
 * <ul>
 *   <li>Archive flags (vint): VOLUME, VOLNUMBER, SOLID, PROTECT, LOCKED</li>
 *   <li>Volume number (vint, optional): present if VOLNUMBER flag set</li>
 * </ul>
 */
public final class Rar5MainHeader {

    /** Volume flag — archive is part of a multi-volume set. */
    public static final int MHFL_VOLUME = 0x0001;

    /** Volume number field is present (all volumes except first). */
    public static final int MHFL_VOLNUMBER = 0x0002;

    /** Solid archive flag. */
    public static final int MHFL_SOLID = 0x0004;

    /** Recovery record is present. */
    public static final int MHFL_PROTECT = 0x0008;

    /** Locked archive. */
    public static final int MHFL_LOCK = 0x0010;

    private final long flags;
    private final long volumeNumber;
    private final boolean isVolume;
    private final boolean isSolid;
    private final boolean isLocked;
    private final boolean hasRecovery;
    private final boolean isFirstVolume;

    private Rar5MainHeader(final long flags, final long volumeNumber,
                           final boolean isVolume, final boolean isSolid,
                           final boolean isLocked, final boolean hasRecovery,
                           final boolean isFirstVolume) {
        this.flags = flags;
        this.volumeNumber = volumeNumber;
        this.isVolume = isVolume;
        this.isSolid = isSolid;
        this.isLocked = isLocked;
        this.hasRecovery = hasRecovery;
        this.isFirstVolume = isFirstVolume;
    }

    /**
     * @return main header flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return volume number (0 for first volume or non-volume archives)
     */
    public long getVolumeNumber() {
        return volumeNumber;
    }

    /**
     * @return true if this archive is part of a multi-volume set
     */
    public boolean isVolume() {
        return isVolume;
    }

    /**
     * @return true if this is a solid archive
     */
    public boolean isSolid() {
        return isSolid;
    }

    /**
     * @return true if this archive is locked
     */
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * @return true if a recovery record is present
     */
    public boolean hasRecovery() {
        return hasRecovery;
    }

    /**
     * @return true if this is the first volume of a multi-volume archive
     */
    public boolean isFirstVolume() {
        return isFirstVolume;
    }

    /**
     * Parses the main header fields from the given byte array.
     *
     * @param data   the byte array containing the main header fields
     * @param offset the starting offset (after the common block header)
     * @param endOffset the offset where the main header fields end (before extra area)
     * @return the parsed main header
     * @throws IllegalArgumentException if parameters are invalid
     * @throws Rar5HeaderException      if the header is malformed
     */
    public static Rar5MainHeader parse(final byte[] data, final int offset, final int endOffset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || endOffset < offset) {
            throw new IllegalArgumentException("invalid offset range");
        }

        int pos = offset;

        // Read archive flags (vint)
        final com.github.junrar.rar5.io.VInt.Result flagsResult =
            com.github.junrar.rar5.io.VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        final boolean isVolume = (flags & MHFL_VOLUME) != 0;
        final boolean isSolid = (flags & MHFL_SOLID) != 0;
        final boolean isLocked = (flags & MHFL_LOCK) != 0;
        final boolean hasRecovery = (flags & MHFL_PROTECT) != 0;

        // Read optional volume number
        long volumeNumber = 0;
        if ((flags & MHFL_VOLNUMBER) != 0) {
            final com.github.junrar.rar5.io.VInt.Result volResult =
                com.github.junrar.rar5.io.VInt.read(data, pos);
            volumeNumber = volResult.getValue();
            pos += volResult.getBytesConsumed();
        }

        final boolean isFirstVolume = isVolume && volumeNumber == 0;

        return new Rar5MainHeader(flags, volumeNumber, isVolume, isSolid,
                                  isLocked, hasRecovery, isFirstVolume);
    }
}
