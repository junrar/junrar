package com.github.junrar.rar5.header.extra;

/**
 * High precision file time extra record (FHEXTRA_HTIME = 0x03).
 *
 * <p>Fields:
 * <ul>
 *   <li>Flags (vint): UNIXTIME, MTIME, CTIME, ATIME, UNIX_NS</li>
 *   <li>mtime (uint32 or uint64, optional)</li>
 *   <li>ctime (uint32 or uint64, optional)</li>
 *   <li>atime (uint32 or uint64, optional)</li>
 *   <li>Nanosecond adjustments (uint32 each, optional, only if UNIX_NS)</li>
 * </ul>
 */
public final class Rar5FileTimeRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x03;

    /** Use Unix time_t format flag. */
    public static final int FHEXTRA_HTIME_UNIXTIME = 0x01;

    /** mtime is present flag. */
    public static final int FHEXTRA_HTIME_MTIME = 0x02;

    /** ctime is present flag. */
    public static final int FHEXTRA_HTIME_CTIME = 0x04;

    /** atime is present flag. */
    public static final int FHEXTRA_HTIME_ATIME = 0x08;

    /** Unix format with nanosecond precision flag. */
    public static final int FHEXTRA_HTIME_UNIX_NS = 0x10;

    private final long flags;
    private final boolean unixTime;
    private final boolean unixNs;
    private final Long mtime;
    private final Long ctime;
    private final Long atime;
    private final Long mtimeNs;
    private final Long ctimeNs;
    private final Long atimeNs;

    Rar5FileTimeRecord(final long flags, final boolean unixTime,
                               final boolean unixNs, final Long mtime,
                               final Long ctime, final Long atime,
                               final Long mtimeNs, final Long ctimeNs,
                               final Long atimeNs) {
        this.flags = flags;
        this.unixTime = unixTime;
        this.unixNs = unixNs;
        this.mtime = mtime;
        this.ctime = ctime;
        this.atime = atime;
        this.mtimeNs = mtimeNs;
        this.ctimeNs = ctimeNs;
        this.atimeNs = atimeNs;
    }

    @Override
    public long getRecordType() {
        return TYPE;
    }

    /**
     * @return time record flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return true if Unix time format is used
     */
    public boolean isUnixTime() {
        return unixTime;
    }

    /**
     * @return true if nanosecond precision is present
     */
    public boolean hasNanoseconds() {
        return unixNs;
    }

    /**
     * @return mtime as Unix timestamp (seconds) or Windows FILETIME, or null if not present
     */
    public Long getMtime() {
        return mtime;
    }

    /**
     * @return ctime as Unix timestamp (seconds) or Windows FILETIME, or null if not present
     */
    public Long getCtime() {
        return ctime;
    }

    /**
     * @return atime as Unix timestamp (seconds) or Windows FILETIME, or null if not present
     */
    public Long getAtime() {
        return atime;
    }

    /**
     * @return mtime nanosecond adjustment, or null if not present
     */
    public Long getMtimeNs() {
        return mtimeNs;
    }

    /**
     * @return ctime nanosecond adjustment, or null if not present
     */
    public Long getCtimeNs() {
        return ctimeNs;
    }

    /**
     * @return atime nanosecond adjustment, or null if not present
     */
    public Long getAtimeNs() {
        return atimeNs;
    }
}
