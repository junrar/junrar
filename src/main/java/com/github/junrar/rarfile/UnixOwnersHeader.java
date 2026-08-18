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

    public UnixOwnersHeader(SubBlockHeader sb, byte[] uoHeader) {
        super(sb);
        if (uoHeader.length < 4) {
            // The buffer is sized from the header's own declared size, so it can be shorter than
            // the two name-size fields. Leave both names unset and mark the header broken
            // (issue #12's treatment of a bad header CRC) instead of reading past it.
            setBrokenHeader(true);
            return;
        }
        int pos = 0;
        ownerNameSize = Raw.readShortLittleEndian(uoHeader, pos) & 0xFFFF;
        pos += 2;
        groupNameSize = Raw.readShortLittleEndian(uoHeader, pos) & 0xFFFF;
        pos += 2;
        // Each name is the last field before the next one, so a name whose bytes end exactly at
        // the end of the buffer is complete. A name that does not fit is simply left unset, and
        // is not a defect: "Old Unix owners header didn't include string fields into header size,
        // but included them into CRC" (d861246:arcread.cpp:517-519), so a genuine old sub-block
        // declares names that its own header size -- and therefore this buffer -- does not span.
        // A declared size of zero is a length the sub-block stated, so the name is present and
        // empty -- unlike one whose bytes the buffer does not span, which is absent and stays
        // null. Keeping the empty string is also what this returned before the bound was fixed,
        // so getOwner().isEmpty() on such a name does not become an NPE.
        if (pos + ownerNameSize <= uoHeader.length) {
            owner = new String(uoHeader, pos, ownerNameSize);
        }
        pos += ownerNameSize;
        if (pos + groupNameSize <= uoHeader.length) {
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
