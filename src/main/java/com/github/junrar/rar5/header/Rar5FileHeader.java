package com.github.junrar.rar5.header;

import com.github.junrar.rar5.header.extra.Rar5ExtraRecord;
import com.github.junrar.rar5.header.extra.Rar5ExtraParser;
import com.github.junrar.rar5.header.extra.Rar5FileCryptRecord;
import com.github.junrar.rar5.header.extra.Rar5FileHashRecord;
import com.github.junrar.rar5.header.extra.Rar5FileTimeRecord;
import com.github.junrar.rar5.header.extra.Rar5RedirRecord;
import com.github.junrar.rar5.header.extra.Rar5UnixOwnerRecord;
import com.github.junrar.rar5.header.extra.Rar5FileVersionRecord;
import com.github.junrar.rar5.io.VInt;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;

import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * RAR5 file header (HEAD_FILE) and service header (HEAD_SERVICE).
 *
 * <p>Both share the same structure. Fields are read after the common block header:
 * <ul>
 *   <li>File flags (vint): DIRECTORY, UTIME, CRC32, UNPUNKNOWN</li>
 *   <li>Unpacked size (vint)</li>
 *   <li>Attributes (vint): OS-specific file attributes</li>
 *   <li>mtime (uint32, optional): Unix modification time</li>
 *   <li>Data CRC32 (uint32, optional)</li>
 *   <li>Compression info (vint): algorithm, method, dictionary size</li>
 *   <li>Host OS (vint): 0=Windows, 1=Unix</li>
 *   <li>Name length (vint)</li>
 *   <li>Name (UTF-8 string)</li>
 *   <li>Extra area records (optional)</li>
 * </ul>
 */
public final class Rar5FileHeader extends FileHeader {

    /** File is a directory. */
    public static final int FHFL_DIRECTORY = 0x0001;

    /** Unix time field is present. */
    public static final int FHFL_UTIME = 0x0002;

    /** CRC32 field is present. */
    public static final int FHFL_CRC32 = 0x0004;

    /** Unknown unpacked size. */
    public static final int FHFL_UNPUNKNOWN = 0x0008;

    /** Compression info: algorithm version mask (bits 0-5). */
    public static final int FCI_ALGO_MASK = 0x003F;

    /** Compression info: solid flag (bit 6). */
    public static final int FCI_SOLID = 0x0040;

    /** Compression info: method mask (bits 7-9). */
    public static final int FCI_METHOD_MASK = 0x0380;

    /** Compression info: method shift. */
    public static final int FCI_METHOD_SHIFT = 7;

    /** Compression info: dictionary size mask (bits 10-14). */
    public static final int FCI_DICT_MASK = 0x7C00;

    /** Compression info: dictionary size shift. */
    public static final int FCI_DICT_SHIFT = 10;

    /** Compression info: RAR5 compat flag for algorithm v1 (bit 20). */
    public static final int FCI_RAR5_COMPAT = 0x100000;

    /** Host OS: Windows. */
    public static final int HOST_WINDOWS = 0;

    /** Host OS: Unix. */
    public static final int HOST_UNIX = 1;

    private final long fileFlags;
    private final long unpackedSize;
    private final long attributes;
    private final Long mtime;
    private final Long dataCrc32;
    private final long compressionInfo;
    private final int hostOS;
    private final String fileName;
    private final boolean isDirectory;
    private final boolean hasUnixTime;
    private final boolean hasCrc32;
    private final boolean unknownUnpSize;
    private final int algorithmVersion;
    private final int compressionMethod;
    private final long dictionarySize;
    private final boolean isSolid;
    private final List<Rar5ExtraRecord> extraRecords;
    private long dataPosition = -1;
    private long packedSize = 0;
    private byte[] compressedData;

