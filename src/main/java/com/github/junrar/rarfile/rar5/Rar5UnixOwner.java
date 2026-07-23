package com.github.junrar.rarfile.rar5;

/**
 * RAR5 Unix owner/group fact (FHEXTRA_UOWNER, unrar {@code FileHeader::UnixOwnerName}/
 * {@code UnixGroupName}/{@code UnixOwnerID}/{@code UnixGroupID}, {@code 8f437ab:arcread.cpp
 * :1195-1225}).
 */
public final class Rar5UnixOwner {

    private final String userName;
    private final String groupName;
    private final long ownerId;
    private final long groupId;
    private final boolean numericOwnerId;
    private final boolean numericGroupId;

    public Rar5UnixOwner(
            final String userName,
            final String groupName,
            final long ownerId,
            final long groupId,
            final boolean numericOwnerId,
            final boolean numericGroupId) {
        this.userName = userName;
        this.groupName = groupName;
        this.ownerId = ownerId;
        this.groupId = groupId;
        this.numericOwnerId = numericOwnerId;
        this.numericGroupId = numericGroupId;
    }

    public String getUserName() {
        return this.userName;
    }

    public String getGroupName() {
        return this.groupName;
    }

    public long getOwnerId() {
        return this.ownerId;
    }

    public long getGroupId() {
        return this.groupId;
    }

    public boolean isNumericOwnerId() {
        return this.numericOwnerId;
    }

    public boolean isNumericGroupId() {
        return this.numericGroupId;
    }
}
