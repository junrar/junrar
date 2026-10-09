package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A RAR5 entry whose unpacked size is unknown ({@code FHFL_UNPUNKNOWN}, written by {@code rar a
 * -si}) still holds all its data: unrar unpacks it until the data ends ({@code INT64NDF},
 * {@code d861246:arcread.cpp:818-819}). junrar used to cap every write at a {@code -1} size and
 * extract such entries as empty -- silently, when the entry also stores no checksum, so a
 * scanner reading through junrar saw an empty file where unrar extracts the payload.
 *
 * <p>The fixtures are existing archives with only the flag set and the header CRC32 refixed;
 * the expected bytes are what junrar extracts from the unpatched archive, and what {@code unrar
 * 7.2.7 p} prints for the patched one.
 */
class Rar5UnknownSizeTest {

    /** {@code FHFL_UNPUNKNOWN}, unrar {@code headers5.hpp:35}. */
    private static final int FHFL_UNPUNKNOWN = 0x0008;

    @TempDir Path tempDir;

    @Test
    void anEntryWithoutAChecksumIsExtractedRatherThanEmpty() throws Exception {
        assertExtractsLikeTheKnownSizeOriginal("rar5-nochecksum.rar");
    }

    @Test
    void aCompressedEntryIsExtractedAndVerified() throws Exception {
        assertExtractsLikeTheKnownSizeOriginal("rar5unpack/m3-plain-128k.rar");
    }

    private void assertExtractsLikeTheKnownSizeOriginal(final String resource) throws Exception {
        final Path original = Paths.get(getClass().getResource(resource).toURI());
        final byte[] expected = extract(original.toFile());
        assertThat(expected).isNotEmpty();

        final File patched = withUnknownSize(original);
        try (Archive archive = new Archive(patched)) {
            final FileHeader header = archive.getFileHeaders().get(0);
            assertThat(header.isUnpSizeUnknown()).isTrue();

            final ByteArrayOutputStream direct = new ByteArrayOutputStream();
            archive.extractFile(header, direct);
            assertThat(direct.toByteArray()).isEqualTo(expected);

            try (InputStream in = archive.getInputStream(header)) {
                assertThat(IOUtils.toByteArray(in)).isEqualTo(expected);
            }
        }
    }

    private static byte[] extract(final File file) throws Exception {
        try (Archive archive = new Archive(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            archive.extractFile(archive.getFileHeaders().get(0), out);
            return out.toByteArray();
        }
    }

    /** Copy of {@code original} with FHFL_UNPUNKNOWN set on its first FILE header. */
    private File withUnknownSize(final Path original) throws Exception {
        final long position;
        try (Archive archive = new Archive(original.toFile())) {
            position = archive.getFileHeaders().get(0).getPositionInFile();
        }
        final byte[] bytes = Files.readAllBytes(original);
        final int start = (int) position;
        final int length =
                Rar5BaseBlock.checkHeaderSize(
                        Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, start, start + length);
        final int flags = Rar5BaseBlock.parse(header, false).getFieldsOffset();
        // The file flags are the first field; a one-byte vint stays one byte with this bit set.
        assertThat(header[flags] & 0x80).isZero();
        header[flags] |= FHFL_UNPUNKNOWN;
        Raw.writeIntLittleEndian(
                header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, start, length);
        return Files.write(tempDir.resolve("unknown-size.rar"), bytes).toFile();
    }
}
