package com.github.junrar.rar5.header;

import com.github.junrar.rar5.Rar5Constants;
import com.github.junrar.rar5.header.extra.Rar5ExtraRecord;
import com.github.junrar.rar5.header.extra.Rar5FileTimeRecord;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * RAR5 file header (HEAD_FILE) and service header (HEAD_SERVICE).
 *
 * <p>Both share the same structure. Fields are read after the common block header:
 * <ul>
 *   <li>File flags (vint): DIRECTORY, UTIME, CRC32, UNPUNKNOWN — see {@link Rar5Constants}</li>
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
public class RAR5FileHeader extends FileHeader {

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
    private final long dictionarySize;
    private final boolean isSolid;

    public RAR5FileHeader(final long fileFlags, final long unpackedSize,
                           final long attributes, final FileTime mtime,
                           final Long dataCrc32, final long compressionInfo,
                           final int hostOS, final String fileName,
                           final int unpackVersion, final int compressionMethod,
                           final long dictionarySize, final boolean isSolid,
                           final List<Rar5ExtraRecord> extraRecords) {
        super(unpackedSize, hostOS == HOST_UNIX ? HostSystem.unix : HostSystem.win32,
                dataCrc32 != null ? (int) dataCrc32.longValue() : 0, 0, fileName, (byte) compressionMethod,
                (byte) unpackVersion);
        this.fileFlags = fileFlags;
        this.dictionarySize = dictionarySize;
        this.isSolid = isSolid;
        setFileNameW(fileName);

        if (mtime != null) {
            setLastModifiedTime(mtime);
        }
        if (getLastModifiedTime() == null && extraRecords != null) {
            for (Rar5ExtraRecord record : extraRecords) {
                if (record instanceof Rar5FileTimeRecord) {
                    Rar5FileTimeRecord timeRecord = (Rar5FileTimeRecord) record;
                    Long extraMtime = timeRecord.getMtime();
                    Long mtimeNs = timeRecord.getMtimeNs();
                    if (extraMtime != null) {
                        long unixMillis;
                        if (timeRecord.isUnixTime()) {
                            // Unix timestamp (seconds since 1970-01-01 UTC)
                            unixMillis = extraMtime * 1000;
                        } else {
                            // Windows FILETIME: 100-ns intervals since 1601-01-01 UTC.
                            // FILETIME is defined as UTC in Windows API.
                            // Conversion: FILETIME (100-ns units) / 10000 = milliseconds
                            // Offset: 11644473600000 ms between 1601-01-01 and 1970-01-01
                            unixMillis = (extraMtime / 10000) - 11644473600000L;
                        }
                        if (timeRecord.hasNanoseconds() && mtimeNs != null) {
                            unixMillis += mtimeNs / 1_000_000;
                        }
                        // Store as UTC. Display in local timezone should be done at UI layer.
                        setLastModifiedTime(FileTime.from(java.time.Instant.ofEpochMilli(unixMillis)));
                    }
                    break;
                }
            }
        }
        if (attributes != 0) {
            setFileAttr((int) attributes);
        }

        // Map RAR5 state to the RAR4-era BaseBlock.flags field so that callers
        // using getFlags() for backwards-compatible checks continue to work.
        short mappedFlags = 0;
        if (isSolid) {
            mappedFlags |= LHD_SOLID;
        }
        if ((fileFlags & Rar5Constants.FHFL_DIRECTORY) != 0) {
            mappedFlags |= LHD_DIRECTORY;
        }
        setFlags(mappedFlags);
    }

    /**
     * RAR5 stores directory flag in fileFlags, unlike RAR4 which uses the flags field.
     * @return true if this is a directory
     */
    @Override
    public boolean isDirectory() {
        return (fileFlags & Rar5Constants.FHFL_DIRECTORY) != 0;
    }

    /**
     * Returns whether the file is stored in a solid archive.
     * Only applicable for RAR5 archives.
     * @return true if solid
     */
    public boolean isSolid() {
        return isSolid;
    }

    /**
     * Filenames are always stored as UTF-8 in RAR5.
     * @return true
     */
    @Override
    public boolean isUnicode() {
        return true;
    }

    /**
     * Returns the dictionary size used for compression.
     * Only applicable for RAR5 archives.
     * @return dictionary size in bytes, or 0 if not set
     */
    public long getDictionarySize() {
        return dictionarySize;
    }

    public Set<PosixFilePermission> getPermissions() {
        final int attrs = getFileAttr();
        if (getHostOS() == HostSystem.unix) {
            return unixPermissions(attrs);
        }
        return windowsPermissions(attrs);
    }

    private static Set<PosixFilePermission> unixPermissions(final int mode) {
        if (mode == 0) {
            return Collections.emptySet();
        }
        final Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        return Collections.unmodifiableSet(perms);
    }

    // Windows FILE_ATTRIBUTE_READONLY flag
    private static final int WIN_ATTR_READONLY = 0x0001;

    private static Set<PosixFilePermission> windowsPermissions(final int attrs) {
        if (attrs == 0) {
            return Collections.emptySet();
        }
        final Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.OTHERS_READ);
        if ((attrs & WIN_ATTR_READONLY) == 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        return Collections.unmodifiableSet(perms);
    }
}
