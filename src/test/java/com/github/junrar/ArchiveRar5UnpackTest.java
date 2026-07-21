package com.github.junrar;

import com.github.junrar.exception.RarException;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.7 (issue #28) archive-level RAR5 decode acceptance. Extracts the core fixture matrix
 * (m0/m3/m5 &times; plain/solid &times; dict {128 KB, 32 MB}, plus {@code -p}/{@code -hp}
 * encrypted rows deferred from M3.4) on the public {@code Archive} path and asserts each
 * payload is byte-identical to the unrar 7.23 oracle SHA-256. Fixtures were produced by
 * {@code rar 7.23 -ma5} (see {@code rar5unpack/README.md}); the recorded digests are the
 * oracle {@code unrar p} output SHA-256s.
 *
 * <p>Solid rows mirror the existing RAR4 solid tests: in-order, out-of-order, and reverse
 * random access all reconstruct byte-identical output via {@code extractFile}'s
 * rewind-and-replay (plan &sect;6 R8).
 */
class ArchiveRar5UnpackTest {

    @TempDir
    Path tempDir;

    // Oracle SHA-256 of each payload (unrar 7.23 `p` output).
    private static final String SHA_SMALL = "afa47c7795a9476007865eed8bae473d873ae6e80c769f20a42c00bc88cbe09c";
    private static final String SHA_MED = "ae9df0480568cad2c176c7a0cb228a0676ce927a3fbc7df80ae324edc7bf24b0";
    private static final String SHA_BIG = "6864d2cd164a21bb287e58a0ae1940c254eb0ff5af61c365b0e8d11119fb2e78";
    private static final String[] SHA_SOLID = {
        "d99fac30d0b27a3dd7bc437a716ba05c3574617fa82efa4959650fb576dd9e11",
        "7e753e1f8090deba1687f6ccd5610e105d8b49c6c8a71a4e0b87f1fb7b086126",
        "43c943b9814425661e6fdeaca9060ab1e7e8fa0af501618810eb3c8d55629b46",
        "634bd533555da52660aa54ecd1d266ff124897a66e989c2d36a460e6852ca412",
        "d7539b9930a82e3320c498edf3170b9502dfd8efecd60f6ca6651e94ff88d237",
    };

    private File fixture(final String name) throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource("rar5unpack/" + name).toURI()));
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

    private static byte[] extract(final Archive a, final FileHeader hd) throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        a.extractFile(hd, os);
        return os.toByteArray();
    }

    private void assertSingleFile(final String archive, final String expectedSha) throws Exception {
        assertSingleFile(archive, expectedSha, ArchiveOptions.builder().build());
    }

    private void assertSingleFile(final String archive, final String expectedSha, final ArchiveOptions opts) throws Exception {
        try (Archive a = new Archive(fixture(archive), opts)) {
            final List<FileHeader> files = a.getFileHeaders();
            assertThat(files).hasSize(1);
            assertThat(sha256(extract(a, files.get(0)))).isEqualTo(expectedSha);
        }
    }

    // ---- core matrix: plain ------------------------------------------------------------------

    @Test
    void storeMethodExtractsByteIdentical() throws Exception {
        assertSingleFile("m0-plain-128k.rar", SHA_SMALL);
    }

    @Test
    void m3Plain128kExtractsByteIdentical() throws Exception {
        assertSingleFile("m3-plain-128k.rar", SHA_MED);
    }

    @Test
    void m5Plain128kExtractsByteIdentical() throws Exception {
        assertSingleFile("m5-plain-128k.rar", SHA_MED);
    }

    @Test
    void m3Plain32mExtractsByteIdentical() throws Exception {
        // 40 MB payload into a 32 MB window: exercises per-archive window sizing, the
        // UnpWriteBuf write-metering across many MB, and a window wrap.
        assertSingleFile("m3-plain-32m.rar", SHA_BIG);
    }

    // ---- encrypted rows deferred from M3.4 ---------------------------------------------------

    @Test
    void m3EncryptedDataExtractsByteIdentical() throws Exception {
        assertSingleFile("m3-enc-p.rar", SHA_MED, ArchiveOptions.builder().password("junrar").build());
    }

    @Test
    void m3EncryptedHeadersAndDataExtractsByteIdentical() throws Exception {
        assertSingleFile("m3-enc-hp.rar", SHA_MED, ArchiveOptions.builder().password("junrar").build());
    }

    // ---- solid random access (R8) ------------------------------------------------------------

    @Test
    void solidInOrderExtractsByteIdentical() throws Exception {
        assertSolid("m3-solid-128k.rar", new int[]{0, 1, 2, 3, 4});
    }

    @Test
    void solidOutOfOrderExtractsByteIdentical() throws Exception {
        assertSolid("m3-solid-128k.rar", new int[]{2, 0, 4, 1, 3});
    }

    @Test
    void solidReverseExtractsByteIdentical() throws Exception {
        assertSolid("m5-solid-128k.rar", new int[]{4, 3, 2, 1, 0});
    }

    private void assertSolid(final String archive, final int[] order) throws Exception {
        try (Archive a = new Archive(fixture(archive))) {
            final List<FileHeader> files = a.getFileHeaders();
            assertThat(files).hasSize(SHA_SOLID.length);
            for (final int idx : order) {
                assertThat(sha256(extract(a, files.get(idx))))
                    .as("solid file s%d.bin", idx).isEqualTo(SHA_SOLID[idx]);
            }
        }
    }

    // ---- hostile rows: corruption yields a typed exception, never a crash or hang -------------

    @Test
    @Timeout(30)
    void corruptedCompressedDataThrowsTypedException() throws Exception {
        final byte[] bytes = Files.readAllBytes(
            Paths.get(getClass().getResource("rar5unpack/m3-plain-128k.rar").toURI()));
        bytes[bytes.length / 2] ^= 0xFF; // flip a byte deep in the compressed data (past the header)
        final Path p = tempDir.resolve("m3-corrupt.rar");
        Files.write(p, bytes);
        try (Archive a = new Archive(p.toFile())) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(catchThrowable(() -> a.extractFile(hd, new ByteArrayOutputStream())))
                .isInstanceOf(RarException.class);
        }
    }

    @Test
    @Timeout(30)
    void truncatedStreamThrowsTypedException() throws Exception {
        final byte[] full = Files.readAllBytes(
            Paths.get(getClass().getResource("rar5unpack/m3-plain-128k.rar").toURI()));
        final byte[] truncated = new byte[full.length - 4096]; // drop the tail of the packed data
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        final Path p = tempDir.resolve("m3-trunc.rar");
        Files.write(p, truncated);
        assertThat(catchThrowable(() -> {
            try (Archive a = new Archive(p.toFile())) {
                a.extractFile(a.getFileHeaders().get(0), new ByteArrayOutputStream());
            }
        })).isInstanceOf(RarException.class);
    }

    // ---- B-S3: growth-capped window under a byte-patched 1 GB dictionary claim ----------------

    @Test
    @Timeout(30)
    void oneGbClaimExtractsUnderDefaultOptions() throws Exception {
        // The archive-level window-capacity seam was deleted with the M3.11 gate lift; the
        // growth-capped allocation policy stays pinned at the unit level (Unpack5Test).
        // The archive-level fact that survives here is B-S3's user-visible half: a 1 GB claim
        // extracts under DEFAULT options, with no eager gigabyte allocation blowing the heap.
        try (Archive a = new Archive(fixture("m3-1gb-claim.rar"))) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getRar5WinSize()).as("header claims a 1 GB dictionary").isEqualTo(1L << 30);
            assertThat(sha256(extract(a, hd)))
                .isEqualTo("d77aa01d309fea8acfcf79acf0140b83e4f72f413ecc807f603190d4bfa55576");
        }
    }
}
