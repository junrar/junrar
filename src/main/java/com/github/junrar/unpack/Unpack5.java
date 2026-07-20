/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.unpack.decode.Compress;
import com.github.junrar.unpack.decode.Decode5;

import java.util.Arrays;

/**
 * RAR5 (unpack method 0x50) decode engine — <b>skeleton</b> (issue #27, M3.6). Built as a
 * <em>sibling</em> of the RAR3 {@code BitInput -> Unpack15 -> Unpack20 -> Unpack} chain, not an
 * extension of it (manual &sect;4.1/&sect;5.2): RAR5 windows are per-archive and dynamic and the
 * RAR3 {@code MAXWINMASK}/{@code int}-index idioms break, so chain extension is wrong by
 * construction. This chunk lands the window, bit input, block-header read, and the 5 Huffman
 * tables; the decode loop, {@code UnpWriteBuf}, and archive lifecycle wiring land in M3.7. The V5
 * extraction gate stays in place until M3.11, so nothing here is reachable from public extraction.
 *
 * <p><b>Dictionary resource model (plan &sect;6 R1, review B-S3 — three separated concepts).</b>
 * <ol>
 *   <li><b>Engine capability ceiling</b> — what the flat {@code byte[]} window can address: 1 GB
 *       at M3 ({@link #CAPABILITY_MAX_WIN_SIZE}); larger throws {@link
 *       UnsupportedDictionarySizeException}. Raised to 64 GB only when the M4.3 segmented window
 *       lands.</li>
 *   <li><b>Caller resource limit</b> — {@code ArchiveOptions.maxDictionarySize} (P0.8; default
 *       4 GiB = unrar's {@code WinSizeLimit}). Checked <em>before</em> any window allocation.</li>
 *   <li><b>Allocation policy</b> — growth-capped, never header-eager (see {@link Unpack5Window}).</li>
 * </ol>
 *
 * <p>C++ refs: {@code 8f437ab:unpack.cpp:80-158} (Init window rules), {@code 8f437ab:unpack50.cpp}
 * (block header + table read), {@code 8f437ab:unpack.cpp:227} (MakeDecodeTables),
 * {@code 8f437ab:unpackinline.cpp} (DecodeNumber), {@code 8f437ab:getbits.hpp} (bit input). Every
 * {@code >>} on a value that is unsigned in C++ is Java {@code >>>} (no-go C15).
 */
public class Unpack5 {

    /** Engine capability ceiling at M3: the largest flat {@code byte[]} window we can address (1 GB). */
    public static final long CAPABILITY_MAX_WIN_SIZE = 1L << 30;

    private final ComprDataIO unpIO;
    private final boolean extraDist;

    private Unpack5Window window;

    // Bit input (own RAR5 reader; the RAR3 vm/BitInput has no getbits32 and the sibling rule keeps
    // this off the RAR3 base class). Mirrors 8f437ab:getbits.hpp.
    private byte[] inBuf = new byte[0];
    private int inAddr;
    private int inBit;
    private int readTop;
    private int readBorder;

    // Block header (8f437ab:unpack.hpp struct UnpackBlockHeader).
    private int blockSize;
    private int blockBitSize;
    private int blockStart;
    private int headerSize;
    private boolean lastBlockInFile;
    private boolean tablePresent;

    // The 5 Huffman tables (struct UnpackBlockTables).
    private final Decode5 ld = new Decode5(Compress.NC5);   // literals / lengths
    private final Decode5 dd = new Decode5(Compress.DC5);   // distances
    private final Decode5 ldd = new Decode5(Compress.LDC5); // low distance bits
    private final Decode5 rd = new Decode5(Compress.RC5);   // repeat distances
    private final Decode5 bd = new Decode5(Compress.BC5);   // bit lengths (table decode)

    private boolean tablesRead5;

    /**
     * @param unpIO     packed-byte pump + CRC/hash seam (manual &sect;4.12); the stream refill path
     *                  that consumes it lands with the M3.7 decode loop.
     * @param extraDist RAR7 (method 0x70) selects the 80-slot distance alphabet with this flag; the
     *                  same engine serves RAR5 and RAR7 (manual &sect;5.2). Fixed {@code false}
     *                  until M4 — designed in from day one so RAR7 is a flag, not a fork.
     */
    public Unpack5(final ComprDataIO unpIO, final boolean extraDist) {
        this.unpIO = unpIO;
        this.extraDist = extraDist;
    }

    /** Test/standalone constructor with no IO channel (M3.6 drives crafted external buffers). */
    Unpack5(final boolean extraDist) {
        this(null, extraDist);
    }

