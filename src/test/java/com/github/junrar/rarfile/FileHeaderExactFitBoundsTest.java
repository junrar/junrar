package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.exception.CorruptHeaderException;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/**
 * The counterpart to {@link FileHeaderShortBodyTest}, which only ever exercises a field the header
 * is too short to hold. Every bound those guards use has an exclusive form one character away, and
 * the exclusive form is wrong: a field whose bytes end exactly at the end of the header is
 * complete, because nothing follows it that a byte has to be reserved for. Reading it as absent
 * silently drops real data -- the defect fixed in {@link UnixOwnersHeader}, where {@code pos + size
 * &lt; length} dropped any Unix owner name that ran to the end of its sub-block.
 *
 * <p>Each test here writes a field that ends exactly at the buffer's last byte and asserts both
 * that the value came back and that the header was not marked broken. Flip any guard in {@link
 * FileHeader} from its inclusive form to the exclusive one and a test here fails.
 *
 * <p>One guard is deliberately not covered, because its boundary is unreachable: the high size
 * fields leave exactly eight bytes only when no bytes remain for the name, which clamps the
 * declared size to zero and is rejected a few lines later. Loosening that guard to {@code <= 8} was measured to change nothing
 * a caller can see -- both forms raise {@link CorruptHeaderException}, differing only in a message
 * nothing dispatches on.
 */
class FileHeaderExactFitBoundsTest {

    private static final short LONG_BLOCK = (short) 0x8000;
    private static final short FILE = 0x74;
    private static final short NEW_SUB = 0x7a;

    @Test
    void aNameEndingAtTheLastByteOfTheHeaderIsKept() throws CorruptHeaderException {
        byte[] body = body("readme.txt".length(), b -> b.write("readme.txt"));

        FileHeader header = parse(FILE, LONG_BLOCK, body);

        assertThat(header.getFileName()).isEqualTo("readme.txt");
        assertThat(header.isBrokenHeader()).isFalse();
    }

