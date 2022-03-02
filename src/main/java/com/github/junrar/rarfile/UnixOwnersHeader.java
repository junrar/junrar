package com.github.junrar.rarfile;

import com.github.junrar.io.Raw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UnixOwnersHeader extends SubBlockHeader {
    private static final Logger logger = LoggerFactory.getLogger(UnixOwnersHeader.class);
    private int ownerNameSize;
    private int groupNameSize;
    private String owner;
    private String group;

    public  UnixOwnersHeader(SubBlockHeader sb, byte[] uoHeader) {
        super(sb);
        int pos = 0;
        ownerNameSize = Raw.readShortLittleEndian(uoHeader, pos) & 0xFFFF;
        pos += 2;
        groupNameSize = Raw.readShortLittleEndian(uoHeader, pos) & 0xFFFF;
        pos += 2;
        if (pos + ownerNameSize < uoHeader.length) {
            owner = new String(uoHeader, pos, ownerNameSize);
        }
        pos += ownerNameSize;
        if (pos + groupNameSize < uoHeader.length) {
            group = new String(uoHeader, pos, groupNameSize);
        }
    }
    /**
     * @return the group
     */
    public String getGroup() {
        return group;
    }
    /**
     * @param group the group to set
     */
    public void setGroup(String group) {
        this.group = group;
    }
    /**
     * @return the groupNameSize
     */
    public int getGroupNameSize() {
        return groupNameSize;
    }
    /**
     * @param groupNameSize the groupNameSize to set
     */
    public void setGroupNameSize(int groupNameSize) {
        this.groupNameSize = groupNameSize;
    }
    /**
     * @return the owner
     */
    public String getOwner() {
        return owner;
    }
    /**
     * @param owner the owner to set
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }
    /**
     * @return the ownerNameSize
     */
    public int getOwnerNameSize() {
        return ownerNameSize;
    }
    /**
     * @param ownerNameSize the ownerNameSize to set
     */
    public void setOwnerNameSize(int ownerNameSize) {
        this.ownerNameSize = ownerNameSize;
    }

    /* (non-Javadoc)
     * @see de.innosystec.unrar.rarfile.SubBlockHeader#print()
     */
    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            logger.info("ownerNameSize: {}", ownerNameSize);
            logger.info("owner: {}", owner);
            logger.info("groupNameSize: {}", groupNameSize);
            logger.info("group: {}", group);
        }
    }
}
