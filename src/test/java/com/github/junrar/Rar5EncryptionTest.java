package com.github.junrar;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Extraction of encrypted RAR5 archives. Both fixtures use the password
 * {@code junrar} and contain a single file {@code file1.txt} with the
 * contents {@code "file1\n"}.
 */
class Rar5EncryptionTest {

    private static final String PASSWORD = "junrar";

    /** Headers are plaintext; only the file content is AES-256-CBC encrypted. */
    @Test
    void fileContentEncrypted_canExtractWithPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar5-password-junrar.rar")) {
            try (Archive archive = new Archive(is, PASSWORD)) {
                assertThat(archive.isEncrypted()).isFalse();
                assertThat(archive.isPasswordProtected()).isTrue();

                List<FileHeader> fileHeaders = archive.getFileHeaders();
                assertThat(fileHeaders).hasSize(1);

                FileHeader fileHeader = fileHeaders.get(0);
                assertThat(fileHeader.isEncrypted()).isTrue();
                assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(fileHeader, baos);
                    assertThat(baos.toString()).isEqualTo("file1\n");
                }
            }
        }
    }

    /** The whole header stream is encrypted behind a HEAD_CRYPT block. */
    @Test
    void headersEncrypted_canExtractWithPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar5-encrypted-junrar.rar")) {
            try (Archive archive = new Archive(is, PASSWORD)) {
                assertThat(archive.isEncrypted()).isTrue();
                assertThat(archive.isPasswordProtected()).isTrue();

                List<FileHeader> fileHeaders = archive.getFileHeaders();
                assertThat(fileHeaders).hasSize(1);

                FileHeader fileHeader = fileHeaders.get(0);
                assertThat(fileHeader.isEncrypted()).isTrue();
                assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(fileHeader, baos);
                    assertThat(baos.toString()).isEqualTo("file1\n");
                }
            }
        }
    }

    /** A wrong password fails the file checksum, matching RAR4 behaviour. */
    @Test
    void fileContentEncrypted_wrongPasswordFailsChecksum() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar5-password-junrar.rar")) {
            try (Archive archive = new Archive(is, "wrong-password")) {
                FileHeader fileHeader = archive.getFileHeaders().get(0);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    Throwable thrown = catchThrowable(() -> archive.extractFile(fileHeader, baos));
                    assertThat(thrown).isInstanceOf(CrcErrorException.class);
                }
            }
        }
    }

    /** A wrong password yields garbage headers that fail the header CRC32. */
    @Test
    void headersEncrypted_wrongPasswordFailsHeaderCrc() {
        Throwable thrown = catchThrowable(() -> {
            try (InputStream is = getClass().getResourceAsStream("password/rar5-encrypted-junrar.rar")) {
                new Archive(is, "wrong-password").close();
            }
        });
        assertThat(thrown).isInstanceOf(CorruptHeaderException.class);
    }
}
