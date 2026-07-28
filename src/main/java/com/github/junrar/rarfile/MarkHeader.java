/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 24.05.2007
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
 * the header to recognize a file to be a rar archive
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class MarkHeader extends BaseBlock {

    private final Logger logger = LoggerFactory.getLogger(MarkHeader.class.getName());

    private RARVersion version;

    public MarkHeader(BaseBlock bb) {
        super(bb);
    }

    /**
     * A synthetic RAR 1.4 mark header (P1, issue #293). The RAR 1.4 marker is a bare 4-byte
     * signature ({@code 52 45 7e 5e}, unrar {@code RARFMT14}) with no {@link BaseBlock}-shaped
     * payload behind it, so {@link #isSignature()}/{@link #isValid()} -- built around the
     * RAR15+ 7-byte BaseBlock layout -- do not apply; {@code Archive}'s RAR 1.4 loop uses this
     * factory instead of the byte-parsing constructor above, purely so
     * {@link #isOldFormat()} (and therefore {@code Archive.isOldFormat()}) is truthful.
     *
     * @return a mark header whose {@link #getVersion()} is {@link RARVersion#OLD}.
     */
    public static MarkHeader old() {
        // Not `new BaseBlock()`: its default headerType (0) matches no UnrarHeadertype, and
        // the BaseBlock(BaseBlock) copy constructor this class's own constructor uses
        // dereferences getHeaderType().getHeaderByte() unconditionally -- an NPE. Seed a real
        // MarkHeader type byte through the byte[] constructor instead.
        final byte[] seed = new byte[BaseBlock.BaseBlockSize];
        seed[2] = UnrarHeadertype.MarkHeader.getHeaderByte();
        final MarkHeader mh = new MarkHeader(new BaseBlock(seed));
        mh.version = RARVersion.OLD;
        return mh;
    }

    public boolean isValid() {
        if (!(getHeadCRC() == 0x6152)) {
            return false;
        }
        if (!(getHeaderType() == UnrarHeadertype.MarkHeader)) {
            return false;
        }
        if (!(getFlags() == 0x1a21)) {
            return false;
        }
        return getHeaderSize(false) == BaseBlockSize;
    }

    public boolean isSignature() {
        byte[] d = new byte[BaseBlock.BaseBlockSize];
        Raw.writeShortLittleEndian(d, 0, headCRC);
        d[2] = headerType;
        Raw.writeShortLittleEndian(d, 3, flags);
        Raw.writeShortLittleEndian(d, 5, headerSize);

        if (d[0] == 0x52) {
            if (d[1] == 0x45 && d[2] == 0x7e && d[3] == 0x5e) {
                version = RARVersion.OLD;
            } else if (d[1] == 0x61
                    && d[2] == 0x72
                    && d[3] == 0x21
                    && d[4] == 0x1a
                    && d[5] == 0x07) {
                if (d[6] == 0x00) {
                    version = RARVersion.V4;
                } else if (d[6] == 0x01) {
                    version = RARVersion.V5;
                }
            }
        }
        return version == RARVersion.OLD || version == RARVersion.V4;
    }

    public boolean isOldFormat() {
        return RARVersion.isOldFormat(version);
    }

    public RARVersion getVersion() {
        return version;
    }

    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            logger.info("valid: {}", isValid());
        }
    }
}
