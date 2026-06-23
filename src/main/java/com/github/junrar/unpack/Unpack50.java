package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import com.github.junrar.io.Raw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RAR5 Huffman+LZ77 decompressor. Ported from the reference C++ implementation
 * (unpack50.cpp, unpackinline.cpp, unpack.cpp).
 */
public class Unpack50 extends Unpack29 {

    private static final int NC = 306;
    private static final int DCB = 64;  // Base distance codes up to 4 GB.
    private static final int DCX = 80;  // Extended distance codes up to 1 TB (RAR7).
    private static final int LDC = 16;
    private static final int RC = 44;
    private static final int BC = 20;
    private static final int HUFF_TABLE_SIZEB = NC + DCB + LDC + RC; // 430
    private static final int HUFF_TABLE_SIZEX = NC + DCX + LDC + RC; // 446
    private static final int MAX_QUICK_DECODE_BITS = 9;
    private static final int UNPACK_MAX_WRITE = 0x400000;
    private static final int MAX_FILTER_BLOCK_SIZE = 0x400000;
    private static final int MAX_UNPACK_FILTERS = 8192;
    private static final int INP_BUF_SIZE = 0x8000;

    private static final int FILTER_DELTA = 0;
    private static final int FILTER_E8 = 1;
    private static final int FILTER_E8E9 = 2;
    private static final int FILTER_ARM = 3;
    private static final int FILTER_NONE = 10;

    private static class DecodeTable {
        int maxNum;
        final int[] decodeLen = new int[16];
        final int[] decodePos = new int[16];
        int quickBits;
        final byte[] quickLen = new byte[1 << MAX_QUICK_DECODE_BITS];
        final int[] quickNum = new int[1 << MAX_QUICK_DECODE_BITS];
        int[] decodeNum = new int[0];
    }

    private static class BlockHeader {
        int blockSize = -1;
        int blockBitSize;
        int blockStart;
        boolean lastBlockInFile;
        boolean tablePresent;
    }

    private static class Filter5 {
        int blockStart;
        int blockLength;
        int type;
        int channels;
        boolean nextWindow;
    }

    // Bit input - separate from inherited to avoid conflicts
    private final byte[] inBuf50 = new byte[INP_BUF_SIZE];
    private int inAddr50;
    private int inBit50;
    private int readTop;
    private int readBorder;

    // Circular dictionary window - separate from inherited window
    private byte[] window50;
    private int maxWinSize;
    private int writeBorder;
    private boolean firstWinDone;
    private int prevPtr;

    // LZ state - separate from inherited unpPtr50, wrPtr50
    private int unpPtr50;
    private int wrPtr50;
    private final long[] oldDist = new long[]{-1, -1, -1, -1};
    private int lastLength;
    private boolean tablesRead5;

    // True for VER_PACK7 (RAR 7.0): use 80 distance codes instead of 64,
    // allowing distances up to 1 TB. See unpack.cpp:184.
    private boolean extraDist;

    // Decode tables
    private final DecodeTable ldTable = new DecodeTable();
    private final DecodeTable ddTable = new DecodeTable();
    private final DecodeTable lddTable = new DecodeTable();
    private final DecodeTable rdTable = new DecodeTable();
    private final DecodeTable bdTable = new DecodeTable();

    private final BlockHeader blockHeader = new BlockHeader();

    // Filter state
    private final List<Filter5> filters = new ArrayList<>();
    private byte[] filterSrcMemory = new byte[0];
    private byte[] filterDstMemory = new byte[0];

    // Extraction state - inherited from Unpack15/Unpack29:
    // destUnpSize, writtenFileSize, fileExtracted, unpPtr50, wrPtr50, window, unpIO

    public void setDictionarySize(long rawSize) {
        int newSize = (rawSize > 0 && rawSize <= 0x40000000L) ? (int) rawSize : 0x100000;
        if (window50 == null || maxWinSize < newSize) {
            window50 = new byte[newSize];
            maxWinSize = newSize;
        }
    }

    public boolean isFileExtracted() {
        return fileExtracted;
    }

    /**
     * Toggle extended distance codes. Set before calling {@link #unpack50(boolean)}.
     * RAR 5.0 (VER_PACK5) uses 64 distance codes; RAR 7.0 (VER_PACK7) uses 80.
     */
    public void setExtraDist(boolean extraDist) {
        this.extraDist = extraDist;
    }

