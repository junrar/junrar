package com.github.junrar.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extends {@code com.github.junrar.LargeEntryContractTest}'s C13 coverage: that suite's 2^31
 * boundary cannot distinguish the correct {@code (int)(pointer >>> BLOCK_SHIFT)} block index
 * from a truncate-before-shift bug, because both formulas coincide exactly at 2^31. This
 * pins an offset with bits set ABOVE bit 31 (2^32 + BLOCK_SIZE), where the two diverge:
 * shift-then-truncate resolves to block 8388609, truncate-then-shift resolves to block 1.
 */
class RandomAccessStreamSignednessTest {

    private static final long TARGET = 4_294_967_808L; // 2^32 + BLOCK_SIZE, block-aligned

    @Test
    @Timeout(60)
    void givenOffsetWithBitsAboveBit31_whenReadThroughBlockCache_thenUsesShiftBeforeTruncateIndex() throws Exception {
        int blockShift = getStaticInt("BLOCK_SHIFT");
        int blockSize = getStaticInt("BLOCK_SIZE");
        int correctBlockIndex = (int) (TARGET >>> blockShift);
        int truncateFirstBlockIndex = ((int) TARGET) >>> blockShift;
        assertThat(TARGET & (blockSize - 1)).as("target must be block-aligned for this seed").isZero();
        assertThat(correctBlockIndex).as("the two formulas must diverge for this target")
            .isNotEqualTo(truncateFirstBlockIndex);

        byte[] block = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            block[i] = SyntheticByteChannel.valueAt(TARGET + i);
        }

        RandomAccessInputStream stream = new RandomAccessInputStream(new SyntheticByteChannel());
        Vector<byte[]> data = getField(stream, "data");
        data.setSize(correctBlockIndex + 1);
        data.setElementAt(block, correctBlockIndex);
        setField(stream, "length", TARGET + blockSize);
        stream.seek(TARGET);

        byte[] read = new byte[8];
        int count = stream.read(read, 0, read.length);

        assertThat(count).isEqualTo(read.length);
        for (int i = 0; i < read.length; i++) {
            assertThat(read[i]).as("byte at offset %d", TARGET + i).isEqualTo(SyntheticByteChannel.valueAt(TARGET + i));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws ReflectiveOperationException {
        Field field = RandomAccessInputStream.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = RandomAccessInputStream.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getStaticInt(String name) throws ReflectiveOperationException {
        Field field = RandomAccessInputStream.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

}
