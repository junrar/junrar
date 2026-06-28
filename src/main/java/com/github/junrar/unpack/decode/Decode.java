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

    /**
     * Number of leading code bits resolved by the quick-decode lookup tables.
     * Mirrors unrar's {@code MAX_QUICK_DECODE_BITS}. The large literal/length
     * alphabets use the full width; smaller alphabets use 3 bits fewer.
     */
    private static final int MAX_QUICK_DECODE_BITS = 9;

    private int maxNum;

    private final int[] decodeLen = new int[16];

    private final int[] decodePos = new int[16];

    protected int[] decodeNum = new int[2];

    private int quickBits;

    private byte[] quickLen = new byte[0];

    private int[] quickNum = new int[0];

    /**
     * Quick-decode width for an alphabet of the given size. The literal/length
     * tables (NC20 for RAR2.0, NC for RAR2.9/3.0, NC50 for RAR5) get the full
     * width; every other alphabet gets {@code MAX_QUICK_DECODE_BITS - 3}.
     * See unrar {@code MakeDecodeTables} (unpack.cpp).
     */
    public static int quickBitsFor(int size) {
        if (size == Compress.NC || size == Compress.NC20 || size == Compress.NC50) {
            return MAX_QUICK_DECODE_BITS;
        }
        return MAX_QUICK_DECODE_BITS > 3 ? MAX_QUICK_DECODE_BITS - 3 : 0;
    }

    /**
     * Builds the Huffman decode tables (decodeLen/decodePos/decodeNum) and the
     * quick-decode lookup tables from a per-symbol bit-length table. This is a
     * faithful port of unrar's {@code Unpack::MakeDecodeTables} and is shared by
     * every RAR format (1.5 excepted, which uses a different tiny-table decoder).
     *
     * @param lengthTable per-symbol code lengths (low nibble of each byte)
     * @param offset      start index of this alphabet within lengthTable
     * @param size        alphabet size
     */
    public void buildDecodeTable(byte[] lengthTable, int offset, int size) {
        maxNum = size;

        int[] lengthCount = new int[16];
        for (int i = 0; i < size; i++) {
            lengthCount[lengthTable[offset + i] & 0xF]++;
        }
        lengthCount[0] = 0;

        if (decodeNum.length < size) {
            decodeNum = new int[size];
        } else {
            Arrays.fill(decodeNum, 0, size, 0);
        }

        decodePos[0] = 0;
        decodeLen[0] = 0;
        int upperLimit = 0;
        for (int i = 1; i < 16; i++) {
            upperLimit += lengthCount[i];
            decodeLen[i] = upperLimit << (16 - i);
            upperLimit *= 2;
            decodePos[i] = decodePos[i - 1] + lengthCount[i - 1];
        }

        int[] copyDecodePos = decodePos.clone();
        for (int i = 0; i < size; i++) {
            int curBitLength = lengthTable[offset + i] & 0xF;
            if (curBitLength != 0) {
                decodeNum[copyDecodePos[curBitLength]++] = i;
            }
        }

        quickBits = quickBitsFor(size);
        int quickDataSize = 1 << quickBits;
        if (quickLen.length < quickDataSize) {
            quickLen = new byte[quickDataSize];
            quickNum = new int[quickDataSize];
        }
        int curBitLength = 1;
        for (int code = 0; code < quickDataSize; code++) {
            int bitField = code << (16 - quickBits);
            while (curBitLength < 16 && bitField >= decodeLen[curBitLength]) {
                curBitLength++;
            }
            quickLen[code] = (byte) curBitLength;
            int dist = bitField - decodeLen[curBitLength - 1];
            dist >>>= (16 - curBitLength);
            int pos;
            if (curBitLength < 16 && (pos = decodePos[curBitLength] + dist) < size) {
                quickNum[code] = decodeNum[pos];
            } else {
                quickNum[code] = 0;
            }
        }
    }

    /**
     * @return number of leading bits resolved by the quick-decode tables
     */
    public int getQuickBits() {
        return quickBits;
    }

    /**
     * @return quick-decode code-length lookup (indexed by left-aligned bit field)
     */
    public byte[] getQuickLen() {
        return quickLen;
    }

    /**
     * @return quick-decode symbol lookup (indexed by left-aligned bit field)
     */
    public int[] getQuickNum() {
        return quickNum;
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
