package com.github.junrar;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.UnsafeLinkException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import com.github.junrar.rarfile.rar5.Rar5Redirection;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

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
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

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
     * verify RAR3 header CRCs. This branch does (P0.7 / issue #12, {@code abfe34f2}), so the entry
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
     * escaping name driven through a well-formed header, so nothing but {@code makeFile} can be
     * what stops it. Reverting {@code makeFile} to its pre-fix per-component {@code mkdir} loop
     * makes this fail (executed negative control), so the guard is load-bearing, not decorative.
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
            .as("no directory is created outside the destination").doesNotExist();
        assertThat(new File(dest.toFile(), "payload.txt"))
            .as("the entry itself still lands, normalized, inside the destination").exists();
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
        final Throwable thrown = catchThrowable(
            () -> lfe.extract(mock(Archive.class), symlinkHeader("link", "../extract_evil/pwned")));
        assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
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
