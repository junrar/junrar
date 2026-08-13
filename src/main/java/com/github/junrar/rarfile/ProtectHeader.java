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

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;

/**
 * recovery header
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class ProtectHeader extends BlockHeader {

    /**
     * the header size, incremental past BlockHeader (unrar 3.7.3
     * headers.hpp:242-249 SIZEOF_PROTECTHEAD 26 minus SIZEOF_LONGBLOCKHEAD 11)
     */
    public static final int protectHeaderSize = 15;

    private static final int MARK_SIZE = 8;

    private byte version;
    private final short recSectors;
    private final int totalBlocks;
    private final byte[] mark;

    public ProtectHeader(BlockHeader bh, byte[] protectHeader) throws CorruptHeaderException {
        super(bh);

        mark = new byte[MARK_SIZE];
        if (protectHeader.length < protectHeaderSize) {
            // The buffer is sized from the header's own declared size, so it can be shorter than
            // this fixed layout. Reject rather than stand values in for the fields it does not
            // hold: `mark` is a byte[8] and `version` a byte, neither of which has a way to say
            // "not there", and a zero mark is indistinguishable from a genuine one
            // (MIGRATION_MANUAL section 4.7). Archive skips such a block and reports it through
            // Archive.getHeaderFailures().
            throw new CorruptHeaderException(
                    "Recovery record header shorter than its fixed layout");
        } else {
            int pos = 0;
            version |= protectHeader[pos] & 0xff;
            pos++;

            recSectors = Raw.readShortLittleEndian(protectHeader, pos);
            pos += 2;
            totalBlocks = Raw.readIntLittleEndian(protectHeader, pos);
            pos += 4;

            System.arraycopy(protectHeader, pos, mark, 0, MARK_SIZE);
        }
    }

    public byte[] getMark() {
        return mark;
    }

    public short getRecSectors() {
        return recSectors;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public byte getVersion() {
        return version;
    }
}
