package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.volume.VolumeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Junrar {

    private static final Logger logger = LoggerFactory.getLogger(Junrar.class);

    public static List<File> extract(final String rarPath, final String destinationPath) throws IOException, RarException {
        // Explicit cast disambiguates from the new (String, String, ArchiveOptions) overload.
        return extract(rarPath, destinationPath, (String) null);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #extract(String, String, ArchiveOptions)} with a {@code char[]} password. Also,
     * since that overload's introduction, a bare {@code null} literal is ambiguous here and no
     * longer compiles — use the two-argument overload, cast to {@code (String) null}, or an
     * {@code ArchiveOptions} without a password.
     */
    public static List<File> extract(final String rarPath, final String destinationPath, final String password) throws IOException, RarException {
        if (rarPath == null || destinationPath == null) {
            throw new IllegalArgumentException("archive and destination must be set");
        }
        return extract(new File(rarPath), new File(destinationPath), password);
    }

    /**
     * Since this overload's introduction, a bare {@code null} password literal is ambiguous
     * against {@link #extract(String, String, String)} and no longer compiles. Use the
     * two-argument overload, cast to {@code (String) null}, or pass an {@code ArchiveOptions}
     * without a password.
     */
    public static List<File> extract(final String rarPath, final String destinationPath, final ArchiveOptions options) throws IOException, RarException {
        if (rarPath == null || destinationPath == null) {
            throw new IllegalArgumentException("archive and destination must be set");
        }
        return extract(new File(rarPath), new File(destinationPath), options);
    }

    public static List<File> extract(final File rar, final File destinationFolder) throws RarException, IOException {
        // Explicit cast disambiguates from the new (File, File, ArchiveOptions) overload.
        return extract(rar, destinationFolder, (String) null);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #extract(File, File, ArchiveOptions)} with a {@code char[]} password. Also, since
     * that overload's introduction, a bare {@code null} literal is ambiguous here and no
     * longer compiles — use the two-argument overload, cast to {@code (String) null}, or an
     * {@code ArchiveOptions} without a password.
     */
    public static List<File> extract(final File rar, final File destinationFolder, final String password)
            throws RarException, IOException {
        validateRarPath(rar);
        validateDestinationPath(destinationFolder);

        final Archive archive = createArchiveOrThrowException(rar, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(archive, lfe);
    }

    /**
     * Since this overload's introduction, a bare {@code null} password literal is ambiguous
     * against {@link #extract(File, File, String)} and no longer compiles. Use the
     * two-argument overload, cast to {@code (String) null}, or pass an {@code ArchiveOptions}
     * without a password.
     *
     * @see ArchiveOptions
     */
    public static List<File> extract(final File rar, final File destinationFolder, final ArchiveOptions options)
            throws RarException, IOException {
        validateRarPath(rar);
        validateDestinationPath(destinationFolder);

        final Archive archive = createArchiveOrThrowException(rar, options);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(archive, lfe);
    }

    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder) throws RarException, IOException {
        // Explicit cast disambiguates from the new (InputStream, File, ArchiveOptions) overload.
        return extract(resourceAsStream, destinationFolder, (String) null);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #extract(InputStream, File, ArchiveOptions)} with a {@code char[]} password. Also,
     * since that overload's introduction, a bare {@code null} literal is ambiguous here and no
     * longer compiles — use the two-argument overload, cast to {@code (String) null}, or an
     * {@code ArchiveOptions} without a password.
     */
    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder, final String password) throws RarException, IOException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(resourceAsStream, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    /**
     * Since this overload's introduction, a bare {@code null} password literal is ambiguous
     * against {@link #extract(InputStream, File, String)} and no longer compiles. Use the
     * two-argument overload, cast to {@code (String) null}, or pass an {@code ArchiveOptions}
     * without a password.
     */
    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder, final ArchiveOptions options) throws RarException, IOException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(resourceAsStream, options);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    public static List<File> extract(final VolumeManager volumeManager, final File destinationFolder) throws IOException, RarException {
        validateDestinationPath(destinationFolder);

        // Explicit cast disambiguates from the new (VolumeManager, ArchiveOptions) overload.
        final Archive arch = createArchiveOrThrowException(volumeManager, (String) null);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #extract(VolumeManager, File, ArchiveOptions)} with a {@code char[]} password.
     * Also, since that overload's introduction, a bare {@code null} literal is ambiguous here
     * and no longer compiles — use the two-argument overload, cast to {@code (String) null},
     * or an {@code ArchiveOptions} without a password.
     */
    public static List<File> extract(final VolumeManager volumeManager, final File destinationFolder, final String password) throws IOException, RarException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(volumeManager, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    /**
     * Since this overload's introduction, a bare {@code null} password literal is ambiguous
     * against {@link #extract(VolumeManager, File, String)} and no longer compiles. Use the
     * two-argument overload, cast to {@code (String) null}, or pass an {@code ArchiveOptions}
     * without a password.
     */
    public static List<File> extract(final VolumeManager volumeManager, final File destinationFolder, final ArchiveOptions options) throws IOException, RarException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(volumeManager, options);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }


    public static List<ContentDescription> getContentsDescription(final File rar) throws RarException, IOException {
        validateRarPath(rar);
        // Explicit cast disambiguates from the new createArchiveOrThrowException(File, ArchiveOptions) overload.
        final Archive arch = createArchiveOrThrowException(rar, (String) null);
        return getContentsDescriptionFromArchive(arch);
    }

    public static List<ContentDescription> getContentsDescription(final InputStream resourceAsStream) throws RarException, IOException {
        // Explicit cast disambiguates from the new createArchiveOrThrowException(InputStream, ArchiveOptions) overload.
        final Archive arch = createArchiveOrThrowException(resourceAsStream, (String) null);
        return getContentsDescriptionFromArchive(arch);
    }

    private static List<ContentDescription> getContentsDescriptionFromArchive(final Archive arch) throws RarException, IOException {
        final List<ContentDescription> contents = new ArrayList<>();
        try {
            if (arch.isEncrypted()) {
                logger.warn("archive is encrypted cannot extract");
                return new ArrayList<>();
            }
            for (final FileHeader fileHeader : arch) {
                contents.add(new ContentDescription(fileHeader.getFileName(), fileHeader.getUnpSize()));
            }
        } finally {
            arch.close();
        }
        return contents;
    }

    private static Archive createArchiveOrThrowException(final VolumeManager volumeManager, final String password) throws RarException, IOException {
        try {
            return new Archive(volumeManager, null, password);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final VolumeManager volumeManager, final ArchiveOptions options) throws RarException, IOException {
        try {
            return new Archive(volumeManager, options);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final InputStream rarAsStream, final String password) throws RarException, IOException {
        try {
            return new Archive(rarAsStream, password);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final InputStream rarAsStream, final ArchiveOptions options) throws RarException, IOException {
        try {
            return new Archive(rarAsStream, options);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final File file, final String password) throws RarException, IOException {
        try {
            return new Archive(file, password);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final File file, final ArchiveOptions options) throws RarException, IOException {
        try {
            return new Archive(file, options);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static void validateDestinationPath(final File destinationFolder) {
        if (destinationFolder == null) {
            throw new IllegalArgumentException("archive and destination must me set");
        }
        if (!destinationFolder.exists() || !destinationFolder.isDirectory()) {
            throw new IllegalArgumentException("the destination must exist and point to a directory: " + destinationFolder);
        }
    }

    private static void validateRarPath(final File rar) {
        if (rar == null) {
            throw new IllegalArgumentException("archive and destination must me set");
        }
        if (!rar.exists()) {
            throw new IllegalArgumentException("the archive does not exit: " + rar);
        }
        if (!rar.isFile()) {
            throw new IllegalArgumentException("First argument should be a file but was " + rar.getAbsolutePath());
        }
    }

    private static List<File> extractArchiveTo(final Archive arch, final LocalFolderExtractor destination) throws IOException, RarException {
        final List<File> extractedFiles = new ArrayList<>();
        try {
            for (final FileHeader fh : arch) {
                try {
                    final File file = tryToExtract(destination, arch, fh);
                    if (file != null) {
                        extractedFiles.add(file);
                    }
                } catch (final IOException | RarException e) {
                    logger.error("error extracting the file", e);
                    throw e;
                }
            }
        } finally {
            arch.close();
        }
        return extractedFiles;
    }

    private static File tryToExtract(
            final LocalFolderExtractor destination,
            final Archive arch,
            final FileHeader fileHeader
    ) throws IOException, RarException {
        final String fileNameString = fileHeader.getFileName();

        Junrar.logger.info("extracting: {}", fileNameString);
        if (fileHeader.isDirectory()) {
            return destination.createDirectory(fileHeader);
        } else {
            return destination.extract(arch, fileHeader);
        }
    }

}
