/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 22.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.rarfile.rar5.Rar5HostOS;
import com.github.junrar.rarfile.rar5.Rar5Redirection;
import com.github.junrar.rarfile.rar5.Rar5UnixOwner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class FileHeader extends BlockHeader {

    private static final Logger logger = LoggerFactory.getLogger(FileHeader.class);

    private static final byte SALT_SIZE = 8;

    private static final byte NEWLHD_SIZE = 32;

    private static final long NANOS_PER_UNIT = 100L; // 100ns units

    private final long unpSize;

    private final HostSystem hostOS;

    private final int fileCRC;

    private byte unpVersion;

    private byte unpMethod;

    /** RAR5 decode window size in bytes (0x20000 &lt;&lt; dictBits); 0 for RAR3 headers and directories. */
    private long rar5WinSize;

    /**
     * Absolute file offset of this RAR5 entry's packed data, set by the archive reader once the
     * on-disk header size is known (it includes the encryption IV + AES padding for
     * header-encrypted archives, which the plaintext header size cannot express). 0 for RAR3.
     */
    private long rar5DataStartOffset;

    private short nameSize;

    private final int highPackSize;

    private int highUnpackSize;

    private final byte[] fileNameBytes;

    private String fileName;
    private String fileNameW;

    private byte[] subData;

    private final byte[] salt = new byte[SALT_SIZE];

    private FileTime mTime;

    private FileTime cTime;

    private FileTime aTime;

    private FileTime arcTime;

    private long fullPackSize;

    private long fullUnpackSize;

    private int fileAttr;

    private int subFlags; // same as fileAttr (in header)

    private int recoverySectors = -1;

    /**
     * How many bytes of the type-specific header buffer field-by-field
     * parsing actually consumed (P0.7, issue #12). Equal to the buffer's
     * full length except for an old-style ({@code LHD_COMMENT}) file header,
     * whose trailing bytes are a legacy embedded comment blob this
     * constructor never reads -- see {@link #hasComment()}, used by
     * {@code Archive}'s header-CRC coverage computation (unrar
     * {@code CRCProcessedOnly}, {@code d861246:arcread.cpp:430-431}).
     */
    private final int parsedLength;

    // ---- RAR5-only facts (M3.3, issue #24) -- unset (false/0/null) for a RAR3 entry. ----

    private boolean unknownUnpSize;

    private byte[] hashDigest;
    private Rar5HashType hashType;

    private Rar5Redirection redirection;

    private long fileVersion;

    private Rar5UnixOwner unixOwner;

    private byte[] salt16;
    private byte[] initV;
    private int lg2Count;
    private boolean usePswCheck;
    private boolean useHashKey;
    private byte[] pswCheck;

    private long rar5HostOsValue;
    private Rar5HostOS rar5HostOS;

    /**
     * Whether this entry's header block lives in a RAR5-format container ({@code
     * Format==RARFMT50}), set unconditionally by the RAR5 constructor below -- true for EVERY
     * RAR5-container entry regardless of its decoded algorithm version, unlike {@link
     * #isRar5Family()} (issue #43).
     */
    private final boolean rar5Container;

    public FileHeader(BlockHeader bh, byte[] fileHeader) throws CorruptHeaderException {
        super(bh);

        int position = 0;
        unpSize = Raw.readIntLittleEndianAsLong(fileHeader, position);
        position += 4;
        hostOS = HostSystem.findHostSystem(fileHeader[4]);
        position++;

        fileCRC = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;

        int fileTime = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;

        unpVersion |= fileHeader[13] & 0xff;
        position++;
        unpMethod |= fileHeader[14] & 0xff;
        position++;
        nameSize = Raw.readShortLittleEndian(fileHeader, position);
        position += 2;

        fileAttr = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;
        if (isLargeBlock()) {
            highPackSize = Raw.readIntLittleEndian(fileHeader, position);
            position += 4;

            highUnpackSize = Raw.readIntLittleEndian(fileHeader, position);
            position += 4;
        } else {
            highPackSize = 0;
            highUnpackSize = 0;
            if (unpSize == 0xffffffffL) {
                highUnpackSize = Integer.MAX_VALUE;
            }

        }
        fullPackSize |= highPackSize;
        fullPackSize <<= 32;
        fullPackSize |= getPackSize();

        fullUnpackSize |= highUnpackSize;
        fullUnpackSize <<= 32;
        fullUnpackSize += unpSize;

        nameSize = nameSize > 4 * 1024 ? 4 * 1024 : nameSize;

        if (nameSize <= 0) {
            throw new CorruptHeaderException("Invalid file name with negative size");
        }

        fileNameBytes = new byte[nameSize];
        System.arraycopy(fileHeader, position, fileNameBytes, 0, nameSize);
        position += nameSize;

        if (isFileHeader()) {
            if (isUnicode()) {
                int length = 0;
                while (length < fileNameBytes.length
                    && fileNameBytes[length] != 0) {
                    length++;
                }
                // unrar 3.7.3 arcread.cpp:186-205: the ANSI-fallback bytes
                // (before the NUL, or the whole field when there is no
                // split) get NO charset conversion (raw strncpyz) -- decode
                // byte-transparently, not as UTF-8.
                fileName = new String(fileNameBytes, 0, length, StandardCharsets.ISO_8859_1);
                if (length != nameSize) {
                    int ansiNameLen = length;
                    length++;
                    fileNameW = FileNameDecoder.decode(fileNameBytes, ansiNameLen, length);
                } else {
                    // unrar 3.7.3 arcread.cpp:189-194: no NUL split means the
                    // whole field is the unicode name, UTF-8 encoded
                    // (UtfToWide), not the RLE FileNameDecoder blob.
                    fileNameW = new String(fileNameBytes, 0, nameSize, StandardCharsets.UTF_8);
                }
            } else {
                // unrar 3.7.3 arcread.cpp:186-205: the non-LHD_UNICODE path
                // does no charset conversion either (raw strncpyz) -- decode
                // byte-transparently, not as UTF-8.
                fileName = new String(fileNameBytes, StandardCharsets.ISO_8859_1);
                fileNameW = "";
            }

            if (!isFilenameValid(getFileName())) {
                throw new CorruptHeaderException("Invalid filename: " + getFileName());
            }
        }

        if (UnrarHeadertype.NewSubHeader.equals(headerType)) {
            int datasize = headerSize - NEWLHD_SIZE - nameSize;
            if (hasSalt()) {
                datasize -= SALT_SIZE;
            }
            if (datasize > 0) {
                subData = new byte[datasize];
                for (int i = 0; i < datasize; i++) {
                    subData[i] = (fileHeader[position]);
                    position++;
                }
            }

            if (NewSubHeaderType.SUBHEAD_TYPE_RR.byteEquals(fileNameBytes)) {
                recoverySectors = (subData[8] & 0xff) + ((subData[9] & 0xff) << 8)
                    + ((subData[10] & 0xff) << 16) + ((subData[11] & 0xff) << 24);
            }
        }

        if (hasSalt()) {
            for (int i = 0; i < SALT_SIZE; i++) {
                salt[i] = fileHeader[position];
                position++;
            }
        }

        mTime = FileTime.fromMillis(getDateDos(fileTime));

        if (hasExtTime()) {
            short extTimeFlags;
            if (position + 1 < fileHeader.length) {
                extTimeFlags = Raw.readShortLittleEndian(fileHeader, position);
                position += 2;
            } else {
                extTimeFlags = 0;
                logger.warn("FileHeader for entry '{}' signals extended time data, but does not contain any", (getFileName()));
            }

            TimePositionTuple mTimeTuple = parseExtTime(12, extTimeFlags, fileHeader, position, mTime);
            mTime = mTimeTuple.time;
            position = mTimeTuple.position;

            TimePositionTuple cTimeTuple = parseExtTime(8, extTimeFlags, fileHeader, position);
            cTime = cTimeTuple.time;
            position = cTimeTuple.position;

            TimePositionTuple aTimeTuple = parseExtTime(4, extTimeFlags, fileHeader, position);
            aTime = aTimeTuple.time;
            position = aTimeTuple.position;

            TimePositionTuple arcTimeTuple = parseExtTime(0, extTimeFlags, fileHeader, position);
            arcTime = arcTimeTuple.time;
            position = arcTimeTuple.position;
        }

        this.rar5Container = false;

        this.parsedLength = Math.min(position, fileHeader.length);
    }

    /**
     * RAR5 FILE/SERVICE constructor (M3.3, issue #24). Populates the SAME unified header the
     * RAR3 constructor above builds, from a {@link Rar5FileHeaderReader.Parsed} field bag -- see
     * that reader for the wire-format transcription (unrar {@code ReadHeader50}'s
     * {@code HEAD_FILE}/{@code HEAD_SERVICE} case plus {@code ProcessExtra50}'s FHEXTRA
     * records). Normalizes RAR5 facts onto the inherited RAR3 {@code flags} short (directory/
     * split-before/split-after/solid/password) so every EXISTING predicate ({@link
     * #isDirectory()}, {@link #isSplitBefore()}, {@link #isSplitAfter()}, {@link #isSolid()},
     * {@link #isEncrypted()}) works unmodified; RAR3-only flag bits (unicode/salt/large/exttime/
     * comment) are deliberately left unset so those RAR3-only parse paths stay dormant.
     *
     * @param p the parsed RAR5 fields.
     * @throws CorruptHeaderException if the (post version-suffix) entry name fails the same
     *                                {@link #isFilenameValid} gate the RAR3 constructor applies
     *                                (C10) -- a REDIR target is not gated, only the entry name.
     */
    FileHeader(Rar5FileHeaderReader.Parsed p) throws CorruptHeaderException {
        this.headerType = p.service ? UnrarHeadertype.NewSubHeader.getHeaderByte() : UnrarHeadertype.FileHeader.getHeaderByte();

        short f = 0;
        if (p.directory) {
            f |= LHD_DIRECTORY;
        }
        if (p.splitBefore) {
            f |= LHD_SPLIT_BEFORE;
        }
        if (p.splitAfter) {
            f |= LHD_SPLIT_AFTER;
        }
        if (p.solid) {
            f |= LHD_SOLID;
        }
        if (p.encrypted) {
            f |= LHD_PASSWORD;
        }
        this.flags = f;

        setPackAndDataSize(p.packSize);
        this.fullPackSize = p.packSize;
        this.highPackSize = 0;

        this.unpSize = p.unpSize;
        this.fullUnpackSize = p.unpSize;
        this.unknownUnpSize = p.unknownUnpSize;

        this.fileAttr = (int) p.fileAttr;
        this.fileCRC = p.hasCrc32 ? p.fileCRC : 0;

        this.unpMethod = (byte) p.unpMethod;
        this.unpVersion = p.unpVersion;
        this.rar5WinSize = p.winSize;

        this.rar5HostOsValue = p.hostOsRaw;
        this.rar5HostOS = p.hostOsEnum;
        this.hostOS = p.hostOsEnum == Rar5HostOS.WINDOWS ? HostSystem.win32
            : p.hostOsEnum == Rar5HostOS.UNIX ? HostSystem.unix : null;

        this.fileNameBytes = p.fileNameBytes;
        this.fileName = p.fileName;
        this.fileNameW = "";
        this.nameSize = (short) p.fileNameBytes.length;

        if (isFileHeader() && !isFilenameValid(getFileName())) {
            throw new CorruptHeaderException("Invalid filename: " + getFileName());
        }

        this.mTime = p.mTime;
        this.cTime = p.cTime;
        this.aTime = p.aTime;

        this.subData = p.subData;

        this.hashDigest = p.hashDigest;
        this.hashType = p.hashType;

        this.redirection = p.redirType == null ? null : new Rar5Redirection(p.redirType, p.redirIsDirectory, p.redirTarget);

        this.fileVersion = p.fileVersion;
        this.unixOwner = p.unixOwner;

        this.salt16 = p.salt16;
        this.initV = p.initV;
        this.lg2Count = p.lg2Count;
        this.usePswCheck = p.usePswCheck;
        this.useHashKey = p.useHashKey;
        this.pswCheck = p.pswCheck;

        // This constructor is only ever invoked from Rar5FileHeaderReader.read, which is only
        // ever invoked from Archive.readHeadersRar5 -- i.e. exactly when Format==RARFMT50.
        this.rar5Container = true;

        this.parsedLength = 0;
    }

    private static boolean isFilenameValid(String filename) {
        try {
            String ignored = new File(filename).getCanonicalPath();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static final class TimePositionTuple {
        private final int position;
        private final FileTime time;

        private TimePositionTuple(int position, FileTime time) {
            this.position = position;
            this.time = time;
        }
    }

    private static TimePositionTuple parseExtTime(int shift, short flags, byte[] fileHeader, int position) {
        return parseExtTime(shift, flags, fileHeader, position, null);
    }

    private static TimePositionTuple parseExtTime(int shift, short flags, byte[] fileHeader, int position, FileTime baseTime) {
        int flag = flags >>> shift;
        if ((flag & 0x8) != 0) {
            long seconds;
            if (baseTime != null) {
                seconds = baseTime.to(TimeUnit.SECONDS);
            } else {
                seconds = TimeUnit.MILLISECONDS.toSeconds(getDateDos(Raw.readIntLittleEndian(fileHeader, position)));
                position += 4;
            }
            int count = flag & 0x3;
            long remainder = 0;
            for (int i = 0; i < count; i++) {
                int b = fileHeader[position] & 0xff;
                remainder = (b << 16) | (remainder >>> 8);
                position++;
            }
            long nanos = remainder * NANOS_PER_UNIT;
            if ((flag & 0x4) != 0) {
                nanos += TimeUnit.SECONDS.toNanos(1);
            }
            FileTime time = FileTime.from(Instant.ofEpochSecond(seconds, nanos));
            return new TimePositionTuple(position, time);
        } else {
            return new TimePositionTuple(position, baseTime);
        }
    }

    @Override
    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            StringBuilder str = new StringBuilder();
            str.append("unpSize: ").append(getUnpSize());
            str.append("\nHostOS: ").append(hostOS.name());
            str.append("\nMTime: ").append(mTime);
            str.append("\nCTime: ").append(cTime);
            str.append("\nATime: ").append(aTime);
            str.append("\nArcTime: ").append(arcTime);
            str.append("\nFileName: ").append(fileName);
            str.append("\nFileNameW: ").append(fileNameW);
            str.append("\nunpMethod: ").append(Integer.toHexString(getUnpMethod()));
            str.append("\nunpVersion: ").append(Integer.toHexString(getUnpVersion()));
            str.append("\nfullpackedsize: ").append(getFullPackSize());
            str.append("\nfullunpackedsize: ").append(getFullUnpackSize());
            str.append("\nisEncrypted: ").append(isEncrypted());
            str.append("\nisfileHeader: ").append(isFileHeader());
            str.append("\nisSolid: ").append(isSolid());
            str.append("\nisSplitafter: ").append(isSplitAfter());
            str.append("\nisSplitBefore:").append(isSplitBefore());
            str.append("\nunpSize: ").append(getUnpSize());
            str.append("\ndataSize: ").append(getDataSize());
            str.append("\nisUnicode: ").append(isUnicode());
            str.append("\nhasVolumeNumber: ").append(hasVolumeNumber());
            str.append("\nhasArchiveDataCRC: ").append(hasArchiveDataCRC());
            str.append("\nhasSalt: ").append(hasSalt());
            str.append("\nhasEncryptVersions: ").append(hasEncryptVersion());
            str.append("\nisSubBlock: ").append(isSubBlock());
            logger.info(str.toString());
        }
    }

    private static long getDateDos(int time) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (time >>> 25) + 1980);
        cal.set(Calendar.MONTH, ((time >>> 21) & 0x0f) - 1);
        cal.set(Calendar.DAY_OF_MONTH, (time >>> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (time >>> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (time >>> 5) & 0x3f);
        cal.set(Calendar.SECOND, (time & 0x1f) * 2);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static Date toDate(FileTime time) {
        return time != null ? new Date(time.toMillis()) : null;
    }

    private static FileTime toFileTime(Date time) {
        return time != null ? FileTime.fromMillis(time.getTime()) : null;
    }

    /**
     * The time in which the file was archived.
     * Corresponds to te {@link FileHeader#arcTime} field.
     *
     * @return the timestamp, or null if absent.
     */
    public FileTime getArchivalTime() {
        return arcTime;
    }

    /**
     * Sets the time in which the file was archived.
     * Corresponds to te {@link FileHeader#arcTime} field.
     *
     * @param archivalTime the timestamp, or null to clear it.
     */
    public void setArchivalTime(FileTime archivalTime) {
        this.arcTime = archivalTime;
    }

    /**
     * Gets {@link FileHeader#getArchivalTime()} as a {@link Date}.
     * The maximum granularity is reduced from microseconds to milliseconds.
     *
     * @return the date, or null if absent.
     */
    public Date getArcTime() {
        return toDate(getArchivalTime());
    }

    /**
     * Sets {@link FileHeader#setArchivalTime(FileTime)} from a {@link Date}.
     *
     * @param arcTime the date, or null to clear it.
     */
    public void setArcTime(Date arcTime) {
        setArchivalTime(toFileTime(arcTime));
    }

    /**
     * The time in which the file was last accessed.
     * Corresponds to te {@link FileHeader#aTime} field.
     *
     * @return the timestamp, or null if absent.
     */
    public FileTime getLastAccessTime() {
        return aTime;
    }

    /**
     * Sets the time in which the file was last accessed.
     * Corresponds to te {@link FileHeader#aTime} field.
     *
     * @param time the timestamp, or null to clear it.
     */
    public void setLastAccessTime(FileTime time) {
        aTime = time;
    }

    /**
     * Gets {@link FileHeader#getLastAccessTime()} as a {@link Date}.
     * The maximum granularity is reduced from microseconds to milliseconds.
     *
     * @return the date, or null if absent.
     */
    public Date getATime() {
        return toDate(getLastAccessTime());
    }

    /**
     * Sets {@link FileHeader#setLastAccessTime(FileTime)} from a {@link Date}.
     *
     * @param time the date, or null to clear it.
     */
    public void setATime(Date time) {
        setLastAccessTime(toFileTime(time));
    }

    /**
     * The time in which the file was created.
     * Corresponds to te {@link FileHeader#cTime} field.
     *
     * @return the timestamp, or null if absent.
     */
    public FileTime getCreationTime() {
        return cTime;
    }

    /**
     * Sets the time in which the file was created.
     * Corresponds to te {@link FileHeader#cTime} field.
     *
     * @param time the timestamp, or null to clear it.
     */
    public void setCreationTime(FileTime time) {
        cTime = time;
    }

    /**
     * Gets {@link FileHeader#getCreationTime()} as a {@link Date}.
     * The maximum granularity is reduced from microseconds to milliseconds.
     *
     * @return the date, or null if absent.
     */
    public Date getCTime() {
        return toDate(getCreationTime());
    }

    /**
     * Sets {@link FileHeader#setCreationTime(FileTime)} from a {@link Date}.
     *
     * @param time the date, or null to clear it.
     */
    public void setCTime(Date time) {
        setCreationTime(toFileTime(time));
    }

    public int getFileAttr() {
        return fileAttr;
    }

    public void setFileAttr(int fileAttr) {
        this.fileAttr = fileAttr;
    }

    public int getFileCRC() {
        return fileCRC;
    }

    public byte[] getFileNameByteArray() {
        return fileNameBytes;
    }

    /**
     * The ASCII filename.
     *
     * @return the ASCII filename
     * @deprecated As of 7.2.0, replaced by {@link #getFileName()}
     */
    @Deprecated
    public String getFileNameString() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * The unicode filename.
     *
     * @return the Unicode filename, or null if the filename is ASCII only
     * @deprecated As of 7.2.0, replaced by {@link #getFileName()}
     */
    @Deprecated
    public String getFileNameW() {
        return fileNameW;
    }

    public void setFileNameW(String fileNameW) {
        this.fileNameW = fileNameW;
    }

    public int getHighPackSize() {
        return highPackSize;
    }

    public int getHighUnpackSize() {
        return highUnpackSize;
    }

    public HostSystem getHostOS() {
        return hostOS;
    }

    /**
     * The time in which the file was last modified.
     * Corresponds to te {@link FileHeader#mTime} field.
     *
     * @return the timestamp, or null if absent.
     */
    public FileTime getLastModifiedTime() {
        return mTime;
    }

    /**
     * Sets the time in which the file was last modified.
     * Corresponds to te {@link FileHeader#mTime} field.
     *
     * @param time the timestamp, or null to clear it.
     */
    public void setLastModifiedTime(FileTime time) {
        mTime = time;
    }

    /**
     * Gets {@link FileHeader#getLastModifiedTime()} as a {@link Date}.
     * The maximum granularity is reduced from microseconds to milliseconds.
     *
     * @return the date, or null if absent.
     */
    public Date getMTime() {
        return toDate(getLastModifiedTime());
    }

    /**
     * Sets {@link FileHeader#setLastModifiedTime(FileTime)} from a {@link Date}.
     *
     * @param time the date, or null to clear it.
     */
    public void setMTime(Date time) {
        setLastModifiedTime(toFileTime(time));
    }

    public short getNameSize() {
        return nameSize;
    }

    public int getRecoverySectors() {
        return recoverySectors;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getSubData() {
        return subData;
    }

    public int getSubFlags() {
        return subFlags;
    }

    public byte getUnpMethod() {
        return unpMethod;
    }

    public long getUnpSize() {
        return unpSize;
    }

    public byte getUnpVersion() {
        return unpVersion;
    }

    /**
     * @return whether this entry belongs to the RAR5-format engine family: version 50 (RAR5, and a
     *         RAR7 header that {@code FCI_RAR5_COMPAT} demoted) or version 70 (RAR7). unrar treats
     *         the two as one case throughout — {@code case VER_PACK5: case VER_PACK7:} in
     *         {@code d861246:unpack.cpp:182-184} — and RAR7 reuses the RARFMT50 container
     *         ({@code d861246:archive.cpp:120}), so volume merging and the split-before guard key
     *         off this, not off version 50 alone (M4.2, issue #34).
     */
    public boolean isRar5Family() {
        return unpVersion == 50 || unpVersion == 70;
    }

    /**
     * @return whether this entry's header block lives in a RAR5-format container ({@code
     *         Format==RARFMT50}), independent of the decoded algorithm version -- unlike {@link
     *         #isRar5Family()}, true even for an unrecognized ({@code VER_UNKNOWN}) algorithm
     *         version. unrar's {@code CheckUnpVer} ({@code d861246:extract.cpp:1517-1520}) keys
     *         its by-name method refusal off exactly this container fact, not off the decoded
     *         version (issue #43).
     */
    public boolean isRar5Container() {
        return rar5Container;
    }

    /**
     * @return the RAR5 decode dictionary/window size in bytes ({@code 0x20000 << dictBits} from
     *         the compression-info field, {@code arcread.cpp:855}); 0 for RAR3 headers and RAR5
     *         directory entries. Consumed by {@code Unpack5.init} for per-archive window sizing.
     */
    public long getRar5WinSize() {
        return rar5WinSize;
    }

    /** @param offset absolute file offset of this RAR5 entry's packed data (archive reader only). */
    public void setRar5DataStartOffset(final long offset) {
        this.rar5DataStartOffset = offset;
    }

    /**
     * @param archiveEncrypted whether the archive uses header encryption (RAR3/4 padding only)
     * @return the absolute file offset where this entry's packed data begins. RAR5 entries carry
     *         it precomputed ({@link #setRar5DataStartOffset}, which folds in the encryption IV and
     *         AES padding a plaintext header size cannot express); RAR3/4 keep the existing
     *         {@code position + headerSize(+padding)} formula.
     */
    public long getDataStartOffset(final boolean archiveEncrypted) {
        if (rar5DataStartOffset > 0) {
            return rar5DataStartOffset;
        }
        return positionInFile + getHeaderSize(archiveEncrypted);
    }

    public long getFullPackSize() {
        return fullPackSize;
    }

    public long getFullUnpackSize() {
        return fullUnpackSize;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * the file will be continued in the next archive part
     *
     * @return isSplitAfter
     */
    public boolean isSplitAfter() {
        return (this.flags & BlockHeader.LHD_SPLIT_AFTER) != 0;
    }

    /**
     * the file is continued in this archive
     *
     * @return isSplitBefore
     */
    public boolean isSplitBefore() {
        return (this.flags & LHD_SPLIT_BEFORE) != 0;
    }

    /**
     * this file is compressed as solid (all files handeled as one)
     *
     * @return isSolid
     */
    public boolean isSolid() {
        return (this.flags & LHD_SOLID) != 0;
    }

    /**
     * the file is encrypted
     *
     * @return isEncrypted
     */
    public boolean isEncrypted() {
        return (this.flags & BlockHeader.LHD_PASSWORD) != 0;
    }

    /**
     * the filename is also present in unicode
     *
     * @return isUnicode
     */
    public boolean isUnicode() {
        return (flags & LHD_UNICODE) != 0;
    }

    public boolean isFileHeader() {
        return UnrarHeadertype.FileHeader.equals(headerType);
    }

    public boolean hasSalt() {
        return (flags & LHD_SALT) != 0;
    }

    public boolean hasExtTime() {
        return (flags & LHD_EXTTIME) != 0;
    }

    /**
     * Whether this header carries an old-style (up to RAR 2.9) comment blob
     * embedded past the fields this constructor parses (P0.7, issue #12;
     * unrar {@code CommentInHeader}, {@code d861246:arcread.cpp:268}). Only
     * meaningful for {@link #isFileHeader()} entries -- see
     * {@link #getParsedLength()}.
     *
     * @return isComment
     */
    public boolean hasComment() {
        return (flags & LHD_COMMENT) != 0;
    }

    /**
     * How many bytes of the type-specific header buffer field-by-field
     * parsing actually consumed (P0.7, issue #12) -- see
     * {@link #hasComment()}.
     *
     * @return the parsed length, in bytes, relative to the start of the
     *         type-specific header buffer (i.e. excluding the base and block
     *         header portions).
     */
    public int getParsedLength() {
        return parsedLength;
    }

    public boolean isLargeBlock() {
        return (flags & LHD_LARGE) != 0;
    }

    /**
     * whether this fileheader represents a directory
     *
     * @return isDirectory
     */
    public boolean isDirectory() {
        return (flags & LHD_WINDOWMASK) == LHD_DIRECTORY;
    }

    /**
     * The filename either in Unicode or ASCII.
     *
     * @return the Unicode filename if it exists, else the ASCII filename
     * @since 7.2.0
     */
    public String getFileName() {
        return isUnicode() && fileNameW != null && !fileNameW.isEmpty() ? fileNameW : fileName;
    }

    // ---- RAR5-only accessors (M3.3, issue #24) -- see the RAR5 constructor. ----

    /**
     * @return whether the RAR5 unpacked size is unknown (unrar {@code FHFL_UNPUNKNOWN}/
     *         {@code INT64NDF}); always {@code false} for a RAR3 entry.
     */
    public boolean isUnpSizeUnknown() {
        return unknownUnpSize;
    }

    /**
     * @return the Blake2sp digest from a FHEXTRA_HASH record, or {@code null} if this entry
     *         has none (a RAR3 entry, or a RAR5 entry whose only checksum is the legacy CRC32).
     */
    public byte[] getHashDigest() {
        return hashDigest == null ? null : hashDigest.clone();
    }

    /**
     * @return the hash algorithm of {@link #getHashDigest()}, or {@code null} if none.
     */
    public Rar5HashType getHashType() {
        return hashType;
    }

    /**
     * @return the FHEXTRA_REDIR fact (symlink/junction/hardlink/file-copy target), or
     *         {@code null} if this entry has none. The target is parsed verbatim, not
     *         sanitized -- see {@link Rar5Redirection}.
     */
    public Rar5Redirection getRedirection() {
        return redirection;
    }

    /**
     * @return the FHEXTRA_VERSION file version, or 0 if this entry has none (unrar appends
     *         {@code ";N"} to {@link #getFileName()} when this is non-zero).
     */
    public long getFileVersion() {
        return fileVersion;
    }

    /**
     * @return the FHEXTRA_UOWNER fact, or {@code null} if this entry has none.
     */
    public Rar5UnixOwner getUnixOwner() {
        return unixOwner;
    }

    /**
     * @return the FHEXTRA_CRYPT 16-byte salt, or {@code null} if this entry is not
     *         RAR5-encrypted. Distinct from the RAR3 8-byte {@link #getSalt()}.
     */
    public byte[] getSalt16() {
        return salt16 == null ? null : salt16.clone();
    }

    /**
     * @return the FHEXTRA_CRYPT 16-byte initialization vector, or {@code null} if this entry is
     *         not RAR5-encrypted.
     */
    public byte[] getInitVector() {
        return initV == null ? null : initV.clone();
    }

    /**
     * @return the FHEXTRA_CRYPT KDF iteration count (log2), or 0 if this entry is not
     *         RAR5-encrypted.
     */
    public int getLg2Count() {
        return lg2Count;
    }

    /**
     * @return whether a valid FHEXTRA_CRYPT password-check record is present (its SHA-256-prefix
     *         csum verified at parse time, M3.4). When true, {@link #getPswCheck()} is non-null.
     */
    public boolean isUsePswCheck() {
        return usePswCheck;
    }

    /**
     * @return the 8-byte FHEXTRA_CRYPT password-check value, or {@code null} if absent or its
     *         csum failed to verify. Compared against the value {@code Rar5Crypt} derives from
     *         the password to detect a wrong password before decrypting file data (M3.4).
     */
    public byte[] getPswCheck() {
        return pswCheck == null ? null : pswCheck.clone();
    }

    /**
     * @return whether the FHEXTRA_CRYPT HASHMAC flag is set, i.e. the stored checksum is
     *         HMAC-masked with the KDF hash key ({@code ConvertHashToMAC}); consumed by the
     *         extraction-time hash verification (Blake2 variant lands in M3.5).
     */
    public boolean isUseHashKey() {
        return useHashKey;
    }

    /**
     * @return the raw RAR5 {@code HostOS} wire value.
     */
    public long getRar5HostOsValue() {
        return rar5HostOsValue;
    }

    /**
     * @return the RAR5 host OS, or {@code null} for a RAR3 entry or an unrecognized value
     *         (unrar {@code HSYS_UNKNOWN}).
     */
    public Rar5HostOS getRar5HostOS() {
        return rar5HostOS;
    }
}