    public boolean isExtraDist() {
        return extraDist;
    }

    /**
     * Size and allocate the window for a file, applying the dictionary resource model before any
     * allocation. Delegates the budget to {@link ComprDataIO#getMaxDictionarySize()}.
     *
     * @param declaredWinSize the header-declared window size in bytes ({@code 0x20000 << bits})
     * @param solid           whether this entry continues a solid stream
     */
    public void init(final long declaredWinSize, final boolean solid) throws RarException {
        init(declaredWinSize, solid, unpIO.getMaxDictionarySize());
    }

    /**
     * Window sizing with an explicit budget (test seam). Mirrors {@code Unpack::Init(WinSize,Solid)}
     * ({@code 8f437ab:unpack.cpp:73-158}) for the parts that matter to a flat-array port: the
     * min-alloc floor and the solid no-shrink rule; the resource-model guards replace unrar's
     * {@code CheckWinLimit}.
     */
    void init(final long declaredWinSize, final boolean solid, final long maxDictionarySize)
            throws RarException {
        // Min-alloc floor: a sub-0x40000 dictionary is bumped up (unrar MinAllocSize, unpack.cpp:85).
        long winSize = declaredWinSize < Unpack5Window.MIN_ALLOC ? Unpack5Window.MIN_ALLOC : declaredWinSize;

        // Caller resource limit — BEFORE any window allocation (the -mdx / CheckWinLimit analog).
        if (winSize > maxDictionarySize) {
            throw new UnsupportedDictionarySizeException(
                "RAR5 dictionary " + winSize + " exceeds the configured maxDictionarySize " + maxDictionarySize);
        }
        // Engine capability ceiling — a flat byte[] cannot address beyond 1 GB at M3.
        if (winSize > CAPABILITY_MAX_WIN_SIZE) {
            throw new UnsupportedDictionarySizeException(
                "RAR5 dictionary " + winSize + " exceeds the M3 engine capability " + CAPABILITY_MAX_WIN_SIZE);
        }

        if (window != null && winSize <= window.size()) {
            // Reuse: solid streams never shrink, and a non-solid file needing no more room keeps the
            // larger buffer (unrar: if (WinSize<=MaxWinSize) return).
            return;
        }
        if (window != null && solid) {
            // Archiving guarantees a solid window never grows mid-set; a larger claim is malformed.
            throw new UnsupportedDictionarySizeException("solid RAR5 window must not grow mid-set");
        }
        window = new Unpack5Window((int) winSize);
        tablesRead5 = false;
    }

    /** @return the window, or {@code null} before {@link #init} (also after a rejected init). */
    public Unpack5Window window() {
        return window;
    }

    // ---- bit input ---------------------------------------------------------------------------

    /**
     * Load a whole packed stream as the input buffer (external-buffer mode). M3.6 has no stream
     * refill; {@link #unpReadBuf()} returns {@code false}, so the read-border guards fail closed on
     * truncation. The array is over-allocated so {@code getbits}/{@code getbits32} may over-read a
     * few bytes past {@code readTop} without an index fault.
     */
    void feed(final byte[] packed) {
        feed(packed, packed.length);
    }

    void feed(final byte[] packed, final int readTop) {
        this.inBuf = Arrays.copyOf(packed, Math.max(packed.length, readTop) + 64);
        this.readTop = readTop;
        this.readBorder = readTop - 30;
        this.inAddr = 0;
        this.inBit = 0;
    }

    /** ponytail: M3.6 skeleton has no ComprDataIO refill; that lands with the M3.7 decode loop. */
    private boolean unpReadBuf() {
        return false;
    }

    /** Ensure at least {@code need} bytes are readable ahead, else try to refill. */
    private boolean ensure(final int need) {
        return inAddr <= readTop - need || unpReadBuf();
    }

    /** 16 bits from the current position, highest bit first (8f437ab:getbits.hpp getbits). */
    int getbits() {
        final int v = ((inBuf[inAddr] & 0xff) << 16)
            | ((inBuf[inAddr + 1] & 0xff) << 8)
            | (inBuf[inAddr + 2] & 0xff);
        return (v >>> (8 - inBit)) & 0xffff;
    }

    /** 32 bits from the current position, highest bit first (8f437ab:getbits.hpp getbits32). */
    int getbits32() {
        int v = ((inBuf[inAddr] & 0xff) << 24)
            | ((inBuf[inAddr + 1] & 0xff) << 16)
            | ((inBuf[inAddr + 2] & 0xff) << 8)
            | (inBuf[inAddr + 3] & 0xff);
        v <<= inBit;
        v |= (inBuf[inAddr + 4] & 0xff) >>> (8 - inBit);
        return v;
    }

