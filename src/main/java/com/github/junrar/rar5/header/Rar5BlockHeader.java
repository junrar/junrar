package com.github.junrar.rar5.header;

/**
 * Base RAR5 block header containing fields common to all block types.
 *
 * <p>Every RAR5 block starts with:
 * <ul>
 *   <li>Header CRC32 (uint32)</li>
 *   <li>Header size (vint) — size from Header type through extra area</li>
 *   <li>Header type (vint)</li>
 *   <li>Header flags (vint)</li>
 *   <li>Extra area size (vint, optional, present if HFL_EXTRA flag)</li>
 *   <li>Data size (vint, optional, present if HFL_DATA flag)</li>
 * </ul>
 */
public final class Rar5BlockHeader {

    private final long headCrc;
    private final long headerSize;
    private final HeaderType headerType;
    private final long flags;
    private final long extraSize;
    private final long dataSize;
    private final boolean hasExtra;
    private final boolean hasData;

    public Rar5BlockHeader(final long headCrc, final long headerSize,
                            final HeaderType headerType, final long flags,
                            final long extraSize, final long dataSize,
                            final boolean hasExtra, final boolean hasData) {
        this.headCrc = headCrc;
        this.headerSize = headerSize;
        this.headerType = headerType;
        this.flags = flags;
        this.extraSize = extraSize;
        this.dataSize = dataSize;
        this.hasExtra = hasExtra;
        this.hasData = hasData;
    }

    /**
     * @return CRC32 of the header data
     */
    public long getHeadCrc() {
        return headCrc;
    }

    /**
     * @return size of header data from Header type through extra area
     */
    public long getHeaderSize() {
        return headerSize;
    }

    /**
     * @return the block type
     */
    public HeaderType getHeaderType() {
        return headerType;
    }

    /**
     * @return block flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return extra area size, or 0 if not present
     */
    public long getExtraSize() {
        return extraSize;
    }

    /**
     * @return data area size, or 0 if not present
     */
    public long getDataSize() {
        return dataSize;
    }

    /**
     * @return true if extra area is present
     */
    public boolean hasExtra() {
        return hasExtra;
    }

    /**
     * @return true if data area is present
     */
    public boolean hasData() {
        return hasData;
    }

    /**
     * @return true if this block's data continues from a previous volume
     */
    public boolean isSplitBefore() {
        return (flags & BlockFlags.HFL_SPLITBEFORE) != 0;
    }

    /**
     * @return true if this block's data continues in the next volume
     */
    public boolean isSplitAfter() {
        return (flags & BlockFlags.HFL_SPLITAFTER) != 0;
    }

    /**
     * Parses a RAR5 block header from a byte array.
     *
     * @param data   the byte array containing the block header
     * @param offset the starting offset
     * @return the parsed block header
     * @throws IllegalArgumentException if data is null or offset is invalid
     * @throws Rar5HeaderException      if the header is malformed
     */
    public static Rar5BlockHeader parse(final byte[] data, final int offset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }

        int pos = offset;

        // Read Header CRC32 (4 bytes, little-endian)
        final long headCrc = readUint32(data, pos);
        pos += 4;

        // Read Header size (vint)
        final com.github.junrar.rar5.io.VInt.Result sizeResult =
            com.github.junrar.rar5.io.VInt.read(data, pos);
        final long headerSize = sizeResult.getValue();
        pos += sizeResult.getBytesConsumed();

        if (headerSize == 0) {
            throw new Rar5HeaderException("header size must not be zero");
        }

        // Read Header type (vint)
        final com.github.junrar.rar5.io.VInt.Result typeResult =
            com.github.junrar.rar5.io.VInt.read(data, pos);
        final long typeValue = typeResult.getValue();
        pos += typeResult.getBytesConsumed();

        final HeaderType headerType = HeaderType.fromValue(typeValue);
        if (headerType == null) {
            throw new Rar5HeaderException("unknown header type: " + typeValue);
        }

        // Read Header flags (vint)
        final com.github.junrar.rar5.io.VInt.Result flagsResult =
            com.github.junrar.rar5.io.VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        // Read optional extra area size
        final boolean hasExtra = (flags & BlockFlags.HFL_EXTRA) != 0;
        long extraSize = 0;
        if (hasExtra) {
            final com.github.junrar.rar5.io.VInt.Result extraResult =
                com.github.junrar.rar5.io.VInt.read(data, pos);
            extraSize = extraResult.getValue();
            pos += extraResult.getBytesConsumed();
        }

        // Read optional data size
        final boolean hasData = (flags & BlockFlags.HFL_DATA) != 0;
        long dataSize = 0;
        if (hasData) {
            final com.github.junrar.rar5.io.VInt.Result dataResult =
                com.github.junrar.rar5.io.VInt.read(data, pos);
            dataSize = dataResult.getValue();
        }

        return new Rar5BlockHeader(headCrc, headerSize, headerType, flags,
                                   extraSize, dataSize, hasExtra, hasData);
    }

    /**
     * Reads a 32-bit unsigned integer from a byte array (little-endian).
     */
    private static long readUint32(final byte[] data, final int offset) {
        return ((data[offset + 3] & 0xFFL) << 24)
             | ((data[offset + 2] & 0xFFL) << 16)
             | ((data[offset + 1] & 0xFFL) << 8)
             | (data[offset] & 0xFFL);
    }
}
