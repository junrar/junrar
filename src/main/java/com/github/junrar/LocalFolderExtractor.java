package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LocalFolderExtractor {

    private final File folderDestination;
    private static final Logger logger = LoggerFactory.getLogger(LocalFolderExtractor.class);

    LocalFolderExtractor(final File destination) {
        this.folderDestination = destination;
    }

    File createDirectory(final FileHeader fh) {
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
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return f;
    }

    File extract(final Archive arch, final FileHeader fileHeader) throws RarException, IOException {
        final File f = createFile(fileHeader, folderDestination);
        try (OutputStream stream = new FileOutputStream(f)) {
            arch.extractFile(fileHeader, stream);
        }
        return f;
    }

    private File createFile(final FileHeader fh, final File destination) throws IOException {
        String name = invariantSeparatorsPathString(fh.getFileName());
        File f = new File(destination, name);
        String dirCanonPath = f.getCanonicalPath();
        if (!dirCanonPath.startsWith(destination.getCanonicalPath() + File.separator)) {
            String errorMessage = "Rar contains file with invalid path: '" + dirCanonPath + "'";
            throw new IllegalStateException(errorMessage);
        }
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

    static String invariantSeparatorsPathString(String path) {
        return path.replace("\\", "/");
    }
}
