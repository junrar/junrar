package com.github.junrar.rar5.header.extra;

import com.github.junrar.rar5.io.VInt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses extra area records from a RAR5 block header.
 *
 * <p>Extra area records are located at the end of the block header. The extra
 * area size is given in the common block header flags (HFL_EXTRA). Records
 * are read sequentially from the start of the extra area.
 */
public final class Rar5ExtraParser {

    private Rar5ExtraParser() {
    }

    /**
     * Parses all extra area records from the given byte array.
     *
     * @param data       the full block header bytes
     * @param extraStart the offset where the extra area begins
     * @param extraSize  the size of the extra area in bytes
     * @return an unmodifiable list of parsed extra records
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static List<Rar5ExtraRecord> parse(final byte[] data, final int extraStart, final int extraSize) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (extraStart < 0) {
            throw new IllegalArgumentException("extraStart must not be negative");
        }
        if (extraSize < 0) {
            throw new IllegalArgumentException("extraSize must not be negative");
        }
        if (extraStart + extraSize > data.length) {
            throw new IllegalArgumentException("extra area extends beyond data");
        }

        final List<Rar5ExtraRecord> records = new ArrayList<>();
        final int end = extraStart + extraSize;
        int pos = extraStart;

        while (pos < end) {
            // Read field size (vint) — size starting from Type field
            final VInt.Result sizeResult = VInt.read(data, pos);
            final long fieldSize = sizeResult.getValue();
            pos += sizeResult.getBytesConsumed();

            if (fieldSize <= 0) {
                break;
            }

            final int fieldEnd = pos + (int) fieldSize;
            if (fieldEnd > end) {
                break;
            }

            // Read field type (vint)
            final VInt.Result typeResult = VInt.read(data, pos);
            final long fieldType = typeResult.getValue();
            pos += typeResult.getBytesConsumed();

            final int recordDataEnd = fieldEnd;
            final Rar5ExtraRecord record = parseRecord(data, pos, recordDataEnd, fieldType);
            if (record != null) {
                records.add(record);
            }

            pos = fieldEnd;
        }

        return Collections.unmodifiableList(records);
    }

    private static Rar5ExtraRecord parseRecord(final byte[] data, int pos,
                                                final int end, final long type) {
        switch ((int) type) {
            case (int) Rar5FileCryptRecord.TYPE:
                return parseCryptRecord(data, pos, end);
            case (int) Rar5FileHashRecord.TYPE:
                return parseHashRecord(data, pos, end);
            case (int) Rar5FileTimeRecord.TYPE:
                return parseTimeRecord(data, pos, end);
            case (int) Rar5FileVersionRecord.TYPE:
                return parseVersionRecord(data, pos, end);
            case (int) Rar5RedirRecord.TYPE:
                return parseRedirRecord(data, pos, end);
            case (int) Rar5UnixOwnerRecord.TYPE:
                return parseUnixOwnerRecord(data, pos, end);
            default:
                // Skip unknown record types
                return null;
        }
    }

    private static Rar5FileCryptRecord parseCryptRecord(final byte[] data, int pos, final int end) {
        final VInt.Result verResult = VInt.read(data, pos);
        final long version = verResult.getValue();
        pos += verResult.getBytesConsumed();

        final VInt.Result flagsResult = VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        final int kdfCount = data[pos] & 0xFF;
        pos++;

        final byte[] salt = new byte[Rar5FileCryptRecord.SALT_SIZE];
        System.arraycopy(data, pos, salt, 0, salt.length);
        pos += salt.length;

        final byte[] iv = new byte[Rar5FileCryptRecord.IV_SIZE];
        System.arraycopy(data, pos, iv, 0, iv.length);
        pos += iv.length;

        final boolean hasPswCheck = (flags & Rar5FileCryptRecord.FHEXTRA_CRYPT_PSWCHECK) != 0;
        final boolean hasHashMac = (flags & Rar5FileCryptRecord.FHEXTRA_CRYPT_HASHMAC) != 0;

        byte[] pswCheck = null;
        if (hasPswCheck) {
            pswCheck = new byte[Rar5FileCryptRecord.PSWCHECK_SIZE];
            System.arraycopy(data, pos, pswCheck, 0, pswCheck.length);
            pos += pswCheck.length;

            // Skip the 4-byte checksum (validated by C++ code via SHA-256)
            pos += Rar5FileCryptRecord.PSWCHECK_CSUM_SIZE;
        }

        return new Rar5FileCryptRecord(version, flags, kdfCount, salt, iv, pswCheck, hasPswCheck, hasHashMac);
    }

    private static Rar5FileHashRecord parseHashRecord(final byte[] data, int pos, final int end) {
        final VInt.Result typeResult = VInt.read(data, pos);
        final long hashType = typeResult.getValue();
        pos += typeResult.getBytesConsumed();

        final byte[] digest;
        if (hashType == Rar5FileHashRecord.HASH_BLAKE2) {
            digest = new byte[Rar5FileHashRecord.BLAKE2_DIGEST_SIZE];
            System.arraycopy(data, pos, digest, 0, digest.length);
        } else {
            digest = new byte[0];
        }

        return new Rar5FileHashRecord(hashType, digest);
    }

    private static Rar5FileTimeRecord parseTimeRecord(final byte[] data, int pos, final int end) {
        final VInt.Result flagsResult = VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        final boolean unixTime = (flags & Rar5FileTimeRecord.FHEXTRA_HTIME_UNIXTIME) != 0;
        final boolean unixNs = (flags & Rar5FileTimeRecord.FHEXTRA_HTIME_UNIX_NS) != 0;

        Long mtime = null, ctime = null, atime = null;
        Long mtimeNs = null, ctimeNs = null, atimeNs = null;

        if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_MTIME) != 0) {
            if (unixTime) {
                mtime = readUint32(data, pos);
                pos += 4;
            } else {
                mtime = readUint64(data, pos);
                pos += 8;
            }
        }
        if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_CTIME) != 0) {
            if (unixTime) {
                ctime = readUint32(data, pos);
                pos += 4;
            } else {
                ctime = readUint64(data, pos);
                pos += 8;
            }
        }
        if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_ATIME) != 0) {
            if (unixTime) {
                atime = readUint32(data, pos);
                pos += 4;
            } else {
                atime = readUint64(data, pos);
                pos += 8;
            }
        }
        if (unixTime && unixNs) {
            if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_MTIME) != 0) {
                mtimeNs = readUint32(data, pos) & 0x3FFFFFFFL;
                pos += 4;
            }
            if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_CTIME) != 0) {
                ctimeNs = readUint32(data, pos) & 0x3FFFFFFFL;
                pos += 4;
            }
            if ((flags & Rar5FileTimeRecord.FHEXTRA_HTIME_ATIME) != 0) {
                atimeNs = readUint32(data, pos) & 0x3FFFFFFFL;
                pos += 4;
            }
        }

        return new Rar5FileTimeRecord(flags, unixTime, unixNs, mtime, ctime, atime, mtimeNs, ctimeNs, atimeNs);
    }

    private static Rar5FileVersionRecord parseVersionRecord(final byte[] data, int pos, final int end) {
        VInt.read(data, pos); // Skip flags
        pos += 1; // Flags is typically a single byte vint

        final VInt.Result verResult = VInt.read(data, pos);
        final long version = verResult.getValue();

        return new Rar5FileVersionRecord(version);
    }

    private static Rar5RedirRecord parseRedirRecord(final byte[] data, int pos, final int end) {
        final VInt.Result redirResult = VInt.read(data, pos);
        final long redirType = redirResult.getValue();
        pos += redirResult.getBytesConsumed();

        final VInt.Result flagsResult = VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        final VInt.Result nameSizeResult = VInt.read(data, pos);
        final long nameSize = nameSizeResult.getValue();
        pos += nameSizeResult.getBytesConsumed();

        final String targetName;
        if (nameSize > 0 && nameSize <= (end - pos)) {
            targetName = new String(data, pos, (int) nameSize, StandardCharsets.UTF_8);
        } else {
            targetName = "";
        }

        final boolean dirTarget = (flags & Rar5RedirRecord.FHEXTRA_REDIR_DIR) != 0;

        return new Rar5RedirRecord(redirType, flags, targetName, dirTarget);
    }

    private static Rar5UnixOwnerRecord parseUnixOwnerRecord(final byte[] data, int pos, final int end) {
        final VInt.Result flagsResult = VInt.read(data, pos);
        final long flags = flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        final boolean hasNumericUid = (flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMUID) != 0;
        final boolean hasNumericGid = (flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMGID) != 0;
        final boolean hasUserName = (flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_UNAME) != 0;
        final boolean hasGroupName = (flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_GNAME) != 0;

        String userName = null;
        if (hasUserName && pos < end) {
            final VInt.Result nameLenResult = VInt.read(data, pos);
            final long nameLen = nameLenResult.getValue();
            pos += nameLenResult.getBytesConsumed();
            if (nameLen > 0 && nameLen <= (end - pos)) {
                userName = new String(data, pos, (int) nameLen, StandardCharsets.UTF_8);
                pos += (int) nameLen;
            }
        }

        String groupName = null;
        if (hasGroupName && pos < end) {
            final VInt.Result nameLenResult = VInt.read(data, pos);
            final long nameLen = nameLenResult.getValue();
            pos += nameLenResult.getBytesConsumed();
            if (nameLen > 0 && nameLen <= (end - pos)) {
                groupName = new String(data, pos, (int) nameLen, StandardCharsets.UTF_8);
                pos += (int) nameLen;
            }
        }

        long uid = 0;
        if (hasNumericUid && pos < end) {
            final VInt.Result uidResult = VInt.read(data, pos);
            uid = uidResult.getValue();
            pos += uidResult.getBytesConsumed();
        }

        long gid = 0;
        if (hasNumericGid && pos < end) {
            final VInt.Result gidResult = VInt.read(data, pos);
            gid = gidResult.getValue();
        }

        return new Rar5UnixOwnerRecord(flags, userName, groupName, uid, gid, hasNumericUid, hasNumericGid);
    }

    private static long readUint32(final byte[] data, final int offset) {
        return ((data[offset + 3] & 0xFFL) << 24)
             | ((data[offset + 2] & 0xFFL) << 16)
             | ((data[offset + 1] & 0xFFL) << 8)
             | (data[offset] & 0xFFL);
    }

    private static long readUint64(final byte[] data, final int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= (data[offset + i] & 0xFFL) << (i * 8);
        }
        return result;
    }
}
