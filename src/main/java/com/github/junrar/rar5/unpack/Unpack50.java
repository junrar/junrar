package com.github.junrar.rar5.unpack;

import com.github.junrar.unpack.decode.Decode;
import com.github.junrar.unpack.vm.BitInput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * RAR5 decompressor (algorithm version 5.0).
 *
 * <p>Implements LZSS + Huffman decompression with optional post-processing filters.
 * Based on the C++ unpack50.cpp implementation.
 * Uses shared BitInput and Decode classes from the RAR4 infrastructure.
 */
public final class Unpack50 {

    /** RAR5 alphabet sizes */
    private static final int NC = 306;
    private static final int DCB = 64;
    private static final int DCX = 80;
    private static final int LDC = 16;
    private static final int RC = 44;

    /** RAR5 slot definitions */
    private static final int SLOT_FILTER = 256;
    private static final int SLOT_FULL_REPEAT = 257;
    private static final int SLOT_OLD_DIST_START = 258;
    private static final int SLOT_OLD_DIST_END = 261;
    private static final int SLOT_LZ_MATCH_START = 262;
    private static final int OLD_DIST_COUNT = 4;

    /** Maximum match length increment */
    private static final int MAX_INC_LZ_MATCH = 4100;

    /** Sliding window buffer */
    private byte[] window;

    /** Maximum window size */
    private int maxWinSize;

    /** Current write position in the window */
    private int unpPtr;

    /** Last written (flushed) position */
    private int wrPtr;

    /** Threshold that triggers a flush */
    private int writeBorder;

    /** Bit-level input (shared with RAR4) */
    private final BitInput input;

    /** Block header */
    private final UnpackBlockHeader blockHeader;

    /** Block Huffman tables */
    private UnpackBlockTables blockTables;

    /** Cached old distances (move-to-front) */
    private final int[] oldDist = new int[OLD_DIST_COUNT];

    /** Last match length (for full repeat) */
    private int lastLength;

    /** Filter queue */
    private final java.util.List<UnpackFilter> filters = new java.util.ArrayList<>();

    /** Output stream */
    private OutputStream outputStream;

    /** Total bytes written */
    private long writtenFileSize;

    /** Expected unpacked size (0 if unknown) */
    private long expectedSize;

    /** Whether the first window has been written (for solid archives) */
    private boolean firstWindowDone;

    /** Suspended state (for DLL mode, not used here) */
    private boolean suspended;

    /** Whether decompression is complete */
    private boolean done;

    /** Whether to use extended distance codes (RAR 7.0) */
    private boolean extraDist;

    /**
     * Creates a new RAR5 decompressor.
     */
    public Unpack50() {
        this.input = new BitInput();
        this.blockHeader = new UnpackBlockHeader();
        this.blockTables = new UnpackBlockTables();
        this.maxWinSize = 0x400000; // 4 MB default
        this.window = new byte[maxWinSize];
        resetState(false);
    }

    /**
     * Resets decompressor state.
     *
     * @param solid true if in solid mode (preserve state)
     */
    public void resetState(final boolean solid) {
        if (!solid) {
            unpPtr = 0;
            wrPtr = 0;
            writeBorder = 0;
            lastLength = 0;
            firstWindowDone = false;
            for (int i = 0; i < OLD_DIST_COUNT; i++) {
                oldDist[i] = -1;
            }
            filters.clear();
            writtenFileSize = 0;
            done = false;
        }
    }

    /**
     * Sets the maximum window size for the LZSS sliding window.
     *
     * <p>RAR5 supports dictionary sizes up to 1 TB, but this is capped at
     * {@code Integer.MAX_VALUE - 8} (~2 GB) due to Java's {@code byte[]}
     * array size limit. This is sufficient for virtually all real-world
     * archives; the previous arbitrary 4 MB cap has been removed.
     *
     * @param size the window size in bytes, capped at ~2 GB
     */
    public void setMaxWinSize(final long size) {
        final int winSize = (int) Math.min(size, Integer.MAX_VALUE - 8);
        if (winSize > this.maxWinSize) {
            this.maxWinSize = winSize;
            this.window = new byte[winSize];
        }
    }

    /**
     * Sets the expected unpacked size.
     *
     * @param size the expected size, or 0 if unknown
     */
    public void setExpectedSize(final long size) {
        this.expectedSize = size;
    }

    /**
     * Sets the output stream.
     *
     * @param out the output stream
     */
    public void setOutputStream(final OutputStream out) {
        this.outputStream = out;
    }

