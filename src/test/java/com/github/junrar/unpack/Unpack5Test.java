package com.github.junrar.unpack;

import com.github.junrar.ArchiveOptions;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.unpack.decode.Compress;
import com.github.junrar.unpack.decode.Decode5;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.6 (issue #27) unit tests for the RAR5 {@link Unpack5} skeleton: window sizing +
 * growth-capped allocation, the dictionary resource model (engine capability ceiling vs.
 * {@code ArchiveOptions.maxDictionarySize} budget), block-header parsing, and the 5-table
 * Huffman read. The decode loop and archive-level extraction land in M3.7, so every case
 * here drives the engine directly on crafted (external-buffer) block streams.
 *
 * <p>The crafted streams serialize a Huffman <em>bit-length table</em> — a header data format,
 * not the LZ compression algorithm — so no licensed compression logic is reproduced.
 */
class Unpack5Test {

    private static final long DEFAULT = ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE;
    private static final long ONE_GB = 1L << 30;

    // ---- window-size matrix (accept) --------------------------------------------------------

    @Test
    void windowMatrixAccepts128kAs256kFloor() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(128 * 1024, false, DEFAULT);
        // 128 KB is below the 0x40000 min-alloc floor, so the window is bumped to 256 KB.
        assertThat(u.window().size()).isEqualTo(0x40000);
        assertThat(u.window().capacity()).isEqualTo(0x40000);
    }

    @Test
    void windowMatrixAccepts32m() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(32 << 20, false, DEFAULT);
        assertThat(u.window().size()).isEqualTo(32 << 20);
        assertThat(u.window().capacity()).isEqualTo(0x40000); // lazy: floor only, nothing written
    }

    @Test
    void windowMatrixAccepts1g() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(ONE_GB, false, DEFAULT);
        assertThat(u.window().size()).isEqualTo((int) ONE_GB);
        assertThat(u.window().capacity()).isEqualTo(0x40000);
    }

    // ---- window-size matrix: capability 64 GB, flat path kept at 1 GB and below (M4.3) --------

    @Test
    void windowMatrixAccepts2gAsASegmentedWindow() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(2L << 30, false, DEFAULT);
        assertThat(u.window().size()).isEqualTo(2L << 30);
        assertThat(u.window().isSegmented()).as("past 1 GB the window is segmented").isTrue();
        assertThat(u.window().capacity()).as("lazy: nothing written, nothing allocated")
            .isEqualTo(0x40000);
    }

    @Test
    void windowMatrixAccepts4gAtTheDefaultBudget() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(4L << 30, false, DEFAULT);
        assertThat(u.window().size()).isEqualTo(4L << 30);
        assertThat(u.window().capacity()).isEqualTo(0x40000);
    }

    @Test
    void oneGigabyteAndBelowStaysOnTheFlatPath() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(ONE_GB, false, DEFAULT);
        assertThat(u.window().isSegmented()).as("1 GB is still a single flat segment").isFalse();
        assertThat(u.window().segmentCount()).isEqualTo(1);
    }

    @Test
    void aboveFourGigabytesStaysCallerOptIn() throws Exception {
        // The capability now reaches 64 GB, so a 6 GB dictionary is refused by the BUDGET
        // (default 4 GiB, permanent — review B-S3), exactly the way unrar needs -mdx.
        final Unpack5 u = new Unpack5(false);
        final Throwable t = catchThrowable(() -> u.init(6L << 30, false, DEFAULT));
        assertThat(t).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        assertThat(t.getMessage()).contains("maxDictionarySize");
        assertThat(u.window()).as("guard fires before any window allocation").isNull();

        final Unpack5 optedIn = new Unpack5(false);
        optedIn.init(6L << 30, false, 8L << 30);
        assertThat(optedIn.window().size()).isEqualTo(6L << 30);
        assertThat(optedIn.window().capacity()).isEqualTo(0x40000);
    }

    @Test
    void windowMatrixRejectsPast64gAsCapabilityBeforeAllocation() {
        final Unpack5 u = new Unpack5(false);
        final Throwable t = catchThrowable(() -> u.init((64L << 30) + 1, false, 128L << 30));
        assertThat(t).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        assertThat(t.getMessage()).contains("engine capability");
        assertThat(u.window()).as("guard fires before any window allocation").isNull();
    }

    @Test
    void windowMatrixAcceptsExactly64g() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(64L << 30, false, 64L << 30);
        assertThat(u.window().size()).isEqualTo(64L << 30);
        assertThat(u.window().capacity()).as("a 64 GB claim still allocates only the floor")
            .isEqualTo(0x40000);
    }

    // ---- hostile row (a): budget check fires before allocation --------------------------------

    @Test
    void budgetLoweredBelowClaimRejectsBeforeAllocation() {
        final Unpack5 u = new Unpack5(false);
        // ~100-byte archive claims a 1 GB dict; caller lowered maxDictionarySize to 16 MB.
        final Throwable t = catchThrowable(() -> u.init(ONE_GB, false, 16L << 20));
        assertThat(t).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        assertThat(u.window()).as("no new byte[] before the size check").isNull();
    }

    // ---- solid window rule: never shrink, never grow mid-set ---------------------------------

    @Test
    void solidStreamNeverShrinksWindow() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(4 << 20, false, DEFAULT);
        final Unpack5Window first = u.window();
        assertThat(first.size()).isEqualTo(4 << 20);
        // A solid continuation declaring a smaller dict must keep the existing window.
        u.init(1 << 20, true, DEFAULT);
        assertThat(u.window()).isSameAs(first);
        assertThat(u.window().size()).isEqualTo(4 << 20);
    }

    @Test
    void nonSolidSmallerReusesLargerWindow() throws Exception {
        // unrar: if (WinSize<=MaxWinSize) return; -- the larger buffer is kept, not reallocated.
        final Unpack5 u = new Unpack5(false);
        u.init(4 << 20, false, DEFAULT);
        final Unpack5Window first = u.window();
        u.init(1 << 20, false, DEFAULT);
        assertThat(u.window()).isSameAs(first);
        assertThat(u.window().size()).isEqualTo(4 << 20);
    }

    @Test
    void nonSolidGrowReallocatesWindow() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.init(1 << 20, false, DEFAULT);
        final Unpack5Window first = u.window();
        u.init(4 << 20, false, DEFAULT);
        assertThat(u.window()).isNotSameAs(first);
        assertThat(u.window().size()).isEqualTo(4 << 20);
    }

    // ---- hostile row (b): growth-capped window abstraction ------------------------------------

    @Test
    void bigDictWindowAllocatesOnlyFloorUntilWritten() {
        final Unpack5Window w = new Unpack5Window((int) ONE_GB);
        assertThat(w.capacity()).isEqualTo(0x40000);
        final int n = 5_000_000;
        for (int i = 0; i < n; i++) {
            w.put(i, (byte) i);
        }
        assertThat(w.capacity()).as("growth-capped: capacity <= 2x bytes written").isLessThanOrEqualTo(2 * n);
        assertThat(w.capacity()).as("nowhere near the 1 GB cap").isLessThan((int) ONE_GB);
    }

    @Test
    void smallWriteStaysAtFloor() {
        final Unpack5Window w = new Unpack5Window((int) ONE_GB);
        for (int i = 0; i < 100; i++) {
            w.put(i, (byte) 1);
        }
        assertThat(w.capacity()).isEqualTo(0x40000);
    }

    @Test
    void writesCrossingDoublingBoundariesReadBackByteIdentical() {
        final Unpack5Window w = new Unpack5Window(32 << 20);
        final int n = 3_000_000; // crosses 0x40000 -> 0x80000 -> ... -> 0x400000 boundaries
        for (int i = 0; i < n; i++) {
            w.put(i, (byte) (i * 31 + 7));
        }
        for (int i = 0; i < n; i++) {
            assertThat(w.get(i)).as("byte at %d", i).isEqualTo((byte) (i * 31 + 7));
        }
    }

    // ---- segmented window (M4.3, issue #35) ---------------------------------------------------
    //
    // Driven through the explicit-shift constructor so segment boundaries are reachable without
    // allocating 256 MB per segment; the production shift is exercised by the window-size matrix
    // above and, end to end, by the >4 GB job in ArchiveRar7BigDictTest.

    private static final int SEG = 4096;
    private static final int SEG_SHIFT = 12;

    @Test
    void segmentedWindowAddressesEveryPositionByLongIndex() {
        final Unpack5Window w = new Unpack5Window(4L * SEG, SEG_SHIFT);
        assertThat(w.segmentCount()).isEqualTo(4);
        assertThat(w.isSegmented()).isTrue();
        for (long i = 0; i < 4L * SEG; i++) {
            w.put(i, (byte) (i * 31 + 7));
        }
        for (long i = 0; i < 4L * SEG; i++) {
            assertThat(w.get(i)).as("byte at %d", i).isEqualTo((byte) (i * 31 + 7));
        }
    }

    @Test
    void segmentsAreAllocatedOnlyAsTheWindowFills() {
        final Unpack5Window w = new Unpack5Window(4L * SEG, SEG_SHIFT);
        final long afterFirst = w.capacity();
        for (int i = 0; i < SEG; i++) {
            w.put(i, (byte) 1);
        }
        assertThat(w.capacity()).as("still only the first segment").isEqualTo(afterFirst);
        w.put(SEG, (byte) 1);
        assertThat(w.capacity()).as("the second segment appears only once it is written")
            .isEqualTo(afterFirst + SEG);
    }

    @Test
    void aFractionalSizeLeavesTheLastSegmentShort() {
        // A RAR7 dictionary is size + size/32*frac, so the window is not a whole number of
        // segments: the tail segment must be sized to the remainder, not to a full segment.
        final long size = 3L * SEG + 1000;
        final Unpack5Window w = new Unpack5Window(size, SEG_SHIFT);
        assertThat(w.segmentCount()).isEqualTo(4);
        for (long i = 0; i < size; i++) {
            w.put(i, (byte) i);
        }
        assertThat(w.capacity()).as("the tail segment holds the remainder, not a full segment")
            .isEqualTo(size);
        assertThat(w.run(size - 1, 64)).as("no run reaches past the declared size").isEqualTo(1);
    }

    @Test
    void aFractionalWindowNeverAllocatesPastItsDeclaredSize() {
        // 256 KB + 1/32: the RAR7 fractional shape below 1 GB, so still one flat segment whose
        // length is not a power of two. Doubling must stop at the declared size, not overshoot to
        // the next power of two — the growth cap is an invariant of the resource model (B-S3).
        final long size = 0x42000;
        final Unpack5Window w = new Unpack5Window(size);
        for (long i = 0; i < size; i++) {
            w.put(i, (byte) i);
        }
        assertThat(w.capacity()).isEqualTo(size);
        assertThat(w.get(size - 1)).isEqualTo((byte) (size - 1));
    }

    @Test
    void runStopsAtTheSegmentBoundary() {
        final Unpack5Window w = new Unpack5Window(4L * SEG, SEG_SHIFT);
        assertThat(w.run(0, 4L * SEG)).as("capped at one segment").isEqualTo(SEG);
        assertThat(w.run(SEG - 10, 100)).as("only the bytes left in this segment").isEqualTo(10);
        assertThat(w.run(SEG - 10, 4)).as("never more than asked for").isEqualTo(4);
        assertThat(w.offsetAt(SEG + 5)).isEqualTo(5);
        assertThat(w.bufferAt(SEG + 5)).isNotSameAs(w.bufferAt(5));
    }

    @Test
    void copyOutSpansSegmentBoundaries() {
        final int size = 4 * SEG;
        final Unpack5Window w = new Unpack5Window(size, SEG_SHIFT);
        for (int i = 0; i < size; i++) {
            w.put(i, (byte) (i * 13 + 5));
        }
        final int start = SEG - 7;
        final int len = 2 * SEG + 20; // spans three segments
        final byte[] dst = new byte[len + 3];
        w.copyOut(start, dst, 3, len);
        for (int i = 0; i < len; i++) {
            assertThat(dst[3 + i]).as("copied byte %d", i).isEqualTo((byte) ((start + i) * 13 + 5));
        }
    }

    @Test
    void selfReferencingCopyAcrossASegmentBoundaryReadsWhatWasJustWritten() {
        // copyString's byte-sequential overlap (distance 1) walked across a segment boundary:
        // every read must see the byte written one step earlier, from the neighbouring segment.
        final Unpack5Window w = new Unpack5Window(4L * SEG, SEG_SHIFT);
        w.put(SEG - 3, (byte) 0x5a);
        long src = SEG - 3;
        long dst = SEG - 2;
        for (int i = 0; i < 8; i++) { // runs from the tail of segment 0 into segment 1
            w.put(dst++, w.get(src++));
        }
        for (long i = SEG - 3; i < SEG + 6; i++) {
            assertThat(w.get(i)).as("byte at %d", i).isEqualTo((byte) 0x5a);
        }
    }

    // ---- bit input ---------------------------------------------------------------------------

    @Test
    void getbitsAndGetbits32ReadMsbFirst() {
        final Unpack5 u = new Unpack5(false);
        u.feed(new byte[]{0x12, 0x34, 0x56, 0x78, (byte) 0x9A});
        assertThat(u.getbits()).isEqualTo(0x1234);
        assertThat(u.getbits32()).isEqualTo(0x12345678);
        u.addbits(4);
        assertThat(u.getbits()).isEqualTo(0x2345);
    }

    // ---- block header ------------------------------------------------------------------------

    @Test
    void readBlockHeaderParsesFlagsAndValidatesChecksum() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.feed(blockHeaderBytes(0x80 | 0x40, 3, 0x23, false), 16); // readTop slack for the -7 guard
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.isTablePresent()).isTrue();
        assertThat(u.isLastBlockInFile()).isTrue();
        assertThat(u.getBlockBitSize()).isEqualTo(3);
        assertThat(u.getBlockSize()).isEqualTo(0x23);
    }

    @Test
    void readBlockHeaderRejectsBadChecksum() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.feed(blockHeaderBytes(0x80, 1, 0x10, true), 16); // checksum corrupted
        assertThat(u.readBlockHeader()).isFalse();
    }

    // ---- full 5-table read -------------------------------------------------------------------

    @Test
    void readTablesBuildsAllFiveTablesAndDecodes() throws Exception {
        // Main bit-length table: LD symbols {0,1,2,3}=len 2; DD symbols {0,1}=len 1; rest length 0.
        final int[] mainLen = new int[Compress.HUFF_TABLE_SIZE5];
        mainLen[0] = 2; mainLen[1] = 2; mainLen[2] = 2; mainLen[3] = 2;      // LD (offset 0)
        mainLen[Compress.NC5] = 1; mainLen[Compress.NC5 + 1] = 1;             // DD (offset NC5)

        final BitWriter bw = new BitWriter();
        // Block header: TablePresent, LastBlock, bitSize=1, ByteCount=1, size=1 (unchecked here).
        writeBlockHeader(bw, 0x80 | 0x40, 1, 1);
        // BD section: symbols 0..15 -> len 4 (identity 4-bit codes), 16..19 -> len 0.
        for (int i = 0; i < Compress.BC5; i++) {
            bw.writeBits(i < 16 ? 4 : 0, 4);
        }
        // Main table: 430 entries, each a 4-bit BD code == its length value (0..15).
        for (int i = 0; i < Compress.HUFF_TABLE_SIZE5; i++) {
            bw.writeBits(mainLen[i], 4);
        }
        // Trailing decode bits: LD code "10" (symbol 2), then DD code "1" (symbol 1).
        bw.writeBits(0b10, 2);
        bw.writeBits(0b1, 1);
        bw.pad(64); // slack so the read-border guards never trip on this valid stream

        final Unpack5 u = new Unpack5(false);
        u.feed(bw.toByteArray());
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.readTables()).isTrue();

        final Decode5 ld = u.ld();
        final Decode5 dd = u.dd();
        assertThat(ld.getMaxNum()).isEqualTo(Compress.NC5);
        assertThat(dd.getMaxNum()).isEqualTo(Compress.DC5);
        assertThat(u.ldd().getMaxNum()).isEqualTo(Compress.LDC5);
        assertThat(u.rd().getMaxNum()).isEqualTo(Compress.RC5);
        assertThat(u.bd().getMaxNum()).isEqualTo(Compress.BC5);

        assertThat(u.decodeNumber(ld)).as("LD code 10b decodes to symbol 2").isEqualTo(2);
        assertThat(u.decodeNumber(dd)).as("DD code 1b decodes to symbol 1").isEqualTo(1);
    }

    @Test
    void readTablesWithoutTablePresentReturnsTrueImmediately() throws Exception {
        final Unpack5 u = new Unpack5(false);
        final BitWriter bw = new BitWriter();
        writeBlockHeader(bw, 0x40, 1, 1); // LastBlock, TablePresent=0
        bw.pad(64);
        u.feed(bw.toByteArray());
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.isTablePresent()).isFalse();
        assertThat(u.readTables()).isTrue();
    }

    @Test
    void readTablesRejectsTruncatedTableStream() throws Exception {
        final Unpack5 u = new Unpack5(false);
        final BitWriter bw = new BitWriter();
        writeBlockHeader(bw, 0x80 | 0x40, 1, 1); // TablePresent=1
        bw.writeBits(4, 4); // only one BD nibble, then the stream is cut short
        final byte[] full = bw.toByteArray();
        // readTop=20 leaves room for the 3-byte header (needs >=7) but trips the -25 BD guard.
        u.feed(full, 20);
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.readTables()).as("truncated table stream rejected, no crash").isFalse();
    }

    @Test
    void extraDistFlagStored() {
        assertThat(new Unpack5(true).isExtraDist()).isTrue();
        assertThat(new Unpack5(false).isExtraDist()).isFalse();
    }

    // ---- crafting helpers --------------------------------------------------------------------

    /** RAR5 block header: alignment already at a byte boundary, flags byte, checksum byte, 1 size byte. */
    private static byte[] blockHeaderBytes(final int flags, final int bitSize, final int blockSize, final boolean corruptChecksum) {
        final int flagsByte = (flags & ~0x07) | ((bitSize - 1) & 0x07); // ByteCount=1 => flag bits 3,4 clear
        int checksum = (0x5a ^ flagsByte ^ blockSize ^ (blockSize >>> 8) ^ (blockSize >>> 16)) & 0xff;
        if (corruptChecksum) {
            checksum ^= 0xff;
        }
        return new byte[]{(byte) flagsByte, (byte) checksum, (byte) blockSize};
    }

    private static void writeBlockHeader(final BitWriter bw, final int flags, final int bitSize, final int blockSize) {
        for (final byte b : blockHeaderBytes(flags, bitSize, blockSize, false)) {
            bw.writeBits(b & 0xff, 8);
        }
    }

    /** Minimal MSB-first bit writer matching {@code getbits} order. */
    private static final class BitWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int acc;
        private int nBits;

        void writeBits(final int value, final int count) {
            for (int i = count - 1; i >= 0; i--) {
                acc = (acc << 1) | ((value >>> i) & 1);
                if (++nBits == 8) {
                    out.write(acc & 0xff);
                    acc = 0;
                    nBits = 0;
                }
            }
        }

        void pad(final int bytes) {
            if (nBits > 0) {
                out.write((acc << (8 - nBits)) & 0xff);
                acc = 0;
                nBits = 0;
            }
            for (int i = 0; i < bytes; i++) {
                out.write(0);
            }
        }

        byte[] toByteArray() {
            if (nBits > 0) {
                pad(0);
            }
            return out.toByteArray();
        }
    }
}
