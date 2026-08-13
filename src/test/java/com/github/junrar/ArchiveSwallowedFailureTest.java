package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.crc.RarCRC;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code Archive.setChannel} rethrows five exception types and silently discards every other one,
 * so that a corrupt archive still yields the files it can ("ignore exceptions to allow extraction
 * of working files in corrupt archive"). That catch is what made GHSA-h76x-7cgm-p442 more than a
 * crash: an unchecked bounds error out of header parsing landed in it and the archive opened
 * reporting nothing at all -- no exception, no way to tell a corrupt archive from an empty one,
 * no trace beyond a log line.
 *
 * <p>Guarding the reads removed the instances the advisory named. This pins the amplifier itself:
 * whatever escapes header parsing next must at least leave the archive able to say its header
 * read did not finish.
 *
 * <p>The trigger here is a live one rather than a contrived one. {@code
 * InitDeciphererFailedException} is a {@code RarException} subtype thrown from inside the RAR3
 * header loop when the header decipherer cannot be built, and it is absent from {@code
 * setChannel}'s rethrow list -- so it reaches the discard exactly as the advisory's bounds errors
 * did. An archive whose main header sets the password flag, opened with no password, takes that
 * path.
 */
class ArchiveSwallowedFailureTest {

    private static final byte[] MARKER = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00};

    /** MHD_PASSWORD: the main header says the headers that follow it are encrypted. */
    private static final int MHD_PASSWORD = 0x0080;

    @TempDir Path tempDir;

    @Test
    void anArchiveWhoseHeaderReadWasSwallowedDoesNotLookIntact() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(mainHeader());
        out.write(new byte[16]); // where the next header's salt would be

        Path file = Files.createTempFile(tempDir, "swallowed", ".rar");
        Files.write(file, out.toByteArray());

        try (Archive archive = new Archive(file.toFile())) {
            assertThat(archive.getFileHeaders()).isEmpty();
            assertThat(archive.hasBrokenHeaders()).isTrue();
            assertThat(archive.getHeaderFailures())
                    .singleElement()
                    .satisfies(
                            failure -> {
                                assertThat(failure.isTerminal()).isTrue();
                                assertThat(failure.getReason()).contains("header read failed");
                            });
        }
    }

    /** The returned failures are a snapshot the caller cannot write back into. */
    @Test
    void theReportedFailuresCannotBeModifiedByTheCaller() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(mainHeader());
        out.write(new byte[16]);

        Path file = Files.createTempFile(tempDir, "immutable", ".rar");
        Files.write(file, out.toByteArray());

        try (Archive archive = new Archive(file.toFile())) {
            assertThat(archive.getHeaderFailures()).isNotEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> archive.getHeaderFailures().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(archive.getHeaderFailures()).isNotEmpty();
        }
    }

    /** A main header declaring that the headers behind it are encrypted. */
    private static byte[] mainHeader() throws IOException {
        byte[] header = new byte[13];
        header[2] = 0x73; // MainHeader
        header[3] = (byte) (MHD_PASSWORD & 0xff);
        header[4] = (byte) ((MHD_PASSWORD >>> 8) & 0xff);
        header[5] = 13;
        header[6] = 0;
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }
}