    private Rar5FileHeader(final long fileFlags, final long unpackedSize,
                           final long attributes, final Long mtime,
                           final Long dataCrc32, final long compressionInfo,
                           final int hostOS, final String fileName,
                           final boolean isDirectory, final boolean hasUnixTime,
                           final boolean hasCrc32, final boolean unknownUnpSize,
                           final int algorithmVersion, final int compressionMethod,
                           final long dictionarySize, final boolean isSolid,
                           final List<Rar5ExtraRecord> extraRecords) {
        super(unpackedSize,
              hostOS == HOST_UNIX ? HostSystem.unix : HostSystem.win32,
              dataCrc32 != null ? dataCrc32.intValue() : 0,
              0,
              fileName != null ? fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
        this.fileFlags = fileFlags;
        this.unpackedSize = unpackedSize;
        this.attributes = attributes;
        this.mtime = mtime;
        this.dataCrc32 = dataCrc32;
        this.compressionInfo = compressionInfo;
        this.hostOS = hostOS;
        this.fileName = fileName;
        this.isDirectory = isDirectory;
        this.hasUnixTime = hasUnixTime;
        this.hasCrc32 = hasCrc32;
        this.unknownUnpSize = unknownUnpSize;
        this.algorithmVersion = algorithmVersion;
        this.compressionMethod = compressionMethod;
        this.dictionarySize = dictionarySize;
        this.isSolid = isSolid;
        this.extraRecords = extraRecords;
    }

    /**
     * @return file header flags
     */
    public long getFileFlags() {
        return fileFlags;
    }

    /**
     * @return unpacked file size (may be undefined if unknownUnpSize is true)
     */
    public long getUnpackedSize() {
        return unpackedSize;
    }

    /**
     * @return OS-specific file attributes
     */
    public long getAttributes() {
        return attributes;
    }

    /**
     * @return modification time as Unix timestamp (seconds), or null if not present
     */
    public Long getMtime() {
        return mtime;
    }

    /**
     * @return CRC32 of unpacked data, or null if not present
     */
    public Long getDataCrc32() {
        return dataCrc32;
    }

    /**
     * @return raw compression info field
     */
    public long getCompressionInfo() {
        return compressionInfo;
    }

    /**
     * @return host OS raw value (HOST_WINDOWS=0, HOST_UNIX=1)
     */
    public int getHostOSRaw() {
        return hostOS;
    }

    @Override
    public HostSystem getHostOS() {
        return hostOS == HOST_UNIX ? HostSystem.unix : HostSystem.win32;
    }

    /**
     * @return file name in UTF-8
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @return true if this entry is a directory
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * @return true if Unix time field is present
     */
    public boolean hasUnixTime() {
        return hasUnixTime;
    }

    /**
     * @return true if CRC32 field is present
     */
    public boolean hasCrc32() {
        return hasCrc32;
    }

    /**
     * @return true if the unpacked size is unknown
     */
    public boolean isUnknownUnpSize() {
        return unknownUnpSize;
    }

    /**
     * @return algorithm version (0=RAR5.0, 1=RAR7.0)
     */
    public int getAlgorithmVersion() {
        return algorithmVersion;
    }

    /**
     * @return compression method (0=stored, 1-5=fastest to best)
     */
    public int getCompressionMethod() {
        return compressionMethod;
    }

    /**
     * @return dictionary size in bytes
     */
    public long getDictionarySize() {
        return dictionarySize;
    }

    /**
     * @return true if this file is part of a solid stream
     */
    public boolean isSolid() {
        return isSolid;
    }

    /**
     * @return unmodifiable list of extra area records
     */
    public List<Rar5ExtraRecord> getExtraRecords() {
        return extraRecords;
    }

    /**
     * Finds the first extra record of the given type.
     *
     * @param type the record type to find
     * @return the record, or null if not found
     */
    public Rar5ExtraRecord findExtra(final long type) {
        for (final Rar5ExtraRecord record : extraRecords) {
            if (record.getRecordType() == type) {
                return record;
            }
        }
        return null;
    }

    /**
     * @return the file encryption record, or null if not present
     */
    public Rar5FileCryptRecord getCryptRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5FileCryptRecord.TYPE);
        return record instanceof Rar5FileCryptRecord ? (Rar5FileCryptRecord) record : null;
    }

    /**
     * @return the file hash record, or null if not present
     */
    public Rar5FileHashRecord getHashRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5FileHashRecord.TYPE);
        return record instanceof Rar5FileHashRecord ? (Rar5FileHashRecord) record : null;
    }

    /**
     * @return the file time record, or null if not present
     */
    public Rar5FileTimeRecord getTimeRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5FileTimeRecord.TYPE);
        return record instanceof Rar5FileTimeRecord ? (Rar5FileTimeRecord) record : null;
    }

    /**
     * @return the redirection record, or null if not present
     */
    public Rar5RedirRecord getRedirRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5RedirRecord.TYPE);
        return record instanceof Rar5RedirRecord ? (Rar5RedirRecord) record : null;
    }

    /**
     * @return the Unix owner record, or null if not present
     */
    public Rar5UnixOwnerRecord getUnixOwnerRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5UnixOwnerRecord.TYPE);
        return record instanceof Rar5UnixOwnerRecord ? (Rar5UnixOwnerRecord) record : null;
    }

    /**
     * @return the version record, or null if not present
     */
    public Rar5FileVersionRecord getVersionRecord() {
        final Rar5ExtraRecord record = findExtra(Rar5FileVersionRecord.TYPE);
        return record instanceof Rar5FileVersionRecord ? (Rar5FileVersionRecord) record : null;
    }

    /**
     * @return true if this file is encrypted
     */
    public boolean isEncrypted() {
        return getCryptRecord() != null;
    }

    /**
     * @return true if this is a symlink or hard link
     */
    public boolean isLink() {
        final Rar5RedirRecord redir = getRedirRecord();
        return redir != null && (redir.isSymlink() || redir.isHardLink());
    }

    /**
     * @return true if this is a symbolic link
     */
    public boolean isSymlink() {
        final Rar5RedirRecord redir = getRedirRecord();
        return redir != null && redir.isSymlink();
    }

    /**
     * @return true if this is a hard link
     */
    public boolean isHardLink() {
        final Rar5RedirRecord redir = getRedirRecord();
        return redir != null && redir.isHardLink();
    }

    /** @return the file position where compressed data starts */
    public long getDataPosition() {
        return dataPosition;
    }

    /** @param dataPosition the file position where compressed data starts */
    public void setDataPosition(final long dataPosition) {
        this.dataPosition = dataPosition;
    }

    /** @return the packed (compressed) data size */
    public long getPackedSize() {
        return packedSize;
    }

    /** @param packedSize the packed data size */
    public void setPackedSize(final long packedSize) {
        this.packedSize = packedSize;
    }

    /** @return the compressed data bytes */
    public byte[] getCompressedData() {
        return compressedData;
    }

    /** @param compressedData the compressed data bytes */
    public void setCompressedData(final byte[] compressedData) {
        this.compressedData = compressedData;
    }

    // --- FileHeaderEntry interface methods (inherited from FileHeader, overridden as needed) ---

    @Override
    public long getFullUnpackSize() {
        return unpackedSize;
    }

    @Override
    public long getFullPackSize() {
        return 0;
    }

    @Override
    public int getFileCRC() {
        return dataCrc32 != null ? dataCrc32.intValue() : 0;
    }

    @Override
    public byte getUnpVersion() {
        return (byte) algorithmVersion;
    }

    @Override
    public byte getUnpMethod() {
        return (byte) compressionMethod;
    }

    @Override
    public byte getHostOSByte() {
        return (byte) hostOS;
    }

    @Override
    public Date getMTime() {
        return mtime != null ? new Date(mtime * 1000) : null;
    }

    @Override
    public Date getCTime() {
        final Rar5FileTimeRecord timeRecord = getTimeRecord();
        if (timeRecord != null && timeRecord.getCtime() != null) {
            return new Date(timeRecord.getCtime() * 1000);
        }
        return null;
    }

    @Override
    public Date getATime() {
        final Rar5FileTimeRecord timeRecord = getTimeRecord();
        if (timeRecord != null && timeRecord.getAtime() != null) {
            return new Date(timeRecord.getAtime() * 1000);
        }
        return null;
    }

    @Override
    public FileTime getArchivalTime() {
        return null;
    }

    @Override
    public long getPositionInFile() {
        return 0;
    }

    @Override
    public short getHeaderSize() {
        return 0;
    }

    @Override
    public byte[] getSalt() {
        final Rar5FileCryptRecord crypt = getCryptRecord();
        return crypt != null ? crypt.getSalt() : new byte[0];
    }

    @Override
    public boolean isSplitAfter() {
        return false;
    }

    @Override
    public boolean isSplitBefore() {
        return false;
    }

    /**
     * Parses a file header from the given byte array.
     *
     * @param data        the full block header bytes
     * @param blockHeader the parsed common block header
     * @param fieldOffset the offset where file-specific fields begin (after CRC32, header size, type, flags, extra/data sizes)
     * @return the parsed file header
     * @throws IllegalArgumentException if parameters are invalid
     * @throws Rar5HeaderException      if the header is malformed
     */
    public static Rar5FileHeader parse(final byte[] data, final Rar5BlockHeader blockHeader,
                                       final int fieldOffset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (blockHeader == null) {
            throw new IllegalArgumentException("blockHeader must not be null");
        }

        int pos = fieldOffset;

        // Read file flags (vint)
        final VInt.Result flagsResult = VInt.read(data, pos);
        final long fileFlags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        // Read unpacked size (vint)
        final VInt.Result unpSizeResult = VInt.read(data, pos);
        final long unpackedSize = unpSizeResult.getValue();
        pos += unpSizeResult.getBytesConsumed();

        final boolean unknownUnpSize = (fileFlags & FHFL_UNPUNKNOWN) != 0;

        // Read attributes (vint)
        final VInt.Result attrResult = VInt.read(data, pos);
        final long attributes = attrResult.getValue();
        pos += attrResult.getBytesConsumed();

        // Read optional mtime (uint32, Unix time)
        Long mtime = null;
        if ((fileFlags & FHFL_UTIME) != 0) {
            mtime = readUint32(data, pos);
            pos += 4;
        }

        // Read optional CRC32
        Long dataCrc32 = null;
        if ((fileFlags & FHFL_CRC32) != 0) {
            dataCrc32 = readUint32(data, pos);
            pos += 4;
        }

        // Read compression info (vint)
        final VInt.Result compResult = VInt.read(data, pos);
        final long compressionInfo = compResult.getValue();
        pos += compResult.getBytesConsumed();

        final int algorithmVersion = (int) (compressionInfo & FCI_ALGO_MASK);
        final int compressionMethod = (int) ((compressionInfo & FCI_METHOD_MASK) >>> FCI_METHOD_SHIFT);
        final boolean isSolid = (compressionInfo & FCI_SOLID) != 0;

        // Dictionary size: 128KB * 2^N
        final int dictBits = (int) ((compressionInfo & FCI_DICT_MASK) >>> FCI_DICT_SHIFT);
        long dictionarySize = 0;
        if (algorithmVersion == 0) {
            // RAR5: dictBits 0-15, max 128KB * 2^15 = 4GB
            if (dictBits <= 15) {
                dictionarySize = 0x20000L << dictBits;
            }
        } else if (algorithmVersion == 1) {
            // RAR7: dictBits 0-31, with fraction
            final int dictFrac = (int) ((compressionInfo & 0xF8000) >>> 15);
            if (dictBits <= 31) {
                dictionarySize = 0x20000L << dictBits;
                dictionarySize += dictionarySize / 32 * dictFrac;
            }
            // RAR7 with RAR5 compat flag
            if ((compressionInfo & FCI_RAR5_COMPAT) != 0) {
                // Treat as RAR5 for dictionary size purposes
            }
        }

        // Read host OS (vint)
        final VInt.Result hostResult = VInt.read(data, pos);
        final int hostOS = (int) hostResult.getValue();
        pos += hostResult.getBytesConsumed();

        // Read name length (vint)
        final VInt.Result nameLenResult = VInt.read(data, pos);
        final long nameLength = nameLenResult.getValue();
        pos += nameLenResult.getBytesConsumed();

        // Read file name (UTF-8)
        String fileName = "";
        if (nameLength > 0 && nameLength <= Integer.MAX_VALUE) {
            fileName = new String(data, pos, (int) nameLength, StandardCharsets.UTF_8);
            pos += (int) nameLength;
        }

        // Parse extra area records
        final List<Rar5ExtraRecord> extraRecords;
        if (blockHeader.hasExtra() && blockHeader.getExtraSize() > 0) {
            // Extra area is at the end of the header data
            // fieldOffset + (headerSize - extraSize) = extra area start
            // But we need to compute where the extra area starts relative to fieldOffset
            // The headerSize is the size from type through extra area
            // We've consumed (pos - fieldOffset) bytes of fields
            // The extra area is the last extraSize bytes of the header
            final int extraStart = pos;
            extraRecords = Rar5ExtraParser.parse(data, extraStart, (int) blockHeader.getExtraSize());
        } else {
            extraRecords = Collections.emptyList();
        }

        final boolean isDirectory = (fileFlags & FHFL_DIRECTORY) != 0;
        final boolean hasUnixTime = (fileFlags & FHFL_UTIME) != 0;
        final boolean hasCrc32 = (fileFlags & FHFL_CRC32) != 0;

        return new Rar5FileHeader(fileFlags, unpackedSize, attributes, mtime,
                                  dataCrc32, compressionInfo, hostOS, fileName,
                                  isDirectory, hasUnixTime, hasCrc32, unknownUnpSize,
                                  algorithmVersion, compressionMethod, dictionarySize,
                                  isSolid, extraRecords);
    }

    private static long readUint32(final byte[] data, final int offset) {
        return ((data[offset + 3] & 0xFFL) << 24)
             | ((data[offset + 2] & 0xFFL) << 16)
             | ((data[offset + 1] & 0xFFL) << 8)
             | (data[offset] & 0xFFL);
    }
}
