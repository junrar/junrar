package com.github.junrar.unpack;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.ArchiveOptions;
import com.github.junrar.unpack.decode.Compress;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * M3.8 (issue #29) unit tests for the RAR5 filter layer: the four transform kernels against
 * synthetic filter blocks hand-traced from {@code d861246:unpack50.cpp:427-485} (ARM has no
 * archive fixture — rar has not emitted it when compressing since 5.80, recorded unattainable),
 * the {@code ReadFilter} block-length clamp ({@code MAX_FILTER_BLOCK_SIZE}), and the
 * {@code MAX_UNPACK_FILTERS} flood policy (flush-then-drop, never an exception — matching
 * unrar's {@code AddFilter}).
 */
@Timeout(30)
class Unpack5FilterTest {

    private static Unpack5Filter filter(final int type, final int blockLength) {
        final Unpack5Filter f = new Unpack5Filter();
        f.setType(type);
        f.setBlockLength(blockLength);
        return f;
    }

    // ---- E8 / E8E9 (x86 call/jmp rel32 -> abs32) ----------------------------------------------

    @Test
    void e8ConvertsPositiveCallBelowFileSize() {
        final Unpack5 u = new Unpack5(false);
        // E8 at 0, addr 0x10 (positive, < 0x1000000): addr -= offset, offset = curPos + 0 = 1.
        final byte[] data = {(byte) 0xE8, 0x10, 0x00, 0x00, 0x00, 0x00};
        final byte[] out =
                u.applyFilter(data, data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(out).isSameAs(data);
        assertThat(out).containsExactly(0xE8, 0x0F, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void e8ConvertsNegativeCallReachingZero() {
        final Unpack5 u = new Unpack5(false);
        // E8 at 1 -> offset = 2; addr = -2 (0xFFFFFFFE): addr + offset = 0 >= 0, so
        // addr += 0x1000000 -> 0x00FFFFFE.
        final byte[] data = {
            0x00, (byte) 0xE8, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00
        };
        final byte[] out =
                u.applyFilter(data, data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(out).containsExactly(0x00, 0xE8, 0xFE, 0xFF, 0xFF, 0x00, 0x00);
    }

    @Test
    void e8LeavesNegativeCallStayingNegative() {
        final Unpack5 u = new Unpack5(false);
        // addr = -0x100000 stays negative after += offset -> untouched.
        final byte[] data = {(byte) 0xE8, 0x00, 0x00, (byte) 0xF0, (byte) 0xFF, 0x00};
        final byte[] out =
                u.applyFilter(data.clone(), data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(out).containsExactly(0xE8, 0x00, 0x00, 0xF0, 0xFF, 0x00);
    }

    @Test
    void e8LeavesAddressAtOrAboveFileSize() {
        final Unpack5 u = new Unpack5(false);
        // addr = 0x1000000 (== FileSize): (addr - FileSize) has bit 31 clear -> untouched.
        final byte[] data = {(byte) 0xE8, 0x00, 0x00, 0x00, 0x01, 0x00};
        final byte[] out =
                u.applyFilter(data.clone(), data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(out).containsExactly(0xE8, 0x00, 0x00, 0x00, 0x01, 0x00);
    }

    @Test
    void e8LeavesJmpOpcodeButE8e9ConvertsIt() {
        final byte[] data = {(byte) 0xE9, 0x10, 0x00, 0x00, 0x00, 0x00};
        final Unpack5 u = new Unpack5(false);
        final byte[] e8Out =
                u.applyFilter(data.clone(), data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(e8Out)
                .as("E8 filter ignores E9")
                .containsExactly(0xE9, 0x10, 0x00, 0x00, 0x00, 0x00);
        final byte[] e8e9Out =
                u.applyFilter(data.clone(), data.length, filter(Compress.FILTER_E8E9, data.length));
        assertThat(e8e9Out)
                .as("E8E9 filter converts E9")
                .containsExactly(0xE9, 0x0F, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void e8IgnoresTruncatedTrailingCall() {
        final Unpack5 u = new Unpack5(false);
        // E8 at the last position has no room for a 4-byte address (CurPos+4 < DataSize guard).
        final byte[] data = {0x00, 0x00, 0x00, 0x00, (byte) 0xE8};
        final byte[] out =
                u.applyFilter(data.clone(), data.length, filter(Compress.FILTER_E8, data.length));
        assertThat(out).containsExactly(0x00, 0x00, 0x00, 0x00, 0xE8);
    }

    // ---- ARM (BL rel24 -> abs24, condition 0xEB only) -----------------------------------------

    @Test
    void armConvertsBlAndLeavesOtherConditions() {
        final Unpack5 u = new Unpack5(false);
        final byte[] data = {
            0x00, 0x00, 0x00, (byte) 0xE1, // MOV-like word: condition != 0xEB, untouched
            0x10, 0x00, 0x00, (byte) 0xEB, // BL imm24=0x10 at curPos=4
        };
        final byte[] out =
                u.applyFilter(data, data.length, filter(Compress.FILTER_ARM, data.length));
        assertThat(out).isSameAs(data);
        // offset = 0x10 - (0 + 4)/4 = 0x0F, written back over the low 3 bytes.
        assertThat(out).containsExactly(0x00, 0x00, 0x00, 0xE1, 0x0F, 0x00, 0x00, 0xEB);
    }

    @Test
    void armSubtractionWrapsAcrossImm24() {
        final Unpack5 u = new Unpack5(false);
        // imm24 = 0: offset = 0 - 1 wraps to 0xFFFFFF over 3 bytes (uint arithmetic, truncated).
        final byte[] data = {0x00, 0x00, 0x00, (byte) 0xEB, 0x00, 0x00, 0x00, (byte) 0xEB};
        final byte[] out =
                u.applyFilter(data, data.length, filter(Compress.FILTER_ARM, data.length));
        assertThat(out).containsExactly(0x00, 0x00, 0x00, 0xEB, 0xFF, 0xFF, 0xFF, 0xEB);
    }

    // ---- DELTA (channel de-interleave with running PrevByte) ----------------------------------

    @Test
    void deltaDeinterleavesTwoChannels() {
        final Unpack5 u = new Unpack5(false);
        final Unpack5Filter f = filter(Compress.FILTER_DELTA, 6);
        f.setChannels(2);
        final byte[] data = {1, 2, 3, 4, 5, 6};
        final byte[] out = u.applyFilter(data, data.length, f);
        // Channel 0 (src 1,2,3) -> dst[0],dst[2],dst[4] = -1,-3,-6; channel 1 (src 4,5,6) ->
        // dst[1],dst[3],dst[5] = -4,-9,-15. PrevByte runs per channel.
        assertThat(out).isNotSameAs(data);
        assertThat(out)
                .startsWith((byte) -1, (byte) -4, (byte) -3, (byte) -9, (byte) -6, (byte) -15);
        assertThat(data).as("source copy untouched by DELTA").containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void deltaSingleChannelIsRunningNegativeSum() {
        final Unpack5 u = new Unpack5(false);
        final Unpack5Filter f = filter(Compress.FILTER_DELTA, 4);
        f.setChannels(1);
        final byte[] out = u.applyFilter(new byte[] {(byte) 0xFF, 1, 1, 1}, 4, f);
        // prev = 0-0xFF = 1, then 0, -1, -2.
        assertThat(out).startsWith((byte) 1, (byte) 0, (byte) -1, (byte) -2);
    }

    @Test
    void deltaTailChannelsShorterThanOthers() {
        final Unpack5 u = new Unpack5(false);
        final Unpack5Filter f = filter(Compress.FILTER_DELTA, 5);
        f.setChannels(2);
        // dataSize 5, channels 2: channel 0 gets 3 bytes (dst 0,2,4), channel 1 gets 2 (dst 1,3).
        final byte[] out = u.applyFilter(new byte[] {1, 1, 1, 2, 2}, 5, f);
        assertThat(out).startsWith((byte) -1, (byte) -2, (byte) -2, (byte) -4, (byte) -3);
    }

    // ---- unknown types (4..7): null output, written as nothing (unrar parity) -----------------

    @Test
    void unknownFilterTypeYieldsNull() {
        final Unpack5 u = new Unpack5(false);
        assertThat(u.applyFilter(new byte[8], 8, filter(4, 8))).isNull();
        assertThat(u.applyFilter(new byte[8], 8, filter(7, 8))).isNull();
    }

    // ---- ReadFilter: MAX_FILTER_BLOCK_SIZE clamp (hostile block > 0x400000) -------------------

    @Test
    void readFilterClampsOversizedBlockLengthToZero() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.feed(filterAnnouncement(0, 0x400001, Compress.FILTER_E8, 0));
        final Unpack5Filter f = new Unpack5Filter();
        assertThat(u.readFilter(f)).isTrue();
        assertThat(f.getBlockLength()).as("over-limit block is forced to a no-op").isEqualTo(0);
        assertThat(f.getType()).isEqualTo(Compress.FILTER_E8);
    }

    @Test
    void readFilterClampsBit31BlockLengthToZero() throws Exception {
        // A 4-byte length with bit 31 set is a C++ uint > MAX_FILTER_BLOCK_SIZE; a signed
        // comparison would accept it (no-go C15).
        final Unpack5 u = new Unpack5(false);
        u.feed(filterAnnouncement(0, 0x80000004, Compress.FILTER_E8, 0));
        final Unpack5Filter f = new Unpack5Filter();
        assertThat(u.readFilter(f)).isTrue();
        assertThat(f.getBlockLength()).isEqualTo(0);
    }

    @Test
    void readFilterKeepsLimitBlockLengthAndDeltaChannels() throws Exception {
        final Unpack5 u = new Unpack5(false);
        u.feed(filterAnnouncement(0x1234, 0x400000, Compress.FILTER_DELTA, 3));
        final Unpack5Filter f = new Unpack5Filter();
        assertThat(u.readFilter(f)).isTrue();
        assertThat(f.getBlockStart()).isEqualTo(0x1234);
        assertThat(f.getBlockLength())
                .as("exactly MAX_FILTER_BLOCK_SIZE is legal")
                .isEqualTo(0x400000);
        assertThat(f.getType()).isEqualTo(Compress.FILTER_DELTA);
        assertThat(f.getChannels()).isEqualTo(3);
    }

    @Test
    void readFilterKeepsBit31BlockStartUnsigned() throws Exception {
        // A 4-byte block start with bit 31 set is a C++ uint; sign-extending it would make the
        // window fold in AddFilter negative (no-go C15). M4.3: the field is now a long.
        final Unpack5 u = new Unpack5(false);
        u.feed(filterAnnouncement(0x80000004L, 4, Compress.FILTER_E8, 0));
        final Unpack5Filter f = new Unpack5Filter();
        assertThat(u.readFilter(f)).isTrue();
        assertThat(f.getBlockStart()).isEqualTo(0x80000004L);
    }

    // ---- window folding: remainder, not a single wrap (d861246:unpack50.cpp:228-233) -----------

    @Test
    void filterStartManyWindowsAwayIsFoldedByRemainder() throws Exception {
        // A malformed archive can announce a block start many times the window size. Upstream
        // folds it with % MaxWinSize precisely because subtracting a single window (WrapUp) would
        // leave it outside the window and the filter would silently never apply. 0x300000 is
        // 12 windows of 0x40000, so the remainder lands it at 0 and it applies to the first
        // 4 bytes; a single wrap would leave it at 0x2C0000 and drop it.
        final BitWriter content = new BitWriter();
        writeCraftedTables(content);
        content.writeBits(0b10, 2); // LD code for slot 256 (filter announcement)
        writeFilterVint(content, 0x300000); // blockStart, 12 windows away
        writeFilterVint(content, 4); // blockLength
        content.writeBits(Compress.FILTER_E8, 3);
        for (int i = 0; i < 4; i++) {
            content.writeBits(0, 1); // LD code for literal 0x41
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(collectingIO(frame(content), out), false);
        u.init(0x40000, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(4);
        u.unpack5(false);

        assertThat(out.toByteArray()).containsExactly(0x41, 0x41, 0x41, 0x41);
        assertThat(u.filtersApplied())
                .as("the remainder folds the start into the window, so the filter runs")
                .isEqualTo(1);
    }

    @Test
    void filterBlockStraddlingTheWindowEndIsCopiedOutInTwoParts() throws Exception {
        // The only path that exercises UnpWriteBuf's wrap split: a block whose start sits just
        // below the window end and whose length carries it past, so BlockEnd wraps below
        // BlockStart. Without WrapUp on BlockEnd the copy-out runs off the end of the window.
        final int winSize = 0x40000;
        final int blockLength = 0x20;
        final int leading = winSize - 0x10; // announce with 0x10 bytes of window left
        final int trailing = 0x30;

        final BitWriter content = new BitWriter();
        writeCraftedTables(content);
        for (int i = 0; i < leading; i++) {
            content.writeBits(0, 1);
        }
        content.writeBits(0b10, 2); // filter announcement at unpPtr = winSize - 0x10
        writeFilterVint(content, 0); // blockStart, relative: right here
        writeFilterVint(content, blockLength);
        content.writeBits(Compress.FILTER_E8, 3);
        for (int i = 0; i < trailing; i++) {
            content.writeBits(0, 1);
        }

        final int total = leading + trailing;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(collectingIO(frame(content), out), false);
        u.init(winSize, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(total);
        u.unpack5(false);

        final byte[] expected = new byte[total];
        java.util.Arrays.fill(expected, (byte) 0x41);
        assertThat(out.toByteArray())
                .as("a block wrapping the window end must reassemble byte-identically")
                .isEqualTo(expected);
        assertThat(u.filtersApplied()).as("the wrapping block really was applied").isEqualTo(1);
    }

    // ---- flood policy: > MAX_UNPACK_FILTERS is flush-then-drop, never a throw ------------------

    @Test
    void filterFloodIsDroppedWithoutErrorAndOutputSurvives() throws Exception {
        // Craft a stream whose block announces MAX_UNPACK_FILTERS + 1 filters before any
        // literal: unrar's AddFilter flushes, then drops the whole queue (InitFilters) and
        // keeps going. Literals after the flood must still decode and extract.
        final BitWriter content = new BitWriter();
        writeCraftedTables(content);
        for (int i = 0; i < Compress.MAX_UNPACK_FILTERS + 1; i++) {
            content.writeBits(0b10, 2); // LD code for slot 256 (filter announcement)
            content.writeBits(0, 2); // blockStart: 1 byte
            content.writeBits(0, 8); //   = 0
            content.writeBits(0, 2); // blockLength: 1 byte
            content.writeBits(4, 8); //   = 4
            content.writeBits(Compress.FILTER_E8, 3);
        }
        for (int i = 0; i < 4; i++) {
            content.writeBits(0, 1); // LD code for literal 0x41
        }
        // The block size/bit-size must be exact so the decoder finishes at the block border
        // (LastBlockInFile), the way a real archive ends.
        final int contentBits = content.bitCount();
        final int blockSizeBytes = (contentBits + 7) / 8;
        final int lastByteBits = contentBits - (blockSizeBytes - 1) * 8;
        final BitWriter bw = new BitWriter();
        writeBlockHeader(bw, 0x80 | 0x40, lastByteBits, blockSizeBytes);
        for (final byte b : content.toByteArray()) {
            bw.writeBits(b & 0xff, 8);
        }
        bw.pad(64);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(collectingIO(bw.toByteArray(), out), false);
        u.init(0x40000, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(4);
        u.unpack5(false);

        assertThat(out.toByteArray()).containsExactly(0x41, 0x41, 0x41, 0x41);
        assertThat(u.pendingFilterCount())
                .as("the flooded queue was dropped, not accumulated")
                .isLessThanOrEqualTo(1);
        assertThat(u.filtersApplied())
                .as("only the post-drop filter survives to application")
                .isEqualTo(1);
    }

    // ---- crafting helpers ----------------------------------------------------------------------

    /** Serialize one filter announcement (two vint fields, 3-bit type, DELTA channels). */
    private static byte[] filterAnnouncement(
            final long blockStart, final long blockLength, final int type, final int channels) {
        final BitWriter bw = new BitWriter();
        writeFilterVint(bw, blockStart);
        writeFilterVint(bw, blockLength);
        bw.writeBits(type, 3);
        if (type == Compress.FILTER_DELTA) {
            bw.writeBits(channels - 1, 5);
        }
        bw.pad(64); // slack for the ensure(16) refill guard in external-buffer mode
        return bw.toByteArray();
    }

    /** ReadFilterData wire form: 2-bit byte count - 1, then that many bytes, LSB first. */
    private static void writeFilterVint(final BitWriter bw, final long value) {
        int byteCount = 1;
        while (byteCount < 4 && (value >>> (8 * byteCount)) != 0) {
            byteCount++;
        }
        bw.writeBits(byteCount - 1, 2);
        for (int i = 0; i < byteCount; i++) {
            bw.writeBits((int) ((value >>> (8 * i)) & 0xff), 8);
        }
    }

    /**
     * BD identity table (symbols 0..15 = 4-bit lengths) + a main table where literal 0x41 has the
     * 1-bit code {@code 0}, slot 256 the 2-bit code {@code 10}, and (never-emitted) symbol 300
     * the code {@code 11} — canonical order assigns same-length codes by ascending symbol, so the
     * completing symbol must sit above 256. Same crafting approach as
     * {@code Unpack5Test#readTablesBuildsAllFiveTablesAndDecodes}.
     */
    private static void writeCraftedTables(final BitWriter bw) {
        for (int i = 0; i < Compress.BC5; i++) {
            bw.writeBits(i < 16 ? 4 : 0, 4);
        }
        for (int i = 0; i < Compress.HUFF_TABLE_SIZE5; i++) {
            final int len;
            if (i == 0x41) {
                len = 1;
            } else if (i == 256 || i == 300) {
                len = 2;
            } else {
                len = 0;
            }
            bw.writeBits(len, 4);
        }
    }

    /** ComprDataIO stub: serves the crafted packed stream, collects unpacked output. */
    private static ComprDataIO collectingIO(final byte[] packed, final ByteArrayOutputStream out) {
        return new ComprDataIO(null) {
            private int pos;

            @Override
            public int unpRead(final byte[] addr, final int offset, final int count) {
                final int n = Math.min(count, packed.length - pos);
                if (n <= 0) {
                    return 0;
                }
                System.arraycopy(packed, pos, addr, offset, n);
                pos += n;
                return n;
            }

            @Override
            public void unpWrite(final byte[] addr, final int offset, final int count) {
                out.write(addr, offset, count);
            }
        };
    }

    /** Minimal MSB-first bit writer matching {@code getbits} order (as in {@code Unpack5Test}). */
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

        int bitCount() {
            return out.size() * 8 + nBits;
        }
    }

    /**
     * Wrap crafted block content in a {@code TablePresent | LastBlockInFile} block header whose
     * size and trailing bit count land exactly on the last content bit, the way a real archive's
     * final block does (same shape as the flood test's inline framing).
     */
    private static byte[] frame(final BitWriter content) {
        final int contentBits = content.bitCount();
        final int blockSizeBytes = (contentBits + 7) / 8;
        final int lastByteBits = contentBits - (blockSizeBytes - 1) * 8;
        final BitWriter bw = new BitWriter();
        writeBlockHeader(bw, 0x80 | 0x40, lastByteBits, blockSizeBytes);
        for (final byte b : content.toByteArray()) {
            bw.writeBits(b & 0xff, 8);
        }
        bw.pad(64); // slack for the 30-byte read-border margin getbits relies on
        return bw.toByteArray();
    }

    private static void writeBlockHeader(
            final BitWriter bw, final int flags, final int bitSize, final int blockSize) {
        final int byteCountBits = blockSize > 0xFFFF ? 2 : (blockSize > 0xFF ? 1 : 0);
        final int flagsByte = (flags & ~0x1F) | (byteCountBits << 3) | ((bitSize - 1) & 0x07);
        final int checksum =
                (0x5a ^ flagsByte ^ blockSize ^ (blockSize >>> 8) ^ (blockSize >>> 16)) & 0xff;
        bw.writeBits(flagsByte, 8);
        bw.writeBits(checksum, 8);
        for (int i = 0; i <= byteCountBits; i++) {
            bw.writeBits((blockSize >>> (8 * i)) & 0xff, 8);
        }
    }
}