    public void unpack50(boolean solid) throws IOException, RarException {
        fileExtracted = true;

        if (!solid) {
            Arrays.fill(oldDist, -1L);
            lastLength = 0;
            unpPtr50 = 0;
            wrPtr50 = 0;
            prevPtr = 0;
            firstWinDone = false;
            writeBorder = Math.min(maxWinSize, UNPACK_MAX_WRITE);
            tablesRead5 = false;
        }

        filters.clear();
        inAddr50 = 0;
        inBit50 = 0;
        writtenFileSize = 0;
        readTop = 0;
        readBorder = 0;
        blockHeader.blockSize = -1;

        if (!readBuf()) {
            return;
        }
        if (!readBlockHeader() || !readTables() || !tablesRead5) {
            return;
        }

        while (true) {
            unpPtr50 = wrapUp(unpPtr50);
            firstWinDone |= (prevPtr > unpPtr50);
            prevPtr = unpPtr50;

            if (inAddr50 >= readBorder) {
                boolean fileDone = false;
                while (inAddr50 > blockHeader.blockStart + blockHeader.blockSize - 1
                        || (inAddr50 == blockHeader.blockStart + blockHeader.blockSize - 1
                        && inBit50 >= blockHeader.blockBitSize)) {
                    if (blockHeader.lastBlockInFile) {
                        fileDone = true;
                        break;
                    }
                    if (!readBlockHeader() || !readTables()) {
                        return;
                    }
                }
                if (fileDone || !readBuf()) {
                    break;
                }
            }

            if (wrapDown(writeBorder - unpPtr50) <= MAX_LZ_MATCH && writeBorder != unpPtr50) {
                writeBuf();
                if (writtenFileSize > destUnpSize) {
                    return;
                }
            }

            int mainSlot = decodeNumber(ldTable);

            if (mainSlot < 256) {
                window50[unpPtr50++] = (byte) mainSlot;
                continue;
            }

            if (mainSlot >= 262) {
                int length = slotToLength(mainSlot - 262);
                int distSlot = decodeNumber(ddTable);
                long distance;
                int dBits;
                if (distSlot < 4) {
                    dBits = 0;
                    distance = 1 + distSlot;
                } else {
                    dBits = distSlot / 2 - 1;
                    distance = 1L + ((long) (2 | (distSlot & 1)) << dBits);
                }
                if (dBits > 0) {
                    if (dBits >= 4) {
                        if (dBits > 4) {
                            // For DCX (80 codes, RAR7), dBits can exceed 36, requiring 64-bit reads.
                            if (dBits > 36) {
                                distance += (Raw.getbits64(inBuf50, inAddr50, inBit50) >>> (68 - dBits)) << 4;
                            } else {
                                distance += (Integer.toUnsignedLong(getbits32()) >>> (36 - dBits)) << 4;
                            }
                            addbits50(dBits - 4);
                        }
                        distance += decodeNumber(lddTable);
                    } else {
                        distance += getbits50() >>> (16 - dBits);
                        addbits50(dBits);
                    }
                }
                if (distance > 0x100) {
                    length++;
                    if (distance > 0x2000) {
                        length++;
                        if (distance > 0x40000) {
                            length++;
                        }
                    }
                }
                insertOldDist(distance);
                lastLength = length;
                copyString(length, distance);
                continue;
            }

            if (mainSlot == 256) {
                Filter5 filter = new Filter5();
                if (!readFilter(filter) || !addFilter(filter)) break;
                continue;
            }

            if (mainSlot == 257) {
                if (lastLength != 0) copyString(lastLength, oldDist[0]);
                continue;
            }

            // mainSlot 258..261: repeat recent distance
            int distNum = mainSlot - 258;
            long distance = oldDist[distNum];
            System.arraycopy(oldDist, 0, oldDist, 1, distNum);
            oldDist[0] = distance;
            int lengthSlot = decodeNumber(rdTable);
            int length = slotToLength(lengthSlot);
            lastLength = length;
            copyString(length, distance);
        }
        writeBuf();
    }

    // ─── Bit reading ─────────────────────────────────────────────────────────────

    private int getbits50() {
        int bf = ((inBuf50[inAddr50] & 0xFF) << 16)
                | ((inBuf50[inAddr50 + 1] & 0xFF) << 8)
                | (inBuf50[inAddr50 + 2] & 0xFF);
        return (bf >>> (8 - inBit50)) & 0xFFFF;
    }

