package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.Archive;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the 7.2.7 narrow-name decode rule (issue #44, docs/porting/MIGRATION_MANUAL.md
 * SS6 T6): a RAR3 FILE_HEAD with the LHD_UNICODE flag CLEAR whose name bytes are valid
 * UTF-8 must decode as UTF-8, not byte-transparently.
 * <p>
 * unrar 7.2.7 routes every narrow name through {@code ArcCharToWide(..., ACTW_OEM)}
 * ({@code d861246:arcread.cpp:359-360}), which is a strict multibyte decode in the
 * process locale -- UTF-8 on any modern Unix -- so a valid-UTF-8 name field comes out
 * decoded. Ground truth: {@code unrar 7.23 lb} prints {@code Схема3.spl} both for this
 * fixture and for the real corpus member the byte run was copied from,
 * {@code commoncrawl3/3I/3ILZMJBO2TG2LCBV6L5AHMXEYLEXH4X2}, where junrar used to report
 * the ISO-8859-1 view {@code Ð¡ÑÐµÐ¼Ð°3.spl}.
 * <p>
 * The invalid-UTF-8 half of the same rule stays pinned by {@link FileHeaderAnsiNameTest}:
 * junrar keeps the byte-transparent ISO-8859-1 fallback there rather than adopting either
 * of unrar's lossy platform fallbacks (macOS truncates at the first bad byte; glibc maps
 * bad bytes into the U+E000 private-use area).
 * <p>
 * Fixture provenance: unicode/generate_ansi_name_fixture.py.
 */
class FileHeaderNarrowNameUtf8Test {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-ansi-utf8-name.rar";

    // The fixture's raw name-field bytes are the 15 UTF-8 bytes of this name.
    private static final String EXPECTED_NAME = "Схема3.spl";

    // What a byte-transparent ISO-8859-1 decode of those same bytes produces -- the
    // pre-fix junrar answer, and the dead giveaway that the UTF-8 decode did not run.
    private static final String ISO_8859_1_VIEW = "Ð¡ÑÐµÐ¼Ð°3.spl";

    @Test
    void validUtf8NarrowNameDecodesAsUtf8() throws Exception {
        File f = new File(getClass().getResource(FIXTURE).getPath());
        try (Archive archive = new Archive(f)) {
            List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);

            FileHeader header = headers.get(0);
            // The fixture clears LHD_UNICODE deliberately -- this must run the
            // narrow-name decode branch, not the RLE encoded-wide one.
            assertThat(header.isUnicode()).isFalse();

            String name = header.getFileName();
            assertThat(name).isNotEqualTo(ISO_8859_1_VIEW);
            assertThat(name).isEqualTo(EXPECTED_NAME);
        }
    }
}
