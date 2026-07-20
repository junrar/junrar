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
 * Base class of headers that contain data
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class BlockHeader extends BaseBlock {
    public static final short blockHeaderSize = 4;

    private static final Logger logger = LoggerFactory.getLogger(BlockHeader.class);

    private long dataSize;
    private long packSize;

    public BlockHeader() {

    }

    public BlockHeader(BlockHeader bh) {
        super(bh);
        this.packSize = bh.getDataSize();
        this.dataSize = packSize;
        this.positionInFile = bh.getPositionInFile();
    }

    public BlockHeader(BaseBlock bb, byte[] blockHeader) {
        super(bb);

        this.packSize = Raw.readIntLittleEndianAsLong(blockHeader, 0);
        this.dataSize  = this.packSize;
    }

    public long getDataSize() {
        return dataSize;
    }

    public long getPackSize() {
        return packSize;
    }

    /**
     * Sets both {@link #getPackSize()} and {@link #getDataSize()} to the same value (M3.3,
     * issue #24): a RAR5 FILE/SERVICE header has a single {@code DataSize} field, unlike RAR3's
     * separate pack/data sizes, so {@link Rar5FileHeaderReader} drives both from it.
     * Package-private -- a wire-format detail, not part of the public {@code BlockHeader}
     * contract.
     *
     * @param size the RAR5 block's {@code DataSize}.
     */
    void setPackAndDataSize(final long size) {
        this.packSize = size;
        this.dataSize = size;
    }

    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            logger.info("DataSize: {} packSize: {}", getDataSize(), getPackSize());
        }
    }
}
