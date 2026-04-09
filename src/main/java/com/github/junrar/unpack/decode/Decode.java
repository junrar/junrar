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

/**
 * Used to store information for lz decoding.
 * Enhanced with quick-lookup tables for RAR5 decompression.
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Decode {
    private int maxNum;

    private final int[] decodeLen = new int[16];

    private final int[] decodePos = new int[16];

    protected int[] decodeNum = new int[2];

    /** Quick-lookup bit length table (RAR5 optimization, 2^9 = 512 entries) */
    protected final byte[] quickLen = new byte[512];

    /** Quick-lookup symbol number table (RAR5 optimization, 2^9 = 512 entries) */
    protected final char[] quickNum = new char[512];

    /** Number of bits used for quick lookup (9 for RAR5) */
    protected int quickBits = 0;

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
     * Sets the max num
     * @param maxNum to be set to maxNum
     */
    public void setMaxNum(int maxNum) {
        this.maxNum = maxNum;
    }

    /**
     * Resizes the decode num array.
     * @param newSize the new size
     */
    public void resizeDecodeNum(final int newSize) {
        if (this.decodeNum.length < newSize) {
            this.decodeNum = new int[newSize];
        }
    }

    /**
     * Sets the decode num array directly.
     * @param decodeNum the new decode num array
     */
    public void setDecodeNum(final int[] decodeNum) {
        this.decodeNum = decodeNum;
    }

    /**
     * @return the quick-lookup bit length table
     */
    public byte[] getQuickLen() {
        return quickLen;
    }

    /**
     * @return the quick-lookup symbol number table
     */
    public char[] getQuickNum() {
        return quickNum;
    }

    /**
     * @return the number of quick-lookup bits
     */
    public int getQuickBits() {
        return quickBits;
    }

    /**
     * Sets the number of quick-lookup bits and resizes tables if needed.
     * @param quickBits number of bits (0 to disable, 9 for RAR5)
     */
    public void setQuickBits(final int quickBits) {
        this.quickBits = quickBits;
    }
}
