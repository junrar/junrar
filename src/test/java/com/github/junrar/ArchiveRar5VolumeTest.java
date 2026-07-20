package com.github.junrar;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.MissingNextVolumeException;
import com.github.junrar.exception.MissingPreviousVolumeException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BlockType;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.rarfile.rar5.Rar5MainHeader;
import com.github.junrar.volume.InputStreamVolumeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

/**
 * M3.9 (issue #30) RAR5 multi-volume acceptance: {@code .partN.rar} sets spanning through
 * {@code ComprDataIO}'s volume path, driven through the M3.2 pre-gate harness until M3.11.
 * Fixtures were produced with {@code rar 7.23 -ma5 -v100k} ({@code volumes/rar5-part/README.md});
 * expected SHA-256s are the {@code unrar 7.23} {@code p} oracle output.
 *
 * <p>Split-entry checksum semantics pinned against unrar 6.2.12 ({@code 8f437ab}): every
 * {@code HFL_SPLITAFTER} part header stores the checksum of the <em>packed</em> chunk in that
 * volume, verified at the volume switch ({@code volume.cpp:19-26}); the final part stores the
 * end-to-end <em>unpacked</em> checksum, verified only when the last header's split-after flag
 * is clear ({@code extract.cpp:866}).
 */
@Timeout(60)
class ArchiveRar5VolumeTest {

    private static final String SHA_NOTE = "73445cfe9a3c3b7ed4c6d90a8ce8b44f57421686a3f7e7aae9d1f7de9920c20e";
    private static final String SHA_SPANNED = "6157b5e54e84c4e31adfe9db24111627c8e080d5d7ba8fc83587e07b40191945";
    private static final String SHA_SPANNED2 = "a256034eb71944e9499bac3cd7d7f9546afb3ff4cc380bd9f6e3db85de0ab93c";
    private static final String[] SHA_SOLID = {
        "de7d3a2fa5c711d25a57c2f1e3fbee2357bde7ef7a8c785638270957337fa80f",
        "2ac39336a88a5007b0e0f2dd919f966da0daa49b89444058a2ef757533e0164c",
        "73b834f92a9cbe931ac6845b01ade5da66e688c51f4bb48a4769f05fbd2a26d6",
        "8ffcdfcdfc9ee3f3201a23a0ff375e8d28f33a87ad0a1eb33af1de0eb96f7cb1",
        "d09e2718f398040f47f84a1be5c2a810b72367b257ab9a1fdbf58df83213a2b8",
        "bbe5807f7df08cec918fd6b2289e306f2a91a8ac7c605d3282bc05ab942dca1d",
    };

    @TempDir
    Path tempDir;

    private File part(final String prefix, final int n) throws Exception {
        final String name = prefix + ".part" + n + ".rar";
        final byte[] bytes = Files.readAllBytes(
            Paths.get(getClass().getResource("volumes/rar5-part/" + name).toURI()));
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

    /** Iterator-driven extraction (the volume switch re-parses the header list mid-iteration). */
    private static Map<String, String> extractAll(final Archive a) throws Exception {
        final Map<String, String> digests = new HashMap<>();
        for (final FileHeader fh : a) {
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            a.extractFile(fh, os);
            digests.put(fh.getFileName(), sha256(os.toByteArray()));
        }
        return digests;
    }

    @Test
    void threePartSetExtractsFromFiles() throws Exception {
        final File first = part("vols", 1);
        part("vols", 2);
        part("vols", 3);
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            // MHFL_VOLUME/MHFL_VOLNUMBER parse facts on the first volume.
            final Rar5MainHeader main = mainHeader(a);
            assertThat(main.isVolume()).isTrue();
            assertThat(main.isFirstVolume()).isTrue();
            assertThat(main.getVolumeNumber()).isZero();
            // HFL_SPLITAFTER topology: the spanned entry continues past this volume.
            final List<FileHeader> files = a.getFileHeaders();
            assertThat(files).extracting(FileHeader::getFileName)
                .containsExactly("note.txt", "spanned.bin");
            assertThat(files.get(1).isSplitAfter()).isTrue();

            final Map<String, String> digests = extractAll(a);
            assertThat(digests).containsOnly(
                entry("note.txt", SHA_NOTE),
                entry("spanned.bin", SHA_SPANNED));
        }
    }

