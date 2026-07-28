package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * A RAR 1.4 archive (marker {@code 52 45 7e 5e}, unrar {@code RARFMT14}) opens and lists
 * cleanly as of P1 (issue #293) -- the premise flips from this class's pre-P1 behaviour:
 * before P1, junrar had no RAR 1.4 header reader and rejected the marker with {@link
 * com.github.junrar.exception.UnsupportedRarVersionException}; {@code Archive.readHeaders14}
 * now parses it instead. Kept as {@code Rar14UnsupportedTest} (not renamed) because it is the
 * RED/GREEN complementary half of the P1 proof cited in the brief: its pre-P1 form (asserting
 * the marker was rejected) passed on the base commit, and this replaced form is what the P1
 * RED test run was executed against before any production edit -- it failed for the intended
 * reason (the constructor still throwing {@code UnsupportedRarVersionException}) until the
 * production changes landed.
 *
 * <p>The {@code rar14-empty.rar} fixture is the minimal well-formed RAR 1.4 archive, 7 bytes,
 * synthesized byte-by-byte (no RAR executable involved): marker {@code 52 45 7e 5e}, then the
 * SIZEOF_MAINHEAD14 main header -- head size {@code 07 00} (little-endian, the minimum unrar
 * accepts: {@code ReadHeader14} returns 0 for {@code HeadSize<7}) and flags {@code 00} -- and
 * no entries. SHA-256 {@code faa4ddb2144651aed00e45e14071e8ad6d443b94d04a8299727313692f03c15e}
 * (byte-identical to the pre-P1 fixture; only this file's assertions change).
 */
class Rar14UnsupportedTest {

    @Test
    void emptyRar14ArchiveOpensWithNoFileHeaders() throws Exception {
        final InputStream is = getClass().getResourceAsStream("rar14-empty.rar");
        try (Archive archive = new Archive(is)) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR14);
            assertThat(archive.isOldFormat()).isTrue();
            assertThat(archive.getFileHeaders()).isEmpty();
            assertThat(archive.isEncrypted()).isFalse();
            assertThat(archive.isPasswordProtected()).isFalse();
        }
    }
}
