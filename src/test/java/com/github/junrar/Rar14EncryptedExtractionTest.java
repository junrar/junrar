package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P3 (issue #293) coverage matrix rows 4-6, 8/8b, 9/9b: real, genuine period-archive extraction
 * of encrypted RAR 1.3/1.4 (CRYPT_RAR13), RAR 1.55 (CRYPT_RAR15) and RAR 2.06 (CRYPT_RAR20)
 * entries. Every fixture here is a real DOS-era RAR archive -- none hand-built -- per the P3
 * brief's explicit cancellation of the fixture-synthesis path.
 *
 * <p><b>Provenance -- RAR13/RAR14 (rows 4-6):</b> {@code rar14-password-stored.rar} /
 * {@code rar14-password-compressed.rar} are {@code STOREPWD.RAR} / {@code
 * README_password=password.rar} from github.com/bitplane/oldrar
 * ({@code tests/fixtures/rar13/}), MIT OR Apache-2.0 dual-licensed, genuine DOS RAR 1.402
 * archives (same origin already used for {@code rar14-solid.rar} in {@link
 * Rar14ExtractionTest}). unrar 7.23 extracts both with {@code -ppassword}, "All OK" (oracle run
 * 2026-07-27): {@code STOREPWD.RAR} -&gt; {@code SECRET.TXT}, sha256 {@code
 * 576d7f62397081b85cd4430e9db714b6d84a0196bebe43802ab0df7d119a933c} (stored + encrypted);
 * {@code README_password=password.rar} -&gt; {@code README}, sha256 {@code
 * e70e00c521ee53176d194cfc66d2c284e340d50c07667776071b220ed956570e} (compressed + encrypted).
 * Password for both: {@code password}.
 *
 * <p><b>Provenance -- RAR15/RAR20 (rows 8/8b, 9/9b):</b> {@code rar15-password-*.rar} /
 * {@code rar20-password-*.rar} are genuine period archives authored 2026-07-27 by running the
 * ORIGINAL DOS {@code RAR.EXE} (from {@code rar155.exe} / {@code rar206.exe}, Garbo FTP mirror
 * {@code ftp.lip6.fr/pub/pc/garbo/pc/goldies/}, the same distribution family as the RAR 1.4 issue
 * fixture) under DOSBox-X 2026.07.02: {@code RAR155.EXE a -m0|-m3 -ppassword <out>.RAR
 * SECRET.TXT} and the RAR206.EXE equivalents. All four carry file-header flags {@code 0x8004}
 * ({@code LHD_PASSWORD} set), {@code HEAD_TYPE 0x74}, entry {@code SECRET.TXT}, password
 * {@code password}. unrar 7.23 {@code t -ppassword} = All OK on each; {@code x} yields
 * {@code SECRET.TXT} (87 bytes), sha256 {@code
 * 5f512316b5a4d27c6563b83299c5dd7061fc0c4ee6969a4483363b836c2d6951} from all four -- one oracle
 * digest for every RAR15/RAR20 row. RAR20's pack size (96) is 87 padded up to a 16-byte multiple
 * (CRYPT_RAR20 is a block cipher, unrar {@code crypt.cpp:33-36}); RAR15's pack size (87) is
 * unpadded (CRYPT_RAR15 is a byte-stream cipher) -- {@link
 * #rar20StoredArchiveExtractsByteExactDespiteBlockPadding()} is what actually pins the
 * pack-vs-unpack-size divergence.
 *
 * <p>Regression row 10 (an encrypted RAR3/RAR30 AES archive still extracts after the cipher
 * SELECTION switch changed) is proven by the EXISTING suite, not duplicated here: {@link
 * ArchiveTest}'s
 * {@code givenPasswordProtectedRar4File_whenCreatingArchiveWithPassword_thenItCanExtractContent}
 * exercises {@code password/rar4-password-junrar.rar} (UnpVer 29, CRYPT_RAR30) end to end.
 */
class Rar14EncryptedExtractionTest {

    private static final String ORACLE_SHA256_RAR14_STORED =
            "576d7f62397081b85cd4430e9db714b6d84a0196bebe43802ab0df7d119a933c";
    private static final String ORACLE_SHA256_RAR14_COMPRESSED =
            "e70e00c521ee53176d194cfc66d2c284e340d50c07667776071b220ed956570e";
    private static final String ORACLE_SHA256_RAR15_RAR20 =
            "5f512316b5a4d27c6563b83299c5dd7061fc0c4ee6969a4483363b836c2d6951";

    private static String sha256(final byte[] data) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(data);
            final StringBuilder sb = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] extractFirstEntry(final Archive archive) throws Exception {
        final List<FileHeader> files = archive.getFileHeaders();
        assertThat(files).as("exactly one entry in this fixture").hasSize(1);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        archive.extractFile(files.get(0), out);
        return out.toByteArray();
    }

    // ---- Row 4: real encrypted RAR 1.4 STORED archive, correct password. ----

    @Test
    void rar14StoredArchiveExtractsByteExactWithCorrectPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar14-password-stored.rar");
                Archive archive = new Archive(is, "password")) {
            assertThat(archive.isOldFormat()).isTrue();
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR14_STORED);
        }
    }

    // ---- Row 5: real encrypted RAR 1.4 COMPRESSED archive, correct password. ----

    @Test
    void rar14CompressedArchiveExtractsByteExactWithCorrectPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar14-password-compressed.rar");
                Archive archive = new Archive(is, "password")) {
            assertThat(archive.isOldFormat()).isTrue();
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR14_COMPRESSED);
        }
    }

    // ---- Row 6: wrong password on a real fixture -> CrcErrorException, not NPE/IllegalState.
    // unrar has no password-check mechanism for these pre-RAR5 formats (that concept starts at
    // RAR5's PswCheck); a wrong password decrypts to garbage that only the post-extract CRC
    // compare catches. ----

    @Test
    void wrongPasswordOnRealFixtureThrowsCrcErrorExceptionNotNpeOrIllegalState() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar14-password-stored.rar");
                Archive archive = new Archive(is, "not-the-password")) {
            final FileHeader hd = archive.getFileHeaders().get(0);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Throwable thrown = catchThrowable(() -> archive.extractFile(hd, out));
            assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
        }
    }

    // ---- Row 8: real RAR 1.55 STORED+encrypted fixture (CRYPT_RAR15 alone). ----

    @Test
    void rar15StoredArchiveExtractsByteExactWithCorrectPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar15-password-stored.rar");
                Archive archive = new Archive(is, "password")) {
            assertThat(archive.isOldFormat()).isFalse();
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR15_RAR20);
        }
    }

    // ---- Row 8b: real RAR 1.55 COMPRESSED+encrypted fixture (CRYPT_RAR15 composing with
    // Unpack15). ----

    @Test
    void rar15CompressedArchiveExtractsByteExactWithCorrectPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar15-password-compressed.rar");
                Archive archive = new Archive(is, "password")) {
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR15_RAR20);
        }
    }

    // ---- Row 9: real RAR 2.06 STORED+encrypted fixture (CRYPT_RAR20, pack 96 vs unp 87 --
    // the 16-byte block padding). ----

    @Test
    void rar20StoredArchiveExtractsByteExactDespiteBlockPadding() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar20-password-stored.rar");
                Archive archive = new Archive(is, "password")) {
            final FileHeader hd = archive.getFileHeaders().get(0);
            // Real fixture check (session-verified, not assumed): pack size is padded past
            // unpack size for this stream cipher-vs-block-cipher divergence to be meaningful.
            assertThat(hd.getFullPackSize())
                    .as("pack size padded to 16-byte multiple")
                    .isEqualTo(96);
            assertThat(hd.getFullUnpackSize()).isEqualTo(87);
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR15_RAR20);
        }
    }

    // ---- Row 9b: real RAR 2.06 COMPRESSED+encrypted fixture (CRYPT_RAR20 composing with
    // Unpack20). ----

    @Test
    void rar20CompressedArchiveExtractsByteExactWithCorrectPassword() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar20-password-compressed.rar");
                Archive archive = new Archive(is, "password")) {
            final byte[] content = extractFirstEntry(archive);
            assertThat(sha256(content)).isEqualTo(ORACLE_SHA256_RAR15_RAR20);
        }
    }
}
