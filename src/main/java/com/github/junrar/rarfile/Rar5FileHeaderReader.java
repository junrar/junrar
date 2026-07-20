package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;
import com.github.junrar.io.VInt;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BlockType;
import com.github.junrar.rarfile.rar5.Rar5FileExtraType;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.rarfile.rar5.Rar5HostOS;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import com.github.junrar.rarfile.rar5.Rar5Time;
import com.github.junrar.rarfile.rar5.Rar5UnixOwner;

import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

/**
 * RAR5 FILE/SERVICE header reader (M3.3, issue #24; unrar {@code ReadHeader50}'s
 * {@code HEAD_FILE}/{@code HEAD_SERVICE} case, {@code 8f437ab:arcread.cpp:794-882}, plus the
 * FHEXTRA records of {@code ProcessExtra50}, {@code :948-1225}). Populates the SAME unified
 * {@link FileHeader} the RAR3 loop uses (B-S9: no parallel RAR5 file header type), via {@link
 * Parsed}, a mutable field bag fed to {@link FileHeader}'s package-private RAR5 constructor.
 * <p>
 * Public (not the package-private class the manual sketch names) because {@code Archive}, which
 * dispatches to it, lives in a different package ({@code com.github.junrar}) than {@code
 * com.github.junrar.rarfile}; {@link Parsed} itself stays package-private, visible only to this
 * reader and {@link FileHeader}.
 */
public final class Rar5FileHeaderReader {

    // FHFL_* (RAR 5.0 file header specific flags).
    private static final long FHFL_DIRECTORY = 0x0001;
    private static final long FHFL_UTIME = 0x0002;
    private static final long FHFL_CRC32 = 0x0004;
    private static final long FHFL_UNPUNKNOWN = 0x0008;

    private static final long FCI_SOLID = 0x40;

    /** Mirrors the RAR3 path's 4K clamp (RAR3 {@code FileHeader}'s {@code nameSize} guard). */
    private static final int MAX_NAME_BYTES = 4 * 1024;

    // FHEXTRA_CRYPT payload.
    private static final long CRYPT_VERSION = 0;
    private static final long FHEXTRA_CRYPT_PSWCHECK = 0x01;
    private static final int SIZE_SALT50 = 16;
    private static final int SIZE_INITV = 16;
    private static final int SIZE_PSWCHECK = 8;
    private static final int SIZE_PSWCHECK_CSUM = 4;

    // FHEXTRA_HASH.
    private static final long FHEXTRA_HASH_BLAKE2 = 0;
    private static final int BLAKE2_DIGEST_SIZE = 32;

    // FHEXTRA_HTIME.
    private static final long FHEXTRA_HTIME_UNIXTIME = 0x01;
    private static final long FHEXTRA_HTIME_MTIME = 0x02;
    private static final long FHEXTRA_HTIME_CTIME = 0x04;
    private static final long FHEXTRA_HTIME_ATIME = 0x08;
    private static final long FHEXTRA_HTIME_UNIX_NS = 0x10;

    // FHEXTRA_REDIR.
    private static final long FHEXTRA_REDIR_DIR = 0x01;

    // FHEXTRA_UOWNER.
    private static final long FHEXTRA_UOWNER_UNAME = 0x01;
    private static final long FHEXTRA_UOWNER_GNAME = 0x02;
    private static final long FHEXTRA_UOWNER_NUMUID = 0x04;
    private static final long FHEXTRA_UOWNER_NUMGID = 0x08;

    private Rar5FileHeaderReader() {
    }