    /**
     * @return total bytes written so far
     */
    public long getWrittenFileSize() {
        return writtenFileSize;
    }

    /**
     * @return true if decompression is complete
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Decompresses data from the input stream to the output stream.
     *
     * @param inputStream the compressed data input
     * @throws IOException if an I/O error occurs
     */
    public void doUnpack(final InputStream inputStream) throws IOException {
        // Initial buffer load
        final int initialRead = input.readIntoBuffer(inputStream);
        if (initialRead <= 0) {
            done = true;
            return;
        }

        // Read first block header
        if (!blockHeader.read(input)) {
            done = true;
            return;
        }

        // Read Huffman tables if present
        if (blockHeader.isTablePresent()) {
            if (!blockTables.readTables(input, extraDist)) {
                done = true;
                return;
            }
        }

        // Main decompression loop
        while (!done) {
            // Check if we've reached the expected file size
            final long pendingData = wrapDown(unpPtr - wrPtr);
            if (expectedSize > 0 && writtenFileSize + pendingData >= expectedSize) {
                done = true;
                break;
            }

            // Check for block boundary
            if (isAtBlockEnd()) {
                if (blockHeader.isLastBlockInFile()) {
                    done = true;
                    break;
                }
                // Read next block header
                if (!blockHeader.read(input)) {
                    done = true;
                    break;
                }
                if (blockHeader.isTablePresent()) {
                    if (!blockTables.readTables(input, extraDist)) {
                        done = true;
                        break;
                    }
                }
            }

            // Check write border
            if (wrapDown(writeBorder - unpPtr) <= MAX_INC_LZ_MATCH && writeBorder != unpPtr) {
                unpWriteBuf();
                if (expectedSize > 0 && writtenFileSize > expectedSize) {
                    return;
                }
                if (suspended) {
                    return;
                }
            }

            // Check if we need more input data
            if (input.Overflow(5)) {
                if (input.fillBuffer(inputStream) < 0) {
                    done = true;
                    break;
                }
            }

            // Decode main slot
            final int mainSlot = decodeNumber(input, blockTables.getLd());
            decodeSlot(mainSlot);
        }

        // Final flush
        unpWriteBuf();
    }

    /**
     * Checks if the bit cursor has reached the end of the current block.
     */
    private boolean isAtBlockEnd() {
        final int blockEnd = blockHeader.getBlockStart() + blockHeader.getBlockSize() - 1;
        return input.getInAddr() > blockEnd
                || (input.getInAddr() == blockEnd
                    && input.getInBit() >= blockHeader.getBlockBitSize());
    }

    /**
     * Decodes a single Huffman slot and performs the corresponding action.
     * Based on the C++ DoUnpack loop body.
     */
    private void decodeSlot(final int mainSlot) throws IOException {
        if (mainSlot < 256) {
            // Literal byte
            window[unpPtr++] = (byte) mainSlot;
            return;
        }

        if (mainSlot >= SLOT_LZ_MATCH_START) {
            // LZ match
            final int length = slotToLength(input, mainSlot - SLOT_LZ_MATCH_START);

            long distance = 1;
            final int distSlot = decodeNumber(input, blockTables.getDd());
            int dBits;
            if (distSlot < 4) {
                dBits = 0;
                distance += distSlot;
            } else {
                dBits = distSlot / 2 - 1;
                distance += (long) (2 | (distSlot & 1)) << dBits;
            }

            if (dBits > 0) {
                if (dBits >= 4) {
                    if (dBits > 4) {
                        if (dBits > 36) {
                            final long rawBits = input.getbits64();
                            final long extraBits = (rawBits >>> (68 - dBits)) << 4;
                            input.addbits(dBits - 4);
                            distance += extraBits;
                        } else {
                            final long rawBits = input.getbits32();
                            final long extraBits = (rawBits >>> (36 - dBits)) << 4;
                            input.addbits(dBits - 4);
                            distance += extraBits;
                        }
                    }
                    final int lowDist = decodeNumber(input, blockTables.getLdd());
                    distance += lowDist;
                } else {
                    distance += input.getbits() >>> (16 - dBits);
                    input.addbits(dBits);
                }
            }

            int adjustedLength = length;
            if (distance > 0x100) {
                adjustedLength++;
                if (distance > 0x2000) {
                    adjustedLength++;
                    if (distance > 0x40000) {
                        adjustedLength++;
                    }
                }
            }

            insertOldDist((int) distance);
            lastLength = adjustedLength;
            copyString(adjustedLength, (int) distance);
            return;
        }

        if (mainSlot == SLOT_FILTER) {
            readFilter();
            return;
        }

        if (mainSlot == SLOT_FULL_REPEAT) {
            copyString(lastLength, oldDist[0]);
            return;
        }

        if (mainSlot >= SLOT_OLD_DIST_START && mainSlot <= SLOT_OLD_DIST_END) {
            // Repeat with old distance
            final int distNum = mainSlot - SLOT_OLD_DIST_START;
            final int distance = oldDist[distNum];

            // Move-to-front update
            for (int i = distNum; i > 0; i--) {
                oldDist[i] = oldDist[i - 1];
            }
            oldDist[0] = distance;

            // Decode length from RD table
            final int lengthSlot = decodeNumber(input, blockTables.getRd());
            final int length = slotToLength(input, lengthSlot);
            copyString(length, distance);
            return;
        }
    }

