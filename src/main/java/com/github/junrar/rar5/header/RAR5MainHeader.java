package com.github.junrar.rar5.header;

import com.github.junrar.rar5.Rar5Constants;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.MainHeader;
import com.github.junrar.rarfile.UnrarHeadertype;

import java.nio.file.attribute.FileTime;

public class RAR5MainHeader extends MainHeader {

    /**
     * Archive flags containing archive properties.
     * <ul>
     *   <li>0x0001 - Volume: archive is part of a multivolume set</li>
     *   <li>0x0002 - Volume number field is present</li>
     *   <li>0x0004 - Solid archive</li>
     *   <li>0x0008 - Recovery record is present</li>
     *   <li>0x0010 - Locked archive</li>
     * </ul>
     */
    private int arcFlags;

    /**
     * Volume number for multivolume archives.
     * 0 for first volume, 1 for second volume, etc.
     * Only present if archive flag 0x0002 is set.
     */
    private int volumeNumber;

    /**
     * Flag indicating whether the volume number field is present.
     * Not present for the first volume, present for all other volumes.
     */
    private boolean volumeNumberPresent;

    /**
     * Relative offset from the beginning of the main archive header to the quick open
     * service block. Present only if the locator record flag 0x0001 is set.
     * If equal to 0, should be ignored.
     */
    private long qOpenOffset;

    /**
     * Relative offset from the beginning of the main archive header to the recovery
     * record service block. Present only if the locator record flag 0x0002 is set.
     * If equal to 0, should be ignored.
     */
    private long rrOffset;

    /**
     * Flag indicating whether the locator record is present in the extra area
     * of the main archive header.
     */
    private boolean locator;

    /**
     * Original archive name from the metadata record (type 0x02) in the extra area.
     * Stored in UTF-8 format without trailing zero.
     */
    private String origName;

    /**
     * Original archive creation time from the metadata record.
     * Use {@link #getOrigTime()} to get as {@link FileTime}.
     */
    private FileTime origTime;

    /**
     * CRC32 checksum of the header data, starting from the header size field
     * and up to and including the optional extra area.
     */
    private long headerCrc32;

    public RAR5MainHeader() {
        super(createBaseBlock(), new byte[10]);
    }

    public RAR5MainHeader(int arcFlags) {
        super(createBaseBlock(arcFlags), new byte[6]);
        this.arcFlags = arcFlags;
    }

    /**
     * Creates a main header with the given archive flags and actual header size.
     *
     * @param arcFlags archive flags
     * @param headerSize actual header size read from the archive
     */
    public RAR5MainHeader(final int arcFlags, final short headerSize) {
        super(createBaseBlock(arcFlags), new byte[6]);
        this.arcFlags = arcFlags;
        setHeaderSize(headerSize);
    }

    @Override
    public short getHeaderSize(boolean encrypted) {
        return super.getHeaderSize(false);
    }

    private static BaseBlock createBaseBlock() {
        BaseBlock bb = new BaseBlock();
        bb.setHeaderType(UnrarHeadertype.MainHeader);
        return bb;
    }

    private static BaseBlock createBaseBlock(int arcFlags) {
        BaseBlock bb = new BaseBlock();
        bb.setHeaderType(UnrarHeadertype.MainHeader);
        bb.setFlags((short) arcFlags);
        return bb;
    }

    public RAR5MainHeader(BaseBlock bb, byte[] mainHeader) {
        super(bb, mainHeader);
    }

    public void setArcFlags(int arcFlags) {
        this.arcFlags = arcFlags;
    }

    /**
     * @return the archive flags
     */
    public int getArcFlags() {
        return arcFlags;
    }

    /**
     * @param volumeNumber the volume number
     */
    public void setVolumeNumber(int volumeNumber) {
        this.volumeNumber = volumeNumber;
    }

    /**
     * @return the volume number, or 0 if not set
     */
    public int getVolumeNumber() {
        return volumeNumber;
    }

    /**
     * @param volumeNumberPresent true if volume number is present
     */
    public void setVolumeNumberPresent(boolean volumeNumberPresent) {
        this.volumeNumberPresent = volumeNumberPresent;
    }

    /**
     * @return true if volume number field is present
     */
    public boolean isVolumeNumberPresent() {
        return volumeNumberPresent;
    }

    /**
     * @param qOpenOffset relative offset to quick open service block
     */
    public void setQOpenOffset(long qOpenOffset) {
        this.qOpenOffset = qOpenOffset;
    }

    /**
     * Returns the relative offset to the quick open service block.
     * @return quick open offset, or 0 if not present or should be ignored
     */
    public long getQOpenOffset() {
        return qOpenOffset;
    }

    /**
     * @param rrOffset relative offset to recovery record service block
     */
    public void setRrOffset(long rrOffset) {
        this.rrOffset = rrOffset;
    }

    /**
     * Returns the relative offset to the recovery record service block.
     * @return recovery record offset, or 0 if not present or should be ignored
     */
    public long getRrOffset() {
        return rrOffset;
    }

    /**
     * @param locator true if locator record is present
     */
    public void setLocator(boolean locator) {
        this.locator = locator;
    }

    /**
     * @return true if locator record is present in extra area
     */
    public boolean isLocator() {
        return locator;
    }

    /**
     * @param origName original archive name in UTF-8
     */
    public void setOrigName(String origName) {
        this.origName = origName;
    }

    /**
     * @return original archive name from metadata record, or null if not present
     */
    public String getOrigName() {
        return origName;
    }

    /**
     * @param origTime original archive creation time as {@link FileTime}
     */
    public void setOrigTime(FileTime origTime) {
        this.origTime = origTime;
    }

    /**
     * Returns the original archive creation time.
     * @return creation time as {@link FileTime}, or null if not present
     */
    public FileTime getOrigTime() {
        return origTime;
    }

    /**
     * @param headerCrc32 the header CRC32 value
     */
    public void setHeaderCrc32(long headerCrc32) {
        this.headerCrc32 = headerCrc32;
    }

    /**
     * Returns the CRC32 checksum of the header data.
     * @return header CRC32 value
     */
    public long getHeaderCrc32() {
        return headerCrc32;
    }

    public void setFlagsFromInt(int flags) {
        this.arcFlags = flags;
        this.flags = (short) flags;
    }

    public boolean isVolume() {
        return (arcFlags & Rar5Constants.MHFL_VOLUME) != 0;
    }

    public boolean isSolid() {
        return (arcFlags & Rar5Constants.MHFL_SOLID) != 0;
    }

    public boolean isProtectedPresent() {
        return (arcFlags & Rar5Constants.MHFL_PROTECT) != 0;
    }

    public boolean isLocked() {
        return (arcFlags & Rar5Constants.MHFL_LOCK) != 0;
    }

    @Override
    public boolean isMultiVolume() {
        return isVolume();
    }

    @Override
    public boolean isEncrypted() {
        return false;
    }
}
