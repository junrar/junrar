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
 * Base class of all rar headers
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class BaseBlock {

    private static final Logger logger = LoggerFactory.getLogger(BaseBlock.class);

    public static final short BaseBlockSize = 7;

    //TODO move somewhere else

    public static final short MHD_VOLUME = 0x0001;
    public static final short MHD_COMMENT = 0x0002;
    public static final short MHD_LOCK = 0x0004;
    public static final short MHD_SOLID = 0x0008;
    public static final short MHD_PACK_COMMENT = 0x0010;
    public static final short MHD_NEWNUMBERING = 0x0010;
    public static final short MHD_AV = 0x0020;
    public static final short MHD_PROTECT = 0x0040;
    public static final short MHD_PASSWORD = 0x0080;
    public static final short MHD_FIRSTVOLUME = 0x0100;
    public static final short MHD_ENCRYPTVER = 0x0200;


    public static final short LHD_SPLIT_BEFORE =  0x0001;
    public static final short LHD_SPLIT_AFTER  =  0x0002;
    public static final short LHD_PASSWORD     =  0x0004;
    public static final short LHD_COMMENT      =  0x0008;
    public static final short LHD_SOLID        =  0x0010;

    public static final short LHD_WINDOWMASK   =  0x00e0;
    public static final short LHD_WINDOW64     =  0x0000;
    public static final short LHD_WINDOW128    =  0x0020;
    public static final short LHD_WINDOW256    =  0x0040;
    public static final short LHD_WINDOW512    =  0x0060;
    public static final short LHD_WINDOW1024   =  0x0080;
    public static final short LHD_WINDOW2048   =  0x00a0;
    public static final short LHD_WINDOW4096   =  0x00c0;
    public static final short LHD_DIRECTORY    =  0x00e0;

    public static final short LHD_LARGE        =  0x0100;
    public static final short LHD_UNICODE      =  0x0200;
    public static final short LHD_SALT         =  0x0400;
    public static final short LHD_VERSION      =  0x0800;
    public static final short LHD_EXTTIME      =  0x1000;
    public static final short LHD_EXTFLAGS     =  0x2000;

    public static final short SKIP_IF_UNKNOWN  =  0x4000;
    public static final short LONG_BLOCK       = -0x8000;

    public static final short EARC_NEXT_VOLUME =  0x0001;
    public static final short EARC_DATACRC     =  0x0002;
    public static final short EARC_REVSPACE    =  0x0004;
    public static final short EARC_VOLNUMBER   =  0x0008;


    protected long positionInFile;

    protected short headCRC = 0;
    protected byte headerType = 0;
    protected short flags = 0;
    protected short headerSize = 0;

    private boolean brokenHeader;

    /**
     *
     */
    public BaseBlock() {

    }

    public BaseBlock(BaseBlock bb) {
        this.flags = bb.getFlags();
        this.headCRC = bb.getHeadCRC();
        this.headerType = bb.getHeaderType().getHeaderByte();
        this.headerSize = bb.getHeaderSize(false);
        this.positionInFile = bb.getPositionInFile();
    }
    public BaseBlock(byte[] baseBlockHeader) {

        int pos = 0;
        this.headCRC = Raw.readShortLittleEndian(baseBlockHeader, pos);
        pos += 2;
        this.headerType |= baseBlockHeader[pos] & 0xff;
        pos++;
        this.flags = Raw.readShortLittleEndian(baseBlockHeader, pos);
        pos += 2;
        this.headerSize = Raw.readShortLittleEndian(baseBlockHeader, pos);
    }


    /**
     * Whether this header's stored CRC failed the P0.7 verification (issue #12): unrar's
     * "record and continue" tolerance -- a mismatch does not, by itself, abort archive
     * open. {@link com.github.junrar.rarfile.FileHeader} extraction refuses a broken
     * header instead (see {@code Archive#doExtractFile}); junrar's one conscious,
     * narrower-scoped divergence from unrar's own "warn at open, let the file data CRC
     * decide at extract" tolerance. Always {@code false} for header types exempt from
     * verification (SIGN, AV, old-Unix-owner sub-blocks) and for {@link MarkHeader},
     * which has no CRC of its own.
     *
     * @return true if the header CRC did not match the computed value.
     */
    public boolean isBrokenHeader() {
        return brokenHeader;
    }

    /**
     * Sets whether this header's stored CRC failed verification.
     *
     * @param brokenHeader see {@link #isBrokenHeader()}.
     */
    public void setBrokenHeader(boolean brokenHeader) {
        this.brokenHeader = brokenHeader;
    }

    public boolean hasArchiveDataCRC() {
        return (this.flags & EARC_DATACRC) != 0;
    }

    public boolean hasVolumeNumber() {
        return (this.flags & EARC_VOLNUMBER) != 0;
    }

    /**
     * Whether this (EndArc) header signals REV-recovery-volume space (P0.7, issue #12;
     * unrar {@code EARC_REVSPACE}): its last 7 bytes may legitimately be zero, which
     * {@code Archive}'s header-CRC verification treats as a recovery, not a mismatch.
     *
     * @return isRevSpace
     */
    public boolean hasRevSpace() {
        return (this.flags & EARC_REVSPACE) != 0;
    }

    public boolean hasEncryptVersion() {
        return (flags & MHD_ENCRYPTVER) != 0;
    }

    /**
     * @return is it a sub block
     */
    public boolean isSubBlock() {
        if (UnrarHeadertype.SubHeader.equals(headerType)) {
            return (true);
        }
        if (UnrarHeadertype.NewSubHeader.equals(headerType) && (flags & LHD_SOLID) != 0) {
            return (true);
        }
        return (false);

    }

    public long getPositionInFile() {
        return positionInFile;
    }

    public short getFlags() {
        return flags;
    }

    public short getHeadCRC() {
        return headCRC;
    }

    /**
     * The header size.
     *
     * @return the header size
     * @deprecated As of 7.3.0, replaced by {@link #getHeaderSize(boolean)}
     */
    @Deprecated
    public short getHeaderSize() {
        return headerSize;
    }

    /**
     * The header size, padded if encrypted.
     *
     * @param encrypted if the header is encrypted.
     * @return the header size, and the padded header size if the header is encrypted.
     */
    public short getHeaderSize(boolean encrypted) {
        if (encrypted) {
            return (short) (headerSize + getHeaderPaddingSize());
        } else {
            return headerSize;
        }
    }

    private short getHeaderPaddingSize() {
        return (short) ((~headerSize + 1) & 0xF);
    }

    public UnrarHeadertype getHeaderType() {
        return UnrarHeadertype.findType(headerType);
    }

    public void setPositionInFile(long positionInFile) {
        this.positionInFile = positionInFile;
    }

    public void print() {
        if (logger.isInfoEnabled()) {
            StringBuilder str = new StringBuilder();
            str.append("HeaderType: ").append(getHeaderType());
            str.append("\nHeadCRC: ").append(Integer.toHexString(getHeadCRC()));
            str.append("\nFlags: ").append(Integer.toHexString(getFlags()));
            str.append("\nHeaderSize: ").append(getHeaderSize(false));
            str.append("\nPosition in file: ").append(getPositionInFile());
            logger.info(str.toString());
        }
    }
}
