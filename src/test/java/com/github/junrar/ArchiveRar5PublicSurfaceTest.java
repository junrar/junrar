package com.github.junrar;

import com.github.junrar.volume.FileVolumeManager;
import com.github.junrar.volume.InputStreamVolumeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.11 (issue #32) public-surface RAR5 success matrix: the complete plan &sect;4.2 RAR5
 * fixture matrix driven through the four public surfaces — {@link Junrar#extract(File, File,
 * ArchiveOptions)}, {@link Junrar#extract(InputStream, File, ArchiveOptions)},
 * {@link Junrar#extract(com.github.junrar.volume.VolumeManager, File, ArchiveOptions)} and
 * {@link Junrar#getContentsDescription(File)} — asserting successful extraction against the
 * unrar 7.23 oracle SHA-256 digests recorded at fixture-generation time (fixture READMEs;
 * digests for rows without a prior archive-level test captured 2026-07-21 via
 * {@code unrar p -inul &lt;archive&gt; &lt;entry&gt; | shasum -a 256} with the same
 * unrar 7.23 oracle binary).
 *
 * <p>Authored and executed RED on the gate-closed tree (every row failing with
 * {@code UnsupportedRarV5Exception}), then run GREEN unchanged in the gate-lift commit —
 * the review B-S6 frozen-test discipline; this file is the red&rarr;green evidence and must
 * not be edited between the two runs.
 *
 * <p>Surface notes (conscious, recorded): the InputStream surface is skipped for
 * multi-volume sets (a single stream cannot span volumes; the InputStream volume rows run
 * through {@link InputStreamVolumeManager} on the VolumeManager surface instead) and
 * {@code getContentsDescription} is skipped for header-encrypted ({@code -hp}) archives
 * (it has no password parameter; that failure path is covered by the crypto tests).
 * On the first volume of a multi-volume set {@code getContentsDescription} lists the
 * entries whose headers live in that volume (it reads headers only and never advances
 * volumes).
 */
@Timeout(120)
class ArchiveRar5PublicSurfaceTest {

    private static final String PW = "junrar";

    // Oracle SHA-256 digests: rar5unpack payloads (rar5unpack/README.md).
    private static final String SHA_SMALL = "afa47c7795a9476007865eed8bae473d873ae6e80c769f20a42c00bc88cbe09c";
    private static final String SHA_MED = "ae9df0480568cad2c176c7a0cb228a0676ce927a3fbc7df80ae324edc7bf24b0";
    private static final String SHA_BIG = "6864d2cd164a21bb287e58a0ae1940c254eb0ff5af61c365b0e8d11119fb2e78";
    private static final String SHA_TINY_1GB_CLAIM = "d77aa01d309fea8acfcf79acf0140b83e4f72f413ecc807f603190d4bfa55576";
    private static final String[] SHA_SOLID = {
        "d99fac30d0b27a3dd7bc437a716ba05c3574617fa82efa4959650fb576dd9e11",
        "7e753e1f8090deba1687f6ccd5610e105d8b49c6c8a71a4e0b87f1fb7b086126",
        "43c943b9814425661e6fdeaca9060ab1e7e8fa0af501618810eb3c8d55629b46",
        "634bd533555da52660aa54ecd1d266ff124897a66e989c2d36a460e6852ca412",
        "d7539b9930a82e3320c498edf3170b9502dfd8efecd60f6ca6651e94ff88d237",
    };

    // Filter payloads (rar5filters/README.md).
    private static final String SHA_DELTA = "808dfc21490452cb7740e316f1255a35578aa3ec3b56e42956d18e43dd7279c1";
    private static final String SHA_E8 = "65b41504002936b8c0fc5c389ff5d9a191e8d1d7f584458fe0198e442a1397fd";
    private static final String SHA_E8E9 = "4e53b7d69dc125bf232dff52d9d9d2a183eaecae9dad85bee80358133856f4e3";

    // BLAKE2 payload (blake2/blake2-payload.bin, the unrar-extracted bytes — M3.5).
    private static final String SHA_BLAKE2_PAYLOAD = "9fdc4f360d729e9c8d0534bf686d49a1008055c186d48114b156e96c6364e80f";

    // Multi-volume payloads (volumes/rar5-part/README.md).
    private static final String SHA_NOTE = "73445cfe9a3c3b7ed4c6d90a8ce8b44f57421686a3f7e7aae9d1f7de9920c20e";
    private static final String SHA_SPANNED = "6157b5e54e84c4e31adfe9db24111627c8e080d5d7ba8fc83587e07b40191945";
    private static final String SHA_SPANNED2 = "a256034eb71944e9499bac3cd7d7f9546afb3ff4cc380bd9f6e3db85de0ab93c";
    private static final String[] SHA_VOL_SOLID = {
        "de7d3a2fa5c711d25a57c2f1e3fbee2357bde7ef7a8c785638270957337fa80f",
        "2ac39336a88a5007b0e0f2dd919f966da0daa49b89444058a2ef757533e0164c",
        "73b834f92a9cbe931ac6845b01ade5da66e688c51f4bb48a4769f05fbd2a26d6",
        "8ffcdfcdfc9ee3f3201a23a0ff375e8d28f33a87ad0a1eb33af1de0eb96f7cb1",
        "d09e2718f398040f47f84a1be5c2a810b72367b257ab9a1fdbf58df83213a2b8",
        "bbe5807f7df08cec918fd6b2289e306f2a91a8ac7c605d3282bc05ab942dca1d",
    };

    // M3.3-era fixtures (captured via the unrar 7.23 oracle, see class Javadoc).
    private static final String SHA_FILE1_TXT = "2b69a7f7ba9698a49a49a33d4857610b4408109ff096e5d5ec2a1394cf33314a";
    private static final String SHA_FILE2_TXT = "24040d7b811b76bf0b32993100e66d4d2dadd90ca3e5d250c17a0f20491a3531";
    private static final String[] SHA_SOLID9 = {
        "ecdc5536f73bdae8816f0ea40726ef5e9b810d914493075903bb90623d97b1d8",
        "67ee5478eaadb034ba59944eb977797b49ca6aa8d3574587f36ebcbeeb65f70e",
        "94f6e58bd04a4513b8301e75f40527cf7610c66d1960b26f6ac2e743e108bdac",
        "899e08f22584531d18a4ec45a5dd1daef683ea941daa8c95969f1fe5c08a8137",
        "2dbe9e279b85ee61cab10513c39791a2ce4bad678906832551afd1446c24bbe3",
        "6533870544daa5db6e1f35b346390e92403b2f3eab8839f3e251fae53c699492",
        "ae66f7a411a2c31287c836faf537fb650ea34869571c462c153a3c71544e5679",
        "3b8badb856a190fe3a35a85c9ee6a806986f51e2125f02c46ba9e42a50559acd",
        "9c03f1a0e14af0400b2fd4942754c99718d2a247ab10820f3f2523ffc9e7760b",
    };
    private static final String SHA_PW_FILE1 = "ecdc5536f73bdae8816f0ea40726ef5e9b810d914493075903bb90623d97b1d8";
    private static final String SHA_NONASCII_PAYLOAD = "efaffec1b655c3e25054629091aa6b46f71d34470bf3b587d3b7cdc1b7e8d135";

    @TempDir
    Path tempDir;

    // ---- rar5unpack core matrix ---------------------------------------------------------------

    @Test
    void storePlain128kAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m0-plain-128k.rar", null, mapOf("small.bin", SHA_SMALL), mapOf("small.bin", 8400L));
    }

    @Test
    void m3Plain128kAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m3-plain-128k.rar", null, mapOf("med.bin", SHA_MED), mapOf("med.bin", 307200L));
    }

    @Test
    void m5Plain128kAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m5-plain-128k.rar", null, mapOf("med.bin", SHA_MED), mapOf("med.bin", 307200L));
    }

    @Test
    void m3Plain32mAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m3-plain-32m.rar", null, mapOf("big.bin", SHA_BIG), mapOf("big.bin", 41943040L));
    }

    @Test
    void m3Solid128kAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m3-solid-128k.rar", null, solidExpectation(), solidSizes());
    }

    @Test
    void m5Solid128kAllSurfaces() throws Exception {
        assertAllSurfaces("rar5unpack/m5-solid-128k.rar", null, solidExpectation(), solidSizes());
    }

    @Test
    void m3DataEncryptedAllSurfaces() throws Exception {
        // -p: file data encrypted, headers plaintext — every surface works, listing needs no password.
        assertAllSurfaces("rar5unpack/m3-enc-p.rar", PW, mapOf("med.bin", SHA_MED), mapOf("med.bin", 307200L));
    }

    @Test
    void m3HeaderEncryptedExtractionSurfaces() throws Exception {
        // -hp: headers encrypted; getContentsDescription (no password parameter) is consciously skipped.
        assertExtractionSurfaces("rar5unpack/m3-enc-hp.rar", PW, mapOf("med.bin", SHA_MED));
    }

    @Test
    void m3StringPasswordOverloadExtracts() throws Exception {
        // The legacy String-password overload delegates to the ArchiveOptions path — one row proves it.
        final File dest = newDest();
        final List<File> out = Junrar.extract(fixture("rar5unpack/m3-enc-p.rar"), dest, PW);
        assertThat(out).isNotEmpty();
        assertThat(digestTree(dest)).isEqualTo(mapOf("med.bin", SHA_MED));
    }

    @Test
    void oneGbDictionaryClaimExtractsOnPublicPath() throws Exception {
        // Byte-patched 1 GB dictionary claim (B-S3): extracts under DEFAULT options on the public path.
        assertAllSurfaces("rar5unpack/m3-1gb-claim.rar", null, mapOf("tiny.bin", SHA_TINY_1GB_CLAIM), mapOf("tiny.bin", 3200L));
    }

    // ---- filters ------------------------------------------------------------------------------

    @Test
    void deltaFilterAllSurfaces() throws Exception {
        assertAllSurfaces("rar5filters/rar5-delta.rar", null, mapOf("delta.bin", SHA_DELTA), mapOf("delta.bin", 262144L));
    }

    @Test
    void e8FilterAllSurfaces() throws Exception {
        assertAllSurfaces("rar5filters/rar5-e8.rar", null, mapOf("e8.bin", SHA_E8), mapOf("e8.bin", 196608L));
    }

    @Test
    void e8e9FilterAllSurfaces() throws Exception {
        assertAllSurfaces("rar5filters/rar5-e8e9.rar", null, mapOf("e8e9.bin", SHA_E8E9), mapOf("e8e9.bin", 196608L));
    }

    // ---- BLAKE2 -------------------------------------------------------------------------------

    @Test
    void blake2HtbAllSurfaces() throws Exception {
        assertAllSurfaces("blake2/rar5-htb.rar", null, mapOf("payload.bin", SHA_BLAKE2_PAYLOAD), mapOf("payload.bin", 2000L));
    }

    @Test
    void blake2HtbMacEncryptedAllSurfaces() throws Exception {
        // -htb + -p: headers plaintext, data encrypted with the HASHMAC digest scheme.
        assertAllSurfaces("blake2/rar5-htb-mac.rar", PW, mapOf("payload.bin", SHA_BLAKE2_PAYLOAD), mapOf("payload.bin", 2000L));
    }

    // ---- M3.3-era fixtures (names/metadata rows) ----------------------------------------------

    @Test
    void tinyRar5FixtureAllSurfaces() throws Exception {
        assertAllSurfaces("rar5.rar", null,
            mapOf("FILE1.TXT", SHA_FILE1_TXT, "FILE2.TXT", SHA_FILE2_TXT),
            mapOf("FILE1.TXT", 7L, "FILE2.TXT", 7L));
    }

    @Test
    void solidNineFileFixtureAllSurfaces() throws Exception {
        final Map<String, String> expected = new HashMap<>();
        final Map<String, Long> sizes = new HashMap<>();
        for (int i = 0; i < SHA_SOLID9.length; i++) {
            expected.put("file" + (i + 1) + ".txt", SHA_SOLID9[i]);
            sizes.put("file" + (i + 1) + ".txt", 6L);
        }
        assertAllSurfaces("solid/rar5-solid.rar", null, expected, sizes);
    }

    // ---- password fixtures --------------------------------------------------------------------

    @Test
    void dataEncryptedPasswordFixtureAllSurfaces() throws Exception {
        assertAllSurfaces("password/rar5-password-junrar.rar", PW, mapOf("file1.txt", SHA_PW_FILE1), mapOf("file1.txt", 6L));
    }

    @Test
    void headerEncryptedPasswordFixtureExtracts() throws Exception {
        assertExtractionSurfaces("password/rar5-encrypted-junrar.rar", PW, mapOf("file1.txt", SHA_PW_FILE1));
    }

    @Test
    void headerEncryptedNonAsciiPasswordExtracts() throws Exception {
        assertExtractionSurfaces("password/rar5-nonascii-password.rar", "пароль密码ü", mapOf("file1.txt", SHA_NONASCII_PAYLOAD));
    }

    // ---- multi-volume -------------------------------------------------------------------------

    @Test
    void threePartSetAllVolumeSurfaces() throws Exception {
        assertVolumeSurfaces("vols", 3, mapOf("note.txt", SHA_NOTE, "spanned.bin", SHA_SPANNED));
    }

    @Test
    void solidMultiVolumeAllVolumeSurfaces() throws Exception {
        final Map<String, String> expected = new HashMap<>();
        for (int i = 0; i < SHA_VOL_SOLID.length; i++) {
            expected.put("solid" + i + ".bin", SHA_VOL_SOLID[i]);
        }
        assertVolumeSurfaces("solid", 4, expected);
    }

    @Test
    void blake2MultiVolumeAllVolumeSurfaces() throws Exception {
        assertVolumeSurfaces("blake", 3, mapOf("note.txt", SHA_NOTE, "spanned.bin", SHA_SPANNED));
    }

    @Test
    void bigSpanMultiVolumeAllVolumeSurfaces() throws Exception {
        assertVolumeSurfaces("big", 4, mapOf("spanned2.bin", SHA_SPANNED2));
    }

    @Test
    void firstVolumeContentsDescriptionListsItsEntries() throws Exception {
        // Header-reading surface on a multi-volume set: lists the first volume's entries
        // (it never advances volumes — conscious, recorded in the class Javadoc).
        final File first = volumeFixture("vols", 3);
        final List<ContentDescription> contents = Junrar.getContentsDescription(first);
        assertThat(contents).extracting(c -> c.path).containsExactly("note.txt", "spanned.bin");
    }

    // ---- links (M3.10 routing on the public path) ---------------------------------------------

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void linksFixtureExtractsWithOracleSemanticsOnPublicPath() throws Exception {
        final File dest = newDest();
        Junrar.extract(fixture("links/rar5-links.rar"), dest);
        final Path root = dest.toPath();

        final Path file = root.resolve("file.txt");
        assertThat(file).exists();
        assertThat(Files.isSymbolicLink(root.resolve("link-flat"))).isTrue();
        assertThat(Files.readSymbolicLink(root.resolve("link-flat")).toString()).isEqualTo("file.txt");
        assertThat(Files.isSymbolicLink(root.resolve("sub/link-up"))).isTrue();
        assertThat(Files.readSymbolicLink(root.resolve("sub/link-up")).toString()).isEqualTo("../file.txt");

        final Path hard = root.resolve("hard.txt");
        assertThat(Files.isSymbolicLink(hard)).isFalse();
        assertThat(Files.isSameFile(hard, file)).isTrue();

        final Path copyA = root.resolve("copy-a.bin");
        final Path copyB = root.resolve("copy-b.bin");
        assertThat(Files.isSameFile(copyB, copyA)).isFalse();
        assertThat(Files.readAllBytes(copyB)).isEqualTo(Files.readAllBytes(copyA));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static Map<String, String> solidExpectation() {
        final Map<String, String> expected = new HashMap<>();
        for (int i = 0; i < SHA_SOLID.length; i++) {
            expected.put("s" + i + ".bin", SHA_SOLID[i]);
        }
        return expected;
    }

    private static Map<String, Long> solidSizes() {
        final Map<String, Long> sizes = new HashMap<>();
        for (int i = 0; i < SHA_SOLID.length; i++) {
            sizes.put("s" + i + ".bin", 40960L);
        }
        return sizes;
    }

    private static ArchiveOptions options(final String password) {
        final ArchiveOptions.Builder builder = ArchiveOptions.builder();
        if (password != null) {
            builder.password(password.toCharArray());
        }
        return builder.build();
    }

    private File fixture(final String resource) throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource(resource).toURI()));
        final Path p = tempDir.resolve(resource.substring(resource.lastIndexOf('/') + 1));
        if (!Files.exists(p)) {
            Files.write(p, bytes);
        }
        return p.toFile();
    }

    /** Copies all parts of a {@code volumes/rar5-part} set next to each other; returns part1. */
    private File volumeFixture(final String prefix, final int parts) throws Exception {
        File first = null;
        for (int n = 1; n <= parts; n++) {
            final File f = fixture("volumes/rar5-part/" + prefix + ".part" + n + ".rar");
            if (n == 1) {
                first = f;
            }
        }
        return first;
    }

    private File newDest() throws Exception {
        return Files.createDirectory(tempDir.resolve("out-" + System.nanoTime())).toFile();
    }

    /** SHA-256 of every regular file under {@code dest}, keyed by {@code /}-separated relative path. */
    private static Map<String, String> digestTree(final File dest) throws Exception {
        final Map<String, String> digests = new HashMap<>();
        final Path root = dest.toPath();
        try (Stream<Path> walk = Files.walk(root)) {
            for (final Path p : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                final byte[] d = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
                final StringBuilder sb = new StringBuilder(d.length * 2);
                for (final byte x : d) {
                    sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
                }
                digests.put(root.relativize(p).toString().replace(File.separatorChar, '/'), sb.toString());
            }
        }
        return digests;
    }

    /** The three extraction surfaces: {@code Junrar.extract} from File, InputStream and VolumeManager. */
    private void assertExtractionSurfaces(final String resource, final String password,
                                          final Map<String, String> expected) throws Exception {
        final File archive = fixture(resource);

        final File byFile = newDest();
        assertThat(Junrar.extract(archive, byFile, options(password))).isNotEmpty();
        assertThat(digestTree(byFile)).as("Junrar.extract(File) for %s", resource).isEqualTo(expected);

        final File byStream = newDest();
        try (InputStream in = new FileInputStream(archive)) {
            Junrar.extract(in, byStream, options(password));
        }
        assertThat(digestTree(byStream)).as("Junrar.extract(InputStream) for %s", resource).isEqualTo(expected);

        final File byVolumeManager = newDest();
        Junrar.extract(new FileVolumeManager(archive), byVolumeManager, options(password));
        assertThat(digestTree(byVolumeManager)).as("Junrar.extract(VolumeManager) for %s", resource).isEqualTo(expected);
    }

    /** All four public surfaces for a single-volume archive whose headers are readable without a password. */
    private void assertAllSurfaces(final String resource, final String password,
                                   final Map<String, String> expected, final Map<String, Long> sizes) throws Exception {
        assertExtractionSurfaces(resource, password, expected);

        final List<ContentDescription> contents = Junrar.getContentsDescription(fixture(resource));
        final Map<String, Long> listed = new HashMap<>();
        for (final ContentDescription c : contents) {
            listed.put(c.path.replace('\\', '/'), c.size);
        }
        assertThat(listed).as("Junrar.getContentsDescription for %s", resource).isEqualTo(sizes);
    }

    /** Multi-volume surfaces: File (auto-advance), FileVolumeManager and InputStreamVolumeManager. */
    private void assertVolumeSurfaces(final String prefix, final int parts,
                                      final Map<String, String> expected) throws Exception {
        final File first = volumeFixture(prefix, parts);

        final File byFile = newDest();
        assertThat(Junrar.extract(first, byFile, options(null))).isNotEmpty();
        assertThat(digestTree(byFile)).as("Junrar.extract(File) for %s set", prefix).isEqualTo(expected);

        final File byFileVolumeManager = newDest();
        Junrar.extract(new FileVolumeManager(first), byFileVolumeManager, options(null));
        assertThat(digestTree(byFileVolumeManager)).as("Junrar.extract(FileVolumeManager) for %s set", prefix)
            .isEqualTo(expected);

        final List<InputStream> streams = new ArrayList<>();
        for (int n = 1; n <= parts; n++) {
            streams.add(new FileInputStream(new File(first.getParentFile(), prefix + ".part" + n + ".rar")));
        }
        try {
            final File byStreamVolumeManager = newDest();
            Junrar.extract(new InputStreamVolumeManager(streams), byStreamVolumeManager, options(null));
            assertThat(digestTree(byStreamVolumeManager))
                .as("Junrar.extract(InputStreamVolumeManager) for %s set", prefix).isEqualTo(expected);
        } finally {
            for (final InputStream s : streams) {
                s.close();
            }
        }
    }

    // Java 8 stand-ins for Map.of (test sources compile at release 8).
    private static <V> Map<String, V> mapOf(String k, V v) {
        Map<String, V> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static <V> Map<String, V> mapOf(String k1, V v1, String k2, V v2) {
        Map<String, V> m = mapOf(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
