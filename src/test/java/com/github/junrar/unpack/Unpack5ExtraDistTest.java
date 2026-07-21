/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.ArchiveOptions;
import com.github.junrar.unpack.CraftedRar5Stream.BitWriter;
import com.github.junrar.unpack.decode.Compress;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.2 (issue #34) ExtraDist acceptance: version 70 selects the 80-slot distance alphabet
 * ({@code DCX}) and the extended distance decode, while version 50 — RAR5, or a RAR7 header
 * {@code FCI_RAR5_COMPAT} demoted — keeps the 64-slot alphabet ({@code DCB}). Mirrors
 * {@code d861246:unpack50.cpp:647,710} (table sizing) and {@code :92-104} (distance arithmetic).
 *
 * <p>Driven from crafted streams, the M3.6 (crafted tables) and M3.8 (hostile filter rows)
 * precedent, because no genuine RAR7 archive can reach these paths at M4.2: {@code rar 7.23} only
 * records algorithm version 1 once the dictionary passes the 4 GB that RAR5's four dict bits can
 * encode, so every real RAR7 stream declares a window larger than a Java {@code byte[]} can even
 * address. M4.3 (issue #35) is what makes an archive-level oracle possible.
 */
class Unpack5ExtraDistTest {

    /**
     * The distance alphabet sizes the table read must produce, and with them the position of every
     * slice that follows: {@code LDD} at {@code NC + DCodes} and {@code RD} at
     * {@code NC + DCodes + LDC}. Reading a {@code DCX} block as {@code DCB} (or the reverse) would
     * misplace both, so each row decodes one symbol out of all three tables.
     */
    @Test
    void extraDistReadsTheEightySlotDistanceTableAndTheSlicesBehindIt() throws Exception {
        final Unpack5 u = readTablesFor(true, Compress.DCX5);

        assertThat(u.dd().getMaxNum()).as("DCX").isEqualTo(80);
        assertThat(u.ld().getMaxNum()).isEqualTo(Compress.NC5);
        assertThat(u.ldd().getMaxNum()).isEqualTo(Compress.LDC5);
        assertThat(u.rd().getMaxNum()).isEqualTo(Compress.RC5);

        assertThat(u.decodeNumber(u.dd())).as("top distance slot").isEqualTo(Compress.DCX5 - 1);
        assertThat(u.decodeNumber(u.ldd())).as("LDD slice sits at NC + 80").isEqualTo(1);
        assertThat(u.decodeNumber(u.rd())).as("RD slice sits at NC + 80 + LDC").isEqualTo(Compress.RC5 - 1);
    }

    @Test
    void baseDistanceKeepsTheSixtyFourSlotTableAndItsOwnSliceOffsets() throws Exception {
        final Unpack5 u = readTablesFor(false, Compress.DC5);

        assertThat(u.dd().getMaxNum()).as("DCB").isEqualTo(64);
        assertThat(u.ldd().getMaxNum()).isEqualTo(Compress.LDC5);
        assertThat(u.rd().getMaxNum()).isEqualTo(Compress.RC5);

        assertThat(u.decodeNumber(u.dd())).as("top distance slot").isEqualTo(Compress.DC5 - 1);
        assertThat(u.decodeNumber(u.ldd())).as("LDD slice sits at NC + 64").isEqualTo(1);
        assertThat(u.decodeNumber(u.rd())).as("RD slice sits at NC + 64 + LDC").isEqualTo(Compress.RC5 - 1);
    }

    /**
     * The extended distance decode itself. Slot 79 gives {@code DBits = 79/2 - 1 = 38}, past the
     * {@code DBits > 36} threshold where unrar switches from {@code getbits32} to {@code getbits64}
     * ({@code d861246:unpack50.cpp:95-99}) because the 34-bit raw field no longer fits a 32-bit
     * read. The distance is far beyond any M4-era window, so the visible output is a zero-fill of
     * the right length; the value itself is pinned through the recent-distance list.
     */
    @Test
    void topExtendedSlotDecodesAnEightHundredGigabyteDistance() throws Exception {
        final long raw = (1L << 33) | 1L;         // the 34-bit DBits-4 field, top and bottom bits set
        final long distance = 1L + (3L << 38) + (raw << 4) + 1L;
        assertThat(distance).as("recomputed independently of the engine").isEqualTo(962072674322L);

        final BitWriter content = new BitWriter();
        writeTables(content, Compress.DCX5);
        content.writeBits(0, 1);                  // LD code for literal 'A'
        content.writeBits(0b10, 2);               // LD code for slot 262 -> match, base length 2
        content.writeBits(0b11, 2);               // DD code for slot 79 -> DBits = 38
        content.writeBits(raw, 38 - 4);           // the DBits-4 raw bits, read through getbits64
        content.writeBits(0b1, 1);                // LDD code for symbol 1 -> the low distance bits

        // Base length 2, then +1 for each of the >0x100 / >0x2000 / >0x40000 thresholds.
        final int matchLength = 5;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(CraftedRar5Stream.collectingIO(CraftedRar5Stream.frame(content), out), true);
        u.init(Unpack5Window.MIN_ALLOC, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(1 + matchLength);
        u.unpack5(false);

        assertThat(u.oldDist()[0])
            .as("a 34-bit raw field only survives the 64-bit read: getbits32 would shift by -2")
            .isEqualTo(distance);
        assertThat(out.toByteArray())
            .as("the distance is past the window, so the match zero-fills")
            .containsExactly('A', 0, 0, 0, 0, 0);
    }

    /**
     * The first slot that needs the 64-bit read: slot 77 gives {@code DBits = 37}, so the raw field
     * is 33 bits and {@code getbits32} would be asked to shift by {@code 36 - 37 = -1} — which Java
     * masks to 31, silently collapsing the field to a single bit.
     */
    @Test
    void firstExtendedSlotAboveTheThirtyTwoBitReadKeepsItsFullField() throws Exception {
        final long raw = (1L << 32) | 1L;         // the 33-bit DBits-4 field, top and bottom bits set
        final long distance = 1L + (3L << 37) + (raw << 4) + 1L;
        assertThat(distance).as("recomputed independently of the engine").isEqualTo(481036337170L);

        final BitWriter content = new BitWriter();
        writeTables(content, Compress.DCX5);
        content.writeBits(0, 1);                  // LD code for literal 'A'
        content.writeBits(0b10, 2);               // LD code for slot 262 -> match, base length 2
        content.writeBits(0b10, 2);               // DD code for slot 77 -> DBits = 77/2 - 1 = 37
        content.writeBits(raw, 37 - 4);           // the DBits-4 raw bits, read through getbits64
        content.writeBits(0b1, 1);                // LDD code for symbol 1 -> the low distance bits

        final int matchLength = 5;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(CraftedRar5Stream.collectingIO(CraftedRar5Stream.frame(content), out), true);
        u.init(Unpack5Window.MIN_ALLOC, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(1 + matchLength);
        u.unpack5(false);

        assertThat(u.oldDist()[0])
            .as("a 33-bit field cannot come out of a 32-bit read")
            .isEqualTo(distance);
        assertThat(out.toByteArray()).containsExactly('A', 0, 0, 0, 0, 0);
    }

    /**
     * The {@code DBits = 36} boundary, one slot below the 64-bit read. The raw field is then a
     * full 32 bits and the shift is zero, so the {@code getbits32} result reaches the distance
     * unshifted — and it is a C++ {@code uint}. Widening it as a signed {@code int} would flip a
     * field with bit 31 set into a large subtraction (no-go C15). Plain RAR5 never gets here: its
     * top slot 63 gives {@code DBits = 30}, which always shifts at least one bit off.
     */
    @Test
    void boundarySlotWithAFullWidthRawFieldTreatsItAsUnsigned() throws Exception {
        final long raw = 0x80000001L;             // the 32-bit DBits-4 field, bit 31 set
        final long distance = 1L + (2L << 36) + (raw << 4) + 1L;
        assertThat(distance).as("recomputed independently of the engine").isEqualTo(171798691858L);

        final BitWriter content = new BitWriter();
        writeTables(content, Compress.DCX5);
        content.writeBits(0, 1);                  // LD code for literal 'A'
        content.writeBits(0b10, 2);               // LD code for slot 262 -> match, base length 2
        content.writeBits(0b01, 2);               // DD code for slot 74 -> DBits = 74/2 - 1 = 36
        content.writeBits(raw, 36 - 4);           // the DBits-4 raw bits, read through getbits32
        content.writeBits(0b1, 1);                // LDD code for symbol 1 -> the low distance bits

        final int matchLength = 5;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(CraftedRar5Stream.collectingIO(CraftedRar5Stream.frame(content), out), true);
        u.init(Unpack5Window.MIN_ALLOC, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(1 + matchLength);
        u.unpack5(false);

        assertThat(u.oldDist()[0])
            .as("a sign-extended raw field would subtract 34 GB instead of adding it")
            .isEqualTo(distance);
        assertThat(out.toByteArray()).containsExactly('A', 0, 0, 0, 0, 0);
    }

    /** The flag is per entry, not per engine — a solid stream may mix versions. */
    @Test
    void switchingTheFlagReshapesTheTableOnTheSameEngine() throws Exception {
        final Unpack5 u = new Unpack5(true);
        feedTables(u, Compress.DCX5);
        assertThat(u.dd().getMaxNum()).isEqualTo(Compress.DCX5);

        u.setExtraDist(false);
        feedTables(u, Compress.DC5);
        assertThat(u.dd().getMaxNum()).isEqualTo(Compress.DC5);
    }

    // ---- crafting helpers ------------------------------------------------------------------------

    private static Unpack5 readTablesFor(final boolean extraDist, final int dCodes) throws Exception {
        final Unpack5 u = new Unpack5(extraDist);
        feedTables(u, dCodes);
        return u;
    }

    private static void feedTables(final Unpack5 u, final int dCodes) throws Exception {
        final BitWriter bw = new BitWriter();
        writeTables(bw, dCodes);
        bw.writeBits(0b11, 2);                    // DD code for the top distance slot
        bw.writeBits(0b1, 1);                     // LDD code for symbol 1
        bw.writeBits(0b1, 1);                     // RD code for symbol 43
        u.feed(CraftedRar5Stream.frame(bw));
        assertThat(u.readBlockHeader()).isTrue();
        assertThat(u.readTables()).isTrue();
    }

    /**
     * {@link CraftedRar5Stream#writeTables} plus codes in the RD slice, so a test can prove the
     * last slice offset too. BD stays the identity map; two symbols of each of DD, LDD and RD take
     * a 1-bit code, and canonical order gives the higher symbol of every pair the {@code 1}.
     *
     * <p>The three slices deliberately put their codes in <em>different</em> positions — DD and RD
     * at the top of their alphabet, LDD at the bottom. Placing them all at the top would make a
     * 16-entry slice misalignment invisible: the tail of an 80-slot DD carries the same pattern a
     * 16-entry LDD would, so the wrong offset decodes to the right symbol by accident. DD takes four
     * 2-bit codes rather than two 1-bit ones so the tests can reach every {@code DBits} case around
     * the 32/64-bit read boundary: slots 74, 77 and 79 give {@code DBits} 36, 37 and 38.
     */
    private static void writeTables(final BitWriter bw, final int dCodes) {
        for (int i = 0; i < Compress.BC5; i++) {
            bw.writeBits(i < 16 ? 4 : 0, 4);
        }
        final int ddOff = Compress.NC5;
        final int lddOff = ddOff + dCodes;
        final int rdOff = lddOff + Compress.LDC5;
        final int tableSize = rdOff + Compress.RC5;
        for (int i = 0; i < tableSize; i++) {
            final int len;
            if (i == 'A') {
                len = 1;
            } else if (i == 262 || i == 300) {
                len = 2;
            } else if (i == ddOff + dCodes - 16 || i == ddOff + dCodes - 6
                || i == ddOff + dCodes - 3 || i == ddOff + dCodes - 1) {
                len = 2;                                  // DD slots 64, 74, 77, 79 -> DBits 31, 36, 37, 38
            } else if (i == lddOff || i == lddOff + 1) {
                len = 1;                                  // LDD symbols 0, 1
            } else if (i >= rdOff + Compress.RC5 - 2) {
                len = 1;                                  // RD symbols 42, 43
            } else {
                len = 0;
            }
            bw.writeBits(len, 4);
        }
    }
}