    /**
     * @param base   the already-parsed generic block ({@link Rar5BaseBlock#parse}), of type
     *               {@link Rar5BlockType#FILE} or {@link Rar5BlockType#SERVICE}.
     * @param header the full header buffer, length == {@code base.getRar5HeaderSize()}.
     * @return the populated unified {@link FileHeader}.
     * @throws CorruptHeaderException on a non-positive or oversized name, a name running past
     *                                the header buffer, or a name failing {@code
     *                                FileHeader#isFilenameValid} (C10).
     */
    public static FileHeader read(final Rar5BaseBlock base, final byte[] header) throws CorruptHeaderException {
        final Parsed p = new Parsed();
        p.service = base.getRar5Type() == Rar5BlockType.SERVICE;
        p.packSize = base.getDataSize();
        p.splitBefore = base.isSplitBefore();
        p.splitAfter = base.isSplitAfter();

        int pos = base.getFieldsOffset();

        VInt v = new VInt(header, pos);
        final long fileFlags = v.read();
        pos = v.position();
        p.directory = (fileFlags & FHFL_DIRECTORY) != 0;

        v = new VInt(header, pos);
        final long rawUnpSize = v.read();
        pos = v.position();
        p.unknownUnpSize = (fileFlags & FHFL_UNPUNKNOWN) != 0;
        p.unpSize = p.unknownUnpSize ? -1L : rawUnpSize;

        v = new VInt(header, pos);
        p.fileAttr = v.read();
        pos = v.position();

        if ((fileFlags & FHFL_UTIME) != 0) {
            requireBytes(header, pos, 4);
            p.mTime = Rar5Time.fromUnixSeconds(Raw.readIntLittleEndianAsLong(header, pos));
            pos += 4;
        }

        p.hasCrc32 = (fileFlags & FHFL_CRC32) != 0;
        if (p.hasCrc32) {
            requireBytes(header, pos, 4);
            p.fileCRC = Raw.readIntLittleEndian(header, pos);
            pos += 4;
        }

        v = new VInt(header, pos);
        final long compInfo = v.read();
        pos = v.position();
        p.unpMethod = (int) ((compInfo >>> 7) & 7);
        // ponytail: -1 (0xFF) as the VER_UNKNOWN sentinel could in principle collide with a raw
        // RAR3 unpVersion byte, but RAR3 never emits 0xFF -- a named constant if that changes.
        p.unpVersion = (byte) (((compInfo & 0x3f) + 50) == 50 ? 50 : -1);
        p.solid = !p.service && (compInfo & FCI_SOLID) != 0;

        v = new VInt(header, pos);
        final long hostOsRaw = v.read();
        pos = v.position();
        p.hostOsRaw = hostOsRaw;
        p.hostOsEnum = Rar5HostOS.findHostOS(hostOsRaw);

        v = new VInt(header, pos);
        final long nameSize = v.read();
        pos = v.position();
        if (nameSize <= 0) {
            throw new CorruptHeaderException("RAR5 file header has non-positive name size");
        }
        if (nameSize > MAX_NAME_BYTES || pos + nameSize > header.length) {
            throw new CorruptHeaderException("RAR5 file name size out of bounds");
        }
        final int nameLen = (int) nameSize;
        p.fileNameBytes = Arrays.copyOfRange(header, pos, pos + nameLen);
        p.fileName = new String(p.fileNameBytes, StandardCharsets.UTF_8);

        if (base.getExtraSize() != 0) {
            parseExtras(base, header, p);
        }

        if (p.fileVersion != 0) {
            // unrar: "+ ';' + Version" appended to the name once FHEXTRA_VERSION is known --
            // arcread.cpp:1130-1140.
            p.fileName = p.fileName + ";" + p.fileVersion;
        }

        return new FileHeader(p);
    }

    private static void requireBytes(final byte[] header, final int pos, final int len) throws CorruptHeaderException {
        if (pos + len > header.length) {
            throw new CorruptHeaderException("Truncated RAR5 file header");
        }
    }

    // ---- extra-area record loop (manual &sect;5) ----------------------------

    private static void parseExtras(final Rar5BaseBlock base, final byte[] header, final Parsed p) throws CorruptHeaderException {
        final int headerSize = base.getRar5HeaderSize();
        int pos = headerSize - (int) base.getExtraSize();
        final boolean service = p.service;
        while (headerSize - pos >= 2) {
            final VInt sizeReader = new VInt(header, pos);
            final long fieldSize = sizeReader.read();
            final int afterSize = sizeReader.position();
            if (fieldSize <= 0 || fieldSize > headerSize - afterSize) {
                break;
            }
            final int nextPos = (int) (afterSize + fieldSize);
            final VInt typeReader = new VInt(header, afterSize);
            final long fieldType = typeReader.read();
            final int payloadStart = typeReader.position();
            if (payloadStart > nextPos) {
                // unrar ProcessExtra50: FieldType ran past the record's own declared
                // FieldSize ("FieldSize=int64(NextPos-GetPos()); if (FieldSize<0) break;") --
                // abort the whole extra-area loop, don't let the handler read into the next
                // record's bytes.
                break;
            }

            final Rar5FileExtraType type = Rar5FileExtraType.findType(fieldType);
            if (type != null) {
                dispatchExtra(type, header, payloadStart, nextPos, p, service);
            }
            pos = nextPos;
        }
    }

    private static void dispatchExtra(final Rar5FileExtraType type, final byte[] header, final int start, final int end,
                                       final Parsed p, final boolean service) throws CorruptHeaderException {
        switch (type) {
            case CRYPT:
                parseCrypt(header, start, end, p);
                break;
            case HASH:
                parseHash(header, start, end, p);
                break;
            case HTIME:
                parseHTime(header, start, end, p);
                break;
            case VERSION:
                parseVersion(header, start, end, p);
                break;
            case REDIR:
                parseRedir(header, start, end, p);
                break;
            case UOWNER:
                parseUOwner(header, start, end, p);
                break;
            case SUBDATA:
                parseSubData(header, start, end, p, service);
                break;
            default:
                break;
        }
    }

