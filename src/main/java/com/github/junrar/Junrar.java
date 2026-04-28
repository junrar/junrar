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
        return extract(rarPath, destinationPath, null);
    }

    public static List<File> extract(final String rarPath, final String destinationPath, final String password) throws IOException, RarException {
        if (rarPath == null || destinationPath == null) {
            throw new IllegalArgumentException("archive and destination must be set");
        }
        return extract(new File(rarPath), new File(destinationPath), password);
    }

    public static List<File> extract(final File rar, final File destinationFolder) throws RarException, IOException {
        return extract(rar, destinationFolder, null);
    }

    public static List<File> extract(final File rar, final File destinationFolder, final String password)
            throws RarException, IOException {
        validateRarPath(rar);
        validateDestinationPath(destinationFolder);

        final Archive archive = createArchiveOrThrowException(rar, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(archive, lfe);
    }

    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder) throws RarException, IOException {
        return extract(resourceAsStream, destinationFolder, null);
    }

    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder, final String password) throws RarException, IOException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(resourceAsStream, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    public static List<File> extract(final VolumeManager volumeManager, final File destinationFolder) throws IOException, RarException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(volumeManager, null);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    public static List<File> extract(final VolumeManager volumeManager, final File destinationFolder, final String password) throws IOException, RarException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(volumeManager, password);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }


    public static List<ContentDescription> getContentsDescription(final File rar) throws RarException, IOException {
        validateRarPath(rar);
        final Archive arch = createArchiveOrThrowException(rar, null);
        return getContentsDescriptionFromArchive(arch);
    }

    public static List<ContentDescription> getContentsDescription(final InputStream resourceAsStream) throws RarException, IOException {
        final Archive arch = createArchiveOrThrowException(resourceAsStream, null);
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

    private static Archive createArchiveOrThrowException(final InputStream rarAsStream, final String password) throws RarException, IOException {
        try {
            return new Archive(rarAsStream, password);
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
            for (final com.github.junrar.rarfile.FileHeaderEntry entry : getHeaderEntries(arch)) {
                try {
                    final File file = tryToExtract(destination, arch, entry);
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

    /**
     * Returns all file header entries from the archive (RAR4 and RAR5).
     */
    private static java.util.List<com.github.junrar.rarfile.FileHeaderEntry> getHeaderEntries(final Archive arch) {
        final java.util.List<com.github.junrar.rarfile.FileHeaderEntry> entries = new java.util.ArrayList<>();
        // Reset to beginning
        arch.reset();
        com.github.junrar.rarfile.FileHeaderEntry entry;
        while ((entry = arch.nextFileHeaderEntry()) != null) {
            entries.add(entry);
        }
        // Reset again for extraction
        arch.reset();
        return entries;
    }

    private static File tryToExtract(
            final LocalFolderExtractor destination,
            final Archive arch,
            final com.github.junrar.rarfile.FileHeaderEntry entry
    ) throws IOException, RarException {
        final String fileNameString = entry.getFileName();

        Junrar.logger.info("extracting: {}", fileNameString);
        if (entry.isDirectory()) {
            return destination.createDirectory(entry);
        } else {
            return destination.extract(arch, entry);
        }
    }

}
