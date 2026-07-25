package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * M4.1 (issue #33) RAR7 compression-info parse acceptance: algorithm version 1 routes to
 * {@code VER_PACK7}, the dictionary decodes from 5 dict bits plus 5 fraction bits
 * ({@code WinSize = 0x20000 << bits; WinSize += WinSize/32*frac} — {@code d861246:arcread.cpp:868-874}),
 * {@code FCI_RAR5_COMPAT} demotes the entry to the RAR5 decode path, and an encoding above
 * {@code UNPACK_MAX_DICT} (64 GB, {@code d861246:rardefs.hpp:40}) is refused as an unknown version.
 *
 * <p>Fixture: {@code rar7/rar7-md6g.rar}, a genuine {@code rar 7.23 -m1 -md6g} archive whose
 * header really carries {@code algo=1, dictBits=15, frac=16} (6 GB) — see {@code rar7/README.md}.
 * Real {@code rar} only records a dictionary the payload can realize, so every other encoding in
 * the matrix is patched in-test over that same valid header (plan &sect;4.3 sanctioned class):
 * its compression-info vint is 3 bytes wide, which is exactly the width of the widest word this
 * chunk parses ({@code FCI_RAR5_COMPAT} is bit 20), so each patch is a length-preserving
 * overwrite plus a header CRC32 refix. Nothing hostile is committed.
 *
 * <p>Header facts only. The public extraction path — version-70 routing and the
 * {@code FCI_RAR5_COMPAT} decode — is pinned by {@link ArchiveRar7ExtractionTest} from M4.2 on.
 */
class ArchiveRar7HeaderTest {

    /** The compression-info word rar 7.23 actually wrote: algo=1, method=1, dictBits=15, frac=16. */
    private static final long BASE_COMP_INFO = 0x83c81L;

    private static final long METHOD_M1 = 1L << 7;
    private static final long FCI_RAR5_COMPAT = 1L << 20;

    private static final long KB = 1024L;
    private static final long MB = 1024L * KB;
    private static final long GB = 1024L * MB;

    @TempDir Path tempDir;

    private static long compInfo(final long algo, final long dictBits, final long frac) {
        return algo | METHOD_M1 | (dictBits << 10) | (frac << 15);
    }

    /** Dictionary encodings that decode to a supported window, with their expected {@code long}. */
    private static Stream<Arguments> dictionaryEncodings() {
        return Stream.of(
                // algo, dictBits, frac, expected winSize, expected unpVersion
                Arguments.of(1L, 0L, 0L, 128 * KB, (byte) 70),
                // The plan's worked fractional example: 256 KB + 256 KB/32*16 = 384 KB.
                Arguments.of(1L, 1L, 16L, 384 * KB, (byte) 70),
                Arguments.of(1L, 3L, 0L, MB, (byte) 70),
                Arguments.of(1L, 13L, 0L, GB, (byte) 70),
                Arguments.of(1L, 15L, 0L, 4 * GB, (byte) 70),
                // What the committed fixture really carries: 4 GB + 4 GB/32*16 = 6 GB.
                Arguments.of(1L, 15L, 16L, 6 * GB, (byte) 70),
                // 4 GB + 4 GB/32*31 = 7.875 GB — the widest fraction at the RAR5 dict ceiling.
                Arguments.of(1L, 15L, 31L, 8455716864L, (byte) 70),
                // 0x20000 << 19 == UNPACK_MAX_DICT exactly: accepted, not refused.
                Arguments.of(1L, 19L, 0L, 64 * GB, (byte) 70),
                // algo 0 keeps the RAR5 4-bit dict mask: bits 19 & 0xf == 3 -> 1 MB, NOT 64 GB.
                Arguments.of(0L, 19L, 0L, MB, (byte) 50));
    }

    @ParameterizedTest
    @MethodSource("dictionaryEncodings")
    void dictionaryEncodingDecodesToExpectedWindow(
            final long algo,
            final long dictBits,
            final long frac,
            final long expectedWinSize,
            final byte expectedUnpVersion)
            throws Exception {
        final File archive = patched(compInfo(algo, dictBits, frac));

        try (Archive a = new Archive(archive)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getRar5WinSize())
                    .as("dict bits %d + fraction %d", dictBits, frac)
                    .isEqualTo(expectedWinSize);
            assertThat(hd.getUnpVersion()).isEqualTo(expectedUnpVersion);
        }
    }

    @Test
    void committedFixtureIsGenuineRar7WithAFractionalDictionary() throws Exception {
        // Guards against fixture drift: this row is NOT patched, it is what rar 7.23 wrote.
        try (Archive a = new Archive(fixture())) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion())
                    .as("algorithm version 1 -> VER_PACK7")
                    .isEqualTo((byte) 70);
            assertThat(hd.getRar5WinSize())
                    .as("-md6g, a fraction no RAR5 header can encode")
                    .isEqualTo(6 * GB);
        }
    }

    /** Encodings above UNPACK_MAX_DICT (64 GB) are refused as an unknown version. */
    private static Stream<Arguments> oversizedEncodings() {
        return Stream.of(
                // 64 GB + 64 GB/32*1 == 66 GB: one fraction step past the ceiling.
                Arguments.of(19L, 1L),
                // The widest encodable word: 0x20000 << 31, plus the full fraction.
                Arguments.of(31L, 31L));
    }

    @ParameterizedTest
    @MethodSource("oversizedEncodings")
    void dictionaryAboveUnpackMaxDictIsRefused(final long dictBits, final long frac)
            throws Exception {
        try (Archive a = new Archive(patched(compInfo(1L, dictBits, frac)))) {
            assertThat(a.getFileHeaders().get(0).getUnpVersion())
                    .as("dict bits %d + fraction %d exceeds UNPACK_MAX_DICT", dictBits, frac)
                    .isEqualTo((byte) -1);
        }
    }

    @Test
    void unknownAlgorithmVersionCarriesNoWindow() throws Exception {
        // unrar: `if (hd->Dir || UnpVer>1) hd->WinSize=0;` — an algorithm junrar cannot decode
        // must not surface a dictionary claim at all.
        try (Archive a = new Archive(patched(compInfo(2L, 15L, 16L)))) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion()).isEqualTo((byte) -1);
            assertThat(hd.getRar5WinSize()).isZero();
        }
    }

    @Test
    void rar5CompatFlagRoutesToTheRar5Decoder() throws Exception {
        // FCI_RAR5_COMPAT: written by RAR7, decodable by RAR5 (no ExtraDist) — unrar demotes
        // UnpVer to VER_PACK5 while keeping the RAR7 5-bit + fraction dictionary decode.
        try (Archive a = new Archive(patched(compInfo(1L, 1L, 16L) | FCI_RAR5_COMPAT))) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getUnpVersion()).as("RAR5-decodable").isEqualTo((byte) 50);
            assertThat(hd.getRar5WinSize())
                    .as("still the RAR7 fractional decode")
                    .isEqualTo(384 * KB);
        }
    }

    @Test
    void rar5CompatAboveUnpackMaxDictIsStillRefused() throws Exception {
        // Order matters: unrar demotes to VER_PACK5 first, then the ceiling check overrides it.
        try (Archive a = new Archive(patched(compInfo(1L, 19L, 1L) | FCI_RAR5_COMPAT))) {
            assertThat(a.getFileHeaders().get(0).getUnpVersion()).isEqualTo((byte) -1);
        }
    }

    // ---- public-path behaviour -----------------------------------------------------------------

    @Test
    void dictionaryBombIsRefusedBeforeAllocationUnderDefaultOptions() throws Exception {
        // Dict-bomb row: a 64 GB claim on a kilobyte-scale stream, routed to the RAR5 decoder by
        // FCI_RAR5_COMPAT. The resource guard trips before any window allocation, and the message
        // carries the offending size (B-S3: the default maxDictionarySize budget stays 4 GiB —
        // M4.1 raises nothing).
        final File archive = patched(compInfo(1L, 19L, 0L) | FCI_RAR5_COMPAT);

        try (Archive a = new Archive(archive)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getRar5WinSize()).isEqualTo(64 * GB);

            final Throwable thrown =
                    catchThrowable(() -> a.extractFile(hd, new ByteArrayOutputStream()));
            assertThat(thrown).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
            assertThat(thrown.getMessage()).contains(Long.toString(64 * GB));
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private File fixture() throws Exception {
        final byte[] bytes =
                Files.readAllBytes(Paths.get(getClass().getResource("rar7/rar7-md6g.rar").toURI()));
        final Path p = tempDir.resolve("rar7-md6g.rar");
        Files.write(p, bytes);
        return p.toFile();
    }

    /**
     * Overwrite the first FILE header's 3-byte compression-info vint and recompute the block
     * header CRC32 (the M3.7 patch recipe, as in {@code ArchiveRar5LinkTest#patchTarget}).
     * Length-preserving by construction: RAR5 vints are 7-bit groups with a continuation bit and
     * are not required to be minimal, so any 21-bit word re-encodes into the same 3 bytes.
     */
    private File patched(final long newCompInfo) throws Exception {
        final File archive = fixture();
        long position = -1;
        try (Archive a = new Archive(archive)) {
            position = a.getFileHeaders().get(0).getPositionInFile();
        }

        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int headerLength =
                Rar5BaseBlock.checkHeaderSize(
                        Arrays.copyOfRange(
                                bytes,
                                (int) position,
                                (int) position + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header =
                Arrays.copyOfRange(bytes, (int) position, (int) position + headerLength);

        final byte[] from = vint3(BASE_COMP_INFO);
        final int idx = indexOf(header, from);
        assertThat(idx)
                .as("compression info %s in the fixture header", Long.toHexString(BASE_COMP_INFO))
                .isGreaterThanOrEqualTo(0);
        assertThat(indexOf(header, from, idx + 1))
                .as("compression info must be unambiguous")
                .isEqualTo(-1);

        System.arraycopy(vint3(newCompInfo), 0, header, idx, 3);
        Raw.writeIntLittleEndian(
                header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, (int) position, header.length);
        Files.write(archive.toPath(), bytes);
        return archive;
    }

    /** Encode a value as exactly 3 vint bytes (padding with redundant groups when minimal is shorter). */
    private static byte[] vint3(final long value) {
        if ((value >>> 21) != 0) {
            throw new IllegalArgumentException(
                    "value wider than 3 vint bytes: " + Long.toHexString(value));
        }
        return new byte[] {
            (byte) ((value & 0x7f) | 0x80),
            (byte) (((value >>> 7) & 0x7f) | 0x80),
            (byte) ((value >>> 14) & 0x7f),
        };
    }

    private static int indexOf(final byte[] haystack, final byte[] needle) {
        return indexOf(haystack, needle, 0);
    }

    private static int indexOf(final byte[] haystack, final byte[] needle, final int start) {
        outer:
        for (int i = start; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
