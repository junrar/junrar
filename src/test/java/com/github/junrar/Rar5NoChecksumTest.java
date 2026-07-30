package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A RAR5 entry may omit its CRC32: the {@code FHFL_CRC32} file-header flag is clear and no
 * checksum word is stored (unrar {@code HASH_NONE}). Extraction must succeed without
 * verification -- unrar treats a missing hash as valid ({@code d861246:extract.cpp:934},
 * {@code DataHash::Cmp} of two {@code HASH_NONE} values) and prints {@code "?"} for it.
 *
 * <p>Before the fix the extract path compared the computed CRC against a zero {@link
 * FileHeader#getFileCRC()} and threw {@link com.github.junrar.exception.CrcErrorException} on
 * every non-empty checksum-less file. The {@code rar5-nochecksum.rar} fixture holds a single
 * {@code nochecksum.txt} (stored, no CRC32); {@code unrar p} reports 24 bytes of
 * {@code "no checksum stored here\n"}.
 */
class Rar5NoChecksumTest {

    private static final String EXPECTED = "no checksum stored here\n";

    @Test
    void extractsEntryThatStoresNoChecksum() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("rar5-nochecksum.rar");
                Archive archive = new Archive(is)) {

            final List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).hasSize(1);

            final FileHeader header = headers.get(0);
            assertThat(header.getFileName()).isEqualTo("nochecksum.txt");
            assertThat(header.hasFileCrc()).isFalse();
            assertThat(header.getFileCRC()).isZero();

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            archive.extractFile(header, out); // must not throw CrcErrorException
            assertThat(new String(out.toByteArray(), Charset.forName("UTF-8"))).isEqualTo(EXPECTED);
        }
    }
}