    private int getbits32() {
        long bf = ((inBuf50[inAddr50] & 0xFFL) << 24)
                | ((inBuf50[inAddr50 + 1] & 0xFFL) << 16)
                | ((inBuf50[inAddr50 + 2] & 0xFFL) << 8)
                | (inBuf50[inAddr50 + 3] & 0xFFL);
        bf = (bf << inBit50) | ((inBuf50[inAddr50 + 4] & 0xFF) >>> (8 - inBit50));
        return (int) bf;
    }

    private void addbits50(int bits) {
        bits += inBit50;
        inAddr50 += bits >>> 3;
        inBit50 = bits & 7;
    }

    // ─── Window arithmetic ────────────────────────────────────────────────────────

    /**
     * Wrap position that underflowed (negative in signed Java int) back into window.
     */
    private int wrapDown(int pos) {
        return pos < 0 ? pos + maxWinSize : pos;
    }

    /**
     * Wrap position that overflowed past end of window back to beginning.
     */
    private int wrapUp(int pos) {
        return pos >= maxWinSize ? pos - maxWinSize : pos;
    }

    // ─── Buffer refill ────────────────────────────────────────────────────────────

    private boolean readBuf() throws IOException, RarException {
        int dataSize = readTop - inAddr50;
        if (dataSize < 0) {
            return false;
        }
        blockHeader.blockSize -= inAddr50 - blockHeader.blockStart;
        if (inAddr50 > INP_BUF_SIZE / 2) {
            if (dataSize > 0) System.arraycopy(inBuf50, inAddr50, inBuf50, 0, dataSize);
            inAddr50 = 0;
            readTop = dataSize;
        } else {
            dataSize = readTop;
        }
        int readCode = 0;
        if (INP_BUF_SIZE != dataSize) {
            readCode = unpIO.unpRead(inBuf50, dataSize, INP_BUF_SIZE - dataSize);
        }
        if (readCode > 0) {
            readTop += readCode;
        }
        readBorder = readTop - 30;
        blockHeader.blockStart = inAddr50;
        if (blockHeader.blockSize != -1) {
            readBorder = Math.min(readBorder, blockHeader.blockStart + blockHeader.blockSize - 1);
        }
        return readCode != -1;
    }

    // ─── Block header ─────────────────────────────────────────────────────────────

    private boolean readBlockHeader() throws IOException, RarException {
        if (inAddr50 > readTop - 7) {
            if (!readBuf()) {
                return false;
            }
        }

        // Byte-align
        addbits50((8 - inBit50) & 7);

        int blockFlags = (getbits50() >>> 8) & 0xFF;
        addbits50(8);
        int byteCount = ((blockFlags >> 3) & 3) + 1;
        if (byteCount == 4) {
            return false;
        }

        blockHeader.blockBitSize = (blockFlags & 7) + 1;

        int savedCheckSum = (getbits50() >>> 8) & 0xFF;
        addbits50(8);

        int blockSize = 0;
        for (int i = 0; i < byteCount; i++) {
            blockSize += ((getbits50() >>> 8) & 0xFF) << (i * 8);
            addbits50(8);
        }

        blockHeader.blockSize = blockSize;
        int checkSum = (0x5a ^ blockFlags ^ blockSize ^ (blockSize >> 8) ^ (blockSize >> 16)) & 0xFF;
        if (checkSum != savedCheckSum) {
            return false;
        }

        blockHeader.blockStart = inAddr50;
        readBorder = Math.min(readBorder, blockHeader.blockStart + blockHeader.blockSize - 1);
        blockHeader.lastBlockInFile = (blockFlags & 0x40) != 0;
        blockHeader.tablePresent = (blockFlags & 0x80) != 0;
        return true;
    }

    // ─── Huffman table reader ─────────────────────────────────────────────────────

