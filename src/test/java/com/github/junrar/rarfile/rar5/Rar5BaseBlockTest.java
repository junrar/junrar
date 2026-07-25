package com.github.junrar.rarfile.rar5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CorruptHeaderException;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * M3.2 (issue #23) direct-parse unit tests for the RAR5 block framework: the allocation-free
 * header-size boundary contract ({@link Rar5BaseBlock#checkHeaderSize}), the CRC
 * record-vs-fatal split and the Type/Flags/Extra/Data decode ({@link Rar5BaseBlock#parse}),
 * plus the wire-value enum round-trip (the C11 lesson, manual &sect;4.6). Crafted byte arrays
 * throughout -- no channel, no {@code Archive}.
 */
class Rar5BaseBlockTest {

    // ---- crafting helpers -------------------------------------------------

    /** Encodes a value as a RAR5 vint (base-128 LE, continuation bit 0x80). */
    private static byte[] vint(long value) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long x = value;
        while (true) {
            final int b = (int) (x & 0x7f);
            x >>>= 7;
            if (x != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                break;
            }
        }
        return out.toByteArray();
    }

    /** A 3-byte size vint holding {@code value} (must fit 21 bits), for the boundary rows. */
    private static byte[] vint3(int value) {
        return new byte[] {
            (byte) ((value & 0x7f) | 0x80),
            (byte) (((value >>> 7) & 0x7f) | 0x80),
            (byte) ((value >>> 14) & 0x7f)
        };
    }

    private static void writeLen(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    /**
     * Builds a full, CRC-valid RAR5 block. {@code extraSize}/{@code dataSize} are appended as
     * descriptor vints only when non-null (the caller sets the matching flag); {@code padBody}
     * appends zero bytes to grow the header past its descriptors.
     */
    private static byte[] craft(long type, long flags, Long extraSize, Long dataSize, int padBody) {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeLen(body, vint(type));
        writeLen(body, vint(flags));
        if (extraSize != null) {
            writeLen(body, vint(extraSize));
        }
        if (dataSize != null) {
            writeLen(body, vint(dataSize));
        }
        for (int i = 0; i < padBody; i++) {
            body.write(0);
        }
        final byte[] b = body.toByteArray();
        final byte[] sizeV = vint(b.length);
        final int headerSize = 4 + sizeV.length + b.length;
        final byte[] buf = new byte[headerSize];
        System.arraycopy(sizeV, 0, buf, 4, sizeV.length);
        System.arraycopy(b, 0, buf, 4 + sizeV.length, b.length);
        final CRC32 crc = new CRC32();
        crc.update(buf, 4, headerSize - 4);
        final int v = (int) crc.getValue();
        buf[0] = (byte) v;
        buf[1] = (byte) (v >>> 8);
        buf[2] = (byte) (v >>> 16);
        buf[3] = (byte) (v >>> 24);
        return buf;
    }

    // ---- checkHeaderSize: boundary rows (B-S5) ----------------------------

    @Test
    void oneByteSizeVintYieldsExpectedHeaderSize() throws Exception {
        // CRC(4) + size vint = 0x0b (11) -> HeaderSize = 4 + 1 + 11 = 16.
        final byte[] first = {0, 0, 0, 0, 0x0b, 0, 0};
        assertThat(Rar5BaseBlock.checkHeaderSize(first)).isEqualTo(16);
    }

    @Test
    void threeByteSizeVintAtMaxAcceptedShape() throws Exception {
        // BlockSize 0x1FFFF9 in exactly 3 vint bytes -> HeaderSize = 4 + 3 + 0x1FFFF9 = 2 MB.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[4], 0, 4);
        writeLen(out, vint3(0x1FFFF9));
        assertThat(Rar5BaseBlock.checkHeaderSize(out.toByteArray()))
                .isEqualTo(Rar5BaseBlock.MAX_HEADER_SIZE_RAR5);
    }

    @Test
    void fourByteSizeVintRejected() {
        // A 4-byte size vint (0x81 0x80 0x80 0x00 = over-long encoding of BlockSize 1) that
        // terminates inside the buffer, so the read succeeds and the sizeBytes > 3 guard itself
        // must reject it. An 8-byte buffer is needed to reach the guard at all: production
        // callers pass exactly FIRST_READ_SIZE = 7 bytes, where a 4-byte vint cannot terminate
        // and VInt's underrun throw fires first -- checkHeaderSize is public, so the guard
        // still backs the documented 3-byte contract for any other caller. The message
        // assertion is what pins the guard: without it, BlockSize 1 decodes to HeaderSize 9 and
        // parses clean.
        final byte[] first = {0, 0, 0, 0, (byte) 0x81, (byte) 0x80, (byte) 0x80, 0x00};
        assertThat(catchThrowable(() -> Rar5BaseBlock.checkHeaderSize(first)))
                .isExactlyInstanceOf(CorruptHeaderException.class)
                .hasMessageContaining("vint bytes");
    }

    @Test
    void sizeVintFloodingTheSevenByteFirstReadRejected() {
        // The production shape: both Archive callers pass exactly FIRST_READ_SIZE bytes, so a
        // continuation-bit flood exhausts the buffer before the vint terminates and VInt's
        // underrun throw -- not the sizeBytes guard -- is what rejects it.
        final byte[] first = {0, 0, 0, 0, (byte) 0x80, (byte) 0x80, (byte) 0x80};
        assertThat(catchThrowable(() -> Rar5BaseBlock.checkHeaderSize(first)))
                .isExactlyInstanceOf(CorruptHeaderException.class)
                .hasMessageContaining("Truncated variable-length integer");
    }

    @Test
    void zeroBlockSizeRejected() {
        final byte[] first = {0, 0, 0, 0, 0x00, 0, 0};
        assertThat(catchThrowable(() -> Rar5BaseBlock.checkHeaderSize(first)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void headerSizeBelowMinimumRejected() {
        // BlockSize 1, sizeBytes 1 -> HeaderSize 6 < SIZEOF_SHORTBLOCKHEAD5 (7). (S4 analog:
        // a size field at/under the structural minimum, the framework-level nameSize<=0.)
        final byte[] first = {0, 0, 0, 0, 0x01, 0, 0};
        assertThat(catchThrowable(() -> Rar5BaseBlock.checkHeaderSize(first)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void declaredSizeOverMaxRejectedBeforeAllocation() {
        // 3-byte size vint at its max (0x1FFFFF) -> HeaderSize 0x200006 > 2 MB. The reject is a
        // CorruptHeaderException from checkHeaderSize, NOT a BadRarArchiveException from
        // safelyAllocate -- proving the bound fires before any tail buffer is allocated (S1).
        final byte[] first = {0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0x7F};
        assertThat(catchThrowable(() -> Rar5BaseBlock.checkHeaderSize(first)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    // ---- parse: CRC record-vs-fatal split ---------------------------------

    @Test
    void validBlockParses() throws Exception {
        final byte[] buf = craft(Rar5BlockType.MAIN.getValue(), 0, null, null, 4);
        final Rar5BaseBlock block = Rar5BaseBlock.parse(buf, false);
        assertThat(block.getRar5Type()).isEqualTo(Rar5BlockType.MAIN);
        assertThat(block.isBrokenHeader()).isFalse();
        assertThat(block.getRar5HeaderSize()).isEqualTo(buf.length);
        assertThat(block.hasData()).isFalse();
    }

    @Test
    void badCrcUnencryptedMarksBrokenButDoesNotThrow() throws Exception {
        final byte[] buf = craft(Rar5BlockType.FILE.getValue(), 0, null, null, 4);
        buf[0] ^= 0xFF; // corrupt the stored CRC
        final Rar5BaseBlock block = Rar5BaseBlock.parse(buf, false);
        assertThat(block.isBrokenHeader()).isTrue();
    }

    @Test
    void badCrcEncryptedIsFatal() {
        final byte[] buf = craft(Rar5BlockType.FILE.getValue(), 0, null, null, 4);
        buf[0] ^= 0xFF;
        assertThat(catchThrowable(() -> Rar5BaseBlock.parse(buf, true)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    // ---- parse: Type / Flags / Extra / Data -------------------------------

    @Test
    void dataBlockExposesDataSize() throws Exception {
        final byte[] buf =
                craft(Rar5BlockType.FILE.getValue(), Rar5BaseBlock.HFL_DATA, null, 7L, 0);
        final Rar5BaseBlock block = Rar5BaseBlock.parse(buf, false);
        assertThat(block.hasData()).isTrue();
        assertThat(block.getDataSize()).isEqualTo(7L);
    }

    @Test
    void extraSizeGreaterOrEqualHeadSizeRejected() {
        final byte[] buf =
                craft(Rar5BlockType.FILE.getValue(), Rar5BaseBlock.HFL_EXTRA, 0x100000L, null, 0);
        assertThat(catchThrowable(() -> Rar5BaseBlock.parse(buf, false)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void unknownSkippableTypeParses() throws Exception {
        final byte[] buf = craft(0x40, Rar5BaseBlock.HFL_SKIPIFUNKNOWN, null, null, 2);
        final Rar5BaseBlock block = Rar5BaseBlock.parse(buf, false);
        assertThat(block.getRar5Type()).isNull();
        assertThat(block.getRar5TypeValue()).isEqualTo(0x40);
        assertThat(block.isSkipIfUnknown()).isTrue();
    }

    @Test
    void unknownNonSkippableTypeRejected() {
        final byte[] buf = craft(0x40, 0, null, null, 2);
        assertThat(catchThrowable(() -> Rar5BaseBlock.parse(buf, false)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void truncatedMidHeaderRejected() {
        // Flags declare packed data, but no DataSize descriptor follows -> vint runs off end.
        final byte[] buf =
                craft(Rar5BlockType.FILE.getValue(), Rar5BaseBlock.HFL_DATA, null, null, 0);
        assertThat(catchThrowable(() -> Rar5BaseBlock.parse(buf, false)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void vintFloodInBodyRejected() {
        // A flags field of >10 continuation bytes trips the general vint reader's 10-byte cap.
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeLen(body, vint(Rar5BlockType.FILE.getValue()));
        for (int i = 0; i < 12; i++) {
            body.write(0x80); // never clears the continuation bit
        }
        final byte[] b = body.toByteArray();
        final byte[] sizeV = vint(b.length);
        final int headerSize = 4 + sizeV.length + b.length;
        final byte[] buf = new byte[headerSize];
        System.arraycopy(sizeV, 0, buf, 4, sizeV.length);
        System.arraycopy(b, 0, buf, 4 + sizeV.length, b.length);
        final CRC32 crc = new CRC32();
        crc.update(buf, 4, headerSize - 4);
        final int v = (int) crc.getValue();
        buf[0] = (byte) v;
        buf[1] = (byte) (v >>> 8);
        buf[2] = (byte) (v >>> 16);
        buf[3] = (byte) (v >>> 24);
        assertThat(catchThrowable(() -> Rar5BaseBlock.parse(buf, false)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    // ---- wire-value enum round-trip (C11 lesson) --------------------------

    @ParameterizedTest
    @EnumSource(Rar5BlockType.class)
    void blockTypeRoundTrips(Rar5BlockType type) {
        assertThat(Rar5BlockType.findType(type.getValue())).isEqualTo(type);
    }

    @Test
    void unknownBlockTypeIsNull() {
        assertThat(Rar5BlockType.findType(0x42)).isNull();
    }
}