    @Test
    void highSizeFieldsEndingRightBeforeTheNameAreRead() throws CorruptHeaderException {
        byte[] body =
                body(
                        1,
                        b -> {
                            b.writeInt(2); // highPackSize
                            b.writeInt(3); // highUnpackSize
                            b.write("a");
                        });

        FileHeader header = parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_LARGE), body);

        assertThat(header.getFullPackSize()).isEqualTo(2L << 32);
        assertThat(header.getFullUnpackSize()).isEqualTo(3L << 32);
        assertThat(header.isBrokenHeader()).isFalse();
    }

    @Test
    void aSaltEndingAtTheLastByteOfTheHeaderIsRead() throws CorruptHeaderException {
        byte[] body =
                body(
                        1,
                        b -> {
                            b.write("a");
                            for (int i = 1; i <= 8; i++) {
                                b.writeByte(i);
                            }
                        });

        FileHeader header = parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_SALT), body);

        assertThat(header.getSalt())
                .containsExactly(
                        (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7,
                        (byte) 8);
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /**
     * A subheader's data length is derived from the declared header size rather than read from
     * the wire, so once that derivation accounts for every field ahead of the data it describes
     * exactly the bytes that remain. The clamp guarding the read must therefore never fire on a
     * well-formed sub-block -- see the LHD_LARGE case below, where the derivation used to be
     * eight bytes out and the clamp fired on sound input.
     */
    @Test
    void subHeaderDataEndingAtTheLastByteOfTheHeaderIsKept() throws CorruptHeaderException {
        byte[] body =
                body(
                        2,
                        b -> {
                            b.write("XX");
                            b.writeByte(9);
                            b.writeByte(9);
                            b.writeByte(9);
                        });

        FileHeader header = parse(NEW_SUB, LONG_BLOCK, body);

        assertThat(header.getSubData()).containsExactly((byte) 9, (byte) 9, (byte) 9);
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /**
     * That length is derived as {@code headerSize - NEWLHD_SIZE - nameSize}, which does not account
     * for the eight bytes LHD_LARGE adds ahead of the name. Left uncorrected it over-counts by
     * exactly those eight, so the clamp guarding the read fires on a well-formed sub-block and
     * marks it broken -- and a broken header is one {@code Archive.doExtractFile} refuses. unrar
     * makes the same subtraction ({@code HeadSize-NameSize-SIZEOF_FILEHEAD3}) and never notices,
     * because it zero-fills the eight bytes it then believes are missing.
     */
    @Test
    void subHeaderDataUnderTheLargeFlagIsNotMistakenForATruncatedOne()
            throws CorruptHeaderException {
        byte[] body =
                body(
                        2,
                        b -> {
                            b.writeInt(0); // highPackSize
                            b.writeInt(0); // highUnpackSize
                            b.write("XX");
                            b.writeByte(7);
                            b.writeByte(7);
                            b.writeByte(7);
                        });

        FileHeader header = parse(NEW_SUB, (short) (LONG_BLOCK | BaseBlock.LHD_LARGE), body);

        assertThat(header.getSubData()).containsExactly((byte) 7, (byte) 7, (byte) 7);
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /**
     * Two bytes of extended-time flags ending at the buffer's last byte are still read. Proven
     * through their consequence rather than directly: these flags announce a creation time whose
     * four bytes are not there, which marks the header broken. Read the flags as absent and they
     * stand in as zero -- no time announced, nothing broken -- so the broken flag is the only
     * thing that tells the two apart.
     */
    @Test
    void extendedTimeFlagsEndingAtTheLastByteOfTheHeaderAreRead() throws CorruptHeaderException {
        byte[] body =
                body(
                        1,
                        b -> {
                            b.write("a");
                            b.writeShort(0x0800); // ctime announced, nothing behind these two bytes
                        });

        FileHeader header = parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_EXTTIME), body);

        assertThat(header.getCTime()).isNull();
        assertThat(header.isBrokenHeader()).isTrue();
    }

    /** The selected time's four DOS bytes end at the buffer's last byte. */
    @Test
    void anExtendedTimeValueEndingAtTheLastByteOfTheHeaderIsRead() throws CorruptHeaderException {
        byte[] body =
                body(
                        1,
                        b -> {
                            b.write("a");
                            b.writeShort(0x0800); // ctime present, no remainder bytes
                            b.writeInt(0x3A8A6C4A);
                        });

        FileHeader header = parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_EXTTIME), body);

        assertThat(header.getCTime()).isNotNull();
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /** The one declared sub-second remainder byte ends at the buffer's last byte. */
    @Test
    void anExtendedTimeRemainderEndingAtTheLastByteOfTheHeaderIsRead()
            throws CorruptHeaderException {
        byte[] withRemainder =
                body(
                        1,
                        b -> {
                            b.write("a");
                            b.writeShort(0x0900); // ctime present, one remainder byte
                            b.writeInt(0x3A8A6C4A);
                            b.writeByte(0x40);
                        });
        byte[] withoutRemainder =
                body(
                        1,
                        b -> {
                            b.write("a");
                            b.writeShort(0x0800); // the same ctime, no remainder byte
                            b.writeInt(0x3A8A6C4A);
                        });

        FileHeader header =
                parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_EXTTIME), withRemainder);
        FileHeader baseline =
                parse(FILE, (short) (LONG_BLOCK | BaseBlock.LHD_EXTTIME), withoutRemainder);

        assertThat(header.isBrokenHeader()).isFalse();
        // The remainder byte moves the timestamp off the whole second; dropping it would leave
        // these two equal, which is what treating the last byte as absent would produce.
        assertThat(header.getCTime()).isNotEqualTo(baseline.getCTime());
    }

    /** Builds a header body whose declared size is exactly the bytes written. */
    private static FileHeader parse(short type, short flags, byte[] body)
            throws CorruptHeaderException {
        int headerSize = BaseBlock.BaseBlockSize + BlockHeader.blockHeaderSize + body.length;
        byte[] base = new byte[7];
        base[2] = (byte) type;
        base[3] = (byte) (flags & 0xff);
        base[4] = (byte) ((flags >>> 8) & 0xff);
        base[5] = (byte) (headerSize & 0xff);
        base[6] = (byte) ((headerSize >>> 8) & 0xff);
        return new FileHeader(new BlockHeader(new BaseBlock(base), new byte[] {0, 0, 0, 0}), body);
    }

    private interface Tail {
        void write(Writer out);
    }

    private static byte[] body(int declaredNameSize, Tail tail) {
        Writer out = new Writer();
        out.writeInt(0); // unpSize
        out.writeByte(0); // hostOS
        out.writeInt(0); // fileCRC
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0);
        out.writeByte(0x4A); // fileTime
        out.writeByte(20); // unpVersion
        out.writeByte(0x30); // unpMethod, store
        out.writeShort(declaredNameSize);
        out.writeInt(0); // fileAttr
        tail.write(out);
        return out.toByteArray();
    }

    /** Little-endian byte sink, matching {@code Raw}'s on-disk order. */
    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void writeByte(int b) {
            out.write(b & 0xff);
        }

        void writeShort(int v) {
            writeByte(v);
            writeByte(v >>> 8);
        }

        void writeInt(int v) {
            writeShort(v);
            writeShort(v >>> 16);
        }

        void write(String ascii) {
            for (char c : ascii.toCharArray()) {
                writeByte(c);
            }
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
