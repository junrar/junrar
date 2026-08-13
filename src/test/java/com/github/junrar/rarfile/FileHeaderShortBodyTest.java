package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.exception.CorruptHeaderException;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/**
 * A RAR3 FILE or SERVICE header carries field sizes of its own choosing, so a crafted header can
 * announce a name, high size fields, a salt or subheader data that its own declared header size
 * cannot hold. Reading past the header is not the only thing to avoid: a field that is not there
 * must not be invented either, so the header is marked broken and the entry is refused at
 * extraction rather than reported as if it had been read.
 */
class FileHeaderShortBodyTest {

    private static final short LONG_BLOCK = (short) 0x8000;

    @Test
    void headerTooShortForItsFixedFieldsIsRejected() {
        assertThatThrownBy(() -> new FileHeader(block((byte) 0x74, LONG_BLOCK), new byte[10]))
                .isInstanceOf(CorruptHeaderException.class);
    }

    /**
     * The clamp above can take a declared name size all the way down to nothing when the header
     * ends exactly where the name should start. An empty name is not a name: it passes
     * {@code isFilenameValid} (a zero-length path has a canonical form), so without the
     * post-clamp rejection the entry reaches {@link com.github.junrar.Archive#getFileHeaders()}
     * and {@code new File(destDir, "")} resolves to the destination directory itself. That is
     * the hazard this whole fix exists to close, and it needs its own case: the test below
     * leaves one byte, which the clamp survives.
     */
    @Test
    void aNameSizeClampedAllTheWayToNothingIsRejected() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeFixedFields(body, 5); // announces five name bytes and then ends

        assertThatThrownBy(() -> new FileHeader(block((byte) 0x74, LONG_BLOCK), body.toByteArray()))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void nameSizeBeyondTheHeaderClampsToTheAvailableBytes() throws CorruptHeaderException {
        FileHeader header = new FileHeader(block((byte) 0x74, LONG_BLOCK), body(4096));

        assertThat(header.getFileName()).isEqualTo("a");
        assertThat(header.isBrokenHeader()).isTrue();
    }

    /**
     * The LHD_LARGE flag makes the high halves of the two sizes mandatory, and a {@code long} has
     * no absent value to stand in for one. Reading them as the zero unrar uses would report a size
     * truncated by 4 GiB as though it had been read, so the header is rejected instead and {@code
     * Archive} keeps the block that framed it.
     */
    @Test
    void largeFlagWithoutHighSizeFieldsIsRejected() {
        assertThatThrownBy(
                        () ->
                                new FileHeader(
                                        block((byte) 0x74, (short) (LONG_BLOCK | 0x0100)), body(1)))
                .isInstanceOf(CorruptHeaderException.class);
    }

    /**
     * Same for the salt: an eight-byte array cannot be absent, and a zero salt is a value the
     * header never carried -- one that would decrypt to garbage rather than fail.
     */
    @Test
    void saltFlagWithoutSaltBytesIsRejected() {
        assertThatThrownBy(
                        () ->
                                new FileHeader(
                                        block((byte) 0x74, (short) (LONG_BLOCK | 0x0400)), body(1)))
                .isInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void recoveryRecordSubHeaderShorterThanTheSectorCountLeavesItUnset()
            throws CorruptHeaderException {
        FileHeader header = recoveryRecordSubHeader(4);

        assertThat(header.getRecoverySectors()).isEqualTo(-1);
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /** A declared header size leaving no subheader data at all allocates no subData array. */
    @Test
    void recoveryRecordSubHeaderWithoutSubHeaderDataLeavesTheSectorCountUnset()
            throws CorruptHeaderException {
        FileHeader header = recoveryRecordSubHeader(0);

        assertThat(header.getRecoverySectors()).isEqualTo(-1);
    }

    /**
     * The subheader data length comes from the declared header size, so it can claim more bytes
     * than the body holds. Only the bytes that are there are kept.
     */
    @Test
    void subHeaderDataBeyondTheHeaderKeepsOnlyTheBytesPresent() throws CorruptHeaderException {
        FileHeader header = subHeader("XX", 10, 4);

        assertThat(header.getSubData()).hasSize(4);
        assertThat(header.isBrokenHeader()).isTrue();
    }

    /** The same claim, with no subheader bytes at all behind it. */
    @Test
    void subHeaderDataMissingEntirelyLeavesNoSubData() throws CorruptHeaderException {
        FileHeader header = subHeader("XX", 10, 0);

        assertThat(header.getSubData()).isNull();
        assertThat(header.isBrokenHeader()).isTrue();
    }

    private static BlockHeader block(byte type, short flags) {
        byte[] base = new byte[7];
        base[2] = type;
        base[3] = (byte) (flags & 0xff);
        base[4] = (byte) ((flags >>> 8) & 0xff);
        base[5] = 40;
        return new BlockHeader(new BaseBlock(base), new byte[] {0, 0, 0, 0});
    }

    /** A FILE header body holding the fixed RAR3 fields plus a single name byte. */
    private static byte[] body(int declaredNameSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFixedFields(out, declaredNameSize);
        out.write('a');
        return out.toByteArray();
    }

    /**
     * A SERVICE header whose declared size claims {@code declaredDataSize} subheader bytes while
     * the body carries {@code actualDataSize} of them.
     */
    private static FileHeader subHeader(String name, int declaredDataSize, int actualDataSize)
            throws CorruptHeaderException {
        int nameSize = name.length();
        int headerSize = 32 + nameSize + declaredDataSize;
        byte[] base = new byte[7];
        base[2] = 0x7a; // NewSubHeader
        base[3] = (byte) (LONG_BLOCK & 0xff);
        base[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        base[5] = (byte) (headerSize & 0xff);
        base[6] = (byte) ((headerSize >>> 8) & 0xff);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFixedFields(out, nameSize);
        for (char c : name.toCharArray()) {
            out.write(c);
        }
        for (int i = 0; i < actualDataSize; i++) {
            out.write(0);
        }

        return new FileHeader(
                new BlockHeader(new BaseBlock(base), new byte[] {0, 0, 0, 0}), out.toByteArray());
    }

    /** A SERVICE header named RR whose subheader data is shorter than the sector count field. */
    private static FileHeader recoveryRecordSubHeader(int dataSize) throws CorruptHeaderException {
        int nameSize = 2;
        int headerSize = 32 + nameSize + dataSize;
        byte[] base = new byte[7];
        base[2] = 0x7a; // NewSubHeader
        base[3] = (byte) (LONG_BLOCK & 0xff);
        base[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        base[5] = (byte) (headerSize & 0xff);
        base[6] = (byte) ((headerSize >>> 8) & 0xff);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFixedFields(out, nameSize);
        out.write('R');
        out.write('R');
        for (int i = 0; i < dataSize; i++) {
            out.write(0);
        }

        return new FileHeader(
                new BlockHeader(new BaseBlock(base), new byte[] {0, 0, 0, 0}), out.toByteArray());
    }

    private static void writeFixedFields(ByteArrayOutputStream out, int declaredNameSize) {
        for (int i = 0; i < 4; i++) {
            out.write(0); // unpSize
        }
        out.write(0); // hostOS
        for (int i = 0; i < 4; i++) {
            out.write(0); // fileCRC
        }
        for (int i = 0; i < 3; i++) {
            out.write(0);
        }
        out.write(0x4A); // fileTime
        out.write(20); // unpVersion
        out.write(0x30); // unpMethod, store
        out.write(declaredNameSize & 0xff);
        out.write((declaredNameSize >>> 8) & 0xff);
        for (int i = 0; i < 4; i++) {
            out.write(0); // fileAttr
        }
    }
}
