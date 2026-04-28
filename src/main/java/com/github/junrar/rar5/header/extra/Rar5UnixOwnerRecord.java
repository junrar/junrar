package com.github.junrar.rar5.header.extra;

/**
 * Unix owner extra record (FHEXTRA_UOWNER = 0x06).
 *
 * <p>Fields:
 * <ul>
 *   <li>Flags (vint): UNAME, GNAME, NUMUID, NUMGID</li>
 *   <li>User name (UTF-8 string, optional)</li>
 *   <li>Group name (UTF-8 string, optional)</li>
 *   <li>UID (vint, optional)</li>
 *   <li>GID (vint, optional)</li>
 * </ul>
 */
public final class Rar5UnixOwnerRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x06;

    /** User name string is present flag. */
    public static final int FHEXTRA_UOWNER_UNAME = 0x01;

    /** Group name string is present flag. */
    public static final int FHEXTRA_UOWNER_GNAME = 0x02;

    /** Numeric user ID is present flag. */
    public static final int FHEXTRA_UOWNER_NUMUID = 0x04;

    /** Numeric group ID is present flag. */
    public static final int FHEXTRA_UOWNER_NUMGID = 0x08;

    private final long flags;
    private final String userName;
    private final String groupName;
    private final long uid;
    private final long gid;
    private final boolean hasNumericUid;
    private final boolean hasNumericGid;

    Rar5UnixOwnerRecord(final long flags, final String userName,
                                final String groupName, final long uid,
                                final long gid, final boolean hasNumericUid,
                                final boolean hasNumericGid) {
        this.flags = flags;
        this.userName = userName;
        this.groupName = groupName;
        this.uid = uid;
        this.gid = gid;
        this.hasNumericUid = hasNumericUid;
        this.hasNumericGid = hasNumericGid;
    }

    @Override
    public long getRecordType() {
        return TYPE;
    }

    /**
     * @return unix owner flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return user name, or null if not present
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @return group name, or null if not present
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * @return numeric user ID, or -1 if not present
     */
    public long getUid() {
        return hasNumericUid ? uid : -1;
    }

    /**
     * @return numeric group ID, or -1 if not present
     */
    public long getGid() {
        return hasNumericGid ? gid : -1;
    }

    /**
     * @return true if numeric UID is present
     */
    public boolean hasNumericUid() {
        return hasNumericUid;
    }

    /**
     * @return true if numeric GID is present
     */
    public boolean hasNumericGid() {
        return hasNumericGid;
    }
}
