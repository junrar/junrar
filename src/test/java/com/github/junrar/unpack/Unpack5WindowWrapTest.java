/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.ArchiveOptions;
import com.github.junrar.unpack.decode.Compress;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * M4.3 (issue #35) coverage for LZ matches that straddle the window end — the case
 * {@code d861246:unpackinline.cpp:105-112} keeps its slow {@code WrapUp} loop for, and the one no
 * archive fixture reaches: {@code rar}'s own dictionary is a power of two aligned with its match
 * placement, so across every committed RAR5 fixture and every fractional re-declaration of them,
 * no match happens to sit across a window boundary. Mutating either {@code WrapUp} out of
 * {@link Unpack5#copyString} therefore survives the whole archive matrix, so the streams here are
 * crafted to put a match across the boundary on purpose, from both sides.
 *
 * <p>The crafted stream serializes a Huffman <em>bit-length table</em> — a header data format, not
 * the LZ compression algorithm — so no licensed compression logic is reproduced.
 */
@Timeout(60)
class Unpack5WindowWrapTest {

    private static final int WIN = 0x40000;

    @Test
    void matchDestinationCrossingTheWindowEndWrapsToTheStart() throws Exception {
        // Fill to one byte short of a full window, then emit a length-6 match: positions
        // WIN-2 .. WIN+3 straddle the end, so the destination pointer must wrap mid-match.
        final byte[] out = decode(WIN - 2, false);
        assertThat(out).hasSize(WIN + 4);
        assertThat(out).containsOnly((byte) 0x41);
    }

    @Test
    void matchSourceCrossingTheWindowEndWrapsToTheStart() throws Exception {
        // Same, plus a second match issued after the wrap whose distance reaches back past the
        // window end: its source pointer runs WIN-2, WIN-1, then must wrap to 0.
        final byte[] out = decode(WIN - 2, true);
        assertThat(out).hasSize(WIN + 10);
        assertThat(out).containsOnly((byte) 0x41);
    }

    /**
     * Decode {@code literals} literal {@code 'A'}s, then a length-6 distance-4 match, and
     * optionally a second length-6 distance-6 match right after it.
     */
    private static byte[] decode(final int literals, final boolean secondMatch) throws Exception {
        final CraftedRar5Stream.BitWriter content = new CraftedRar5Stream.BitWriter();
        writeTables(content);
        for (int i = 0; i < literals; i++) {
            content.writeBits(0, 1); // literal 'A'
        }
        content.writeBits(0b10, 2); // length slot 266 -> length 6
        content.writeBits(0, 1); // distance slot 3 -> distance 4 (no extra bits)
        int total = literals + 6;
        if (secondMatch) {
            content.writeBits(0b10, 2); // length 6 again
            content.writeBits(1, 1); // distance slot 4 -> base 5, one raw bit
            content.writeBits(1, 1); //   raw bit set -> distance 6
            total += 6;
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u =
                new Unpack5(
                        CraftedRar5Stream.collectingIO(CraftedRar5Stream.frame(content), out),
                        false);
        u.init(WIN, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(total);
        u.unpack5(false);
        return out.toByteArray();
    }

    /**
     * BD identity table plus a main table giving literal {@code 'A'} the 1-bit code {@code 0},
     * length slot 266 (= 262 + 4, so {@code SlotToLength(4) = 6} with no extra bits) the 2-bit
     * code {@code 10}, and distance slots 3 and 4 the 1-bit codes {@code 0} and {@code 1}.
     * Canonical order assigns equal-length codes by ascending symbol. Symbol 300 completes the
     * 2-bit pair and is never emitted; LDD and RD stay empty because neither is read on this path
     * (slot 3 carries no extra bits, slot 4 reads a raw field rather than a low-distance symbol).
     */
    private static void writeTables(final CraftedRar5Stream.BitWriter bw) {
        for (int i = 0; i < Compress.BC5; i++) {
            bw.writeBits(i < 16 ? 4 : 0, 4);
        }
        final int ddOff = Compress.NC5;
        for (int i = 0; i < Compress.HUFF_TABLE_SIZE5; i++) {
            final int len;
            if (i == 0x41) {
                len = 1;
            } else if (i == 266 || i == 300) {
                len = 2;
            } else if (i == ddOff + 3 || i == ddOff + 4) {
                len = 1;
            } else {
                len = 0;
            }
            bw.writeBits(len, 4);
        }
    }
}
