package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.MissingPreviousVolumeException;
import com.github.junrar.exception.UnsupportedRarMethodException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #43: a RAR5-container entry whose algorithm version this build does not recognize
 * ({@code VER_UNKNOWN}, junrar's {@code -1}) must be refused BY NAME when compressed, and must
 * still extract when stored (method 0) -- unrar's {@code CheckUnpVer}
 * ({@code d861246:extract.cpp:1517-1545}):
 *
 * <pre>
 * if (Arc.Format==RARFMT50)
 *   WrongVer=Arc.FileHead.UnpVer&gt;VER_UNPACK7;
 * ...
 * if (Arc.FileHead.Method==0)
 *   WrongVer=false;
 * </pre>
 *
 * <p>Before the fix, both rows below instead fell into the RAR3 unpack switch (keyed off
 * {@link FileHeader#isRar5Family()}, which is false for {@code VER_UNKNOWN}), which matches no
 * case, writes nothing, and surfaces a bogus {@code CrcErrorException} against an archive that is
 * not corrupt.
 *
 * <p>Both rows are driven from byte-patched compression-info words (plan &sect;4.3 "inflated
 * fact over a genuine stream" class, the {@code ArchiveRar7HeaderTest}/{@code
 * ArchiveRar7ExtractionTest} recipe): the compressed row patches {@code rar7/rar7-md6g.rar}'s
 * 3-byte compression-info vint to algorithm version 2 (an algorithm {@code rar} 7.23 does not
 * even write, since 0 and 1 are the only ones defined); the stored row patches {@code
 * rar5unpack/m0-plain-128k.rar}'s 2-byte compression-info vint from algorithm version 0 to 2,
 * same width. Neither patch touches the packed stream, so the stored row's own unpatched
 * extraction is the byte-identical oracle.
 */
class ArchiveUnknownAlgoVersionTest {

    /** algo=2, method=1 (M1), dictBits=15, frac=16 -- same word {@code ArchiveRar7HeaderTest} uses
     *  to prove the header parses to {@code VER_UNKNOWN} with no window claim. */
    private static final long COMPRESSED_UNKNOWN_VERSION_COMP_INFO = 0x83c82L;

    @TempDir Path tempDir;

    @Test
    void compressedUnknownAlgorithmVersionEntryIsRefusedByName() throws Exception {
        final File archive =
                patchCompInfo("rar7/rar7-md6g.rar", COMPRESSED_UNKNOWN_VERSION_COMP_INFO);

        try (Archive a = new Archive(archive)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion())
                    .as("unrecognized algorithm version")
                    .isEqualTo((byte) -1);
            assertThat(hd.getUnpMethod()).as("compressed, not stored").isNotZero();

            final Throwable thrown =
                    catchThrowable(() -> a.extractFile(hd, new ByteArrayOutputStream()));
            assertThat(thrown).isExactlyInstanceOf(UnsupportedRarMethodException.class);
        }
    }

    @Test
    void storedUnknownAlgorithmVersionEntryExtractsByteIdentically() throws Exception {
        final byte[] oracle = extract(copyOf("rar5unpack/m0-plain-128k.rar"));

        final File patched = patchCompInfo("rar5unpack/m0-plain-128k.rar", 2L);
        try (Archive a = new Archive(patched)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion())
                    .as("unrecognized algorithm version")
                    .isEqualTo((byte) -1);
            assertThat(hd.getUnpMethod()).as("stored").isZero();
        }
        assertThat(extract(patched))
                .as(
                        "a stored entry carries no version-specific encoding -- it must extract"
                            + " byte-identically regardless of an unrecognized algorithm version")
                .isEqualTo(oracle);
    }

    @Test
    void storedUnknownAlgorithmVersionEntryStartedMidSetIsRefusedNotExtracted() throws Exception {
        // The split-before guard in Archive.doExtractFile ("the entry's data begins in a volume
        // this extraction never saw", M3.9 issue #30) keyed off isRar5Family() alone, which is
        // false for VER_UNKNOWN -- so once the routing fix above lets a stored VER_UNKNOWN entry
        // reach extractRar5, opening part2 of this set alone would extract mid-stream bytes as if
        // they were the entry's start, instead of failing closed. That is a container fact (any
        // RAR5-container entry can start mid-set), not a version fact.
        final File first = volumeSet("stored");
        promoteAllVolumesToUnknownAlgorithmVersion(first);

        final File second = new File(first.getParentFile(), "stored.part2.rar");
        try (Archive a = new Archive(second)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion())
                    .as("unrecognized algorithm version")
                    .isEqualTo((byte) -1);
            assertThat(hd.getUnpMethod()).as("stored").isZero();
            assertThat(hd.isSplitBefore()).as("mid-set start").isTrue();

            final Throwable thrown =
                    catchThrowable(() -> a.extractFile(hd, new ByteArrayOutputStream()));
            assertThat(thrown).isExactlyInstanceOf(MissingPreviousVolumeException.class);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Copy a whole {@code .partN.rar} set into the temp dir and return its first volume. */
    private File volumeSet(final String name) throws Exception {
        File first = null;
        for (int part = 1; ; part++) {
            final String resource = "volumes/rar5-part/" + name + ".part" + part + ".rar";
            if (getClass().getResource(resource) == null) {
                break;
            }
            final File f = copyOf(resource);
            if (first == null) {
                first = f;
            }
        }
        assertThat(first).as("volume set %s", name).isNotNull();
        return first;
    }

    /**
     * OR algorithm version 2 (an unrecognized value {@code rar} never writes) into every volume's
     * first FILE header compression-info word, width-preserving, and refix each header CRC32 --
     * the same per-volume promotion {@code ArchiveRar7ExtractionTest#compatPromoted}/
     * {@code #promoteToRar7} use to promote this same fixture set to algorithm version 1.
     */
    private void promoteAllVolumesToUnknownAlgorithmVersion(final File firstVolume)
            throws Exception {
        final File dir = firstVolume.getParentFile();
        final String prefix =
                firstVolume.getName().substring(0, firstVolume.getName().indexOf(".part"));
        for (final File f : dir.listFiles()) {
            if (f.getName().startsWith(prefix + ".part")) {
                orIntoFirstFileHeaderCompInfo(f, 2L);
            }
        }
    }

    private void orIntoFirstFileHeaderCompInfo(final File archive, final long orBits)
            throws Exception {
        final long position;
        try (Archive a = new Archive(archive)) {
            position = a.getFileHeaders().get(0).getPositionInFile();
        }

        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int start = (int) position;
        final int headerLength =
                Rar5BaseBlock.checkHeaderSize(
                        Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, start, start + headerLength);

        final int[] cursor = {4}; // skip the 4-byte header CRC32
        readVint(header, cursor); // header size
        final int compInfoStart = compressionInfoOffset(header, cursor[0]);
        cursor[0] = compInfoStart;
        final long compInfo = readVint(header, cursor);
        final int width = cursor[0] - compInfoStart;

        System.arraycopy(vint(compInfo | orBits, width), 0, header, compInfoStart, width);
        Raw.writeIntLittleEndian(
                header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, start, header.length);
        Files.write(archive.toPath(), bytes);
    }

    private static byte[] extract(final File archive) throws Exception {
        try (Archive a = new Archive(archive)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            a.extractFile(a.getFileHeaders().get(0), out);
            return out.toByteArray();
        }
    }

    private File copyOf(final String resource) throws Exception {
        final byte[] bytes =
                Files.readAllBytes(Paths.get(getClass().getResource(resource).toURI()));
        final Path p = tempDir.resolve(Paths.get(resource).getFileName().toString());
        Files.write(p, bytes);
        return p.toFile();
    }

    /**
     * Overwrite the first FILE header's compression-info vint with {@code newCompInfo}, re-encoded
     * at the SAME width the field already occupies (both rows here need no more bits than the
     * original word already spans, so the header size never shifts), and refix the block CRC32 --
     * the M3.7/M4.1 byte-patch recipe ({@code ArchiveRar7HeaderTest#patched}).
     */
    private File patchCompInfo(final String resource, final long newCompInfo) throws Exception {
        final File archive = copyOf(resource);
        final long position;
        try (Archive a = new Archive(archive)) {
            position = a.getFileHeaders().get(0).getPositionInFile();
        }

        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int start = (int) position;
        final int headerLength =
                Rar5BaseBlock.checkHeaderSize(
                        Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, start, start + headerLength);

        final int[] cursor = {4}; // skip the 4-byte header CRC32
        readVint(header, cursor); // header size (already consumed by checkHeaderSize above)
        final int compInfoStart = compressionInfoOffset(header, cursor[0]);
        cursor[0] = compInfoStart;
        readVint(header, cursor);
        final int width = cursor[0] - compInfoStart;

        System.arraycopy(vint(newCompInfo, width), 0, header, compInfoStart, width);
        Raw.writeIntLittleEndian(
                header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, start, header.length);
        Files.write(archive.toPath(), bytes);
        return archive;
    }

    /**
     * Walk the FILE header to its compression-info field: header type, header flags, the two
     * optional sizes, then the file fields ahead of it ({@code d861246:arcread.cpp:790-849}) --
     * same walk as {@code ArchiveRar7ExtractionTest#compressionInfoOffset}.
     */
    private static int compressionInfoOffset(final byte[] header, final int bodyStart) {
        final int[] c = {bodyStart};
        final long headerType = readVint(header, c);
        assertThat(headerType).as("first block must be a FILE header").isEqualTo(2L);
        final long headerFlags = readVint(header, c);
        if ((headerFlags & 0x0001) != 0) {
            readVint(header, c); // extra area size
        }
        if ((headerFlags & 0x0002) != 0) {
            readVint(header, c); // data size
        }
        final long fileFlags = readVint(header, c);
        readVint(header, c); // unpacked size
        readVint(header, c); // attributes
        if ((fileFlags & 0x0002) != 0) {
            c[0] += 4; // mtime
        }
        if ((fileFlags & 0x0004) != 0) {
            c[0] += 4; // data CRC32
        }
        return c[0];
    }

    private static long readVint(final byte[] bytes, final int[] cursor) {
        long value = 0;
        int shift = 0;
        while (true) {
            final int b = bytes[cursor[0]++] & 0xff;
            value |= (long) (b & 0x7f) << shift;
            shift += 7;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
    }

    /** Encode a value as exactly {@code width} vint bytes (padding with redundant empty groups). */
    private static byte[] vint(final long value, final int width) {
        long v = value;
        final byte[] out = new byte[width];
        for (int i = 0; i < width; i++) {
            out[i] = (byte) ((v & 0x7f) | (i < width - 1 ? 0x80 : 0));
            v >>>= 7;
        }
        assertThat(v)
                .as("value %s does not fit %s vint bytes", Long.toHexString(value), width)
                .isEqualTo(0L);
        return out;
    }
}