    private boolean readTables() throws IOException, RarException {
        if (!blockHeader.tablePresent) {
            return true;
        }

        if (inAddr50 > readTop - 25) {
            if (!readBuf()) {
                return false;
            }
        }

        byte[] bitLength = new byte[BC];
        for (int i = 0; i < BC; i++) {
            int len = (getbits50() >>> 12) & 0xF;
            addbits50(4);
            if (len == 15) {
                int zeroCount = (getbits50() >>> 12) & 0xF;
                addbits50(4);
                if (zeroCount == 0) {
                    bitLength[i] = 15;
                } else {
                    zeroCount += 2;
                    while (zeroCount-- > 0 && i < BC) bitLength[i++] = 0;
                    i--;
                }
            } else {
                bitLength[i] = (byte) len;
            }
        }
        makeDecodeTables(bitLength, 0, bdTable, BC);

        final int tableSize = extraDist ? HUFF_TABLE_SIZEX : HUFF_TABLE_SIZEB;
        byte[] table = new byte[tableSize];
        for (int i = 0; i < tableSize;) {
            if (inAddr50 > readTop - 5) {
                if (!readBuf()) {
                    return false;
                }
            }
            int number = decodeNumber(bdTable);
            if (number < 16) {
                table[i++] = (byte) number;
            } else if (number < 18) {
                int n;
                if (number == 16) {
                    n = (getbits50() >>> 13) + 3;
                    addbits50(3);
                } else {
                    n = (getbits50() >>> 9) + 11;
                    addbits50(7);
                }
                if (i == 0) return false;
                while (n-- > 0 && i < tableSize) {
                    table[i] = table[i - 1];
                    i++;
                }
            } else {
                int n;
                if (number == 18) {
                    n = (getbits50() >>> 13) + 3;
                    addbits50(3);
                } else {
                    n = (getbits50() >>> 9) + 11;
                    addbits50(7);
                }
                while (n-- > 0 && i < tableSize) {
                    table[i++] = 0;
                }
            }
        }

        tablesRead5 = true;
        if (inAddr50 > readTop) {
            return false;
        }

        final int dCodes = extraDist ? DCX : DCB;
        makeDecodeTables(table, 0, ldTable, NC);
        makeDecodeTables(table, NC, ddTable, dCodes);
        makeDecodeTables(table, NC + dCodes, lddTable, LDC);
        makeDecodeTables(table, NC + dCodes + LDC, rdTable, RC);
        return true;
    }

    // ─── Huffman table constructor ────────────────────────────────────────────────

    private void makeDecodeTables(byte[] lengthTable, int tableOffset, DecodeTable dec, int size) {
        dec.maxNum = size;

        int[] lengthCount = new int[16];
        for (int i = 0; i < size; i++) lengthCount[lengthTable[tableOffset + i] & 0xF]++;
        lengthCount[0] = 0;

        dec.decodeNum = new int[size];
        dec.decodePos[0] = 0;
        dec.decodeLen[0] = 0;

        int upperLimit = 0;
        for (int i = 1; i < 16; i++) {
            upperLimit += lengthCount[i];
            dec.decodeLen[i] = upperLimit << (16 - i);
            upperLimit *= 2;
            dec.decodePos[i] = dec.decodePos[i - 1] + lengthCount[i - 1];
        }

        int[] copyDecodePos = dec.decodePos.clone();
        for (int i = 0; i < size; i++) {
            int curBitLength = lengthTable[tableOffset + i] & 0xF;
            if (curBitLength != 0) {
                dec.decodeNum[copyDecodePos[curBitLength]++] = i;
            }
        }

        dec.quickBits = (size == NC) ? MAX_QUICK_DECODE_BITS : Math.max(0, MAX_QUICK_DECODE_BITS - 3);
        int quickDataSize = 1 << dec.quickBits;
        int curBitLength = 1;
        for (int code = 0; code < quickDataSize; code++) {
            int bitField = code << (16 - dec.quickBits);
            while (curBitLength < 16 && bitField >= dec.decodeLen[curBitLength]) curBitLength++;
            dec.quickLen[code] = (byte) curBitLength;
            int dist = bitField - dec.decodeLen[curBitLength - 1];
            dist >>>= (16 - curBitLength);
            if (curBitLength < 16) {
                int pos = dec.decodePos[curBitLength] + dist;
                dec.quickNum[code] = (pos < size) ? dec.decodeNum[pos] : 0;
            } else {
                dec.quickNum[code] = 0;
            }
        }
    }

    // ─── Symbol decoder ───────────────────────────────────────────────────────────

    private int decodeNumber(DecodeTable dec) {
        int bitField = getbits50() & 0xFFFE;
        if (bitField < dec.decodeLen[dec.quickBits]) {
            int code = bitField >>> (16 - dec.quickBits);
            addbits50(dec.quickLen[code] & 0xFF);
            return dec.quickNum[code];
        }
        int bits = 15;
        for (int i = dec.quickBits + 1; i < 15; i++) {
            if (bitField < dec.decodeLen[i]) {
                bits = i;
                break;
            }
        }
        addbits50(bits);
        int dist = bitField - dec.decodeLen[bits - 1];
        dist >>>= (16 - bits);
        int pos = dec.decodePos[bits] + dist;
        if (pos >= dec.maxNum) pos = 0;
        return dec.decodeNum[pos];
    }

