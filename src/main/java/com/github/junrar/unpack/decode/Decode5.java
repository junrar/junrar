/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack.decode;

/**
 * RAR5 Huffman decode table (issue #27, M3.6). Mirrors {@code struct DecodeTable} in
 * {@code 8f437ab:unpack.hpp} — the quick-decode accelerator ({@link #quickLen}/{@link #quickNum})
 * that the older RAR3 {@link Decode} lacks — so it is a sibling holder, not a subclass. Every
 * {@code ushort} field is widened to {@code int} per the signedness rules (manual &sect;4.2).
 */
public class Decode5 {

    /** Real size of the alphabet (and of {@link #decodeNum}). */
    private int maxNum;

    /**
     * Left-aligned upper-limit codes per bit length: {@code decodeLen[len-1]} is the start of the
     * range for {@code len}, {@code decodeLen[len]} the code just past its end.
     */
    private final int[] decodeLen = new int[16];

    /** Start position in the code list for every bit length (running sum of counts). */
    private final int[] decodePos = new int[16];

    /** Bits processed in quick mode; never exceeds {@link Compress#MAX_QUICK_DECODE_BITS}. */
    private int quickBits;

    /** Quick translation of a right-aligned bit field to its bit length. */
    private final int[] quickLen = new int[1 << Compress.MAX_QUICK_DECODE_BITS];

    /** Quick translation of a right-aligned bit field to its alphabet position. */
    private final int[] quickNum = new int[1 << Compress.MAX_QUICK_DECODE_BITS];

    /** Translate a position in the code list to a position in the alphabet. */
    private final int[] decodeNum;

    public Decode5(final int alphabetSize) {
        this.decodeNum = new int[alphabetSize];
    }

    public int getMaxNum() {
        return maxNum;
    }

    public void setMaxNum(final int maxNum) {
        this.maxNum = maxNum;
    }

    public int[] getDecodeLen() {
        return decodeLen;
    }

    public int[] getDecodePos() {
        return decodePos;
    }

    public int getQuickBits() {
        return quickBits;
    }

    public void setQuickBits(final int quickBits) {
        this.quickBits = quickBits;
    }

    public int[] getQuickLen() {
        return quickLen;
    }

    public int[] getQuickNum() {
        return quickNum;
    }

    public int[] getDecodeNum() {
        return decodeNum;
    }
}
