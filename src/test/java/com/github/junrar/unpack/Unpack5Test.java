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

    // ---- window-size matrix (reject: engine capability ceiling = 1 GB) -----------------------

    @Test
    void windowMatrixRejects2gAsCapabilityBeforeAllocation() {
        final Unpack5 u = new Unpack5(false);
        final Throwable t = catchThrowable(() -> u.init(2L << 30, false, DEFAULT));
        assertThat(t).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        assertThat(u.window()).as("guard fires before any window allocation").isNull();
    }

    @Test
    void windowMatrixRejects4g() {
        final Unpack5 u = new Unpack5(false);
        final Throwable t = catchThrowable(() -> u.init(4L << 30, false, DEFAULT));
        assertThat(t).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        assertThat(u.window()).isNull();
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
    void readBlockHeaderParsesFlagsAndValidatesChecksum() {
        final Unpack5 u = new Unpack5(false);
        u.feed(blockHeaderBytes(0x80 | 0x40, 3, 0x23, false), 16); // readTop slack for the -7 guard
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.isTablePresent()).isTrue();
        assertThat(u.isLastBlockInFile()).isTrue();
        assertThat(u.getBlockBitSize()).isEqualTo(3);
        assertThat(u.getBlockSize()).isEqualTo(0x23);
    }

    @Test
    void readBlockHeaderRejectsBadChecksum() {
        final Unpack5 u = new Unpack5(false);
        u.feed(blockHeaderBytes(0x80, 1, 0x10, true), 16); // checksum corrupted
        assertThat(u.readBlockHeader()).isFalse();
    }

    // ---- full 5-table read -------------------------------------------------------------------

    @Test
    void readTablesBuildsAllFiveTablesAndDecodes() {
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
    void readTablesWithoutTablePresentReturnsTrueImmediately() {
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
    void readTablesRejectsTruncatedTableStream() {
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
