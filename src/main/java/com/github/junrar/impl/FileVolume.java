package com.github.junrar.impl;

import java.io.File;
import java.io.IOException;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.io.IReadOnlyAccess;
import com.github.junrar.io.ReadOnlyAccessFile;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 *
 */
public class FileVolume implements Volume {
    private final Archive archive;
    private final File file;
    private String password;

    /**
     * @param archive .
     * @param file .
     */
    public FileVolume(Archive archive, File file) {
        this.archive = archive;
        this.file = file;
        this.password = null;
    }
    
    public FileVolume(Archive archive, File file,String password) {
        this.archive = archive;
        this.file = file;
        this.password = password;
    }

    @Override
    public IReadOnlyAccess getReadOnlyAccess() throws IOException {
        return new ReadOnlyAccessFile(file, password);
    }

    @Override
    public long getLength() {
        return file.length();
    }

    @Override
    public Archive getArchive() {
        return archive;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }
}
