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

    public ProtectHeader(BlockHeader bh, byte[] protectHeader) {
        super(bh);

        int pos = 0;
        version |= protectHeader[pos] & 0xff;
        pos++;

        recSectors = Raw.readShortLittleEndian(protectHeader, pos);
        pos += 2;
        totalBlocks = Raw.readIntLittleEndian(protectHeader, pos);
        pos += 4;

        mark = new byte[MARK_SIZE];
        System.arraycopy(protectHeader, pos, mark, 0, MARK_SIZE);
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
