/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.unpack.decode.Compress;
import com.github.junrar.unpack.decode.Decode5;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private boolean extraDist;

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
    // Sized for the extended alphabet unconditionally, the way unrar's fixed DecodeTable struct is
    // (unpack.hpp:87): a RAR5 block just fills the first DC5 slots. Sizing it per entry instead
    // would break a solid stream that mixes versions, where the flag flips but the window does not.
    private final Decode5 dd = new Decode5(Compress.DCX5);  // distances
    private final Decode5 ldd = new Decode5(Compress.LDC5); // low distance bits
    private final Decode5 rd = new Decode5(Compress.RC5);   // repeat distances
    private final Decode5 bd = new Decode5(Compress.BC5);   // bit lengths (table decode)

    private boolean tablesRead5;

    // ---- decode-loop state (M3.7; 8f437ab:unpack.hpp fields, 8f437ab:unpack50.cpp) -----------

    /** Input buffer size for the archive-driven refill (8f437ab:getbits.hpp MAX_SIZE). */
    private static final int MAX_SIZE = 0x8000;

    // Sliding-window pointers into the per-archive window (masked to maxWinMask each step).
    private int maxWinSize;
    private int maxWinMask;
    private int unpPtr;   // next window write position
    private int wrPtr;    // window position up to which output was flushed
    private int writeBorder;
    private int prevPtr;  // previous masked unpPtr, to detect a window wrap
    private boolean firstWinDone; // the window has wrapped at least once (FirstWinDone parity, M1.4)

    // The 4 most-recent LZ distances (OldDist) and the last match length (unpackinline.cpp).
    // 64-bit, as in d861246:unpack.hpp:252 -- an extended distance slot reaches 1 TB, and even a
    // plain RAR5 top slot overflows 32 bits (see the 4 GB-multiple case unrar patches out at
    // unpack50.cpp:108-114). Window positions stay int; only the distance itself is wide.
    private final long[] oldDist = new long[4];
    private int lastLength;

    private long destUnpSize;      // remaining bytes this entry must produce (setDestSize)
    private long writtenFileSize;  // bytes handed to unpWrite so far, this entry

    /** Filters applied during the current entry (test seam; see Archive test-only hooks). */
    private int filtersApplied;

    // ---- filter state (M3.8; 8f437ab:unpack50.cpp ReadFilter/AddFilter/UnpWriteBuf) -----------

    /** Pending filter queue (unrar {@code Filters}); flood-capped at {@code MAX_UNPACK_FILTERS}. */
    private final List<Unpack5Filter> filters = new ArrayList<Unpack5Filter>();

    // Scratch buffers for the copy-out-then-filter rule (unrar FilterSrcMemory/FilterDstMemory):
    // filters never run in place in the window, because window bytes feed future matches
    // (manual §4.3). Grown on demand, bounded by MAX_FILTER_BLOCK_SIZE via the ReadFilter clamp.
    private byte[] filterSrcMemory;
    private byte[] filterDstMemory;

    /**
     * @param unpIO     packed-byte pump + CRC/hash seam (manual &sect;4.12); the archive-driven
     *                  {@link #unpReadBuf()} refill consumes it. {@code null} in external-buffer
     *                  (test) mode, where {@link #feed} supplies the whole packed stream.
     * @param extraDist the initial {@code ExtraDist} state ({@code d861246:unpack.cpp:26}); RAR7
     *                  (version 70) selects the 80-slot distance alphabet with this flag, and the
     *                  same engine serves RAR5 and RAR7 (manual &sect;5.2). Per-entry routing goes
     *                  through {@link #setExtraDist}.
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
     * Select the distance alphabet for the next entry: {@code true} for version 70 (RAR7),
     * {@code false} for version 50 — including a RAR7 header that {@code FCI_RAR5_COMPAT} demoted.
     * Mirrors {@code ExtraDist=(Method==VER_PACK7)} in {@code d861246:unpack.cpp:184}, which is set
     * per file rather than per engine so a solid stream can mix versions without losing its window.
     */
    public void setExtraDist(final boolean extraDist) {
        this.extraDist = extraDist;
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

    /**
     * Refill the input buffer from the packed stream (8f437ab:unpack50.cpp:217 UnpReadBuf).
     * Compacts the unread tail to the buffer front once past the halfway mark, reads up to a full
     * {@link #MAX_SIZE} block, and re-derives {@link #readBorder} (the 30-byte over-read margin
     * getbits/getbits32 rely on) and the block-size clamp. Returns {@code false} in external-buffer
     * (test) mode — {@link #feed} supplied everything — and on a spent stream ({@code readCode==-1},
     * a missing next volume). A genuinely truncated single-volume stream throws through
     * {@link ComprDataIO#unpRead} instead (the typed-exception path).
     */
    private boolean unpReadBuf() throws IOException, RarException {
        if (unpIO == null) {
            return false; // external-buffer mode: no channel to refill from (M3.6 tests)
        }
        int dataSize = readTop - inAddr; // data left to process
        if (dataSize < 0) {
            return false;
        }
        blockSize -= inAddr - blockStart;
        if (inAddr > MAX_SIZE / 2) {
            // Past the halfway mark: slide the unread tail to the front to free space for new data.
            if (dataSize > 0) {
                System.arraycopy(inBuf, inAddr, inBuf, 0, dataSize);
            }
            inAddr = 0;
            readTop = dataSize;
        } else {
            dataSize = readTop;
        }
        int readCode = 0;
        if (MAX_SIZE != dataSize) {
            readCode = unpIO.unpRead(inBuf, dataSize, MAX_SIZE - dataSize);
        }
        if (readCode > 0) {
            readTop += readCode;
        }
        readBorder = readTop - 30;
        blockStart = inAddr;
        if (blockSize != -1) { // '-1' means not defined yet
            readBorder = Math.min(readBorder, blockStart + blockSize - 1);
        }
        return readCode != -1;
    }

    /** Ensure at least {@code need} bytes are readable ahead, else try to refill. */
    private boolean ensure(final int need) throws IOException, RarException {
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

    /**
     * 64 bits from the current position, highest bit first ({@code d861246:getbits.hpp} getbits64).
     * Needed only by the extended distance decode, where {@code DBits} can reach 38 and the 32-bit
     * read would not hold the whole field.
     */
    long getbits64() {
        long bitField = 0;
        for (int i = 0; i < 8; i++) {
            bitField = (bitField << 8) | (inBuf[inAddr + i] & 0xffL);
        }
        bitField <<= inBit;
        // C++ reads the spill byte as uint and shifts by 8 when InBit is 0, which yields 0 there.
        bitField |= (inBuf[inAddr + 8] & 0xff) >>> (8 - inBit);
        return bitField;
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
    boolean readBlockHeader() throws IOException, RarException {
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
    boolean readTables() throws IOException, RarException {
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

        // ExtraDist widens the distance alphabet, which lengthens the whole bit-length table and
        // shifts every slice after it (d861246:unpack50.cpp:647,710).
        final int dCodes = extraDist ? Compress.DCX5 : Compress.DC5;
        final int tableSize = extraDist ? Compress.HUFF_TABLE_SIZE5X : Compress.HUFF_TABLE_SIZE5;

        final int[] table = new int[tableSize];
        for (int i = 0; i < tableSize;) {
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
                while (repeat-- > 0 && i < tableSize) {
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
                while (zeros-- > 0 && i < tableSize) {
                    table[i++] = 0;
                }
            }
        }
        tablesRead5 = true;
        if (inAddr > readTop) {
            return false;
        }
        makeDecodeTables(table, 0, ld, Compress.NC5);
        makeDecodeTables(table, Compress.NC5, dd, dCodes);
        makeDecodeTables(table, Compress.NC5 + dCodes, ldd, Compress.LDC5);
        makeDecodeTables(table, Compress.NC5 + dCodes + Compress.LDC5, rd, Compress.RC5);
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

    // ---- decode loop (8f437ab:unpack50.cpp Unpack5) ------------------------------------------

    /** Bytes this entry must produce; the write-out meters against it (8f437ab: DestUnpSize). */
    public void setDestSize(final long destSize) {
        this.destUnpSize = destSize;
    }

    /**
     * Per-entry reset (8f437ab:unpack.cpp:193 UnpInitData + unpack50.cpp:528 UnpInitData50). A
     * non-solid entry wipes the LZ state (distances, window pointers, tables, write border); a
     * solid continuation keeps the window and tables and only re-arms the bit input for this
     * file's independent packed stream. The window itself is never cleared (unrar comments out the
     * memset; the growth-capped {@link Unpack5Window} is Java-zeroed on allocation).
     */
    private void unpInitData(final boolean solid) {
        maxWinSize = window.size();
        maxWinMask = window.mask();
        if (!solid) {
            Arrays.fill(oldDist, 0);
            lastLength = 0;
            unpPtr = 0;
            wrPtr = 0;
            prevPtr = 0;
            firstWinDone = false;
            tablesRead5 = false;
            writeBorder = Math.min(maxWinSize, Compress.UNPACK_MAX_WRITE) & maxWinMask;
        }
        // Filters never span several solid files, so they reset at the start of every file
        // (8f437ab:unpack.cpp UnpInitData — InitFilters() runs even for solid continuations).
        initFilters();
        filtersApplied = 0;
        writtenFileSize = 0;
        readTop = 0;
        readBorder = 0;
        inAddr = 0;
        inBit = 0;
        blockStart = 0;
        blockSize = -1; // '-1' means not defined yet
        if (inBuf.length < MAX_SIZE + 64) {
            inBuf = new byte[MAX_SIZE + 64];
        }
    }

    /**
     * Decode one RAR5 file into the window, flushing to {@link ComprDataIO#unpWrite} as it goes
     * (8f437ab:unpack50.cpp:1-155 Unpack5). The case order below is verbatim from upstream and
     * <em>is</em> the format spec (manual &sect;4.5): literal ({@code <256}), match ({@code >=262}),
     * filter (256), repeat-last-length (257), and repeat-distance (258..261).
     */
    public void unpack5(final boolean solid) throws IOException, RarException {
        unpInitData(solid);
        if (!unpReadBuf()) {
            return;
        }
        // TablesRead5 guards use-before-read even when a block header omits the table.
        if (!readBlockHeader() || !readTables() || !tablesRead5) {
            return;
        }

        while (true) {
            unpPtr &= maxWinMask;
            firstWinDone |= (prevPtr > unpPtr);
            prevPtr = unpPtr;

            if (inAddr >= readBorder) {
                boolean fileDone = false;
                // 'while' (not 'if'): an empty block that carries only a Huffman table lands us
                // back on the block border immediately after reading the table.
                while (inAddr > blockStart + blockSize - 1
                    || (inAddr == blockStart + blockSize - 1 && inBit >= blockBitSize)) {
                    if (lastBlockInFile) {
                        fileDone = true;
                        break;
                    }
                    if (!readBlockHeader() || !readTables()) {
                        return;
                    }
                }
                if (fileDone || !unpReadBuf()) {
                    break;
                }
            }

            if (((writeBorder - unpPtr) & maxWinMask) <= Compress.MAX_INC_LZ_MATCH && writeBorder != unpPtr) {
                unpWriteBuf();
                if (writtenFileSize > destUnpSize) {
                    return;
                }
            }

            final int mainSlot = decodeNumber(ld);
            if (mainSlot < 256) {
                window.put(unpPtr++, (byte) mainSlot);
                continue;
            }
            if (mainSlot >= 262) {
                int length = slotToLength(mainSlot - 262);

                long distance = 1;
                final int distSlot = decodeNumber(dd);
                final int dbits;
                if (distSlot < 4) {
                    dbits = 0;
                    distance += distSlot;
                } else {
                    dbits = distSlot / 2 - 1;
                    distance += (long) (2 | (distSlot & 1)) << dbits;
                }
                if (dbits > 0) {
                    if (dbits >= 4) {
                        if (dbits > 4) {
                            // ExtraDist reaches dbits 38 (slot 79), too wide for the 32-bit read.
                            if (dbits > 36) {
                                distance += (getbits64() >>> (68 - dbits)) << 4;
                            } else {
                                distance += ((getbits32() & 0xffffffffL) >>> (36 - dbits)) << 4;
                            }
                            addbits(dbits - 4);
                        }
                        distance += decodeNumber(ldd);
                        // d861246:unpack50.cpp:108-114 forces distances that overflowed a 32-bit
                        // size_t to -1 here. No Java counterpart: the long above never truncates,
                        // which is the same result that branch exists to reproduce.
                    } else {
                        distance += getbits() >>> (16 - dbits);
                        addbits(dbits);
                    }
                }
                // Signed compares are exact: distance is built up from non-negative terms and
                // tops out near 2^40 (slot 79), so it never reaches the sign bit (no-go C15).
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
                final Unpack5Filter filter = new Unpack5Filter();
                if (!readFilter(filter) || !addFilter(filter)) {
                    break;
                }
                continue;
            }
            if (mainSlot == 257) {
                if (lastLength != 0) {
                    copyString(lastLength, oldDist[0]);
                }
                continue;
            }
            // mainSlot 258..261: reuse one of the 4 recent distances, moving it to the front.
            final int distNum = mainSlot - 258;
            final long distance = oldDist[distNum];
            for (int i = distNum; i > 0; i--) {
                oldDist[i] = oldDist[i - 1];
            }
            oldDist[0] = distance;

            final int length = slotToLength(decodeNumber(rd));
            lastLength = length;
            copyString(length, distance);
        }
        unpWriteBuf();
    }

    /** Stored (method 0) RAR5 entry: pump packed bytes straight through the CRC/hash seam. */
    public void unstore() throws IOException, RarException {
        final byte[] buffer = new byte[0x10000];
        long left = destUnpSize;
        while (left > 0) {
            final int code = unpIO.unpRead(buffer, 0, (int) Math.min(buffer.length, left));
            if (code <= 0) {
                break;
            }
            unpIO.unpWrite(buffer, 0, code);
            left -= code;
        }
    }

    /** Match length from a length slot (8f437ab:unpackinline.cpp SlotToLength). */
    private int slotToLength(final int slot) {
        int length = 2;
        final int lbits;
        if (slot < 8) {
            lbits = 0;
            length += slot;
        } else {
            lbits = slot / 4 - 1;
            length += (4 | (slot & 3)) << lbits;
        }
        if (lbits > 0) {
            length += getbits() >>> (16 - lbits);
            addbits(lbits);
        }
        return length;
    }

    /** Push a distance onto the recent-distance list (8f437ab:unpackinline.cpp InsertOldDist). */
    private void insertOldDist(final long distance) {
        oldDist[3] = oldDist[2];
        oldDist[2] = oldDist[1];
        oldDist[1] = oldDist[0];
        oldDist[0] = distance;
    }

    /**
     * Copy an LZ match into the window (8f437ab:unpackinline.cpp CopyString + the FirstWinDone
     * arm, manual M1.4). Uniformly byte-sequential with per-byte masking: this reproduces
     * upstream's fast/slow paths bit-for-bit (both copy byte-by-byte forward, overlap-safe — the
     * memcpy self-overlap trap, manual &sect;4.3), and per-byte masking keeps the growth-capped
     * window from being read before a position is written.
     *
     * <p>A distance pointing into never-written window space (before the first wrap, or beyond the
     * whole window) is zero-filled deterministically instead of reading ungrown bytes, matching
     * unrar's zero-initialized window; the resulting CRC mismatch is what surfaces the typed error.
     */
    private void copyString(int length, final long distance) {
        // 64-bit distance against int window positions: a slot-79 distance is ~2^40, far past any
        // window, and must read as "out of range" rather than wrapping into it (no-go C15).
        if (distance > unpPtr && (!firstWinDone || distance > maxWinSize)) {
            while (length-- > 0) {
                window.put(unpPtr, (byte) 0);
                unpPtr = (unpPtr + 1) & maxWinMask;
            }
            return;
        }
        int srcPtr = (int) ((unpPtr - distance) & maxWinMask);
        while (length-- > 0) {
            window.put(unpPtr, window.get(srcPtr));
            srcPtr = (srcPtr + 1) & maxWinMask;
            unpPtr = (unpPtr + 1) & maxWinMask;
        }
    }

    // ---- filters (M3.8, issue #29; 8f437ab:unpack50.cpp:158-214 + d861246 4-type census) ------

    /**
     * One variable-length filter field: a 2-bit byte count ({@code 1..4}), then that many bytes
     * assembled little-endian (8f437ab:unpack50.cpp:158 ReadFilterData). The value is a C++
     * {@code uint}; 4-byte fields can set bit 31, so callers compare unsigned (no-go C15).
     */
    private int readFilterData() {
        final int byteCount = (fgetbits() >>> 14) + 1;
        addbits(2);
        int data = 0;
        for (int i = 0; i < byteCount; i++) {
            data += (fgetbits() >>> 8) << (i * 8);
            addbits(8);
        }
        return data;
    }

    /**
     * Read one filter announcement from the bit stream (8f437ab:unpack50.cpp:173 ReadFilter):
     * relative block start, block length, 3-bit type, and — for DELTA — the 5-bit channel count.
     * An oversized block length (&gt; {@link Compress#MAX_FILTER_BLOCK_SIZE}, unsigned) is forced
     * to 0, which the sweep treats as a no-op filter, matching unrar.
     */
    boolean readFilter(final Unpack5Filter filter) throws IOException, RarException {
        if (!ensure(16)) {
            return false;
        }
        filter.setBlockStart(readFilterData());
        filter.setBlockLength(readFilterData());
        if (Integer.compareUnsigned(filter.getBlockLength(), Compress.MAX_FILTER_BLOCK_SIZE) > 0) {
            filter.setBlockLength(0);
        }

        filter.setType(fgetbits() >>> 13);
        faddbits(3);

        if (filter.getType() == Compress.FILTER_DELTA) {
            filter.setChannels((fgetbits() >>> 11) + 1);
            faddbits(5);
        }
        return true;
    }

    /**
     * Queue a filter for the write sweep (8f437ab:unpack50.cpp:197 AddFilter). At the
     * {@link Compress#MAX_UNPACK_FILTERS} flood cap unrar flushes via {@link #unpWriteBuf()} and,
     * if still over the cap, drops the whole queue ({@link #initFilters()}) — a no-op, never an
     * error. A filter whose start lies in not-yet-written data of the circular window is deferred
     * with the {@code NextWindow} flag until that older data has been written.
     */
    private boolean addFilter(final Unpack5Filter filter) throws IOException {
        if (filters.size() >= Compress.MAX_UNPACK_FILTERS) {
            unpWriteBuf(); // write data, apply and flush filters
            if (filters.size() >= Compress.MAX_UNPACK_FILTERS) {
                initFilters(); // still too many filters, prevent excessive memory use
            }
        }
        // BlockStart is still the raw header-relative distance here — a C++ uint, so the wrap
        // test against the written-data distance must compare unsigned (no-go C15).
        filter.setNextWindow(wrPtr != unpPtr
            && Integer.compareUnsigned((wrPtr - unpPtr) & maxWinMask, filter.getBlockStart()) <= 0);
        filter.setBlockStart((filter.getBlockStart() + unpPtr) & maxWinMask);
        filters.add(filter);
        return true;
    }

    /** Drop every pending filter (8f437ab:unpack50.cpp:684 InitFilters). */
    private void initFilters() {
        filters.clear();
    }

    /**
     * Apply one filter to a copied-out block (8f437ab:unpack50.cpp:398 ApplyFilter;
     * d861246:unpack50.cpp:427-485 is the authoritative 4-type census — DELTA/E8/E8E9/ARM only).
     * E8/E8E9/ARM transform {@code data} in place and return it; DELTA de-interleaves into the
     * destination scratch buffer; any other type returns {@code null} (nothing written, matching
     * unrar's NULL). {@code data} is always the scratch copy, never window storage.
     */
    byte[] applyFilter(final byte[] data, final int dataSize, final Unpack5Filter flt) {
        switch (flt.getType()) {
            case Compress.FILTER_E8:
            case Compress.FILTER_E8E9: {
                final int fileOffset = (int) writtenFileSize; // C++ (uint)WrittenFileSize
                final int fileSize = 0x1000000;
                final int cmpByte2 = flt.getType() == Compress.FILTER_E8E9 ? 0xe9 : 0xe8;
                // C++ guards with "CurPos+4" (not "DataSize-4") against uint underflow for
                // DataSize<4; dataSize is <= MAX_FILTER_BLOCK_SIZE here, so int stays exact.
                for (int curPos = 0; curPos + 4 < dataSize;) {
                    final int curByte = data[curPos] & 0xff;
                    curPos++;
                    if (curByte == 0xe8 || curByte == cmpByte2) {
                        // fileOffset is a truncated uint; the sum can wrap, so unsigned remainder.
                        final int offset = Integer.remainderUnsigned(curPos + fileOffset, fileSize);
                        final int addr = rawGet4(data, curPos);
                        // Upstream tests the 0x80000000 bit rather than '< 0'; addr is a uint.
                        if ((addr & 0x80000000) != 0) {              // addr < 0
                            if (((addr + offset) & 0x80000000) == 0) { // addr + offset >= 0
                                rawPut4(addr + fileSize, data, curPos);
                            }
                        } else if (((addr - fileSize) & 0x80000000) != 0) { // addr < fileSize
                            rawPut4(addr - offset, data, curPos);
                        }
                        curPos += 4;
                    }
                }
                return data;
            }
            case Compress.FILTER_ARM: {
                final int fileOffset = (int) writtenFileSize;
                // "CurPos+3" guard as above; BL condition byte 0xEB = '1110' (always).
                for (int curPos = 0; curPos + 3 < dataSize; curPos += 4) {
                    if (data[curPos + 3] == (byte) 0xeb) {
                        int offset = (data[curPos] & 0xff)
                            | ((data[curPos + 1] & 0xff) << 8)
                            | ((data[curPos + 2] & 0xff) << 16);
                        offset -= (fileOffset + curPos) >>> 2; // uint division by 4
                        data[curPos] = (byte) offset;
                        data[curPos + 1] = (byte) (offset >>> 8);
                        data[curPos + 2] = (byte) (offset >>> 16);
                    }
                }
                return data;
            }
            case Compress.FILTER_DELTA: {
                // RAR5 stores the channel count in 5 bits, so no excessive-channels rejection
                // is needed (unlike RAR3).
                final int channels = flt.getChannels();
                int srcPos = 0;
                if (filterDstMemory == null || filterDstMemory.length < dataSize) {
                    filterDstMemory = new byte[dataSize];
                }
                final byte[] dstData = filterDstMemory;
                // Bytes of one channel are grouped into continual blocks; place them back to
                // their interleaved positions.
                for (int curChannel = 0; curChannel < channels; curChannel++) {
                    byte prevByte = 0;
                    for (int destPos = curChannel; destPos < dataSize; destPos += channels) {
                        prevByte -= data[srcPos++];
                        dstData[destPos] = prevByte;
                    }
                }
                return dstData;
            }
            default:
                return null;
        }
    }

    /** Little-endian 32-bit read (unrar RawGet4). */
    private static int rawGet4(final byte[] d, final int pos) {
        return (d[pos] & 0xff)
            | ((d[pos + 1] & 0xff) << 8)
            | ((d[pos + 2] & 0xff) << 16)
            | ((d[pos + 3] & 0xff) << 24);
    }

    /** Little-endian 32-bit write (unrar RawPut4). */
    private static void rawPut4(final int value, final byte[] d, final int pos) {
        d[pos] = (byte) value;
        d[pos + 1] = (byte) (value >>> 8);
        d[pos + 2] = (byte) (value >>> 16);
        d[pos + 3] = (byte) (value >>> 24);
    }

    /**
     * Flush freshly decoded window bytes to the output with the written-region filter sweep
     * (8f437ab:unpack50.cpp:255 UnpWriteBuf). Each pending filter whose block lies inside the
     * write range is applied to a wrap-aware two-part <em>copy</em> of the window bytes and the
     * filtered output is written INSTEAD of them (the window itself is never modified — its bytes
     * feed future matches, manual &sect;4.3). A filter block extending past the write pass rolls
     * {@code wrPtr} back to defer the tail (and clears {@code NextWindow} on every later filter);
     * processed filters are compacted out of the queue. Finally the write border is re-armed
     * ahead of the window pointer, capped at {@link Compress#UNPACK_MAX_WRITE}.
     */
    private void unpWriteBuf() throws IOException {
        int writtenBorder = wrPtr;
        final int fullWriteSize = (unpPtr - writtenBorder) & maxWinMask;
        int writeSizeLeft = fullWriteSize;
        boolean notAllFiltersProcessed = false;
        for (int i = 0; i < filters.size(); i++) {
            final Unpack5Filter flt = filters.get(i);
            if (flt.getType() == Compress.FILTER_NONE) {
                continue;
            }
            if (flt.isNextWindow()) {
                // A filter deferred by the circular-window wrap becomes applicable once the
                // write range has covered its block start (upstream keeps this check "just in
                // case"; the compressor bounds start-to-announcement distance by the window).
                if (((flt.getBlockStart() - wrPtr) & maxWinMask) <= fullWriteSize) {
                    flt.setNextWindow(false);
                }
                continue;
            }
            final int blockStart = flt.getBlockStart();
            final int blockLength = flt.getBlockLength();
            if (((blockStart - writtenBorder) & maxWinMask) < writeSizeLeft) {
                if (writtenBorder != blockStart) {
                    unpWriteArea(writtenBorder, blockStart);
                    writtenBorder = blockStart;
                    writeSizeLeft = (unpPtr - writtenBorder) & maxWinMask;
                }
                if (blockLength <= writeSizeLeft) {
                    if (blockLength > 0) { // ReadFilter sets 0 for invalid filters: a no-op
                        final int blockEnd = (blockStart + blockLength) & maxWinMask;
                        if (filterSrcMemory == null || filterSrcMemory.length < blockLength) {
                            filterSrcMemory = new byte[blockLength];
                        }
                        final byte[] mem = filterSrcMemory;
                        // Real copy-out, wrap split into two arraycopies (manual §4.3): the
                        // window bytes stay untouched for future string matches.
                        if (blockStart < blockEnd || blockEnd == 0) {
                            System.arraycopy(window.buffer(), blockStart, mem, 0, blockLength);
                        } else {
                            final int firstPartLength = maxWinSize - blockStart;
                            System.arraycopy(window.buffer(), blockStart, mem, 0, firstPartLength);
                            System.arraycopy(window.buffer(), 0, mem, firstPartLength, blockEnd);
                        }

                        final byte[] outMem = applyFilter(mem, blockLength, flt);

                        flt.setType(Compress.FILTER_NONE);

                        if (outMem != null) {
                            unpIO.unpWrite(outMem, 0, blockLength);
                            filtersApplied++;
                        }
                        writtenFileSize += blockLength;
                        writtenBorder = blockEnd;
                        writeSizeLeft = (unpPtr - writtenBorder) & maxWinMask;
                    }
                } else {
                    // The filter block intersects the write border: roll the window border back
                    // so the whole block is processed in a later pass, and stop here.
                    wrPtr = writtenBorder;
                    // Filter start positions only increase, so no later filter can apply in this
                    // pass either; their NextWindow deferral is reset.
                    for (int j = i; j < filters.size(); j++) {
                        final Unpack5Filter later = filters.get(j);
                        if (later.getType() != Compress.FILTER_NONE) {
                            later.setNextWindow(false);
                        }
                    }
                    notAllFiltersProcessed = true;
                    break;
                }
            }
        }

        // Compact processed filters out of the queue (upstream's shift-down + truncate).
        int emptyCount = 0;
        for (int i = 0; i < filters.size(); i++) {
            final Unpack5Filter flt = filters.get(i);
            if (emptyCount > 0) {
                filters.set(i - emptyCount, flt);
            }
            if (flt.getType() == Compress.FILTER_NONE) {
                emptyCount++;
            }
        }
        if (emptyCount > 0) {
            filters.subList(filters.size() - emptyCount, filters.size()).clear();
        }

        if (!notAllFiltersProcessed) { // only if all filters are processed
            // Write the data left after the last filter.
            unpWriteArea(writtenBorder, unpPtr);
            wrPtr = unpPtr;
        }

        writeBorder = (unpPtr + Math.min(maxWinSize, Compress.UNPACK_MAX_WRITE)) & maxWinMask;
        if (writeBorder == unpPtr
            || (wrPtr != unpPtr && ((wrPtr - unpPtr) & maxWinMask) < ((writeBorder - unpPtr) & maxWinMask))) {
            writeBorder = wrPtr;
        }
    }

    /** Write the window range {@code [start, end)}, splitting a wrap into two parts (UnpWriteArea). */
    private void unpWriteArea(final int startPtr, final int endPtr) throws IOException {
        if (endPtr < startPtr) {
            unpWriteData(startPtr, maxWinSize - startPtr);
            unpWriteData(0, endPtr);
        } else {
            unpWriteData(startPtr, endPtr - startPtr);
        }
    }

    /** Write one contiguous window run, clamped to the remaining {@code destUnpSize} (UnpWriteData). */
    private void unpWriteData(final int pos, final int size) throws IOException {
        if (size <= 0 || writtenFileSize >= destUnpSize) {
            return;
        }
        int writeSize = size;
        final long leftToWrite = destUnpSize - writtenFileSize;
        if (writeSize > leftToWrite) {
            writeSize = (int) leftToWrite;
        }
        unpIO.unpWrite(window.buffer(), pos, writeSize);
        writtenFileSize += size;
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

    /**
     * @return the 4 most-recent LZ distances, newest first. An extended distance is far past any
     *         M4-era window, so it zero-fills and leaves no trace in the output; this is the only
     *         way a test can pin the decoded value itself (M4.2 arithmetic seam).
     */
    long[] oldDist() {
        return oldDist;
    }

    /** @return filters applied during the current entry (M3.8 fixture-honesty seam). */
    public int filtersApplied() {
        return filtersApplied;
    }

    /** @return filters still queued (M3.8 flood-policy seam). */
    int pendingFilterCount() {
        return filters.size();
    }
}