    @Test
    void threePartSetExtractsFromInputStreams() throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            streams.add(new FileInputStream(part("vols", n)));
        }
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(new InputStreamVolumeManager(streams))) {
            final Map<String, String> digests = extractAll(a);
            assertThat(digests).containsOnly(
                entry("note.txt", SHA_NOTE),
                entry("spanned.bin", SHA_SPANNED));
        }
    }

    @Test
    void solidMultiVolumeExtracts() throws Exception {
        final File first = part("solid", 1);
        for (int n = 2; n <= 4; n++) {
            part("solid", n);
        }
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            // Beyond the first member, every entry depends on the preceding solid window.
            assertThat(a.getFileHeaders().get(1).isSolid()).isTrue();
            final Map<String, String> digests = extractAll(a);
            final Map<String, String> expected = new HashMap<>();
            for (int i = 0; i < SHA_SOLID.length; i++) {
                expected.put("solid" + i + ".bin", SHA_SOLID[i]);
            }
            assertThat(digests).isEqualTo(expected);
        }
    }

    @Test
    void blake2SplitSetExtractsAcrossSpan() throws Exception {
        final File first = part("blake", 1);
        part("blake", 2);
        part("blake", 3);
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            final List<FileHeader> files = a.getFileHeaders();
            // -htb row is not vacuous: the split entry really carries a BLAKE2 digest.
            assertThat(files.get(1).getHashType()).isEqualTo(Rar5HashType.BLAKE2);
            assertThat(files.get(1).isSplitAfter()).isTrue();
            final Map<String, String> digests = extractAll(a);
            assertThat(digests).containsOnly(
                entry("note.txt", SHA_NOTE),
                entry("spanned.bin", SHA_SPANNED));
        }
    }

    /**
     * 400 KB &gt; the decode window's 256 KB min-alloc floor, so unpacked-data flushes
     * interleave with the volume switches: the end-to-end BLAKE2 digest only matches when the
     * unpacked accumulators survive every merge (unrar keeps {@code UnpHash} running across
     * {@code MergeArchive}; the smaller sets flush once at the end and cannot see a reset).
     */
    @Test
    void bigSpanKeepsUnpackedDigestAcrossInterleavedFlushes() throws Exception {
        final File first = part("big", 1);
        for (int n = 2; n <= 4; n++) {
            part("big", n);
        }
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            final Map<String, String> digests = extractAll(a);
            assertThat(digests).containsOnly(entry("spanned2.bin", SHA_SPANNED2));
        }
    }

    @Test
    void missingPart2FileThrowsTyped() throws Exception {
        final File first = part("vols", 1);
        part("vols", 3); // part2 deliberately absent
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            final Throwable thrown = catchThrowable(() -> extractAll(a));
            assertThat(thrown).isExactlyInstanceOf(MissingNextVolumeException.class);
        }
    }

    @Test
    void missingPart2StreamThrowsTyped() throws Exception {
        // Only the first stream is supplied: the manager's null return is the missing-volume path.
        final List<InputStream> streams = new ArrayList<>();
        streams.add(new FileInputStream(part("vols", 1)));
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(new InputStreamVolumeManager(streams))) {
            final Throwable thrown = catchThrowable(() -> extractAll(a));
            assertThat(thrown).isExactlyInstanceOf(MissingNextVolumeException.class);
        }
    }

    @Test
    void startedMidSetThrowsTyped() throws Exception {
        final File second = part("vols", 2);
        part("vols", 3);
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(second)) {
            final List<FileHeader> files = a.getFileHeaders();
            assertThat(files.get(0).isSplitBefore()).isTrue();
            final Throwable thrown = catchThrowable(() -> extractAll(a));
            assertThat(thrown).isExactlyInstanceOf(MissingPreviousVolumeException.class);
        }
    }

    /**
     * Byte-patches part1's split file header to lie about its packed-chunk CRC32 (field flip +
     * header-CRC recompute, the M3.7 patch recipe). The data itself is intact, so the end-to-end
     * unpacked checksum alone would pass — only the merge-time packed-hash check
     * ({@code volume.cpp:19-26}) can catch the lie.
     */
    @Test
    void lyingPackedCrcFailsAtVolumeSwitch() throws Exception {
        final File first = part("vols", 1);
        part("vols", 2);
        part("vols", 3);

        final long position;
        final int fileCrc;
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            final FileHeader spanned = a.getFileHeaders().get(1);
            position = spanned.getPositionInFile();
            fileCrc = spanned.getFileCRC();
        }

        final byte[] bytes = Files.readAllBytes(first.toPath());
        final int headerLength = Rar5BaseBlock.checkHeaderSize(
            Arrays.copyOfRange(bytes, (int) position, (int) position + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, (int) position, (int) position + headerLength);
        int crcOffset = -1;
        for (int i = 4; i <= header.length - 4; i++) {
            if (Raw.readIntLittleEndian(header, i) == fileCrc) {
                assertThat(crcOffset).as("Data CRC32 field must be unique in the header").isEqualTo(-1);
                crcOffset = i;
            }
        }
        assertThat(crcOffset).isGreaterThan(0);
        header[crcOffset] ^= 0x5A;
        final int headerCrc = RarCRC.computeHeaderCrc32(header, 4, header.length - 4);
        Raw.writeIntLittleEndian(header, 0, headerCrc);
        System.arraycopy(header, 0, bytes, (int) position, header.length);
        Files.write(first.toPath(), bytes);

        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(first)) {
            final Throwable thrown = catchThrowable(() -> extractAll(a));
            assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
        }
    }

    private static Rar5MainHeader mainHeader(final Archive a) {
        for (final Object block : a.getHeaders()) {
            if (block instanceof Rar5MainHeader
                && ((Rar5MainHeader) block).getRar5Type() == Rar5BlockType.MAIN) {
                return (Rar5MainHeader) block;
            }
        }
        throw new AssertionError("no RAR5 main header parsed");
    }
}
