package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
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
        if (rarPath == null || destinationPath == null) {
            throw new RuntimeException("archive and destination must be set");
        }
        final File arch = new File(rarPath);
        final File dest = new File(destinationPath);
        return extract(arch, dest);
    }

    public static List<File> extract(final File rar, final File destinationFolder) throws RarException, IOException {
        validateRarPath(rar);
        validateDestinationPath(destinationFolder);

        final Archive archive = createArchiveOrThrowException(rar);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(archive, lfe);
    }

    public static List<File> extract(final InputStream resourceAsStream, final File destinationFolder) throws RarException, IOException {
        validateDestinationPath(destinationFolder);

        final Archive arch = createArchiveOrThrowException(resourceAsStream);
        LocalFolderExtractor lfe = new LocalFolderExtractor(destinationFolder);
        return extractArchiveTo(arch, lfe);
    }

    public static List<File> extract(
        final ExtractDestination destination,
        final VolumeManager volumeManager
    ) throws RarException, IOException {
        final Archive archive = new Archive(volumeManager);
        return extractArchiveTo(archive, destination);
    }

    public static List<ContentDescription> getContentsDescription(final File rar) throws RarException, IOException {
        validateRarPath(rar);

        final Archive arch = createArchiveOrThrowException(rar);

        final List<ContentDescription> contents = new ArrayList<ContentDescription>();
        try {
            if (arch.isEncrypted()) {
                logger.warn("archive is encrypted cannot extract");
                return new ArrayList<ContentDescription>();
            }
            for (final FileHeader fileHeader : arch) {
                contents.add(new ContentDescription(fileHeader.getFileNameString(), fileHeader.getUnpSize()));
            }
        } finally {
            arch.close();
        }
        return contents;
    }

    private static Archive createArchiveOrThrowException(final InputStream rarAsStream) throws RarException, IOException {
        try {
            return new Archive(rarAsStream);
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static Archive createArchiveOrThrowException(final File file) throws RarException, IOException {
        try {
            return new Archive(new FileVolumeManager(file));
        } catch (final RarException | IOException e) {
            Junrar.logger.error("Error while creating archive", e);
            throw e;
        }
    }

    private static void validateDestinationPath(final File destinationFolder) {
        if (destinationFolder == null) {
            throw new RuntimeException("archive and destination must me set");
        }
        if (!destinationFolder.exists() || !destinationFolder.isDirectory()) {
            throw new IllegalArgumentException("the destination must exist and point to a directory: " + destinationFolder);
        }
    }

    private static void validateRarPath(final File rar) {
        if (rar == null) {
            throw new RuntimeException("archive and destination must me set");
        }
        if (!rar.exists()) {
            throw new IllegalArgumentException("the archive does not exit: " + rar);
        }
        if (!rar.isFile()) {
            throw new IllegalArgumentException("First argument should be a file but was " + rar.getAbsolutePath());
        }
    }

    private static List<File> extractArchiveTo(final Archive arch, final ExtractDestination destination) throws IOException, RarException {
        if (arch.isEncrypted()) {
            logger.warn("archive is encrypted cannot extract");
            arch.close();
            return new ArrayList<File>();
        }

        final List<File> extractedFiles = new ArrayList<File>();
        try {
            for (final FileHeader fh : arch) {
                try {
                    final File file = tryToExtract(logger, destination, arch, fh);
                    if (file != null) {
                        extractedFiles.add(file);
                    }
                } catch (final IOException e) {
                    logger.error("error extracting the file", e);
                    throw e;
                } catch (final RarException e) {
                    logger.error("error extraction the file", e);
                    throw e;
                }
            }
        } finally {
            arch.close();
        }
        return extractedFiles;
    }

    private static File tryToExtract(
        final Logger logger,
        final ExtractDestination destination,
        final Archive arch,
        final FileHeader fileHeader
    ) throws IOException, RarException {
        final String fileNameString = fileHeader.getFileNameString();
        if (fileHeader.isEncrypted()) {
            logger.warn("file is encrypted cannot extract: " + fileNameString);
            return null;
        }
        logger.info("extracting: " + fileNameString);
        if (fileHeader.isDirectory()) {
            return destination.createDirectory(fileHeader);
        } else {
            return destination.extract(arch, fileHeader);
        }
    }

}
