package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.MissingNextVolumeException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * P4 (issue #293) RAR 1.4 multi-volume extraction: entries spanning multiple {@code RE~^}
 * (RARFMT14) volumes through the same old-numbering ({@code .rar}/{@code .r00}/{@code .r01}/...)
 * continuation path RAR3 uses. Fixtures were authored with the real DOS RAR 1.40.2 binary under
 * DOSBox-X and validated {@code All OK} by unrar 7.23
 * ({@code /private/tmp/rar14-oracle/legacy-matrix/PROVENANCE.md}); copied byte-identical
 * (SHA-256 verified against that directory's {@code SHA256SUMS.txt}) into {@code
 * volumes/rar14/}, renamed lowercase to match {@link com.github.junrar.volume.VolumeHelper}'s
 * generated old-numbering extension case ({@code r00}, not {@code R00}).
 *
 * <p>Both sets carry a single entry, {@code BIG.TXT} (79404 bytes), split across parts with
 * advancing {@code LHD_SPLIT_BEFORE}/{@code LHD_SPLIT_AFTER} flags (part1 AFTER-only, middle
 * parts BOTH, last part BEFORE-only) and every part's {@code UnpSize} equal to the whole-file
 * size (unrar {@code arcread.cpp:1256-1331}).
 */
@Timeout(60)
class Rar14VolumeExtractionTest {

    private static final String BIG_TXT_SHA256 =
            "26eea139ab8117eed88aa434760f5d9bc93e7d9f07de774442e2005880eb1a99";

    @TempDir Path tempDir;

    private byte[] resourceBytes(final String name) throws IOException {
        try {
            return Files.readAllBytes(
                    Paths.get(getClass().getResource("volumes/rar14/" + name).toURI()));
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    private Path copyResource(final String name) throws IOException {
        final Path p = tempDir.resolve(name);
        Files.write(p, resourceBytes(name));
        return p;
    }

    private static String sha256(final byte[] b) throws Exception {
        final byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
        final StringBuilder sb = new StringBuilder(d.length * 2);
        for (final byte x : d) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16))
                    .append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }

    /** Coverage row 1: 4-part STORED set, opened from part 1, extracts byte-exact. */
    @Test
    void storedFourPartSetExtractsByteExact() throws Exception {
        copyResource("r14volst.r00");
        copyResource("r14volst.r01");
        copyResource("r14volst.r02");
        final Path first = copyResource("r14volst.rar");
        try (Archive a = new Archive(first.toFile())) {
            final FileHeader hd = a.nextFileHeader();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            a.extractFile(hd, os);
            assertThat(sha256(os.toByteArray())).isEqualTo(BIG_TXT_SHA256);
        }
    }

    /**
     * Coverage row 2: 3-part COMPRESSED set -- proves the Unpack15 decoder state and the
     * Checksum14 accumulator both survive a volume switch, not just the STORED (raw-copy) path.
     */
    @Test
    void compressedThreePartSetExtractsByteExact() throws Exception {
        copyResource("r14volcm.r00");
        copyResource("r14volcm.r01");
        final Path first = copyResource("r14volcm.rar");
        try (Archive a = new Archive(first.toFile())) {
            final FileHeader hd = a.nextFileHeader();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            a.extractFile(hd, os);
            assertThat(sha256(os.toByteArray())).isEqualTo(BIG_TXT_SHA256);
        }
    }

    /**
     * Coverage row 3: split-flag plumbing -- each part's OWN {@link FileHeader}, read directly
     * (one {@link Archive} per part file, not the transparent multi-volume walk rows 1/2 use),
     * carries the exact advancing flags PROVENANCE.md records, and every part agrees on the
     * whole-file {@code getFullUnpackSize()}.
     */
    @Test
    void splitFlagsAndFullUnpackSizeAreConsistentAcrossParts() throws Exception {
        final String[] names = {"r14volst.rar", "r14volst.r00", "r14volst.r01", "r14volst.r02"};
        final boolean[] expectedSplitBefore = {false, true, true, true};
        final boolean[] expectedSplitAfter = {true, true, true, false};
        for (final String name : names) {
            copyResource(name);
        }
        for (int i = 0; i < names.length; i++) {
            try (Archive a = new Archive(tempDir.resolve(names[i]).toFile())) {
                final FileHeader hd = a.nextFileHeader();
                assertThat(hd.isSplitBefore())
                        .as(names[i] + " isSplitBefore")
                        .isEqualTo(expectedSplitBefore[i]);
                assertThat(hd.isSplitAfter())
                        .as(names[i] + " isSplitAfter")
                        .isEqualTo(expectedSplitAfter[i]);
                assertThat(hd.getFullUnpackSize())
                        .as(names[i] + " getFullUnpackSize")
                        .isEqualTo(79404L);
            }
        }
    }

    /**
     * Coverage row 4: only part 1 present -- extraction must throw {@link
     * MissingNextVolumeException}, not silently truncate and not report a bogus {@link
     * CrcErrorException}.
     */
    @Test
    void missingContinuationVolumeThrowsMissingNextVolumeException() throws Exception {
        final Path first = copyResource("r14volst.rar");
        try (Archive a = new Archive(first.toFile())) {
            final FileHeader hd = a.nextFileHeader();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            final Throwable thrown = catchThrowable(() -> a.extractFile(hd, os));
            assertThat(thrown).isInstanceOf(MissingNextVolumeException.class);
        }
    }

    /**
     * Coverage row 5: a byte flipped in a MIDDLE part's payload (a temp-dir copy, the committed
     * fixture is never touched) must fail the whole-file Checksum14 compare -- proving the
     * 16-bit accumulator really spans volumes rather than being reset at the switch (a reset
     * accumulator would still match the corrupted tail's own local checksum by coincidence far
     * less often than it would here, where the corruption sits mid-stream and every subsequent
     * byte is hashed against the wrong running state).
     */
    @Test
    void corruptedMiddlePartPayloadFailsChecksum() throws Exception {
        copyResource("r14volst.r00");
        final byte[] part2 = resourceBytes("r14volst.r01");
        // Fixed header (21 bytes) + "BIG.TXT" (7 bytes) = 28-byte name-inclusive header; offset
        // 40 is safely inside the payload, twelve bytes past the header/name.
        part2[40] ^= 0x01;
        Files.write(tempDir.resolve("r14volst.r01"), part2);
        copyResource("r14volst.r02");
        final Path first = copyResource("r14volst.rar");
        try (Archive a = new Archive(first.toFile())) {
            final FileHeader hd = a.nextFileHeader();
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            final Throwable thrown = catchThrowable(() -> a.extractFile(hd, os));
            assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
        }
    }
}