    /**
     * Decodes a Huffman symbol using the given Decode table.
     * Matches the C++ DecodeNumber implementation exactly.
     */
    private int decodeNumber(final BitInput inp, final Decode dec) {
        // Left aligned 15 bit length raw bit field
        final int bitField = inp.getbits() & 0xfffe;

        if (dec.getQuickBits() > 0 && bitField < dec.getDecodeLen()[dec.getQuickBits()]) {
            final int code = bitField >>> (16 - dec.getQuickBits());
            inp.addbits(dec.getQuickLen()[code]);
            return dec.getQuickNum()[code];
        }

        // Detect the real bit length for current code
        int bits = 15;
        for (int i = dec.getQuickBits() + 1; i < 15; i++) {
            if (bitField < dec.getDecodeLen()[i]) {
                bits = i;
                break;
            }
        }

        inp.addbits(bits);

        // Calculate the distance from the start code for current bit length
        int dist = bitField - dec.getDecodeLen()[bits - 1];

        // Start codes are left aligned, but we need the normal right aligned number
        dist >>= (16 - bits);

        // Now we can calculate the position in the code list
        final int pos = dec.getDecodePos()[bits] + dist;
        if (pos < 0 || pos >= dec.getDecodeNum().length) {
            return 0;
        }
        return dec.getDecodeNum()[pos];
    }

    /**
     * Converts a length slot to an actual length value.
     * Matches the C++ SlotToLength implementation exactly.
     */
    private int slotToLength(final BitInput inp, int slot) {
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
            length += inp.getbits() >>> (16 - lBits);
            inp.addbits(lBits);
        }

