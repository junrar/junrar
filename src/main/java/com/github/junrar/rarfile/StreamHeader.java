package com.github.junrar.rarfile;

import com.github.junrar.io.Raw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamHeader extends SubBlockHeader {
    private static final Logger logger = LoggerFactory.getLogger(StreamHeader.class);

    public static final short streamHeaderSize = 10;

    private final int unpSize;
    private byte unpVer;
    private byte method;
    private final int SCRC;

    public StreamHeader(SubBlockHeader bh, byte[] streamHead) {
        super(bh);
        int pos = 0;
        unpSize = Raw.readIntLittleEndian(streamHead, pos);
        pos += 4;
        unpVer = (byte) (streamHead[pos] & 0xff);
        pos++;
        method = (byte) (streamHead[pos] & 0xff);
        pos++;
        SCRC = Raw.readIntLittleEndian(streamHead, pos);
    }

    /**
     * @return the SCRC
     */
    public int getSCRC() {
        return SCRC;
    }

    /**
     * @return the method
     */
    public byte getMethod() {
        return method;
    }

    /**
     * @return the unpSize
     */
    public int getUnpSize() {
        return unpSize;
    }

    /**
     * @return the unpVer
     */
    public byte getUnpVer() {
        return unpVer;
    }

    public void print() {
        if (logger.isInfoEnabled()) {
            logger.info("unpSize: {}", unpSize);
            logger.info("unpVersion: {}", unpVer);
            logger.info("method: {}", method);
            logger.info("SCRC: {}", SCRC);
        }
    }
}