    void addbits(final int bits) {
        final int total = bits + inBit;
        inAddr += total >>> 3;
        inBit = total & 7;
    }

    private int fgetbits() {
        return getbits();
    }

    private void faddbits(final int bits) {
        addbits(bits);
    }

    // ---- block header (8f437ab:unpack50.cpp ReadBlockHeader) ----------------------------------

    /**
     * Read one RAR5 compressed block header (flags, block size, checksum). Returns {@code false} on
     * a bad checksum, a 4-byte size count (reserved), or truncation.
     */
    boolean readBlockHeader() {
        headerSize = 0;
        if (!ensure(7)) {
            return false;
        }
        faddbits((8 - inBit) & 7); // align to a byte boundary

        final int blockFlags = fgetbits() >>> 8;
        faddbits(8);
        final int byteCount = ((blockFlags >>> 3) & 3) + 1; // block-size byte count
        if (byteCount == 4) {
            return false;
        }
        headerSize = 2 + byteCount;
        blockBitSize = (blockFlags & 7) + 1;

        final int savedCheckSum = fgetbits() >>> 8;
        faddbits(8);

        int size = 0;
        for (int i = 0; i < byteCount; i++) {
            size += (fgetbits() >>> 8) << (i * 8);
            addbits(8);
        }
        blockSize = size;
        final int checkSum = (0x5a ^ blockFlags ^ size ^ (size >>> 8) ^ (size >>> 16)) & 0xff;
        if (checkSum != savedCheckSum) {
            return false;
        }

        blockStart = inAddr;
        readBorder = Math.min(readBorder, blockStart + blockSize - 1);
        lastBlockInFile = (blockFlags & 0x40) != 0;
        tablePresent = (blockFlags & 0x80) != 0;
        return true;
    }

    // ---- Huffman tables (8f437ab:unpack50.cpp ReadTables) -------------------------------------

    /**
     * Read the block's 5 Huffman tables when {@link #isTablePresent()}. Returns {@code false} on
     * truncation or a malformed table (a "repeat previous" code at position 0). When no table is
     * present, returns {@code true} and the previously read tables stay in effect.
     */
    boolean readTables() {
        if (!tablePresent) {
            return true;
        }
        if (!ensure(25)) {
            return false;
        }

        final int[] bitLength = new int[Compress.BC5];
        for (int i = 0; i < Compress.BC5; i++) {
            final int length = fgetbits() >>> 12;
            faddbits(4);
            if (length == 15) {
                int zeroCount = fgetbits() >>> 12;
                faddbits(4);
                if (zeroCount == 0) {
                    bitLength[i] = 15;
                } else {
                    zeroCount += 2;
                    while (zeroCount-- > 0 && i < Compress.BC5) {
                        bitLength[i++] = 0;
                    }
                    i--;
                }
            } else {
                bitLength[i] = length;
            }
        }
        makeDecodeTables(bitLength, 0, bd, Compress.BC5);

        final int[] table = new int[Compress.HUFF_TABLE_SIZE5];
        for (int i = 0; i < Compress.HUFF_TABLE_SIZE5;) {
            if (!ensure(5)) {
                return false;
            }
            final int number = decodeNumber(bd);
            if (number < 16) {
                table[i] = number;
                i++;
            } else if (number < 18) {
                final int n;
                if (number == 16) {
                    n = (fgetbits() >>> 13) + 3;
                    faddbits(3);
                } else {
                    n = (fgetbits() >>> 9) + 11;
                    faddbits(7);
                }
                if (i == 0) {
                    // A "repeat previous" code cannot be first; refuse (unpack50.cpp comment).
                    return false;
                }
                int repeat = n;
                while (repeat-- > 0 && i < Compress.HUFF_TABLE_SIZE5) {
                    table[i] = table[i - 1];
                    i++;
                }
            } else {
                final int n;
                if (number == 18) {
                    n = (fgetbits() >>> 13) + 3;
                    faddbits(3);
                } else {
                    n = (fgetbits() >>> 9) + 11;
                    faddbits(7);
                }
                int zeros = n;
                while (zeros-- > 0 && i < Compress.HUFF_TABLE_SIZE5) {
                    table[i++] = 0;
                }
            }
        }
        tablesRead5 = true;
        if (inAddr > readTop) {
            return false;
        }
        makeDecodeTables(table, 0, ld, Compress.NC5);
        makeDecodeTables(table, Compress.NC5, dd, Compress.DC5);
        makeDecodeTables(table, Compress.NC5 + Compress.DC5, ldd, Compress.LDC5);
        makeDecodeTables(table, Compress.NC5 + Compress.DC5 + Compress.LDC5, rd, Compress.RC5);
        return true;
    }

