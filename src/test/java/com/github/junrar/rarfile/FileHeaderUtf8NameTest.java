package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.Archive;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins T6 (docs/porting/MIGRATION_MANUAL.md SS6, FileHeader.java:157-165): a
 * RAR3 FILE_HEAD whose name field has no embedded NUL split (the whole field
 * is the UTF-8-encoded name, unrar 3.7.3 arcread.cpp:189-194's
 * {@code Length==hd->NameSize} branch) must decode {@code fileNameW} via that
 * branch (unrar's {@code UtfToWide}), not leave it {@code ""}. Also covers
 * the general ANSI-name decode no longer depending on the JVM's default
 * charset. Since issue #44 that branch is no longer separate: 7.2.7 routes this
 * field through the same {@code decodeNarrowName} as a plain-ANSI name, whose
 * UTF-8 decode succeeds here and yields the identical result.
 * <p>
 * Fixture provenance: unicode/generate_utf8_name_fixture.py.
 */
class FileHeaderUtf8NameTest {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-utf8-name.rar";
    private static final String SPLIT_FIXTURE =
            "/com/github/junrar/unicode/rar3-utf8-name-split.rar";
    private static final String EXPECTED_NAME = "café-résumé-日本語.txt";

    @Test
    void wholeNameUtf8BranchPopulatesFileNameW() throws Exception {
        File f = new File(getClass().getResource(FIXTURE).getPath());
        try (Archive archive = new Archive(f)) {
            List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);

            FileHeader header = headers.get(0);
            assertThat(header.isUnicode()).isTrue();
            // The dead giveaway of the pre-fix bug: fileNameW stays "" even
            // though LHD_UNICODE is set and the whole-name-UTF-8 branch
            // applies -- independent of the running JVM's default charset.
            assertThat(header.getFileNameW()).isEqualTo(EXPECTED_NAME);
            assertThat(header.getFileName()).isEqualTo(EXPECTED_NAME);
        }
    }

    /**
     * Pins the third narrow-decode site (issue #44, no-go D4): the ANSI half of a
     * NUL-split LHD_UNICODE name field. unrar keeps only one name here -- when
     * {@code EncodeFileName::Decode} succeeds, {@code hd->FileName} is non-empty and
     * {@code ArcCharToWide} never runs on the narrow half. junrar keeps both halves,
     * and falls back to this one whenever {@code fileNameW} comes out empty (no-go C4),
     * which is exactly the case unrar does convert. So the narrow half gets the same
     * decode, and this fixture's half -- valid UTF-8 -- must come out decoded rather
     * than as the ISO-8859-1 view {@code cafÃ©-rÃ©sumÃ©-æ—¥æœ¬èªž.txt}.
     * <p>
     * Only the deprecated {@link FileHeader#getFileNameString()} exposes it here, since
     * {@code fileNameW} is populated and wins in {@link FileHeader#getFileName()} -- which
     * is also why the regression corpus cannot see this site: it records
     * {@code getFileName()} only.
     */
    @Test
    @SuppressWarnings("deprecation")
    void splitNameAnsiHalfGetsTheSameNarrowDecode() throws Exception {
        File f = new File(getClass().getResource(SPLIT_FIXTURE).getPath());
        try (Archive archive = new Archive(f)) {
            List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);

            FileHeader header = headers.get(0);
            assertThat(header.isUnicode()).isTrue();
            // The RLE-decoded half is unaffected and still wins getFileName().
            assertThat(header.getFileName()).isEqualTo(EXPECTED_NAME);
            assertThat(header.getFileNameString()).isEqualTo(EXPECTED_NAME);
        }
    }
}