    // ─── Length slot decoder ──────────────────────────────────────────────────────

    private int slotToLength(int slot) {
        int lBits;
        int length = 2;
        if (slot < 8) {
            lBits = 0;
            length += slot;
        } else {
            lBits = slot / 4 - 1;
            length += (4 | (slot & 3)) << lBits;
        }
        if (lBits > 0) {
            length += getbits50() >>> (16 - lBits);
            addbits50(lBits);
        }
        return length;
    }

    // ─── LZ helpers ───────────────────────────────────────────────────────────────

    private void insertOldDist(long distance) {
        oldDist[3] = oldDist[2];
        oldDist[2] = oldDist[1];
        oldDist[1] = oldDist[0];
        oldDist[0] = distance;
    }

    private void copyString(int length, long distance) {
        unpPtr50 = copyMatch(window50, maxWinSize, unpPtr50, length, distance, firstWinDone, true);
    }

    // ─── Filter reading ───────────────────────────────────────────────────────────

    private int readFilterData() {
        int byteCount = (getbits50() >>> 14) + 1;
        addbits50(2);
        int data = 0;
        for (int i = 0; i < byteCount; i++) {
            data += ((getbits50() >>> 8) & 0xFF) << (i * 8);
            addbits50(8);
        }
        return data;
    }

    private boolean readFilter(Filter5 filter) throws IOException, RarException {
        if (inAddr50 > readTop - 16) {
            if (!readBuf()) {
                return false;
            }
        }
        filter.blockStart = readFilterData();
        filter.blockLength = readFilterData();
        if (filter.blockLength > MAX_FILTER_BLOCK_SIZE) {
            filter.blockLength = 0;
        }
        filter.type = getbits50() >>> 13;
        addbits50(3);
        if (filter.type == FILTER_DELTA) {
            filter.channels = (getbits50() >>> 11) + 1;
            addbits50(5);
        }
        return true;
    }

    private boolean addFilter(Filter5 filter) throws IOException, RarException {
        if (filters.size() >= MAX_UNPACK_FILTERS) {
            writeBuf();
            if (filters.size() >= MAX_UNPACK_FILTERS) filters.clear();
        }
        filter.nextWindow = wrPtr50 != unpPtr50 && wrapDown(wrPtr50 - unpPtr50) <= filter.blockStart;
        filter.blockStart = (filter.blockStart + unpPtr50) % maxWinSize;
        filters.add(filter);
        return true;
    }

    // ─── Window flush ─────────────────────────────────────────────────────────────

    private void writeBuf() throws IOException, RarException {
        int writtenBorder = wrPtr50;
        int fullWriteSize = wrapDown(unpPtr50 - writtenBorder);
        int writeSizeLeft = fullWriteSize;
        boolean notAllFiltersProcessed = false;

        for (int i = 0; i < filters.size(); i++) {
            Filter5 flt = filters.get(i);
            if (flt.type == FILTER_NONE) continue;
            if (flt.nextWindow) {
                if (wrapDown(flt.blockStart - wrPtr50) <= fullWriteSize) {
                    flt.nextWindow = false;
                }
                continue;
            }
            int blockStart = flt.blockStart;
            int blockLength = flt.blockLength;
            if (wrapDown(blockStart - writtenBorder) < writeSizeLeft) {
                if (writtenBorder != blockStart) {
                    writeArea(writtenBorder, blockStart);
                    writtenBorder = blockStart;
                    writeSizeLeft = wrapDown(unpPtr50 - writtenBorder);
                }
                if (blockLength <= writeSizeLeft) {
                    if (blockLength > 0) {
                        int blockEnd = wrapUp(blockStart + blockLength);
                        if (filterSrcMemory.length < blockLength) filterSrcMemory = new byte[blockLength];
                        if (blockStart < blockEnd || blockEnd == 0) {
                            System.arraycopy(window50, blockStart, filterSrcMemory, 0, blockLength);
                        } else {
                            int firstPart = maxWinSize - blockStart;
                            System.arraycopy(window50, blockStart, filterSrcMemory, 0, firstPart);
                            System.arraycopy(window50, 0, filterSrcMemory, firstPart, blockEnd);
                        }
                        byte[] outMem = applyFilter(filterSrcMemory, blockLength, flt);
                        flt.type = FILTER_NONE;
                        if (outMem != null) {
                            unpIO.unpWrite(outMem, 0, blockLength);
                        }
                        writtenFileSize += blockLength;
                        writtenBorder = blockEnd;
                        writeSizeLeft = wrapDown(unpPtr50 - writtenBorder);
                    }
                } else {
                    wrPtr50 = writtenBorder;
                    for (int j = i; j < filters.size(); j++) {
                        Filter5 f = filters.get(j);
                        if (f.type != FILTER_NONE) {
                            f.nextWindow = false;
                        }
                    }
                    notAllFiltersProcessed = true;
                    break;
                }
            }
        }
        filters.removeIf(f -> f.type == FILTER_NONE);

        if (!notAllFiltersProcessed) {
            writeArea(writtenBorder, unpPtr50);
            wrPtr50 = unpPtr50;
        }

        writeBorder = wrapUp(unpPtr50 + Math.min(maxWinSize, UNPACK_MAX_WRITE));
        if (writeBorder == unpPtr50
                || wrPtr50 != unpPtr50 && wrapDown(wrPtr50 - unpPtr50) < wrapDown(writeBorder - unpPtr50)) {
            writeBorder = wrPtr50;
        }
    }

