/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 23.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class SeekableReadOnlyFile implements SeekableReadOnlyByteChannel {
    private final RandomAccessFile file;


    /**
     * @param file the file
     * @throws FileNotFoundException .
     */
    public SeekableReadOnlyFile(final File file) throws FileNotFoundException {
        this.file = new RandomAccessFile(file, "r");
    }

    @Override
    public int readFully(final byte[] buffer, final int count) throws IOException {
        assert count >= 0 : count;
        file.readFully(buffer, 0, count);
        return count;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    @Override
    public long getPosition() throws IOException {
        return file.getFilePointer();
    }

    @Override
    public void setPosition(final long pos) throws IOException {
        file.seek(pos);
    }

    @Override
    public int read() throws IOException {
        return file.read();
    }

    @Override
    public int read(byte[] buffer, int off, int count) throws IOException {
        return file.read(buffer, off, count);
    }
}
