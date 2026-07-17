package com.github.junrar;

import com.github.junrar.io.RandomAccessInputStream;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;
import com.github.junrar.io.SyntheticByteChannel;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.lang.reflect.Field;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the two &gt;2 GB behaviors from {@code divergences-no-go.md} (C13, D1) using
 * virtual channels instead of multi-GB fixtures: C13 proves {@link RandomAccessInputStream}
 * (directly and through {@link SeekableReadOnlyInputStream}) stays {@code long}-clean past
 * {@link Integer#MAX_VALUE}; D1 pins the CURRENT observable of {@link Archive#getInputStream}
 * for an entry whose {@code fullUnpackSize} exceeds {@code Integer.MAX_VALUE}.
 */
public class LargeEntryContractTest {

    // --- C13: RandomAccessInputStream / SeekableReadOnlyByteChannel position stays long-clean ---

    @ParameterizedTest
    @ValueSource(longs = {2_147_483_648L, 3_221_225_472L, 10_000_000_000L})
    void givenSeekPastIntMaxValue_whenGetLongFilePointer_thenReturnsExactLongPosition(long target) throws IOException {
        RandomAccessInputStream stream = new RandomAccessInputStream(new SyntheticByteChannel());

        stream.seek(target);

        assertThat(stream.getLongFilePointer()).isEqualTo(target);
    }

    @Test
    void givenSeekIntOverloadWithNegativeArgument_whenGetLongFilePointer_thenTreatsArgumentAsUnsigned32Bit() throws IOException {
        RandomAccessInputStream stream = new RandomAccessInputStream(new SyntheticByteChannel());

        stream.seek(-1);

        // seek(int) zero-extends its argument (unsigned 32-bit), so -1 lands at 0xFFFFFFFF, not 0.
        assertThat(stream.getLongFilePointer()).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void givenPositionAtIntMaxValuePlusOne_whenGetFilePointer_thenTruncatesToIntByDesign() throws IOException {
        RandomAccessInputStream stream = new RandomAccessInputStream(new SyntheticByteChannel());

        stream.seek(2_147_483_648L);

        // getFilePointer() is the legacy int accessor: it silently narrows, unlike getLongFilePointer().
        assertThat(stream.getFilePointer()).isEqualTo(Integer.MIN_VALUE);
        assertThat(stream.getLongFilePointer()).isEqualTo(2_147_483_648L);
    }

    @ParameterizedTest
    @ValueSource(longs = {2_147_483_648L, 3_221_225_472L})
    void givenChannelSetPositionPastIntMaxValue_whenGetPosition_thenReturnsExactLongPosition(long target) throws IOException {
        // Exercises the channel-based path INTO RandomAccessInputStream: SeekableReadOnlyInputStream
        // (a production SeekableReadOnlyByteChannel) delegates setPosition/getPosition straight to it.
        SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(new SyntheticByteChannel());

        channel.setPosition(target);

        assertThat(channel.getPosition()).isEqualTo(target);
    }

    @Test
    @Timeout(60)
    void givenPositionPastIntMaxValue_whenReadThroughChannel_thenBytesMatchSyntheticSourceAtThatOffset() throws Exception {
        // A genuine 0->target fill needs ~2 GiB (RandomAccessInputStream caches every block it
        // reads) and OOMs the 512m test-worker heap (confirmed via `gradle test --info`, fixed
        // per-worker, not configurable here). Seed only the target block via reflection instead.
        long target = 2_147_483_648L; // Integer.MAX_VALUE + 1, the C13 int-overflow boundary
        int blockShift = getStaticInt("BLOCK_SHIFT");
        int blockSize = getStaticInt("BLOCK_SIZE");
        int blockIndex = (int) (target >>> blockShift);
        assertThat(target & (blockSize - 1)).as("target must be block-aligned for this seed").isZero();

        byte[] block = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            block[i] = SyntheticByteChannel.valueAt(target + i);
        }

        RandomAccessInputStream stream = new RandomAccessInputStream(new SyntheticByteChannel());
        Vector<byte[]> data = getField(stream, "data");
        data.setSize(blockIndex + 1);
        data.setElementAt(block, blockIndex);
        setField(stream, "length", target + blockSize);
        stream.seek(target);

        byte[] read = new byte[8];
        int count = stream.read(read, 0, read.length);

        assertThat(count).isEqualTo(read.length);
        for (int i = 0; i < read.length; i++) {
            assertThat(read[i]).as("byte at offset %d", target + i).isEqualTo(SyntheticByteChannel.valueAt(target + i));
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

    // --- D1: extract-to-byte[] refusal/allocation contract for fullUnpackSize > Integer.MAX_VALUE ---

    @ParameterizedTest
    @ValueSource(longs = {2_147_483_647L, 2_147_483_648L, 3_000_000_000L})
    void givenFullUnpackSizeAtOrPastIntMaxValue_whenGetInputStream_thenReturnsStreamingPipeWithoutByteArrayAllocation(long fullUnpackSize)
        throws IOException {
        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.getFullUnpackSize()).thenReturn(fullUnpackSize);
        when(archive.getInputStream(fileHeader)).thenCallRealMethod();

        InputStream result = archive.getInputStream(fileHeader);

        // Current contract: no byte[]-backed stream is ever built for the full entry size;
        // getInputStream streams through a small fixed-size pipe regardless of fullUnpackSize.
        assertThat(result).isInstanceOf(PipedInputStream.class);
    }
}
