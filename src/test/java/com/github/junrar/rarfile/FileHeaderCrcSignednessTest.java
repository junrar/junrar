package com.github.junrar.rarfile;

import com.github.junrar.Archive;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the signedness contract of {@link FileHeader#getFileCRC()} against the recurring
 * suggestion to widen it to {@code long} (junrar/junrar#260, raised again in the review of
 * junrar/junrar#289).
 * <p>
 * A CRC32 is unsigned in the format, so half of all checksums have the high bit set and read
 * back negative through the {@code int} accessor. Widening the accessor to return the unsigned
 * value looks like a pure improvement, but the extraction check compares it against
 * {@code ~ComprDataIO.getUnpFileCRC()}, whose backing field holds {@code (int)(~crc)} widened
 * to {@code long} -- sign-extended. Both sides are int-shaped today and the sign extension
 * cancels; returning {@code 0x00000000ECEDC4E5} from one side while the other still produces
 * {@code 0xFFFFFFFFECEDC4E5} makes every such entry fail its checksum.
 * <p>
 * This fixture's single member has CRC32 {@code ECEDC4E5} -- high bit set, confirmed against
 * {@code unrar 7.23 lt}. Extracting it exercises the comparison for real, so the test goes red
 * on any change that breaks the cancellation, rather than merely asserting the accessor's
 * return type.
 */
class FileHeaderCrcSignednessTest {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-ansi-utf8-name.rar";
    private static final String EXPECTED_CONTENT = "utf8-name-payload\n";

    @Test
    void entryWhoseCrcHasTheHighBitSetExtractsWithoutACrcError() throws Exception {
        final File f = new File(getClass().getResource(FIXTURE).getPath());
        try (Archive archive = new Archive(f)) {
            final List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);
            final FileHeader header = headers.get(0);

            // The precondition that makes this fixture worth using: the raw accessor is
            // negative, and only the unsigned widening reproduces what unrar prints.
            assertThat(header.getFileCRC()).isNegative();
            assertThat(Integer.toUnsignedLong(header.getFileCRC())).isEqualTo(0xECEDC4E5L);

            // extractFile, not getInputStream: the latter runs extraction on an executor
            // thread, so a CrcErrorException never reaches the reader and the check would be
            // invisible here. Verified by mutation -- through getInputStream this test passes
            // even with the comparison deliberately broken.
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            archive.extractFile(header, out);

            assertThat(new String(out.toByteArray(), StandardCharsets.UTF_8)).isEqualTo(EXPECTED_CONTENT);
        }
    }
}
