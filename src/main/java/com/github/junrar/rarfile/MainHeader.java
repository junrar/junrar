/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 22.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.rarfile;

import com.github.junrar.io.Raw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The main header of an rar archive. holds information concerning the whole archive (solid, encrypted etc).
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class MainHeader extends BaseBlock {
    private static final Logger logger = LoggerFactory.getLogger(MainHeader.class);
    public static final short mainHeaderSizeWithEnc = 7;
    public static final short mainHeaderSize = 6;
    private final short highPosAv;
    private final int posAv;
    private byte encryptVersion;

    public MainHeader(BaseBlock bb, byte[] mainHeader) {
        super(bb);
        int pos = 0;
        highPosAv = Raw.readShortLittleEndian(mainHeader, pos);
        pos += 2;
        posAv = Raw.readIntLittleEndian(mainHeader, pos);
        pos += 4;

        if (hasEncryptVersion()) {
            encryptVersion |= mainHeader[pos] & 0xff;
        }
    }

    /**
     * old cmt block is present
     * @return true if has cmt block
     */
    public boolean hasArchCmt() {
        return (this.flags & BaseBlock.MHD_COMMENT) != 0;
    }
    /**
     * the version the the encryption
     * @return .
     */
    public byte getEncryptVersion() {
        return encryptVersion;
    }

    public short getHighPosAv() {
        return highPosAv;
    }

    public int getPosAv() {
        return posAv;
    }

    /**
     * returns whether the archive is encrypted
     * @return .
     */
    public boolean isEncrypted() {
        return (this.flags & BaseBlock.MHD_PASSWORD) != 0;
    }

    /**
     *
     * @return whether the archive is a multivolume archive
     */
    public boolean isMultiVolume() {
        return (this.flags & BaseBlock.MHD_VOLUME) != 0;
    }

    /**
     *
     * @return if the archive is a multivolume archive this method returns whether this instance is the first part of the multivolume archive
     */
    public boolean isFirstVolume() {
        return (this.flags & BaseBlock.MHD_FIRSTVOLUME) != 0;
    }

    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            StringBuilder str = new StringBuilder();
            str.append("posav: ").append(getPosAv());
            str.append("\nhighposav: ").append(getHighPosAv());
            str.append("\nhasencversion: ").append(hasEncryptVersion()).append(hasEncryptVersion() ? getEncryptVersion() : "");
            str.append("\nhasarchcmt: ").append(hasArchCmt());
            str.append("\nisEncrypted: ").append(isEncrypted());
            str.append("\nisMultivolume: ").append(isMultiVolume());
            str.append("\nisFirstvolume: ").append(isFirstVolume());
            str.append("\nisSolid: ").append(isSolid());
            str.append("\nisLocked: ").append(isLocked());
            str.append("\nisProtected: ").append(isProtected());
            str.append("\nisAV: ").append(isAV());
            logger.info(str.toString());
        }
    }

    /**
     * @return whether this archive is solid. in this case you can only extract all file at once
     */
    public boolean isSolid() {
        return (this.flags & MHD_SOLID) != 0;
    }

    public boolean isLocked() {
        return (this.flags & MHD_LOCK) != 0;
    }

    public boolean isProtected() {
        return (this.flags & MHD_PROTECT) != 0;
    }

    public boolean isAV() {
        return (this.flags & MHD_AV) != 0;
    }

    /**
     *
     * @return the numbering format a multivolume archive
     */
    public boolean isNewNumbering() {
        return (this.flags & MHD_NEWNUMBERING) != 0;
    }
}
