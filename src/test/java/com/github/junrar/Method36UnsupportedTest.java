package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarMethodException;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Public-surface coverage for the M1.5 (issue #19) method-36 drop. The fixture
 * (generate_method36_fixture.py next to it) is test.rar with its first FILE_HEAD's
 * unpVersion byte-patched from 29 to 36 -- "alternative hash" (RAR 3.6.1 beta), dropped
 * upstream in unrar 5.0.0 (reports/unrar-delta-map.md &sect;3).
 *
 * Pre-fix, {@code Unpack.doUnpack} routes method 36 into {@code unpack29} the same as
 * method 29, so extraction does NOT throw {@link UnsupportedRarMethodException} (it either
 * "succeeds" with garbage output or fails some other way). Post-fix, method 36 is refused
 * up front with a typed {@link UnsupportedRarMethodException}. Header parsing itself must
 * stay clean (header CRC is valid) -- only extraction is expected to throw.
 */
class Method36UnsupportedTest {

    private static final String FIXTURE = "/com/github/junrar/abnormal/method36.rar";

    @Test
    @Timeout(5)
    void extractingMethod36MemberThrowsUnsupportedRarMethodException() throws Exception {
        try (Archive archive = new Archive(getClass().getResourceAsStream(FIXTURE))) {
            FileHeader header = archive.nextFileHeader();
            assertThat(header).as("header parsing must succeed (valid header CRC)").isNotNull();
            assertThat(header.getUnpVersion())
                .as("fixture must declare unpVersion (method) 36")
                .isEqualTo((byte) 36);

            Throwable thrown = catchThrowable(() -> archive.extractFile(header, NullOutputStream.INSTANCE));

            assertThat(thrown)
                .as("method-36 member must fail extraction with a typed UnsupportedRarMethodException")
                .isInstanceOf(RarException.class)
                .isInstanceOf(UnsupportedRarMethodException.class);
        }
    }
}
