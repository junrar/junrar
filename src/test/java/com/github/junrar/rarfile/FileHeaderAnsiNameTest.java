package com.github.junrar.rarfile;

import com.github.junrar.Archive;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the T6 fix round (docs/porting/MIGRATION_MANUAL.md SS6 T6,
 * FileHeader.java:158,169): a RAR3 FILE_HEAD with the LHD_UNICODE flag
 * CLEAR (the genuinely plain-ANSI path, {@code 2e71167:arcread.cpp:186-205}'s
 * non-LHD_UNICODE branch) must decode its name bytes byte-transparently
 * (unrar's {@code strncpyz} does NO charset conversion on this path -- raw
 * bytes straight into {@code FileHeader.FileName}), not as UTF-8. UTF-8
 * decoding a legacy-codepage byte that is not also valid UTF-8 silently and
 * irreversibly replaces it with U+FFFD -- exactly what this test's fixture
 * byte (0xE9, a lone non-UTF-8-valid byte) would suffer under the pre-fix
 * {@code StandardCharsets.UTF_8} decode.
 * <p>
 * Fixture provenance: unicode/generate_ansi_name_fixture.py.
 */
class FileHeaderAnsiNameTest {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-ansi-name.rar";

    // The fixture's raw name-field bytes are b'caf\xe9.txt'. Decoded
    // byte-transparently (ISO-8859-1), 0xE9 is U+00E9 ('é', LATIN
    // SMALL LETTER E WITH ACUTE) -- i.e. "café.txt".
    private static final String EXPECTED_NAME = "café.txt";

    @Test
    void plainAnsiNameDecodesByteTransparently() throws Exception {
        File f = new File(getClass().getResource(FIXTURE).getPath());
        try (Archive archive = new Archive(f)) {
            List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);

            FileHeader header = headers.get(0);
            // The fixture clears LHD_UNICODE deliberately -- this must run
            // FileHeader.java's genuinely-non-Unicode decode branch.
            assertThat(header.isUnicode()).isFalse();

            String name = header.getFileName();
            // The dead giveaway of the pre-fix bug: UTF-8 decoding the lone
            // non-UTF-8-valid byte 0xE9 produces a replacement character.
            assertThat(name).doesNotContain("�");
            assertThat(name).isEqualTo(EXPECTED_NAME);
        }
    }
}
