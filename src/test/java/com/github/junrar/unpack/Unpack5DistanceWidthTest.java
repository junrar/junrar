/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.ArchiveOptions;
import com.github.junrar.unpack.decode.Compress;
import com.github.junrar.unpack.CraftedRar5Stream.BitWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.2 (issue #34) distance-width regression: an LZ distance is 64-bit
 * ({@code d861246:unpack50.cpp:76}, {@code size_t Distance}), not 32-bit.
 *
 * <p>Widening it is a prerequisite for {@code ExtraDist} — slot 79 gives {@code DBits = 38}, which
 * an {@code int} cannot even shift — but it also fixes plain RAR5, where the top distance slot
 * already overflows 32 bits. unrar patches the 32-bit build's truncation out explicitly
 * ({@code :108-114}: distances that wrap to a multiple of 4 GB are replaced with {@code -1} so
 * {@code CopyString} zero-fills them); junrar was reproducing the truncation instead of the
 * 64-bit result.
 */
class Unpack5DistanceWidthTest {

    /** The window floor: the crafted stream wraps it, which is what makes stale bytes visible. */
    private static final int WINDOW = Unpack5Window.MIN_ALLOC;

    /**
     * A distance that is an exact multiple of 4 GB must zero-fill, not wrap to a tiny self-copy.
     *
     * <p>Reachable inside plain RAR5, no {@code ExtraDist} needed: the top distance slot 63 gives
     * {@code DBits = 63/2 - 1 = 30}, so {@code 1 + (3 << 30) + (0x3ffffff << 4) + 15} is exactly
     * {@code 0x1_0000_0000}. Truncated to {@code int} that is 0 — and a zero distance fails the
     * {@code Distance > UnpPtr} guard, so it copies the window onto itself and resurrects the
     * previous window pass byte-for-byte, while also losing the three length bumps a real
     * &gt; 0x40000 distance earns. The 64-bit value is far past the window and must zero-fill.
     */
    @Test
    void distanceThatIsAnExactMultipleOfFourGigabytesZeroFills() throws Exception {
        final BitWriter content = new BitWriter();
        CraftedRar5Stream.writeTables(content, Compress.DC5);
        // Fill the whole window with 'A' so it wraps: only then does a self-copy read back real
        // bytes instead of the never-written zeros a fresh window would return.
        for (int i = 0; i < WINDOW; i++) {
            content.writeBits(0, 1);              // LD code for literal 'A'
        }
        content.writeBits(0b10, 2);               // LD code for slot 262 -> match, base length 2
        content.writeBits(0b1, 1);                // DD code for slot 63 -> DBits = 30
        content.writeBits(0x3ffffff, 26);         // the DBits-4 raw bits, all ones
        content.writeBits(0b1, 1);                // LDD code for symbol 15

        final long distance = 1L + (3L << 30) + (0x3ffffffL << 4) + 15L;
        assertThat(distance).as("the crafted distance is exactly 4 GB").isEqualTo(1L << 32);
        // Base length 2, then +1 for each of the >0x100 / >0x2000 / >0x40000 thresholds.
        final int matchLength = 5;

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Unpack5 u = new Unpack5(CraftedRar5Stream.collectingIO(CraftedRar5Stream.frame(content), out), false);
        u.init(WINDOW, false, ArchiveOptions.DEFAULT_MAX_DICTIONARY_SIZE);
        u.setDestSize(WINDOW + matchLength);
        u.unpack5(false);

        final byte[] expected = new byte[WINDOW + matchLength];
        Arrays.fill(expected, 0, WINDOW, (byte) 'A');
        assertThat(out.toByteArray())
            .as("a 4 GB distance is past the window: zero-fill, not a self-copy of the previous pass")
            .containsExactly(expected);
    }
}
