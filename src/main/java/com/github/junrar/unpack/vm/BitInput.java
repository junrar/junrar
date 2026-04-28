/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
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
package com.github.junrar.unpack.vm;

import com.github.junrar.io.Raw;

import java.io.IOException;
import java.io.InputStream;

/**
 * Bit-level input reader for RAR decompression.
 * Enhanced with RAR5 capabilities: 32/64-bit reads, buffer management, EOF tracking.
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class BitInput {

    /**
     * the max size of the input
     */
    public static final int MAX_SIZE = 0x8000;

    /** Guard bytes at end of buffer to prevent OOB reads */
    private static final int GUARD_BYTES = 8;

    protected int inAddr;
    protected int inBit;
    protected byte[] inBuf;
    protected int bufSize;
    protected boolean eofReached;


    public void InitBitInput() {
        inAddr = 0;
        inBit = 0;
    }
    /**
     * @param Bits .
     */
    public void addbits(int Bits) {
        Bits += inBit;
        inAddr += Bits >>> 3;
        inBit = Bits & 7;
    }

    /**
     * @return the bits (unsigned short)
     */
    public int getbits() {
        // Read 3 bytes as a 24-bit value, shift right by (8-inBit), mask to 16 bits
        // Matches C++: ((InBuf[InAddr]<<16)|(InBuf[InAddr+1]<<8)|InBuf[InAddr+2]) >> (8-InBit) & 0xffff
        final int b0 = inBuf[inAddr] & 0xFF;
        final int b1 = inBuf[inAddr + 1] & 0xFF;
        final int b2 = inBuf[inAddr + 2] & 0xFF;
        return ((b0 << 16) | (b1 << 8) | b2) >>> (8 - inBit) & 0xFFFF;
    }

    /**
     * Returns the next 32 bits from the current position (MSB-first).
     * Used by RAR5 for large distance codes.
     *
     * @return 32 bits as a long
     */
    public long getbits32() {
        // Read 4 bytes as big-endian, shift left by inBit, OR in bits from 5th byte
        // Mask to 32 bits (matching C++ uint return type)
        return ((Raw.readIntBigEndian(inBuf, inAddr) & 0xFFFFFFFFL) << inBit
            | ((inBuf[inAddr + 4] & 0xFFL) >>> (8 - inBit))) & 0xFFFFFFFFL;
    }

    /**
     * Returns the next 64 bits from the current position (MSB-first).
     * Used by RAR5 for very large distance codes.
     *
     * @return 64 bits as a long
     */
    public long getbits64() {
        // Read 8 bytes as big-endian, shift left by inBit, OR in bits from 9th byte
        // Matches C++: RawGetBE8(InBuf+InAddr) << InBit | InBuf[InAddr+8] >> (8-InBit)
        return Raw.readLongBigEndian(inBuf, inAddr) << inBit
            | ((inBuf[inAddr + 8] & 0xFFL) >>> (8 - inBit));
    }

    /**
     * Aligns the bit cursor to the next byte boundary.
     */
    public void alignToByte() {
        if (inBit != 0) {
            inAddr++;
            inBit = 0;
        }
    }

    /**
     * @return the current byte position in the buffer
     */
    public int getInAddr() {
        return inAddr;
    }

    /**
     * @return the current bit position within the byte (0-7)
     */
    public int getInBit() {
        return inBit;
    }

    /**
     * @return the number of valid bytes in the buffer
     */
    public int getBufSize() {
        return bufSize;
    }

    /**
     * @return true if EOF has been reached
     */
    public boolean isEofReached() {
        return eofReached;
    }

    /**
     * Checks if there are enough bytes remaining in the buffer.
     *
     * @param bytes the number of bytes needed
     * @return true if at least {@code bytes} bytes are available
     */
    public boolean hasBytes(final int bytes) {
        return inAddr + bytes <= bufSize;
    }

    /**
     * Refills the buffer from the input stream.
     *
     * @param input the input stream to read from
     * @return the number of bytes read, or -1 if EOF
     * @throws IOException if an I/O error occurs
     */
    public int fillBuffer(final InputStream input) throws IOException {
        if (eofReached) {
            return -1;
        }

        // Move remaining data to the start of the buffer
        final int remaining = bufSize - inAddr;
        if (remaining > 0) {
            System.arraycopy(inBuf, inAddr, inBuf, 0, remaining + GUARD_BYTES);
        }
        inAddr = 0;
        bufSize = remaining;

        // Read more data
        final int toRead = MAX_SIZE - bufSize;
        if (toRead <= 0) {
            return 0;
        }

        int totalRead = 0;
        while (totalRead < toRead) {
            final int read = input.read(inBuf, bufSize + totalRead, toRead - totalRead);
            if (read < 0) {
                eofReached = true;
                break;
            }
            totalRead += read;
        }

        if (totalRead > 0) {
            bufSize += totalRead;
            // Fill guard bytes with zeros
            for (int i = 0; i < GUARD_BYTES; i++) {
                inBuf[bufSize + i] = 0;
            }
        }

        return totalRead > 0 ? totalRead : (eofReached ? -1 : 0);
    }

    /**
     * Reads bytes directly into the buffer, replacing current content.
     * Used for initial buffer load.
     *
     * @param input the input stream
     * @return the number of bytes read
     * @throws IOException if an I/O error occurs
     */
    public int readIntoBuffer(final InputStream input) throws IOException {
        inAddr = 0;
        inBit = 0;
        bufSize = 0;
        eofReached = false;

        int totalRead = 0;
        while (totalRead < MAX_SIZE) {
            final int read = input.read(inBuf, totalRead, MAX_SIZE - totalRead);
            if (read < 0) {
                eofReached = true;
                break;
            }
            totalRead += read;
        }

        bufSize = totalRead;
        // Fill guard bytes
        for (int i = 0; i < GUARD_BYTES; i++) {
            inBuf[bufSize + i] = 0;
        }

        return totalRead;
    }

    /**
     */
    public BitInput() {
        inBuf = new byte[MAX_SIZE + GUARD_BYTES];
    }

    /**
     * @param Bits add the bits
     */
    public void faddbits(int Bits) {
        addbits(Bits);
    }


    /**
     * @return get the bits
     */
    public int fgetbits() {
        return (getbits());
    }

    /**
     * Indicates an Overfow
     * @param IncPtr how many bytes to inc
     * @return true if an Oververflow would occur
     */
    public boolean Overflow(int IncPtr) {
        return (inAddr + IncPtr >= MAX_SIZE);
    }
    public byte[] getInBuf() {
        return inBuf;
    }


}
