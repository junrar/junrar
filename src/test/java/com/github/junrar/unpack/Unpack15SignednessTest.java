package com.github.junrar.unpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link Unpack15#decodeNum(int, int, int[], int[])} — the Huffman-position decoder
 * shared by every RAR 1.5 unpack path (shortLZ/longLZ/huffDecode/getFlagsBuf) — against
 * {@code Num} values with bits set through the masked range. {@code decodeNum} masks
 * {@code Num} to {@code 0xfff0} before the shift, so the composed value is always
 * non-negative; these tests still pin the exact arithmetic (table walk + {@code >>>}) that a
 * shift-width or table-index regression would break. Expected values are hand-derived
 * against Unpack15's own static tables (derivation in each test's comment); huffDecode,
 * shortLZ, longLZ and getFlagsBuf themselves need full decoder state and are exercised by
 * {@code ArchiveTest.testAudioDecompression} (audio/BoatModernEnglish-regular-unpack15-*.rar) —
 * see signedness-audit.md for the justified deferral.
 */
class Unpack15SignednessTest {

    private static Unpack15 newUnpack15() {
        return new Unpack15() {
            @Override
            protected void unpInitData(final boolean solid) {
                // decodeNum only reads its own arguments plus Unpack15's static tables
            }
        };
    }

    @Test
    void givenMaxMaskedNum_whenDecodeNumWithDecL1_thenReturnsHandDerivedPosition()
            throws ReflectiveOperationException {
        Unpack15 unpack = newUnpack15();
        int[] decL1 = getStaticIntArray("DecL1");
        int[] posL1 = getStaticIntArray("PosL1");

        // Num &= 0xfff0 -> 0xfff0 (65520); walks decL1 through index 9 (decL1[10]=0xffff > Num),
        // StartPos 2->12; (65520 - decL1[9]=0xf200=61952) >>> (16-12) = 3568 >>> 4 = 223;
        // + posL1[12]=32 => 255.
        assertThat(unpack.decodeNum(0xFFF0, 2, decL1, posL1)).isEqualTo(255);
    }

    @Test
    void givenExactTableBoundaryNum_whenDecodeNumWithDecHf0_thenReturnsHandDerivedPosition()
            throws ReflectiveOperationException {
        Unpack15 unpack = newUnpack15();
        int[] decHf0 = getStaticIntArray("DecHf0");
        int[] posHf0 = getStaticIntArray("PosHf0");

        // Num &= 0xfff0 -> 0x8000 (32768); decHf0[0]=0x8000 <= Num (equal counts),
        // decHf0[1]=0xc000 > Num, StartPos 4->5; (32768 - decHf0[0]) >>> (16-5) = 0 >>> 11 = 0;
        // + posHf0[5]=8 => 8. The subtracted operand is exactly 0 here, so a wrong shift
        // AMOUNT would still return 8 (0 >>> anything = 0) — givenMaxMaskedNum's nonzero
        // operand (3568) is what discriminates shift-amount mutations; this case pins the
        // table-boundary equality (`DecTab[I] <= Num`) and StartPos/PosTab indexing instead.
        assertThat(unpack.decodeNum(0x8000, 4, decHf0, posHf0)).isEqualTo(8);
    }

    private static int[] getStaticIntArray(final String name) throws ReflectiveOperationException {
        Field field = Unpack15.class.getDeclaredField(name);
        field.setAccessible(true);
        return (int[]) field.get(null);
    }
}
