package com.github.junrar;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.RarException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    void openingHostileEncnameArchiveThrowsCorruptHeaderNotRawBounds() throws Exception {
        Path archive = writeFixtureToTempFile();
        try {
            Throwable thrown = catchThrowable(() -> {
                try (Archive ignored = new Archive(archive.toFile())) {
                    // constructor eagerly parses headers; failure happens here
                }
            });
            assertThat(thrown)
                .as("hostile encname archive must fail header parsing with a typed RarException")
                .isInstanceOf(RarException.class)
                .isInstanceOf(CorruptHeaderException.class);
            for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                assertThat(cause)
                    .as("must not leak a raw bounds error (guard defeated)")
                    .isNotInstanceOf(ArrayIndexOutOfBoundsException.class);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    @Timeout(5)
    void getFileHeadersSurfaceNeverLeaksRawBoundsError() throws Exception {
        Path archive = writeFixtureToTempFile();
        try {
            Throwable thrown = catchThrowable(() -> {
                try (Archive a = new Archive(archive.toFile())) {
                    a.getFileHeaders();
                }
            });
            assertThat(thrown).isInstanceOf(CorruptHeaderException.class);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private Path writeFixtureToTempFile() throws Exception {
        Path tmp = Files.createTempFile("junrar-encname-", ".rar");
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("resource %s", FIXTURE).isNotNull();
            Files.write(tmp, input.readAllBytes());
        }
        return tmp;
    }
}
