package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.exception.UnsupportedRarVersionException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * A RAR 1.4 archive (marker {@code 52 45 7e 5e}, unrar {@code RARFMT14}) must be rejected with
 * {@link UnsupportedRarVersionException}, not misread. junrar has no RAR 1.4 header reader or
 * decoder -- unrar routes the format to a dedicated {@code ReadHeader14}
 * ({@code d861246:arcread.cpp:1258}) whose layout shares nothing with the RAR 1.5+ block loop --
 * so before the fix the marker was classified as the RAR 1.5 family and the 1.4 bytes misparsed
 * as a {@code BaseBlock}, surfacing as a misleading
 * {@link com.github.junrar.exception.CorruptHeaderException} (issue #293, reproduced with
 * rar140dc.rar, the RAR 1.40 distribution archive).
 *
 * <p>The {@code rar14-empty.rar} fixture is the minimal well-formed RAR 1.4 archive, 7 bytes,
 * synthesized byte-by-byte (no RAR executable involved): marker {@code 52 45 7e 5e}, then the
 * SIZEOF_MAINHEAD14 main header -- head size {@code 07 00} (little-endian, minimum unrar accepts:
 * {@code ReadHeader14} returns 0 for {@code HeadSize<7}) and flags {@code 00} -- and no entries.
 * SHA-256 {@code faa4ddb2144651aed00e45e14071e8ad6d443b94d04a8299727313692f03c15e}. The archive
 * body is irrelevant to the bug: classification happens on the 4 marker bytes alone.
 */
class Rar14UnsupportedTest {

    @Test
    void rar14ArchiveIsRejectedAsUnsupportedVersion() {
        final InputStream is = getClass().getResourceAsStream("rar14-empty.rar");
        assertThatThrownBy(
                        () -> {
                            try (Archive archive = new Archive(is)) {
                                archive.getFileHeaders();
                            }
                        })
                .isInstanceOf(UnsupportedRarVersionException.class);
    }
}
