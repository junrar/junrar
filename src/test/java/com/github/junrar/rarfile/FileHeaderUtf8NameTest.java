package com.github.junrar.rarfile;

import com.github.junrar.Archive;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins T6 (docs/porting/MIGRATION_MANUAL.md SS6, FileHeader.java:157-165): a
 * RAR3 FILE_HEAD whose name field has no embedded NUL split (the whole field
 * is the UTF-8-encoded name, unrar 3.7.3 arcread.cpp:189-194's
 * {@code Length==hd->NameSize} branch) must decode {@code fileNameW} via that
 * branch (unrar's {@code UtfToWide}), not leave it {@code ""}. Also covers
 * the general ANSI-name decode no longer depending on the JVM's default
 * charset (both paths now use an explicit {@link java.nio.charset.StandardCharsets#UTF_8}).
 * <p>
 * Fixture provenance: unicode/generate_utf8_name_fixture.py.
 */
class FileHeaderUtf8NameTest {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-utf8-name.rar";
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
}