    private void writeArea(int startPtr, int endPtr) throws IOException, RarException {
        if (endPtr < startPtr) {
            writeData(window50, startPtr, maxWinSize - startPtr);
            writeData(window50, 0, endPtr);
        } else if (endPtr > startPtr) {
            writeData(window50, startPtr, endPtr - startPtr);
        }
    }

    private void writeData(byte[] data, int offset, int size) throws IOException {
        if (writtenFileSize >= destUnpSize) {
            return;
        }
        long leftToWrite = destUnpSize - writtenFileSize;
        int writeSize = (int) Math.min(size, leftToWrite);
        if (writeSize > 0) unpIO.unpWrite(data, offset, writeSize);
        writtenFileSize += size;
    }

    // ─── Filter application ───────────────────────────────────────────────────────

    private byte[] applyFilter(byte[] data, int dataSize, Filter5 flt) {
        switch (flt.type) {
            case FILTER_E8:
            case FILTER_E8E9: {
                int fileOffset = (int) writtenFileSize;
                final int fileSize = 0x1000000;
                byte cmpByte2 = (byte) (flt.type == FILTER_E8E9 ? 0xe9 : 0xe8);
                for (int curPos = 0; curPos + 4 < dataSize;) {
                    byte curByte = data[curPos++];
                    if (curByte == (byte) 0xe8 || curByte == cmpByte2) {
                        int offset = (curPos + fileOffset) % fileSize;
                        int addr = Raw.readIntLittleEndian(data, curPos);
                        if ((addr & 0x80000000) != 0) {
                            if (((addr + offset) & 0x80000000) == 0) {
                                int newAddr = addr + fileSize;
                                Raw.writeIntLittleEndian(data, curPos, newAddr);
                            }
                        } else {
                            if (((addr - fileSize) & 0x80000000) != 0) {
                                int newAddr = addr - offset;
                                Raw.writeIntLittleEndian(data, curPos, newAddr);
                            }
                        }
                        curPos += 4;
                    }
                }
                return data;
            }
            case FILTER_ARM: {
                int fileOffset = (int) writtenFileSize;
                for (int curPos = 0; curPos + 3 < dataSize; curPos += 4) {
                    if ((data[curPos + 3] & 0xFF) == 0xEB) {
                        int offset = (data[curPos] & 0xFF)
                                | ((data[curPos + 1] & 0xFF) << 8)
                                | ((data[curPos + 2] & 0xFF) << 16);
                        offset -= (fileOffset + curPos) / 4;
                        data[curPos] = (byte) offset;
                        data[curPos + 1] = (byte) (offset >> 8);
                        data[curPos + 2] = (byte) (offset >> 16);
                    }
                }
                return data;
            }
            case FILTER_DELTA: {
                int channels = flt.channels;
                if (filterDstMemory.length < dataSize) filterDstMemory = new byte[dataSize];
                int srcPos = 0;
                for (int ch = 0; ch < channels; ch++) {
                    byte prev = 0;
                    for (int destPos = ch; destPos < dataSize; destPos += channels) {
                        filterDstMemory[destPos] = prev -= data[srcPos++];
                    }
                }
                return filterDstMemory;
            }
            default:
                return null;
        }
    }
}