        return length;
    }

    /**
     * Inserts a distance into the old distance cache (move-to-front).
     */
    private void insertOldDist(final int distance) {
        oldDist[3] = oldDist[2];
        oldDist[2] = oldDist[1];
        oldDist[1] = oldDist[0];
        oldDist[0] = distance;
    }

    /**
     * Reads a filter specification from the bit stream.
     */
    private void readFilter() {
        final UnpackFilter filter = new UnpackFilter();

        // Read block start (variable length: 1-4 bytes)
        filter.setBlockStart(readFilterData());

        // Read block length (variable length: 1-4 bytes)
        long blockLengthLong = readFilterData();
        int blockLength = blockLengthLong > 0x10000 ? 0x10000 : (int) blockLengthLong;
        filter.setBlockLength(blockLength);

        // Read filter type (3 bits)
        if (!input.hasBytes(1)) {
            return;
        }
        final int type = input.getbits() >>> 13;
        input.addbits(3);
        filter.setType(type);

        // Read channels for DELTA filter
        if (type == UnpackFilter.TYPE_DELTA) {
            if (!input.hasBytes(1)) {
                return;
            }
            filter.setChannels((input.getbits() >>> 11) + 1);
            input.addbits(5);
        }

        // Check if filter belongs to next window
        // Convert relative blockStart to absolute window position (matching C++ AddFilter)
        long absBlockStart = filter.getBlockStart() + unpPtr;
        filter.setNextWindow(absBlockStart >= maxWinSize);
        filter.setBlockStart((int) (absBlockStart % maxWinSize));

        filters.add(filter);
    }

    /**
     * Reads a variable-length filter data value (1-4 bytes, LSB-first).
     */
    private long readFilterData() {
        if (!input.hasBytes(1)) {
            return 0;
        }
        final int byteCount = (input.getbits() >>> 14) + 1;
        input.addbits(2);

        long value = 0;
        for (int i = 0; i < byteCount; i++) {
            if (!input.hasBytes(1)) {
                break;
            }
            value |= (long) (input.getbits() >>> 8) << (i * 8);
            input.addbits(8);
        }

        return value;
    }

    /**
     * Copies a string from the dictionary to the current position.
     * Uses three-path optimization (matching RAR4 copyString):
     * 1. Distance-1: Arrays.fill for run-length encoding
     * 2. Non-overlapping: System.arraycopy for bulk copies
     * 3. Overlapping: byte-by-byte fallback
     */
    private void copyString(final int length, final int distance) {
        if (distance <= 0) {
            return;
        }

        int destPtr = unpPtr - distance;

        // Wrap negative destination pointers within the window
        if (destPtr < 0) {
            destPtr += maxWinSize;
        }

        // Check if we're safely away from window boundaries
        if (destPtr >= 0 && destPtr < maxWinSize - MAX_INC_LZ_MATCH
                && unpPtr < maxWinSize - MAX_INC_LZ_MATCH) {
            if (distance == 1) {
                // Path 1: distance == 1 — run-length encoding
                java.util.Arrays.fill(window, unpPtr, unpPtr + length, window[destPtr]);
                unpPtr += length;
            } else if (destPtr + length <= unpPtr) {
                // Path 2: non-overlapping — bulk copy
                System.arraycopy(window, destPtr, window, unpPtr, length);
                unpPtr += length;
            } else {
                // Path 3: overlapping — byte-by-byte
                for (int i = 0; i < length; i++) {
                    window[unpPtr++] = window[destPtr++];
                }
            }
        } else {
            // Boundary case: wrap around the window
            for (int i = 0; i < length; i++) {
                window[unpPtr] = window[destPtr];
                unpPtr = wrapUp(unpPtr + 1);
                destPtr = wrapUp(destPtr + 1);
            }
        }
    }

    /**
     * Flushes decompressed data through filters to the output stream.
     */
    private void unpWriteBuf() throws IOException {
        if (outputStream == null || wrPtr == unpPtr) {
            return;
        }

        // Apply filters
        for (final UnpackFilter filter : filters) {
            if (filter.isNextWindow()) {
                continue;
            }

            final int filterStart = (int) filter.getBlockStart();
            final int filterEnd = wrapUp(filterStart + filter.getBlockLength());

            // Check if filter block is within the current write range
            final int writeStart = wrPtr;
            final int writeEnd = unpPtr;

            if (isInRange(filterStart, filterEnd, writeStart, writeEnd)) {
                applyFilter(filter, filterStart, filter.getBlockLength());
            }
        }

        // Write data to output
        int writeSize;
        if (unpPtr >= wrPtr) {
            writeSize = unpPtr - wrPtr;
        } else {
            writeSize = maxWinSize - wrPtr;
        }

        if (writeSize > 0) {
            // Truncate to expected size (matching C++ UnpWriteData)
            if (expectedSize > 0) {
                final long leftToWrite = expectedSize - writtenFileSize;
                if (writeSize > leftToWrite) {
                    writeSize = (int) leftToWrite;
                }
            }
            if (writeSize > 0) {
                outputStream.write(window, wrPtr, writeSize);
                writtenFileSize += writeSize;
            }
            wrPtr = wrapUp(wrPtr + writeSize);
            firstWindowDone = true;
            // Update writeBorder to control next flush (matching C++ UnpWriteBuf)
            writeBorder = wrapUp(unpPtr + Math.min(maxWinSize, 0x100000));
        }
    }

    /**
     * Checks if a filter block overlaps with the current write range.
     */
    private boolean isInRange(final int filterStart, final int filterEnd,
                               final int writeStart, final int writeEnd) {
        if (writeEnd >= writeStart) {
            return filterStart < writeEnd && filterEnd > writeStart;
        } else {
            return filterStart < writeEnd || filterEnd > writeStart;
        }
    }

    /**
     * Applies a filter transformation to the given data range.
     */
    private void applyFilter(final UnpackFilter filter, final int start, final int length) {
        final int dataSize = Math.min(length, 0x10000);
        final byte[] data = new byte[dataSize];

        // Copy data from window
        int src = start;
        for (int i = 0; i < dataSize; i++) {
            data[i] = window[src];
            src = wrapUp(src + 1);
        }

        // Apply filter
        switch (filter.getType()) {
            case UnpackFilter.TYPE_DELTA:
                applyDeltaFilter(data, dataSize, filter.getChannels());
                break;
            case UnpackFilter.TYPE_E8:
                applyE8Filter(data, dataSize, (int) writtenFileSize);
                break;
            case UnpackFilter.TYPE_E8E9:
                applyE8E9Filter(data, dataSize, (int) writtenFileSize);
                break;
            case UnpackFilter.TYPE_ARM:
                applyARMFilter(data, dataSize, (int) writtenFileSize);
                break;
            default:
                return;
        }

        // Copy filtered data back to window
        int dst = start;
        for (int i = 0; i < dataSize; i++) {
            window[dst] = data[i];
            dst = wrapUp(dst + 1);
        }
    }

    /**
     * Applies the DELTA filter (de-interleave channels and delta decode).
     */
    private void applyDeltaFilter(final byte[] data, final int dataSize, final int channels) {
        final byte[] dst = new byte[dataSize];
        int srcPos = 0;

        for (int curChannel = 0; curChannel < channels; curChannel++) {
            byte prevByte = 0;
            for (int destPos = curChannel; destPos < dataSize; destPos += channels) {
                prevByte -= data[srcPos++];
                dst[destPos] = prevByte;
            }
        }

        System.arraycopy(dst, 0, data, 0, dataSize);
    }

    /**
     * Applies the E8 filter (x86 CALL relative-to-absolute conversion).
     */
    private void applyE8Filter(final byte[] data, final int dataSize, final int fileOffset) {
        applyE8E9Filter(data, dataSize, fileOffset, (byte) 0);
    }

    /**
     * Applies the E8E9 filter (x86 CALL + JMP conversion).
     */
    private void applyE8E9Filter(final byte[] data, final int dataSize, final int fileOffset) {
        applyE8E9Filter(data, dataSize, fileOffset, (byte) 0xE9);
    }

    private void applyE8E9Filter(final byte[] data, final int dataSize,
                                  final int fileOffset, final byte cmpByte2) {
        int curPos = 0;
        while (curPos + 4 < dataSize) {
            final byte curByte = data[curPos];
            curPos++;

            if (curByte == (byte) 0xE8 || curByte == cmpByte2) {
                final int offset = (curPos + fileOffset) % 0x1000000;
                int addr = (data[curPos] & 0xFF) | ((data[curPos + 1] & 0xFF) << 8)
                         | ((data[curPos + 2] & 0xFF) << 16) | (data[curPos + 3] << 24);

                if (addr < 0) {
                    if (addr + offset >= 0) {
                        addr += 0x1000000;
                    }
                } else {
                    if (addr < 0x1000000) {
                        addr -= offset;
                    }
                }

                data[curPos] = (byte) (addr & 0xFF);
                data[curPos + 1] = (byte) ((addr >>> 8) & 0xFF);
                data[curPos + 2] = (byte) ((addr >>> 16) & 0xFF);
                data[curPos + 3] = (byte) (addr >>> 24);
                curPos += 4;
            }
        }
    }

    /**
     * Applies the ARM filter (ARM BL instruction conversion).
     */
    private void applyARMFilter(final byte[] data, final int dataSize, final int fileOffset) {
        for (int curPos = 0; curPos + 3 < dataSize; curPos += 4) {
            if (data[curPos + 3] == (byte) 0xEB) {
                final int offset = (data[curPos] & 0xFF) | ((data[curPos + 1] & 0xFF) << 8)
                                 | ((data[curPos + 2] & 0xFF) << 16);
                final int adjustedOffset = offset - (fileOffset + curPos) / 4;
                data[curPos] = (byte) (adjustedOffset & 0xFF);
                data[curPos + 1] = (byte) ((adjustedOffset >>> 8) & 0xFF);
                data[curPos + 2] = (byte) ((adjustedOffset >>> 16) & 0xFF);
            }
        }
    }

    /**
     * Wraps a position up (forward) within the window.
     */
    private int wrapUp(final int pos) {
        return pos >= maxWinSize ? pos - maxWinSize : pos;
    }

    /**
     * Wraps a position down (backward) within the window.
     * Handles negative values by adding maxWinSize.
     */
    private int wrapDown(final int pos) {
        if (pos < 0) {
            return pos + maxWinSize;
        }
        return pos >= maxWinSize ? pos - maxWinSize : pos;
    }

    /**
     * @return true if extended distance codes are used
     */
    public boolean isExtraDist() {
        return extraDist;
    }

    /**
     * Sets whether extended distance codes are used (RAR 7.0).
     *
     * @param extraDist true for extended distance codes
     */
    public void setExtraDist(final boolean extraDist) {
        this.extraDist = extraDist;
    }
}
