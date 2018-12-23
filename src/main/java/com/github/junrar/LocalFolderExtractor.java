package com.github.junrar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public class LocalFolderExtractor implements ExtractDestination {

    private File folderDestination;
    private static final Log logger = LogFactory.getLog(LocalFolderExtractor.class);

    public LocalFolderExtractor(final File destination) {
        this.folderDestination = destination;
    }

    @Override
    public File createDirectory(final FileHeader fh) {
        File f = null;
        if (fh.isDirectory() && fh.isUnicode()) {
            f = new File(folderDestination, fh.getFileNameW());
            if (!f.exists()) {
                makeDirectory(folderDestination, fh.getFileNameW());
            }
        } else if (fh.isDirectory() && !fh.isUnicode()) {
            f = new File(folderDestination, fh.getFileNameString());
            if (!f.exists()) {
                makeDirectory(folderDestination, fh.getFileNameString());
            }
        }
        return f;
    }

    @Override
    public File extract(
        final Archive arch,
        final FileHeader fileHeader
    ) throws FileNotFoundException, RarException, IOException {
        final File f = createFile(fileHeader, folderDestination);
        final OutputStream stream = new FileOutputStream(f);
        arch.extractFile(fileHeader, stream);
        stream.close();
        return f;
    }

    private void makeDirectory(final File destination, final String fileName) {
        final String[] dirs = fileName.split("\\\\");
        if (dirs == null) {
            return;
        }
        String path = "";
        for (final String dir : dirs) {
            path = path + File.separator + dir;
            new File(destination, path).mkdir();
        }

    }

    private File createFile(final FileHeader fh, final File destination) {
        File f = null;
        String name = null;
        if (fh.isFileHeader() && fh.isUnicode()) {
            name = fh.getFileNameW();
        } else {
            name = fh.getFileNameString();
        }
        f = new File(destination, name);
        if (!f.exists()) {
            try {
                f = makeFile(destination, name);
            } catch (final IOException e) {
                logger.error("error creating the new file: " + f.getName(), e);
            }
        }
        return f;
    }

    private File makeFile(final File destination, final String name) throws IOException {
        final String[] dirs = name.split("\\\\");
        if (dirs == null) {
            return null;
        }
        String path = "";
        final int size = dirs.length;
        if (size == 1) {
            return new File(destination, name);
        } else if (size > 1) {
            for (int i = 0; i < dirs.length - 1; i++) {
                path = path + File.separator + dirs[i];
                new File(destination, path).mkdir();
            }
            path = path + File.separator + dirs[dirs.length - 1];
            final File f = new File(destination, path);
            f.createNewFile();
            return f;
        } else {
            return null;
        }
    }


}
