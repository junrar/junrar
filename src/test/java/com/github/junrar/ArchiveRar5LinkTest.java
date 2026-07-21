package com.github.junrar;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.UnsafeLinkException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import com.github.junrar.rarfile.rar5.Rar5Redirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.10 (issue #31): RAR5 FHEXTRA_REDIR extraction and the three symlink-safety layers.
 *
 * <p>Benign fixture {@code rar5-links.rar} is made with {@code rar 7.23 -ma5 -ol}; the golden
 * symlink targets / hardlink identity / file-copy independence are the {@code unrar 7.23}
 * oracle. Hostile targets are byte-patched at test runtime (rar refuses to <em>create</em>
 * them): the field is overwritten with an equal-length hostile string and the enclosing RAR5
 * block header CRC32 is recomputed (M3.7/M3.9 patch recipe). See the fixtures README.
 */
@Timeout(60)
class ArchiveRar5LinkTest {

    @TempDir
    Path tempDir;

    private File fixture(final String name) throws Exception {
        final byte[] bytes = Files.readAllBytes(
            Paths.get(getClass().getResource("links/" + name).toURI()));
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private File dest() throws Exception {
        return Files.createDirectory(tempDir.resolve("out-" + System.nanoTime())).toFile();
    }

    /** Mirrors Junrar.tryToExtract routing at the Archive level (the four-surface public-path
     * matrix lives in ArchiveRar5PublicSurfaceTest). */
    private static List<File> extractInto(final Archive a, final File destination) throws Exception {
        final LocalFolderExtractor lfe = new LocalFolderExtractor(destination);
        final List<File> out = new ArrayList<>();
        for (final FileHeader fh : a.getFileHeaders()) {
            final Rar5Redirection r = fh.getRedirection();
            final File f;
            if (r != null && r.getType() != null && r.getType() != Rar5RedirType.NONE) {
                f = lfe.extract(a, fh);
            } else if (fh.isDirectory()) {
                f = lfe.createDirectory(fh);
            } else {
                f = lfe.extract(a, fh);
            }
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    // ---- benign extraction (Unix creates; all OS see metadata) --------------------------------

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void benignLinksExtractWithOracleSemantics() throws Exception {
        final File out = dest();
        try (Archive a = new Archive(fixture("rar5-links.rar"))) {
            extractInto(a, out);
        }
        final Path root = out.toPath();
        final Path file = root.resolve("file.txt");
        assertThat(file).exists();

        // Unix symlink: exact target bytes preserved (unrar oracle).
        assertThat(Files.isSymbolicLink(root.resolve("link-flat"))).isTrue();
        assertThat(Files.readSymbolicLink(root.resolve("link-flat")).toString()).isEqualTo("file.txt");
        assertThat(Files.isSymbolicLink(root.resolve("sub/link-up"))).isTrue();
        assertThat(Files.readSymbolicLink(root.resolve("sub/link-up")).toString()).isEqualTo("../file.txt");

        // Hardlink: same underlying file as its target (shared inode).
        final Path hard = root.resolve("hard.txt");
        assertThat(Files.isSymbolicLink(hard)).isFalse();
        assertThat(Files.isSameFile(hard, file)).isTrue();
        assertThat(Files.readAllBytes(hard)).isEqualTo(Files.readAllBytes(file));

        // File copy: independent file, identical bytes, NOT the same inode.
        final Path copyA = root.resolve("copy-a.bin");
        final Path copyB = root.resolve("copy-b.bin");
        assertThat(Files.isSymbolicLink(copyB)).isFalse();
        assertThat(Files.isSameFile(copyB, copyA)).isFalse();
        assertThat(Files.readAllBytes(copyB)).isEqualTo(Files.readAllBytes(copyA));
        assertThat(copyB.toFile().length()).isEqualTo(4864L);
    }

    /** Metadata surfaces on every OS (Windows CI asserts this instead of creation). */
    @Test
    void redirectionMetadataSurfacesOnAllPlatforms() throws Exception {
        try (Archive a = new Archive(fixture("rar5-links.rar"))) {
            final Rar5Redirection flat = redirectionOf(a, "link-flat");
            assertThat(flat.getType()).isEqualTo(Rar5RedirType.UNIX_SYMLINK);
            assertThat(flat.getTarget()).isEqualTo("file.txt");

            final Rar5Redirection hard = redirectionOf(a, "hard.txt");
            assertThat(hard.getType()).isEqualTo(Rar5RedirType.HARDLINK);
            assertThat(hard.getTarget()).isEqualTo("file.txt");

            final Rar5Redirection copy = redirectionOf(a, "copy-b.bin");
            assertThat(copy.getType()).isEqualTo(Rar5RedirType.FILE_COPY);
            assertThat(copy.getTarget()).isEqualTo("copy-a.bin");
        }
    }

    // ---- hostile rows: all rejected with UnsafeLinkException -----------------------------------
    // Layers 1 & 2 are pure path logic and reject on every OS (validation precedes any creation).

    @Test
    void upLevelSymlinkTargetRejected() throws Exception {
        assertRejected("h-up", "aaaaaaaaa", "../../etc");
    }

    @Test
    void absoluteSymlinkTargetRejected() throws Exception {
        assertRejected("h-abs", "bbbbbbbbb", "/etc/shd0");
    }

    @Test
    void backslashSymlinkTargetRejected() throws Exception {
        // unrar keeps this literal on Unix (backslash is not a path separator there); junrar
        // normalizes '\'->'/' first (S5) and rejects the resulting traversal cross-platform.
        assertRejected("h-bsl", "ccccccccc", "..\\zz\\bad");
    }

    @Test
    void hardlinkTargetOutsideRejected() throws Exception {
        assertRejected("h-hard.txt", "h-file.txt", "../../pt.x");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void fileThroughDirSymlinkRejected() throws Exception {
        // dlnk -> '.' is a benign dir symlink; writing 'dlnk/evil.txt' through it must be
        // refused by layer 6.2.3 (LinksToDirs), needs the symlink to exist on disk (POSIX only).
        final File archive = fixture("rar5-links-hostile.rar");
        patchTarget(archive, "dqqq/evil.txt", "dqqq", "dlnk");
        final File out = dest();
        try (Archive a = new Archive(archive)) {
            final Throwable thrown = catchThrowable(() -> extractInto(a, out));
            assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
            assertThat(thrown.getMessage()).contains("dlnk");
        }
        // The evil payload must never have been written through the symlink.
        assertThat(out.toPath().resolve("dlnk").resolve("evil.txt")).doesNotExist();
    }

    private void assertRejected(final String entry, final String from, final String to) throws Exception {
        final File archive = fixture("rar5-links-hostile.rar");
        patchTarget(archive, entry, from, to);
        final File out = dest();
        try (Archive a = new Archive(archive)) {
            final Throwable thrown = catchThrowable(() -> extractInto(a, out));
            assertThat(thrown).isExactlyInstanceOf(UnsafeLinkException.class);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static Rar5Redirection redirectionOf(final Archive a, final String name) {
        for (final FileHeader fh : a.getFileHeaders()) {
            if (fh.getFileName().equals(name)) {
                return fh.getRedirection();
            }
        }
        throw new AssertionError("no entry " + name);
    }

    /**
     * Overwrite an equal-length ASCII field inside one entry's RAR5 block header and recompute
     * the block header CRC32 (M3.7 recipe). Scoped to that entry's header via
     * {@link FileHeader#getPositionInFile()} so a shared string (e.g. a hardlink target equal to
     * another entry's name) is patched in the right block.
     */
    private void patchTarget(final File archive, final String entry, final String from, final String to)
        throws Exception {
        if (from.length() != to.length()) {
            throw new IllegalArgumentException("patch must be length-preserving");
        }
        long position = -1;
        try (Archive a = new Archive(archive)) {
            for (final FileHeader fh : a.getFileHeaders()) {
                if (fh.getFileName().equals(entry)) {
                    position = fh.getPositionInFile();
                    break;
                }
            }
        }
        assertThat(position).as("entry %s not found", entry).isGreaterThanOrEqualTo(0);

        final byte[] bytes = Files.readAllBytes(archive.toPath());
        final int headerLength = Rar5BaseBlock.checkHeaderSize(
            Arrays.copyOfRange(bytes, (int) position, (int) position + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, (int) position, (int) position + headerLength);
        final int idx = indexOf(header, from.getBytes(StandardCharsets.UTF_8));
        assertThat(idx).as("field '%s' in header of %s", from, entry).isGreaterThanOrEqualTo(0);
        final byte[] toBytes = to.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(toBytes, 0, header, idx, toBytes.length);
        Raw.writeIntLittleEndian(header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, (int) position, header.length);
        Files.write(archive.toPath(), bytes);
    }

    private static int indexOf(final byte[] haystack, final byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
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
