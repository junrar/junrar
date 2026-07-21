package com.github.junrar;

import com.github.junrar.exception.WrongPasswordException;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.4 (issue #25) archive-level crypto tests, on the public {@code Archive} path since
 * the M3.11 gate lift. Covers header decryption of a {@code -hp}
 * archive, plaintext listing of a {@code -p} archive, and the {@link WrongPasswordException}
 * paths (wrong password, missing password) that must survive the {@code setChannel} catch
 * filter rather than "opening" the archive with partial, undecryptable headers (manual &sect;4.9).
 */
@Timeout(30)
class ArchiveRar5CryptoTest {

    @TempDir
    Path tempDir;

    private File fixture(final String name) throws Exception {
        final Path src = Paths.get(getClass().getResource("password/" + name).toURI());
        final Path dst = tempDir.resolve(name);
        Files.copy(src, dst);
        return dst.toFile();
    }

    private static ArchiveOptions pw(final String password) {
        return ArchiveOptions.builder().password(password == null ? null : password.toCharArray()).build();
    }

    private static List<String> names(final Archive a) {
        return a.getFileHeaders().stream().map(FileHeader::getFileName).collect(Collectors.toList());
    }

    // ---- -hp (header-encrypted): headers must be decrypted before names are readable ----

    @Test
    void headerEncryptedArchiveListsWithCorrectPassword() throws Exception {
        try (Archive a = new Archive(fixture("rar5-encrypted-junrar.rar"), pw("junrar"))) {
            assertThat(a.isEncrypted()).isTrue();
            assertThat(names(a)).containsExactly("file1.txt");
            final FileHeader fh = a.getFileHeaders().get(0);
            assertThat(fh.isEncrypted()).isTrue();
            assertThat(fh.getFullUnpackSize()).isEqualTo(6);
        }
    }

    @Test
    void headerEncryptedArchiveNonAsciiPassword() throws Exception {
        try (Archive a = new Archive(fixture("rar5-nonascii-password.rar"), pw("пароль密码ü"))) {
            assertThat(a.isEncrypted()).isTrue();
            assertThat(names(a)).containsExactly("file1.txt");
        }
    }

    @Test
    void wrongPasswordOnHeaderEncryptedArchiveThrowsWrongPassword() throws Exception {
        final File f = fixture("rar5-encrypted-junrar.rar");
        assertThat(catchThrowable(() -> new Archive(f, pw("wrongpass"))))
            .isExactlyInstanceOf(WrongPasswordException.class);
    }

    @Test
    void missingPasswordOnHeaderEncryptedArchiveThrowsWrongPassword() throws Exception {
        final File f = fixture("rar5-encrypted-junrar.rar");
        assertThat(catchThrowable(() -> new Archive(f, pw(null))))
            .isExactlyInstanceOf(WrongPasswordException.class);
    }

    /**
     * The {@code setChannel} filter must rethrow {@link WrongPasswordException}: a wrong password
     * must NOT leave an "opened" archive holding only the plaintext HEAD_CRYPT block. Asserting
     * the throw propagates from the constructor proves the filter passes the type through.
     */
    @Test
    void wrongPasswordIsNotSwallowedIntoAPartialOpen() throws Exception {
        final File f = fixture("rar5-encrypted-junrar.rar");
        assertThat(catchThrowable(() -> new Archive(f, pw("nope"))))
            .isInstanceOf(WrongPasswordException.class);
    }

    // ---- -p (data-encrypted only): headers are plaintext, listing needs no password ----

    @Test
    void dataEncryptedArchiveListsWithoutPassword() throws Exception {
        try (Archive a = new Archive(fixture("rar5-password-junrar.rar"))) {
            // No HEAD_CRYPT block: archive-level header encryption is off even though the file is.
            assertThat(a.isEncrypted()).isFalse();
            assertThat(names(a)).containsExactly("file1.txt");
            final FileHeader fh = a.getFileHeaders().get(0);
            assertThat(fh.isEncrypted()).isTrue();
            assertThat(fh.getSalt16()).isNotNull();
            assertThat(fh.getInitVector()).hasSize(16);
            assertThat(fh.isUsePswCheck()).isTrue();
        }
    }
}
