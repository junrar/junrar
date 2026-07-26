/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 01.06.2007
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
package com.github.junrar.unpack.decode;

import java.util.Arrays;

/**
 * Used to store information for lz decoding
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Decode {
    private int maxNum;

    private final int[] decodeLen = new int[16];

    private final int[] decodePos = new int[16];

    protected int[] decodeNum = new int[2];

    /** Bits processed in quick mode; never exceeds {@link Compress#MAX_QUICK_DECODE_BITS}. */
    private int quickBits;

    /** Quick translation of a right-aligned bit field to its bit length. */
    private final int[] quickLen = new int[1 << Compress.MAX_QUICK_DECODE_BITS];

    /** Quick translation of a right-aligned bit field to its alphabet position. */
    private final int[] quickNum = new int[1 << Compress.MAX_QUICK_DECODE_BITS];

    /**
     * @return number of leading code bits resolved by the quick-decode tables (unrar
     *     {@code DecodeTable::QuickBits})
     */
    public int getQuickBits() {
        return quickBits;
    }

    public void setQuickBits(int quickBits) {
        this.quickBits = quickBits;
    }

    /** @return quick-decode code-length lookup, indexed by the right-aligned quick bit field */
    public int[] getQuickLen() {
        return quickLen;
    }

    /** @return quick-decode symbol lookup, indexed by the right-aligned quick bit field */
    public int[] getQuickNum() {
        return quickNum;
    }

    /**
     * Zero every table in place -- the equivalent of unrar's {@code memset(MD,0,sizeof(MD))} in
     * {@code UnpInitData20}. Reusing the instance instead of allocating a replacement matters now
     * that each one carries the 2 x {@code 1 << MAX_QUICK_DECODE_BITS} quick-decode tables: a
     * fresh set per non-solid entry is several tens of KB of garbage per file.
     */
    public void reset() {
        maxNum = 0;
        quickBits = 0;
        Arrays.fill(decodeLen, 0);
        Arrays.fill(decodePos, 0);
        Arrays.fill(decodeNum, 0);
        Arrays.fill(quickLen, 0);
        Arrays.fill(quickNum, 0);
    }

    /**
     * returns the decode Length array
     * @return decodeLength
     */
    public int[] getDecodeLen() {
        return decodeLen;
    }

    /**
     * returns the decode num array
     * @return decodeNum
     */
    public int[] getDecodeNum() {
        return decodeNum;
    }

    /**
     * returns the decodePos array
     * @return decodePos
     */
    public int[] getDecodePos() {
        return decodePos;
    }

    /**
     * returns the max num
     * @return maxNum
     */
    public int getMaxNum() {
        return maxNum;
    }

    /**
     * sets the max num
     * @param maxNum to be set to maxNum
     */
    public void setMaxNum(int maxNum) {
        this.maxNum = maxNum;
    }
}
