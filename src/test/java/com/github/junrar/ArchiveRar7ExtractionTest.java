package com.github.junrar;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M4.2 (issue #34) public-path acceptance for RAR7 routing: a version-70 entry now reaches the
 * RAR5/RAR7 engine with {@code ExtraDist} on instead of being refused by name, and an entry the
 * {@code FCI_RAR5_COMPAT} flag demotes to version 50 extracts through the base ({@code DCB = 64})
 * distance path — {@code d861246:unpack.cpp:184}, {@code ExtraDist=(Method==VER_PACK7)}.
 *
 * <p>Every row but the first is driven from a patched header, because none of them is producible.
 * {@code rar 7.23} only records algorithm version 1 once the dictionary exceeds the 4 GB that
 * RAR5's four dict bits can encode, so every genuine RAR7 stream declares a &gt; 4 GB window
 * (probes in {@code rar7/README.md}); and {@code FCI_RAR5_COMPAT} is only emitted when RAR7
 * entries are appended onto a smaller-dictionary RAR5 solid stream, which needs two &gt; 4 GB
 * payloads.
 *
 * <p>Both patches are faithful fixtures rather than §4.3 inflated-resource headers, and both are
 * checked against {@code unrar 7.23} (which tests each patched archive {@code All OK}). The compat
 * word's whole meaning is "RAR7 header, RAR5-decodable stream", which is exactly what the patched
 * archive is; a <em>stored</em> stream carries no version-specific encoding at all, so promoting
 * its header to version 70 describes the same bytes truthfully. In both cases the asserted oracle
 * is the unpatched original's own output, so a patched header is never asked to prove a decode the
 * stream does not contain.
 */
class ArchiveRar7ExtractionTest {

    /** Algorithm version 1 plus {@code FCI_RAR5_COMPAT} (bit 20, {@code d861246:headers5.hpp:65}). */
    private static final long ALGO_RAR7 = 1L;
    private static final long FCI_RAR5_COMPAT = 1L << 20;

    @TempDir
    Path tempDir;

    @Test
    void rar7StreamIsRefusedByTheDictionaryGateNotByMethodName() throws Exception {
        // M4.2 routes version 70 into the engine, so the refusal is no longer "unsupported
        // method": the only producible RAR7 stream declares a 6 GB dictionary, which the M4-era
        // capability ceiling rejects by size. M4.3 (issue #35) is what raises that ceiling.
        try (Archive a = new Archive(rar7Fixture())) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion()).isEqualTo((byte) 70);

            final Throwable thrown = catchThrowable(() -> a.extractFile(hd, new ByteArrayOutputStream()));
            assertThat(thrown).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
            assertThat(thrown.getMessage()).contains(Long.toString(6L << 30));
        }
    }

    @Test
    void rar5CompatStreamExtractsThroughTheBaseDistancePath() throws Exception {
        final File plain = rar5Fixture();
        final byte[] oracle = extract(plain);

        final File compat = compatPatched(rar5Fixture());
        try (Archive a = new Archive(compat)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion())
                .as("FCI_RAR5_COMPAT demotes algorithm version 1 to the RAR5 decoder").isEqualTo((byte) 50);
        }
        assertThat(extract(compat))
            .as("a compat-demoted header must decode with DCB=64, byte-identical to the RAR5 original")
            .isEqualTo(oracle);
    }

    @Test
    void storedVersionSeventyEntryMergesVolumesTheRar5Way() throws Exception {
        // A stored stream is byte-identical under version 50 and 70, so promoting the header is a
        // faithful specimen (§4.3 class 2) — unrar 7.23 tests the patched set All OK. It is the
        // only way to reach the volume-merge path with a version-70 entry: every compressed RAR7
        // stream declares a > 4 GB dictionary and never gets as far as reading packed bytes.
        final byte[] oracle = extract(volumeSet("stored"));
        final File first = compatPromoted(volumeSet("stored"));

        try (Archive a = new Archive(first)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion()).isEqualTo((byte) 70);
            assertThat(hd.getUnpMethod()).as("stored").isZero();
            assertThat(hd.isSplitAfter()).as("the entry really does span volumes").isTrue();
        }
        assertThat(extract(first))
            .as("a version-70 entry must merge volumes through the RAR5 path, not the RAR3 one")
            .isEqualTo(oracle);
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
     * Promote every volume's first FILE header to algorithm version 1, keeping its width. A stored
     * entry's compression-info word is 2 vint bytes here and stays 2, so unlike
     * {@link #compatPatched} this patch is length-preserving: only the header CRC32 is refixed.
     */
    private File compatPromoted(final File firstVolume) throws Exception {
        final File dir = firstVolume.getParentFile();
        final String prefix = firstVolume.getName().substring(0, firstVolume.getName().indexOf(".part"));
        for (final File f : dir.listFiles()) {
            if (f.getName().startsWith(prefix + ".part")) {
                promoteToRar7(f);
            }
        }
        return firstVolume;
    }

    private void promoteToRar7(final File archive) throws Exception {
        final long position;
        try (Archive a = new Archive(archive)) {
            position = a.getFileHeaders().get(0).getPositionInFile();
        }
        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int start = (int) position;
        final int headerLength = Rar5BaseBlock.checkHeaderSize(
            Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));

        final int[] cursor = {start + 4};
        readVint(bytes, cursor);                       // header size
        final int bodyStart = cursor[0];

        final int compInfoStart = compressionInfoOffset(bytes, bodyStart);
        cursor[0] = compInfoStart;
        final long compInfo = readVint(bytes, cursor);
        final int width = cursor[0] - compInfoStart;
        System.arraycopy(vint(compInfo | ALGO_RAR7, width), 0, bytes, compInfoStart, width);

        final byte[] header = Arrays.copyOfRange(bytes, start, start + headerLength);
        Raw.writeIntLittleEndian(header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
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

    private File rar7Fixture() throws Exception {
        return copyOf("rar7/rar7-md6g.rar");
    }

    private File rar5Fixture() throws Exception {
        return copyOf("rar5unpack/m3-plain-128k.rar");
    }

    private File copyOf(final String resource) throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource(resource).toURI()));
        final Path p = tempDir.resolve(Paths.get(resource).getFileName().toString());
        Files.write(p, bytes);
        return p.toFile();
    }

    /**
     * Set algorithm version 1 and {@code FCI_RAR5_COMPAT} on the first FILE header's
     * compression-info vint, then repair the header around it.
     *
     * <p>Unlike the M4.1 patches this one is <em>not</em> length-preserving: a RAR5
     * compression-info word never reaches bit 14 (its widest field, the four dict bits, ends at
     * bit 13), so it is at most 2 vint bytes, while bit 20 needs 3. The extra byte is absorbed by
     * bumping the header-size vint, which keeps its own width, and refixing the header CRC32.
     */
    private File compatPatched(final File archive) throws Exception {
        final long position;
        try (Archive a = new Archive(archive)) {
            position = a.getFileHeaders().get(0).getPositionInFile();
        }

        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int start = (int) position;
        final int headerLength = Rar5BaseBlock.checkHeaderSize(
            Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));

        // CRC32(4) | HeaderSize vint | body of exactly HeaderSize bytes.
        final int[] cursor = {start + 4};
        final long headerSize = readVint(bytes, cursor);
        final int sizeFieldWidth = cursor[0] - (start + 4);
        final int bodyStart = cursor[0];
        assertThat(bodyStart + (int) headerSize).isEqualTo(start + headerLength);

        final int compInfoStart = compressionInfoOffset(bytes, bodyStart);
        cursor[0] = compInfoStart;
        final long compInfo = readVint(bytes, cursor);
        final int compInfoWidth = cursor[0] - compInfoStart;

        final byte[] newCompInfo = vint(compInfo | ALGO_RAR7 | FCI_RAR5_COMPAT, 3);
        final int delta = newCompInfo.length - compInfoWidth;
        final byte[] newSizeField = vint(headerSize + delta, sizeFieldWidth);

        final byte[] header = new byte[headerLength + delta];
        System.arraycopy(newSizeField, 0, header, 4, sizeFieldWidth);
        final int bodyOffset = 4 + sizeFieldWidth;
        final int lead = compInfoStart - bodyStart;
        System.arraycopy(bytes, bodyStart, header, bodyOffset, lead);
        System.arraycopy(newCompInfo, 0, header, bodyOffset + lead, newCompInfo.length);
        System.arraycopy(bytes, compInfoStart + compInfoWidth,
            header, bodyOffset + lead + newCompInfo.length,
            (int) headerSize - lead - compInfoWidth);
        Raw.writeIntLittleEndian(header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));

        final byte[] out = new byte[bytes.length + delta];
        System.arraycopy(bytes, 0, out, 0, start);
        System.arraycopy(header, 0, out, start, header.length);
        System.arraycopy(bytes, start + headerLength, out, start + header.length,
            bytes.length - start - headerLength);
        Files.write(archive.toPath(), out);
        return archive;
    }

    /**
     * Walk the FILE header to its compression-info field: header type, header flags, the two
     * optional sizes, then the file fields ahead of it ({@code d861246:arcread.cpp:790-849}).
     */
    private static int compressionInfoOffset(final byte[] bytes, final int bodyStart) {
        final int[] c = {bodyStart};
        final long headerType = readVint(bytes, c);
        assertThat(headerType).as("first block must be a FILE header").isEqualTo(2L);
        final long headerFlags = readVint(bytes, c);
        if ((headerFlags & 0x0001) != 0) {
            readVint(bytes, c); // extra area size
        }
        if ((headerFlags & 0x0002) != 0) {
            readVint(bytes, c); // data size
        }
        final long fileFlags = readVint(bytes, c);
        readVint(bytes, c); // unpacked size
        readVint(bytes, c); // attributes
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
        assertThat(v).as("value %s does not fit %s vint bytes", Long.toHexString(value), width)
            .isEqualTo(0L);
        return out;
    }
}
