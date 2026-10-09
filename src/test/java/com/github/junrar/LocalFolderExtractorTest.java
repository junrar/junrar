package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.UnsafeLinkException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import com.github.junrar.rarfile.rar5.Rar5Redirection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class LocalFolderExtractorTest {
    private static File tempFolder;

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    /**
     * An entry name the platform cannot encode must not abort the extraction with an exception
     * {@code Junrar.extract} does not declare. {@link java.io.File#toPath()} throws {@link
     * java.nio.file.InvalidPathException}, which is unchecked, so a caller honouring the signature
     * cannot catch it.
     *
     * <p>In the field this is reached through the platform encoding: {@code sun.jnu.encoding}
     * follows the locale, a container started without {@code LANG} reports {@code
     * ANSI_X3.4-1968}, and an ordinary CJK entry name then has no {@code Path}. That route only
     * reproduces where the JVM was started with such a locale, so a test using it has to skip
     * itself everywhere else.
     *
     * <p>A lone surrogate has no representation in any charset, UTF-8 included, so it reproduces
     * the same refusal on every host without touching the environment. Measured in
     * {@code eclipse-temurin:17-jdk}: on a UTF-8 JVM {@code "\ud800.txt"} throws while a valid
     * surrogate pair and the CJK name both pass; under {@code LANG=C} all three throw.
     *
     * <p>Setting the variable from inside the test does not work: {@code sun.jnu.encoding} is
     * derived once when the JVM starts, so junit-pioneer's {@code @SetEnvironmentVariable} changes
     * what {@code System.getenv} reports and nothing that {@code File.toPath()} consults.
     */
    @Test
    public void loneSurrogateEntryName_isLocaleIndependent() throws IOException {
        final File dest = TestCommons.createTempDir();
        final LocalFolderExtractor extractor = new LocalFolderExtractor(dest);

        final Archive archive = mock(Archive.class);
        final FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("\ud800.txt");

        final Throwable thrown = catchThrowable(() -> extractor.extract(archive, fileHeader));

        assertThat(thrown)
                .describedAs("extract declares RarException and IOException only")
                .isNull();
        assertThat(dest.listFiles()).isNotEmpty();
    }

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops");

        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");

        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops/");

        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains invalid path")
                .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException2() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "parent-dir.rar");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempFolder);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            File expectedInvalidPath = new File(tempFolder.getParentFile().getParentFile(), "tmp");
            Throwable thrown =
                    catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                    .containsIgnoringCase("Rar contains file with invalid path")
                    .containsIgnoringCase(expectedInvalidPath.toString());
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException3() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "sibling-prefix-traversal.rar");

        File tempDir = new File("/tmp/extract");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempDir);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            String expectedInvalidPath = "/tmp/extract_evil/pwned.txt";
            Throwable thrown =
                    catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                    .containsIgnoringCase("Rar contains file with invalid path")
                    .containsIgnoringCase(expectedInvalidPath);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    /**
     * Upstream's mkdir-escape PoC (junrar `e6e333b1`). The entry name
     * {@code ../extract_evil/../extract/payload.txt} passes canonical containment — it resolves
     * back inside the destination — but the pre-fix {@code makeFile} mkdir'd every path component
     * on the way, creating the sibling {@code extract_evil} outside it.
     *
     * <p>The fixture's headers are additionally corrupt — {@code unrar 7.23} reports "the file
     * header is corrupt" and {@code Total errors: 5} — which upstream ignores, since it does not
     * verify RAR3 header CRCs. This branch does (P0.7 / issue #12, {@code a0870d38}), so the entry
     * is also refused with {@link CorruptHeaderException} where upstream extracts it.
     *
     * <p>That refusal is <b>not</b> what stops the escape, and it must not be mistaken for it:
     * {@code extract} calls {@code createFile} — and therefore {@code makeFile}, which creates
     * directories — <em>before</em> {@code Archive.extractFile}, which is where the header-CRC
     * gate throws. The directories would already exist by then. The {@code doesNotExist} assertion
     * below consequently depends on {@code makeFile} alone, exactly as upstream's does; reverting
     * {@code makeFile} to its pre-fix per-component {@code mkdir} loop makes this row fail
     * (executed negative control), alongside
     * {@link #wellFormedHeaderCannotMkdirOutsideDestination()}, which pins the same guard without
     * relying on a corrupt fixture at all.
     */
    @Test
    public void mkdirEscapePocCreatesNoDirectoryOutsideTarget() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "mkdir-escape.rar");

        Path root = Files.createTempDirectory("mkdir-escape");
        Path tempDir = Files.createDirectories(root.resolve("extract"));
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempDir.toFile());

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            Path mkdirEscapeDir = root.resolve("extract_evil");
            final Throwable thrown =
                    catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            // The security property: no directory outside the destination, whatever the outcome.
            assertThat(mkdirEscapeDir).doesNotExist();
            // And, unlike upstream, the corrupt header is refused rather than extracted.
            assertThat(thrown)
                    .as("a broken FILE header is refused (P0.7, issue #12)")
                    .isExactlyInstanceOf(CorruptHeaderException.class);
        }
    }

    /**
     * The mkdir-escape guard itself, independent of the PoC fixture's corrupt headers: the same
     * escaping name driven through a well-formed header, so nothing but {@code createFile} can be
     * what stops it. Reverting it to the pre-fix per-component {@code mkdir} loop makes this fail
     * (executed negative control), so the guard is load-bearing, not decorative.
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void wellFormedHeaderCannotMkdirOutsideDestination() throws Exception {
        final Path root = Files.createTempDirectory("mkdir-escape-wellformed");
        final Path dest = Files.createDirectories(root.resolve("extract"));
        final FileHeader fh = mock(FileHeader.class);
        when(fh.getFileName()).thenReturn("../extract_evil/../extract/payload.txt");

        new LocalFolderExtractor(dest.toFile()).extract(mock(Archive.class), fh);

        assertThat(new File(root.toFile(), "extract_evil"))
                .as("no directory is created outside the destination")
                .doesNotExist();
        assertThat(new File(dest.toFile(), "payload.txt"))
                .as("the entry itself still lands, normalized, inside the destination")
                .exists();
    }

    /**
     * With a symlinked destination, popping '..' lexically off the symlink leaves the real root:
     * {@code a/lnk/../ex} is {@code a/ex}, while the kernel resolves it to {@code b/ex}. The file
     * must land where the containment check looked, and keep the caller's spelling of the path.
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void fileEntryUnderSymlinkedDestinationStaysInside() throws Exception {
        final Path root = Files.createTempDirectory("file-symlinked-dest");
        final Path real = Files.createDirectories(root.resolve("b/ex"));
        final Path dest =
                Files.createSymbolicLink(
                        Files.createDirectories(root.resolve("a")).resolve("lnk"), real);
        final FileHeader fh = mock(FileHeader.class);
        when(fh.getFileName()).thenReturn("../ex/sub/payload.txt");

        final File written =
                new LocalFolderExtractor(dest.toFile()).extract(mock(Archive.class), fh);

        assertThat(root.resolve("a/ex")).doesNotExist();
        assertThat(real.resolve("sub/payload.txt")).exists();
        assertThat(written).isEqualTo(new File(dest.toFile(), "sub/payload.txt"));
    }

    /**
     * GHSA-ccq9-hw6f-p9cm: the RAR5 link twin of the guard above. Containment passes on the
     * canonical path; on a JVM whose {@code Path.relativize} keeps interposed '..' (Java 8), an
     * un-normalized link path made {@code Files.createDirectories} create the sibling directory.
     */
    @Test
    public void fileCopyEntryCannotMkdirOutsideDestination() throws Exception {
        assertInterposedDotDotStaysInside(Rar5RedirType.FILE_COPY);
    }

    @Test
    public void hardlinkEntryCannotMkdirOutsideDestination() throws Exception {
        assertInterposedDotDotStaysInside(Rar5RedirType.HARDLINK);
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void symlinkEntryCannotMkdirOutsideDestination() throws Exception {
        assertInterposedDotDotStaysInside(Rar5RedirType.UNIX_SYMLINK);
    }

    /**
     * The lexical normalization must be anchored on the canonical destination: with a symlinked
     * destination, '..' popped lexically from the symlink would leave the real root.
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void linkEntryUnderSymlinkedDestinationStaysInside() throws Exception {
        final Path root = Files.createTempDirectory("link-symlinked-dest");
        final Path real = Files.createDirectories(root.resolve("b/ex"));
        final Path dest =
                Files.createSymbolicLink(
                        Files.createDirectories(root.resolve("a")).resolve("lnk"), real);

        extractLink(Rar5RedirType.FILE_COPY, dest, "../ex/sub/link");

        assertThat(root.resolve("a/ex")).doesNotExist();
        assertThat(real.resolve("sub/link")).exists();
    }

    /** A symlink already extracted under a symlinked destination is still refused as a parent. */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void linkThroughEarlierSymlinkUnderSymlinkedDestinationRejected() throws Exception {
        final Path root = Files.createTempDirectory("link-symlinked-dest-dlnk");
        final Path real = Files.createDirectories(root.resolve("b/ex"));
        Files.createDirectory(real.resolve("inside"));
        Files.createSymbolicLink(real.resolve("dlnk"), Paths.get("inside"));
        final Path dest =
                Files.createSymbolicLink(
                        Files.createDirectories(root.resolve("a")).resolve("lnk"), real);

        final Throwable thrown =
                catchThrowable(
                        () -> extractLink(Rar5RedirType.FILE_COPY, dest, "../ex/dlnk/sub/copy"));

        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        assertThat(real.resolve("inside/sub")).doesNotExist();
    }

    /** An absolute entry name is placed under the destination, as for regular files. */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void absoluteLinkNameStaysUnderDestination() throws Exception {
        final Path root = Files.createTempDirectory("link-absolute-name");
        final Path dest = Files.createDirectories(root.resolve("extract"));
        final Path nested = dest.toRealPath().resolve(dest.toString().substring(1)).resolve("link");

        final File written =
                new LocalFolderExtractor(dest.toFile())
                        .extract(
                                mock(Archive.class),
                                symlinkHeader(dest.resolve("link").toString(), "../secret"));

        assertThat(written.toPath()).isEqualTo(nested);
        assertThat(Files.isSymbolicLink(nested)).isTrue();
        assertThat(Files.exists(dest.resolve("link"), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    /** A symlink outside the root pointing into it must not let a link entry replace it. */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void linkEntryCannotReplaceOutsideSymlinkPointingInside() throws Exception {
        final Path root = Files.createTempDirectory("link-outside-symlink");
        final Path dest = Files.createDirectories(root.resolve("extract"));
        Files.createDirectory(dest.resolve("sub"));
        final Path current =
                Files.createSymbolicLink(root.resolve("current"), Paths.get("extract/sub"));

        final Throwable thrown =
                catchThrowable(() -> extractLink(Rar5RedirType.FILE_COPY, dest, "../current"));

        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        assertThat(Files.isSymbolicLink(current)).isTrue();
    }

    /** A name that collapses to the destination itself must not replace the destination. */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void linkEntryNamingTheDestinationRejected() throws Exception {
        final Path root = Files.createTempDirectory("link-names-root");
        final Path dest = Files.createDirectories(root.resolve("extract"));

        final Throwable thrown =
                catchThrowable(
                        () ->
                                new LocalFolderExtractor(dest.toFile())
                                        .extract(
                                                mock(Archive.class),
                                                symlinkHeader("sub/..", "extract")));

        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        assertThat(Files.isDirectory(dest, LinkOption.NOFOLLOW_LINKS)).isTrue();
    }

    private static void assertInterposedDotDotStaysInside(final Rar5RedirType type)
            throws Exception {
        final Path root = Files.createTempDirectory("link-mkdir-escape");
        final Path dest = Files.createDirectories(root.resolve("extract"));

        extractLink(type, dest, "../extract_evil/../extract/sub/link");

        assertThat(root.resolve("extract_evil")).doesNotExist();
        assertThat(Files.exists(dest.resolve("sub/link"), LinkOption.NOFOLLOW_LINKS)).isTrue();
    }

    private static void extractLink(final Rar5RedirType type, final Path dest, final String name)
            throws Exception {
        Files.write(dest.resolve("a.txt"), new byte[] {1});
        final FileHeader fh = mock(FileHeader.class);
        when(fh.getFileName()).thenReturn(name);
        when(fh.getRedirection()).thenReturn(new Rar5Redirection(type, false, "a.txt"));
        new LocalFolderExtractor(dest.toFile()).extract(mock(Archive.class), fh);
    }

    // ---- S5/S6/S7 RAR5 twins: the same guards, re-applied to the new symlink-target path -------
    // (M3.10 issue #31; the no-go rows require each guard to survive on the RAR5 link path.)

    @Test
    public void s7_upLevelSymlinkTargetRejected() throws Exception {
        final Throwable thrown = catchThrowable(() -> extractSymlink("link", "../../evil"));
        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
    }

    @Test
    public void s5_backslashSymlinkTargetRejected() throws Exception {
        // '\'->'/' normalization (S5) before the containment check catches the traversal.
        final Throwable thrown = catchThrowable(() -> extractSymlink("link", "..\\..\\evil"));
        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
    }

    @Test
    public void s6_siblingPrefixSymlinkTargetRejected() throws Exception {
        // Destination "extract"; target resolves to the sibling "extract_evil", which shares a
        // bare prefix. Containment compares against destCanonical + File.separator, so the
        // sibling is rejected (a naive startsWith would let it through).
        final File parent = TestCommons.createTempDir();
        final File extract = new File(parent, "extract");
        extract.mkdir();
        final LocalFolderExtractor lfe = new LocalFolderExtractor(extract);
        final Throwable thrown =
                catchThrowable(
                        () ->
                                lfe.extract(
                                        mock(Archive.class),
                                        symlinkHeader("link", "../extract_evil/pwned")));
        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
    }

    /**
     * A '..' after a name in a symlink target resolves through whatever that name is when the link
     * is followed, not when it is checked. {@code L -> d/../x} is inside while {@code d} is absent;
     * a later {@code d -> .} turns it into {@code <dest>/../x}. Such targets are refused.
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void symlinkChainCannotEscapeThroughLaterLink() throws Exception {
        final Path root = Files.createTempDirectory("symlink-chain");
        final Path dest = Files.createDirectories(root.resolve("extract"));
        final LocalFolderExtractor lfe = new LocalFolderExtractor(dest.toFile());

        final Throwable thrown =
                catchThrowable(
                        () -> lfe.extract(mock(Archive.class), symlinkHeader("L", "d/../x")));
        lfe.extract(mock(Archive.class), symlinkHeader("d", "."));

        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        assertThat(Files.exists(dest.resolve("L"), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void layer3_writeThroughDirSymlinkViaDotDotRejected() throws Exception {
        // Layer 6.2.3 must model the lexical write path: 'x/../dlnk/evil.txt' actually creates
        // through dest/dlnk (makeFile mkdir's each component, including '..'), so the check must
        // pop on '..' -- otherwise the interposed '..' hides the dir-symlink component.
        final File out = TestCommons.createTempDir();
        new File(out, "x").mkdir();
        Files.createSymbolicLink(new File(out, "dlnk").toPath(), Paths.get("."));
        final LocalFolderExtractor lfe = new LocalFolderExtractor(out);
        final FileHeader fh = mock(FileHeader.class);
        when(fh.getFileName()).thenReturn("x/../dlnk/evil.txt");

        final Throwable thrown = catchThrowable(() -> lfe.extract(mock(Archive.class), fh));
        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        assertThat(new File(out, "evil.txt")).doesNotExist();
    }

    private void extractSymlink(final String name, final String target) throws Exception {
        final File out = TestCommons.createTempDir();
        new LocalFolderExtractor(out).extract(mock(Archive.class), symlinkHeader(name, target));
    }

    private static FileHeader symlinkHeader(final String name, final String target) {
        final FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.getFileName()).thenReturn(name);
        when(fileHeader.getRedirection())
                .thenReturn(new Rar5Redirection(Rar5RedirType.UNIX_SYMLINK, false, target));
        return fileHeader;
    }
}
