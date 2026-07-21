package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsafeLinkException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import com.github.junrar.rarfile.rar5.Rar5Redirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class LocalFolderExtractor {

    private final File folderDestination;
    private static final Logger logger = LoggerFactory.getLogger(LocalFolderExtractor.class);

    LocalFolderExtractor(final File destination) {
        this.folderDestination = destination;
    }

    File createDirectory(final FileHeader fh) throws RarException {
        String fileName = null;
        if (fh.isDirectory()) {
            fileName = fh.getFileName();
        }

        if (fileName == null) {
            return null;
        }

        File f = new File(folderDestination, fileName);
        try {
            String fileCanonPath = f.getCanonicalPath();
            if (!fileCanonPath.startsWith(folderDestination.getCanonicalPath() + File.separator)) {
                String errorMessage = "Rar contains invalid path: '" + fileCanonPath + "'";
                throw new IllegalStateException(errorMessage);
            }
            refuseWriteThroughSymlink(fileName);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return f;
    }

    File extract(
        final Archive arch,
        final FileHeader fileHeader
    ) throws RarException, IOException {
        final Rar5Redirection redir = fileHeader.getRedirection();
        if (redir != null && redir.getType() != null && redir.getType() != Rar5RedirType.NONE) {
            return extractRedirection(fileHeader, redir);
        }
        final File f = createFile(fileHeader, folderDestination);
        try (OutputStream stream = new FileOutputStream(f)) {
            arch.extractFile(fileHeader, stream);
        }
        return f;
    }

    private File createFile(final FileHeader fh, final File destination) throws IOException, RarException {
        String name = invariantSeparatorsPathString(fh.getFileName());
        File f = new File(destination, name);
        String dirCanonPath = f.getCanonicalPath();
        if (!dirCanonPath.startsWith(destination.getCanonicalPath() + File.separator)) {
            String errorMessage = "Rar contains file with invalid path: '" + dirCanonPath + "'";
            throw new IllegalStateException(errorMessage);
        }
        refuseWriteThroughSymlink(fh.getFileName());
        if (!f.exists()) {
            try {
                f = makeFile(f.toPath().normalize());
            } catch (final IOException e) {
                logger.error("error creating the new file: {}", f.getName(), e);
            }
        }
        return f;
    }

    private File makeFile(final Path file) throws IOException {
        if (file.getParent() == null) return null;
        Files.createDirectories(file.getParent());
        return file.toFile();
    }

    // ---- RAR5 FHEXTRA_REDIR link extraction (M3.10, issue #31) ---------------------------------

    /**
     * Extract an FHEXTRA_REDIR entry. Unix symlinks are created on POSIX hosts; hardlinks and
     * file copies resolve another archive member; Windows symlinks/junctions are a recorded
     * creation non-goal on the JVM and surface as metadata only ({@link FileHeader#getRedirection()}).
     * Every target passes the three safety layers before anything touches the filesystem.
     */
    private File extractRedirection(final FileHeader fh, final Rar5Redirection redir)
        throws RarException, IOException {
        final String srcName = fh.getFileName();
        final File linkFile = resolveLinkDestination(srcName);
        refuseWriteThroughSymlink(srcName); // layer 6.2.3 LinksToDirs
        switch (redir.getType()) {
            case UNIX_SYMLINK:
            case WIN_SYMLINK:
            case JUNCTION:
                return createSymlink(linkFile, srcName, redir);
            case HARDLINK:
                return createLink(linkFile, redir, false);
            case FILE_COPY:
                return createLink(linkFile, redir, true);
            default:
                return null;
        }
    }

    private File createSymlink(final File linkFile, final String srcName, final Rar5Redirection redir)
        throws RarException, IOException {
        final String rawTarget = redir.getTarget();
        validateSymlinkTarget(srcName, rawTarget); // layers 5.2.5 depth + 6.1.7 target validation
        if (!isPosix()) {
            // Windows symlink/junction creation is a recorded non-goal on the JVM; the redirection
            // fact still surfaces through FileHeader.getRedirection(). unrar creates reparse points
            // here (CreateReparsePoint); junrar deliberately does not (cross-platform port).
            logger.info("skipping symlink creation on non-POSIX host: {} -> {}", srcName, rawTarget);
            return null;
        }
        final Path linkPath = linkFile.toPath();
        Files.createDirectories(linkPath.getParent());
        Files.deleteIfExists(linkPath);
        // Preserve the exact target bytes from the header so readlink matches the unrar oracle.
        Files.createSymbolicLink(linkPath, linkPath.getFileSystem().getPath(rawTarget));
        return linkFile;
    }

    private File createLink(final File linkFile, final Rar5Redirection redir, final boolean copy)
        throws RarException, IOException {
        final File target = resolveTargetWithinDestination(redir.getTarget());
        final Path linkPath = linkFile.toPath();
        Files.createDirectories(linkPath.getParent());
        Files.deleteIfExists(linkPath);
        if (copy) {
            Files.copy(target.toPath(), linkPath, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            // Files.createLink throws NoSuchFileException if the target member was not extracted
            // first -- unrar's "unpack the link target first" error (ExtractHardlink).
            Files.createLink(linkPath, target.toPath());
        }
        return linkFile;
    }

    // ---- the three safety layers --------------------------------------------------------------

    /**
     * Layers 5.2.5 ({@code IsRelativeSymlinkSafe} up-level depth) and 6.1.7 (absolute-target
     * validation). Rejects a symlink whose target is absolute/drive-qualified/UNC, or whose
     * target -- resolved against the link's own directory and canonicalized -- escapes the
     * destination folder. The canonical-containment check subsumes the up-level ".." depth
     * count and re-applies S6/S7 to the new path; separator-normalization first re-applies S5,
     * so a {@code ..\..\x} backslash target is caught cross-platform (unrar keeps it literal on
     * Unix -- a deliberate divergence, see fixtures README).
     */
    private void validateSymlinkTarget(final String srcName, final String rawTarget)
        throws UnsafeLinkException, IOException {
        final String target = invariantSeparatorsPathString(rawTarget); // S5
        if (isAbsolute(target, rawTarget)) {
            throw new UnsafeLinkException("Rar contains a symlink with an absolute target: '"
                + rawTarget + "' for '" + srcName + "'");
        }
        final File linkParent =
            new File(folderDestination, invariantSeparatorsPathString(srcName)).getParentFile();
        final String canon = new File(linkParent, target).getCanonicalPath();
        final String destCanon = folderDestination.getCanonicalPath();
        if (!canon.equals(destCanon) && !canon.startsWith(destCanon + File.separator)) {
            throw new UnsafeLinkException("Rar contains a symlink escaping the destination: '"
                + srcName + "' -> '" + rawTarget + "' resolves to '" + canon + "'");
        }
    }

    /**
     * Resolve a hardlink / file-copy target (an archive-member path relative to the destination
     * root) and enforce the same absolute-target and containment guards. unrar prepares the name
     * against {@code ExtrPath} before {@code ExtractHardlink}; junrar resolves against the
     * destination root and refuses any target that escapes it.
     */
    private File resolveTargetWithinDestination(final String rawTarget)
        throws UnsafeLinkException, IOException {
        final String target = invariantSeparatorsPathString(rawTarget);
        if (isAbsolute(target, rawTarget)) {
            throw new UnsafeLinkException("Rar contains a link with an absolute target: '" + rawTarget + "'");
        }
        final File resolved = new File(folderDestination, target);
        final String canon = resolved.getCanonicalPath();
        if (!canon.startsWith(folderDestination.getCanonicalPath() + File.separator)) {
            throw new UnsafeLinkException("Rar contains a link escaping the destination: '"
                + rawTarget + "' resolves to '" + canon + "'");
        }
        return resolved;
    }

    /**
     * Layer 6.2.3 ({@code LinksToDirs}): refuse to write an entry whose path passes through a
     * previously-extracted directory symlink. unrar deletes the symlink and re-creates a real
     * directory; junrar rejects with a typed exception, which is stricter and matches the M3.10
     * acceptance rows. A legitimate archive never writes a member through its own symlink, so
     * benign extraction is unaffected; RAR3/RAR4 never create symlinks, so this is a no-op there.
     */
    private void refuseWriteThroughSymlink(final String rawName) throws UnsafeLinkException {
        final String[] parts = invariantSeparatorsPathString(rawName).split("/");
        // ponytail: no LastCheckedSymlink cache -- we re-check every component each entry (unrar
        // caches for perf and resets it after each link). Correctness needs no cache; add one
        // only if a deep-tree extraction ever shows up as a hotspot.
        Path p = folderDestination.toPath();
        for (int i = 0; i < parts.length - 1; i++) {
            final String part = parts[i];
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                // Track the same lexical path makeFile builds: a '..' pops a component, so a
                // symlink hidden behind an interposed '..' (x/../dlnk/...) is still detected.
                final Path parent = p.getParent();
                if (parent != null) {
                    p = parent;
                }
                continue;
            }
            p = p.resolve(part);
            if (Files.isSymbolicLink(p)) {
                throw new UnsafeLinkException("Rar refuses to extract '" + rawName
                    + "' through the previously-extracted directory symlink: '" + p + "'");
            }
        }
    }

    private File resolveLinkDestination(final String rawName) throws UnsafeLinkException, IOException {
        final File f = new File(folderDestination, invariantSeparatorsPathString(rawName));
        final String canon = f.getCanonicalPath();
        if (!canon.startsWith(folderDestination.getCanonicalPath() + File.separator)) {
            throw new UnsafeLinkException("Rar contains a link with invalid path: '" + canon + "'");
        }
        return f;
    }

    private static boolean isAbsolute(final String normalized, final String rawTarget) {
        return normalized.startsWith("/") // unix absolute, and normalized UNC / \\?\ prefixes
            || rawTarget.startsWith("\\") // raw UNC or root-based backslash
            || (rawTarget.length() >= 2
                && Character.isLetter(rawTarget.charAt(0))
                && rawTarget.charAt(1) == ':'); // Windows drive letter
    }

    private boolean isPosix() {
        return folderDestination.toPath().getFileSystem()
            .supportedFileAttributeViews().contains("posix");
    }

    static String invariantSeparatorsPathString(String path) {
        return path.replace("\\", "/");
    }
}