    private static void parseCrypt(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        final VInt v = new VInt(header, start);
        final long encVersion = v.read();
        if (encVersion != CRYPT_VERSION) {
            // unrar: warns and continues without populating crypt fields -- not a reject here.
            return;
        }
        final long flags = v.read();
        int pos = v.position();
        if (pos >= end) {
            return;
        }
        final int lg2Count = header[pos++] & 0xff;
        if (pos + SIZE_SALT50 + SIZE_INITV > end) {
            return;
        }
        final byte[] salt16 = Arrays.copyOfRange(header, pos, pos + SIZE_SALT50);
        pos += SIZE_SALT50;
        final byte[] initV = Arrays.copyOfRange(header, pos, pos + SIZE_INITV);
        pos += SIZE_INITV;
        final boolean usePswCheck = (flags & FHEXTRA_CRYPT_PSWCHECK) != 0;
        // csum (SHA-256 prefix of pswCheck) verification deferred to M3.4; not read further here.

        p.encrypted = true;
        p.salt16 = salt16;
        p.initV = initV;
        p.lg2Count = lg2Count;
        p.usePswCheck = usePswCheck && pos + SIZE_PSWCHECK + SIZE_PSWCHECK_CSUM <= end;
    }

    private static void parseHash(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        final VInt v = new VInt(header, start);
        final long type = v.read();
        final int pos = v.position();
        if (type == FHEXTRA_HASH_BLAKE2 && pos + BLAKE2_DIGEST_SIZE <= end) {
            p.hashDigest = Arrays.copyOfRange(header, pos, pos + BLAKE2_DIGEST_SIZE);
            p.hashType = Rar5HashType.BLAKE2;
        }
    }

    private static void parseHTime(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        if (end - start < 5) {
            return;
        }
        final VInt v = new VInt(header, start);
        final long flagsRaw = v.read();
        final int flags = (int) (flagsRaw & 0xff);
        int pos = v.position();
        final boolean unixTime = (flags & FHEXTRA_HTIME_UNIXTIME) != 0;
        final boolean mtimePresent = (flags & FHEXTRA_HTIME_MTIME) != 0;
        final boolean ctimePresent = (flags & FHEXTRA_HTIME_CTIME) != 0;
        final boolean atimePresent = (flags & FHEXTRA_HTIME_ATIME) != 0;
        final int width = unixTime ? 4 : 8;

        if (mtimePresent && pos + width <= end) {
            p.mTime = readTime(header, pos, unixTime);
            pos += width;
        }
        if (ctimePresent && pos + width <= end) {
            p.cTime = readTime(header, pos, unixTime);
            pos += width;
        }
        if (atimePresent && pos + width <= end) {
            p.aTime = readTime(header, pos, unixTime);
            pos += width;
        }

        if (unixTime && (flags & FHEXTRA_HTIME_UNIX_NS) != 0) {
            if (mtimePresent && pos + 4 <= end) {
                p.mTime = adjustNanos(p.mTime, Raw.readIntLittleEndianAsLong(header, pos));
                pos += 4;
            }
            if (ctimePresent && pos + 4 <= end) {
                p.cTime = adjustNanos(p.cTime, Raw.readIntLittleEndianAsLong(header, pos));
                pos += 4;
            }
            if (atimePresent && pos + 4 <= end) {
                p.aTime = adjustNanos(p.aTime, Raw.readIntLittleEndianAsLong(header, pos));
                pos += 4;
            }
        }
    }

    private static FileTime readTime(final byte[] header, final int pos, final boolean unixTime) {
        return unixTime
            ? Rar5Time.fromUnixSeconds(Raw.readIntLittleEndianAsLong(header, pos))
            : Rar5Time.fromWindowsFileTime(Raw.readLongLittleEndian(header, pos));
    }

    private static FileTime adjustNanos(final FileTime base, final long raw) {
        if (base == null) {
            return null;
        }
        final long ns = raw & 0x3fffffffL;
        if (ns >= 1_000_000_000L) {
            return base;
        }
        return FileTime.from(base.toInstant().plusNanos(ns));
    }

    private static void parseVersion(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        if (end - start < 1) {
            return;
        }
        final VInt v = new VInt(header, start);
        v.read(); // flags field, unused.
        if (v.position() >= end) {
            return;
        }
        final long version = v.read();
        if (version != 0) {
            p.fileVersion = version;
        }
    }

