package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.3 (issue #35) acceptance row (b): the archive-level RAR7 oracle, deferred here from M4.1 and
 * M4.2 because no RAR7 stream can exist below a 4 GB dictionary — {@code rar} writes algorithm
 * version 1 only once the recorded dictionary passes what RAR5's four dict bits encode, and it
 * reduces the recorded dictionary to the payload size, so every genuine RAR7 archive needs a
 * &gt; 4 GB payload (probes in {@code rar7/README.md}). M4.3's segmented window is what finally
 * makes {@code rar7/rar7-md6g.rar} extractable.
 *
 * <p><b>Never a PR gate</b> (plan §4.2, review F2): the entry declares a 6 GB dictionary and
 * really unpacks 4.25 GB, so the run needs {@code maxDictionarySize} raised past 6 GB — the
 * caller opt-in that stays opt-in — and a heap above ~4.5 GB. {@code unrar 7.23} needs
 * {@code -md6g} and 3.0 GiB RSS for the same archive. Tagged {@code bigdict} and excluded from
 * {@code test}; run it with {@code ./gradlew bigDictTest}.
 *
 * <p>The payload is never written to disk: it is metered through a digest sink, and the oracle is
 * the {@code unrar 7.23} listing recorded when the fixture was generated.
 */
@Tag("bigdict")
class ArchiveRar7BigDictTest {

    private static final long ORACLE_SIZE = 4563402752L;
    private static final long ORACLE_CRC32 = 0x8E9B8BF4L;

    @Test
    void sixGigabyteDictionaryArchiveExtractsToItsRecordedOracle() throws Exception {
        final ArchiveOptions options = ArchiveOptions.builder().maxDictionarySize(8L << 30).build();
        try (Archive a = new Archive(
                Paths.get(getClass().getResource("rar7/rar7-md6g.rar").toURI()).toFile(), options)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion()).as("a genuine RAR7 entry").isEqualTo((byte) 70);
            assertThat(hd.getRar5WinSize()).as("6 GB, dict bits plus a 5-bit fraction")
                .isEqualTo(6L << 30);
            assertThat(hd.getFullUnpackSize()).isEqualTo(ORACLE_SIZE);

            final DigestSink sink = new DigestSink();
            a.extractFile(hd, sink);

            assertThat(sink.size).as("unpacked size").isEqualTo(ORACLE_SIZE);
            assertThat(sink.crc.getValue()).as("CRC32 recorded by unrar 7.23 at generation time")
                .isEqualTo(ORACLE_CRC32);
        }
    }

    /** Meters and checksums the payload without ever holding or writing it. */
    private static final class DigestSink extends OutputStream {
        private final CRC32 crc = new CRC32();
        private long size;

        @Override
        public void write(final int b) {
            crc.update(b);
            size++;
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            crc.update(b, off, len);
            size += len;
        }
    }
}
