package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pins T4 (docs/porting/MIGRATION_MANUAL.md SS6, Rijndael.java:47): the RAR3
 * KDF must serialize the password as UTF-16LE (unrar 3.7.3 crypt.cpp:240-249
 * CharToWide+WideToRaw), not platform-charset getBytes(). Fixture generation:
 * src/test/resources/com/github/junrar/password/generate_nonascii_password_fixture.py.
 */
class RijndaelNonAsciiPasswordTest {

    private static final String PASSWORD = "пароль密码ü"; // "пароль密码ü"
    private static final String EXPECTED_CONTENT = "nonascii-payload\n";

    @Test
    void headerEncryptedRar3ArchiveWithNonAsciiPasswordExtractsOracleContent() throws Exception {
        try (InputStream is =
                getClass()
                        .getResourceAsStream(
                                "/com/github/junrar/password/rar3-nonascii-password.rar")) {
            try (Archive archive = new Archive(is, PASSWORD)) {
                assertThat(archive.isEncrypted()).isTrue();
                assertThat(archive.isPasswordProtected()).isTrue();

                FileHeader fileHeader = archive.getFileHeaders().get(0);
                assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    archive.extractFile(fileHeader, baos);
                    assertThat(new String(baos.toByteArray(), StandardCharsets.UTF_8))
                            .isEqualTo(EXPECTED_CONTENT);
                }
            }
        }
    }
}
