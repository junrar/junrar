package com.github.junrar.rar5.header.extra;

/**
 * File system redirection extra record (FHEXTRA_REDIR = 0x05).
 *
 * <p>Used for symlinks, hardlinks, and junction points.
 *
 * <p>Fields:
 * <ul>
 *   <li>Redirection type (vint): 1=Unix symlink, 2=Windows shortcut, 3=Hard link, 4=Directory junction</li>
 *   <li>Flags (vint): DIR (0x01) = link target is directory</li>
 *   <li>Name size (vint)</li>
 *   <li>Name (UTF-8 string, no trailing zero)</li>
 * </ul>
 */
public final class Rar5RedirRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x05;

    /** Link target is directory flag. */
    public static final int FHEXTRA_REDIR_DIR = 0x01;

    /** Unix symbolic link. */
    public static final long REDIR_UNIX_SYMLINK = 1;

    /** Windows symbolic link. */
    public static final long REDIR_WINDOWS_SYMLINK = 2;

    /** Hard link. */
    public static final long REDIR_HARD_LINK = 3;

    /** Directory junction. */
    public static final long REDIR_JUNCTION = 4;

    private final long redirType;
    private final long flags;
    private final String targetName;
    private final boolean dirTarget;

    Rar5RedirRecord(final long redirType, final long flags,
                            final String targetName, final boolean dirTarget) {
        this.redirType = redirType;
        this.flags = flags;
        this.targetName = targetName;
        this.dirTarget = dirTarget;
    }

    @Override
    public long getRecordType() {
        return TYPE;
    }

    /**
     * @return redirection type (symlink, hard link, junction)
     */
    public long getRedirType() {
        return redirType;
    }

    /**
     * @return redirection flags
     */
    public long getFlags() {
        return flags;
    }

    /**
     * @return the target path (UTF-8)
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * @return true if the link target is a directory
     */
    public boolean isDirTarget() {
        return dirTarget;
    }

    /**
     * @return true if this is a symbolic link
     */
    public boolean isSymlink() {
        return redirType == REDIR_UNIX_SYMLINK || redirType == REDIR_WINDOWS_SYMLINK;
    }

    /**
     * @return true if this is a hard link
     */
    public boolean isHardLink() {
        return redirType == REDIR_HARD_LINK;
    }
}
