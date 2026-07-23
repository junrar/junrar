package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.Archive;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the invalid-input half of the narrow-name decode rule
 * (docs/porting/MIGRATION_MANUAL.md SS6 T6, no-go D4,
 * {@link FileHeader}{@code .decodeNarrowName}): a RAR3 FILE_HEAD with the
 * LHD_UNICODE flag CLEAR whose name bytes are NOT valid UTF-8 must fall back to
 * a byte-transparent decode. UTF-8 decoding a legacy-codepage byte that is not
 * also valid UTF-8 silently and irreversibly replaces it with U+FFFD -- exactly
 * what this test's fixture byte (0xE9, a lone non-UTF-8-valid byte) would suffer
 * under a plain {@code new String(..., StandardCharsets.UTF_8)}.
 * <p>
 * junrar deliberately diverges from unrar here, and only here: both of unrar's
 * own fallbacks lose information (macOS truncates at the first bad byte -- real
 * {@code unrar 7.23 lb} on this very fixture prints {@code caf}; glibc remaps
 * bad bytes into the U+E000 private-use area). The valid-UTF-8 half of the same
 * rule is parity, pinned by {@link FileHeaderNarrowNameUtf8Test}.
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