    /** Build one decode table from a bit-length slice (8f437ab:unpack.cpp:227 MakeDecodeTables). */
    void makeDecodeTables(final int[] lengthTable, final int off, final Decode5 dec, final int size) {
        dec.setMaxNum(size);

        final int[] lengthCount = new int[16];
        for (int i = 0; i < size; i++) {
            lengthCount[lengthTable[off + i] & 0xf]++;
        }
        lengthCount[0] = 0; // do not count zero-length codes

        final int[] decodeNum = dec.getDecodeNum();
        Arrays.fill(decodeNum, 0, size, 0);

        final int[] decodePos = dec.getDecodePos();
        final int[] decodeLen = dec.getDecodeLen();
        decodePos[0] = 0;
        decodeLen[0] = 0;

        int upperLimit = 0;
        for (int i = 1; i < 16; i++) {
            upperLimit += lengthCount[i];
            final int leftAligned = upperLimit << (16 - i);
            upperLimit *= 2;
            decodeLen[i] = leftAligned;
            decodePos[i] = decodePos[i - 1] + lengthCount[i - 1];
        }

        final int[] copyDecodePos = Arrays.copyOf(decodePos, decodePos.length);
        for (int i = 0; i < size; i++) {
            final int curBitLength = lengthTable[off + i] & 0xf;
            if (curBitLength != 0) {
                decodeNum[copyDecodePos[curBitLength]] = i;
                copyDecodePos[curBitLength]++;
            }
        }

        // Larger alphabets (NC) get more quick-decode bits; everything else -3 (unpack.cpp:303-313).
        final int quickBits = size == Compress.NC5
            ? Compress.MAX_QUICK_DECODE_BITS
            : (Compress.MAX_QUICK_DECODE_BITS > 3 ? Compress.MAX_QUICK_DECODE_BITS - 3 : 0);
        dec.setQuickBits(quickBits);

        final int quickDataSize = 1 << quickBits;
        final int[] quickLen = dec.getQuickLen();
        final int[] quickNum = dec.getQuickNum();
        int curBitLength = 1;
        for (int code = 0; code < quickDataSize; code++) {
            final int bitField = code << (16 - quickBits);
            while (curBitLength < decodeLen.length && bitField >= decodeLen[curBitLength]) {
                curBitLength++;
            }
            quickLen[code] = curBitLength;

            int dist = bitField - decodeLen[curBitLength - 1];
            dist >>>= (16 - curBitLength);
            int pos;
            if (curBitLength < decodePos.length && (pos = decodePos[curBitLength] + dist) < size) {
                quickNum[code] = decodeNum[pos];
            } else {
                quickNum[code] = 0;
            }
        }
    }

    /** Decode one alphabet symbol (8f437ab:unpackinline.cpp DecodeNumber). */
    int decodeNumber(final Decode5 dec) {
        final int bitField = getbits() & 0xfffe; // left-aligned 15-bit raw field
        final int quickBits = dec.getQuickBits();
        final int[] decodeLen = dec.getDecodeLen();

        if (bitField < decodeLen[quickBits]) {
            final int code = bitField >>> (16 - quickBits);
            addbits(dec.getQuickLen()[code]);
            return dec.getQuickNum()[code];
        }

        int bits = 15;
        for (int i = quickBits + 1; i < 15; i++) {
            if (bitField < decodeLen[i]) {
                bits = i;
                break;
            }
        }
        addbits(bits);

        int dist = bitField - decodeLen[bits - 1];
        dist >>>= (16 - bits);
        int pos = dec.getDecodePos()[bits] + dist;
        if (pos >= dec.getMaxNum()) {
            pos = 0; // out-of-bounds safety for damaged archives
        }
        return dec.getDecodeNum()[pos];
    }

    // ---- accessors (test / M3.7 seams) -------------------------------------------------------

    int getBlockSize() {
        return blockSize;
    }

    int getBlockBitSize() {
        return blockBitSize;
    }

    boolean isLastBlockInFile() {
        return lastBlockInFile;
    }

    boolean isTablePresent() {
        return tablePresent;
    }

    boolean isTablesRead() {
        return tablesRead5;
    }

    Decode5 ld() {
        return ld;
    }

    Decode5 dd() {
        return dd;
    }

    Decode5 ldd() {
        return ldd;
    }

    Decode5 rd() {
        return rd;
    }

    Decode5 bd() {
        return bd;
    }
}
