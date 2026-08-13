package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CorruptHeaderException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Public-surface coverage for the M1.5 (issue #19) FileNameDecoder bounds rewrite. The
 * fixture (generate_encname_fixtures.py next to it) is a byte-patched real archive whose
 * FILE_HEAD encoded-wide name field is truncated to 2 bytes right after the NUL separator,
 * forcing FileNameDecoder.decode's case-0 literal-byte read past the end of the field.
 *
 * Pre-fix, FileNameDecoder.decode has no bounds checks and this raises a raw
 * {@link ArrayIndexOutOfBoundsException} out of header parsing. Post-fix (ports unrar 7.2.7
 * encname.cpp EncodeFileName::Decode's bounds checks, MIGRATION_MANUAL &sect;4.7), the same
 * truncation is a typed {@link CorruptHeaderException}. Header parsing happens eagerly in
 * the {@link Archive} constructor, so opening the archive is enough to trigger it -- no
 * extraction needed.
 */
class HostileEncnameArchiveTest {

    private static final String FIXTURE = "/com/github/junrar/abnormal/encname-truncated.rar";

    @Test
    @Timeout(5)
    void openingHostileEncnameArchiveNeverLeaksRawBounds() throws Exception {
        Path archive = writeFixtureToTempFile();
        try {
            Throwable thrown =
                    catchThrowable(
                            () -> {
                                try (Archive ignored = new Archive(archive.toFile())) {
                                    // constructor eagerly parses headers; a defeated guard
                                    // would surface here
                                }
                            });
            assertThat(thrown)
                    .as("a bounds guard must not turn a hostile name into a thrown error at all")
                    .isNull();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    /**
     * The truncation is still corruption, and still has to be reported -- the entry it belongs to
     * simply does not survive header parsing, so it is skipped like any other header that cannot
     * be parsed and the archive says so through {@link Archive#hasBrokenHeaders()}. Before the
     * GHSA-h76x-7cgm-p442 rework this was a CorruptHeaderException at open; before the fix it was
     * a raw {@link ArrayIndexOutOfBoundsException} that {@code Archive.setChannel} swallowed
     * whole.
     */
    @Test
    @Timeout(5)
    void getFileHeadersSurfaceReportsTheTruncatedNameAsABrokenHeader() throws Exception {
        Path archive = writeFixtureToTempFile();
        try (Archive a = new Archive(archive.toFile())) {
            assertThat(a.getFileHeaders()).isEmpty();
            assertThat(a.hasBrokenHeaders()).isTrue();
            assertThat(a.getHeaderFailures()).isNotEmpty();
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private Path writeFixtureToTempFile() throws Exception {
        Path tmp = Files.createTempFile("junrar-encname-", ".rar");
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("resource %s", FIXTURE).isNotNull();
            Files.write(tmp, IOUtils.toByteArray(input));
        }
        return tmp;
    }
}