    private static void parseRedir(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        final VInt v = new VInt(header, start);
        final long redirTypeRaw = v.read();
        if (v.position() >= end) {
            return;
        }
        final long flags = v.read();
        // unrar arcread.cpp:1143-1160 sets RedirType/DirTarget before reading the name, and
        // leaves the target empty when NameSize doesn't fit -- mirror that: the type/dir facts
        // surface even when the name below is rejected or too large.
        p.redirType = Rar5RedirType.findType(redirTypeRaw);
        p.redirIsDirectory = (flags & FHEXTRA_REDIR_DIR) != 0;
        if (v.position() >= end) {
            return;
        }
        final long nameSize = v.read();
        final int pos = v.position();
        // nameSize is an attacker-controlled vint up to ~2^63 -- "pos + nameSize" would
        // overflow int and pass a naive bound check. "end - pos" is a small, already-bounded
        // non-negative int (or negative if the nameSize vint itself ran past end, in which case
        // nameSize>=0 alone already trips this guard), so the subtraction cannot overflow.
        if (nameSize < 0 || nameSize > end - pos) {
            return;
        }
        final int len = (int) nameSize;
        final byte[] nameBytes = Arrays.copyOfRange(header, pos, pos + len);
        // Parse-and-flag only (manual &sect;4a): not sanitized, extraction guards this (M3.10).
        p.redirTarget = new String(nameBytes, StandardCharsets.UTF_8);
    }

    private static void parseUOwner(final byte[] header, final int start, final int end, final Parsed p) throws CorruptHeaderException {
        VInt v = new VInt(header, start);
        final long flags = v.read();
        int pos = v.position();
        final boolean numericUid = (flags & FHEXTRA_UOWNER_NUMUID) != 0;
        final boolean numericGid = (flags & FHEXTRA_UOWNER_NUMGID) != 0;
        String userName = null;
        String groupName = null;
        long ownerId = 0;
        long groupId = 0;

        if ((flags & FHEXTRA_UOWNER_UNAME) != 0 && pos < end) {
            v = new VInt(header, pos);
            final long len = v.read();
            pos = v.position();
            final int l = (int) Math.max(0, Math.min(len, end - pos));
            if (l > 0) {
                userName = new String(header, pos, l, StandardCharsets.UTF_8);
            }
            pos += l;
        }
        if ((flags & FHEXTRA_UOWNER_GNAME) != 0 && pos < end) {
            v = new VInt(header, pos);
            final long len = v.read();
            pos = v.position();
            final int l = (int) Math.max(0, Math.min(len, end - pos));
            if (l > 0) {
                groupName = new String(header, pos, l, StandardCharsets.UTF_8);
            }
            pos += l;
        }
        if (numericUid && pos < end) {
            v = new VInt(header, pos);
            ownerId = v.read();
            pos = v.position();
        }
        if (numericGid && pos < end) {
            v = new VInt(header, pos);
            groupId = v.read();
            pos = v.position();
        }
        p.unixOwner = new Rar5UnixOwner(userName, groupName, ownerId, groupId, numericUid, numericGid);
    }

    private static void parseSubData(final byte[] header, final int start, final int end, final Parsed p, final boolean service) {
        int actualEnd = end;
        if (service && header.length - end == 1) {
            // RAR 5.21 and earlier set FHEXTRA_SUBDATA's declared size 1 byte short for
            // HEAD_SERVICE; unrar detects the resulting 1 stray byte at the end of the extra
            // area and folds it into SubData too (ProcessExtra50, arcread.cpp:1207-1213).
            actualEnd = end + 1;
        }
        actualEnd = Math.min(actualEnd, header.length);
        if (actualEnd > start) {
            p.subData = Arrays.copyOfRange(header, start, actualEnd);
        }
    }

    /**
     * Mutable field bag fed to {@link FileHeader}'s package-private RAR5 constructor -- kept
     * package-private because it is a wire-format transcription detail, not part of the
     * {@code FileHeader} public contract.
     */
    static final class Parsed {
        boolean service;
        long packSize;

        boolean directory;
        boolean splitBefore;
        boolean splitAfter;
        boolean solid;

        long unpSize;
        boolean unknownUnpSize;

        long fileAttr;

        boolean hasCrc32;
        int fileCRC;

        int unpMethod;
        byte unpVersion;

        long hostOsRaw;
        Rar5HostOS hostOsEnum;

        byte[] fileNameBytes;
        String fileName;

        FileTime mTime;
        FileTime cTime;
        FileTime aTime;

        byte[] hashDigest;
        Rar5HashType hashType;

        Rar5RedirType redirType;
        boolean redirIsDirectory;
        String redirTarget;

        long fileVersion;

        Rar5UnixOwner unixOwner;

        boolean encrypted;
        byte[] salt16;
        byte[] initV;
        int lg2Count;
        boolean usePswCheck;

        byte[] subData;
    }
}
