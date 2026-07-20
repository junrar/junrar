package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.8 (issue #29) archive-level RAR5 filter acceptance. Each fixture was produced by
 * {@code rar 7.23 -ma5} from a payload shaped to trigger one applied filter type
 * (DELTA / E8 / E8E9 — see {@code rar5filters/README.md}); extraction through the M3.2
 * pre-gate harness must be byte-identical to the unrar 7.23 {@code p} oracle SHA-256.
 * The engine-side filter counter proves each fixture actually exercised the filter sweep
 * (plan &sect;4.2 filter row) — without it a filterless archive would pass vacuously.
 *
 * <p>No ARM row: rar has not emitted the ARM filter when compressing since 5.80, so the
 * fixture is unattainable; the ARM transform is unit-tested against a synthetic filter
 * block in {@code Unpack5FilterTest} instead (recorded in the issue close).
 */
@Timeout(30)
class ArchiveRar5FilterTest {

    @TempDir
    Path tempDir;

    // Oracle SHA-256 of each payload (unrar 7.23 `p` output == the deterministic source payload).
    private static final String SHA_DELTA = "808dfc21490452cb7740e316f1255a35578aa3ec3b56e42956d18e43dd7279c1";
    private static final String SHA_E8 = "65b41504002936b8c0fc5c389ff5d9a191e8d1d7f584458fe0198e442a1397fd";
    private static final String SHA_E8E9 = "4e53b7d69dc125bf232dff52d9d9d2a183eaecae9dad85bee80358133856f4e3";

    private File fixture(final String name) throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource("rar5filters/" + name).toURI()));
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private static String sha256(final byte[] b) throws Exception {
        final byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
        final StringBuilder sb = new StringBuilder(d.length * 2);
        for (final byte x : d) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    private void assertFiltered(final String archive, final String expectedSha) throws Exception {
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(fixture(archive))) {
            final List<FileHeader> files = a.getFileHeaders();
            assertThat(files).hasSize(1);
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            a.extractFile(files.get(0), os);
            assertThat(sha256(os.toByteArray())).isEqualTo(expectedSha);
            assertThat(a.testOnlyLastUnpack5FiltersApplied())
                .as("the fixture must actually exercise the filter sweep").isGreaterThan(0);
        }
    }

    @Test
    void deltaFilterExtractsByteIdentical() throws Exception {
        assertFiltered("rar5-delta.rar", SHA_DELTA);
    }

    @Test
    void e8FilterExtractsByteIdentical() throws Exception {
        assertFiltered("rar5-e8.rar", SHA_E8);
    }

    @Test
    void e8e9FilterExtractsByteIdentical() throws Exception {
        assertFiltered("rar5-e8e9.rar", SHA_E8E9);
    }
}
