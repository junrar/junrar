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

import java.io.IOException;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public interface SeekableReadOnlyByteChannel {

    /**
     * @return the current position in the channel
     * @throws IOException .
     */
    long getPosition() throws IOException;

    /**
     * @param pos the position in the channel
     * @throws IOException .
     */
    void setPosition(long pos) throws IOException;

    /**
     * Read a single byte of data.
     *
     * @return read read
     *
     * @throws IOException .
     */
    int read() throws IOException;

    /**
     * Read up to <tt>count</tt> bytes to the specified buffer.
     *
     * @param buffer .
     * @param off .
     * @param count .
     *
     * @return read read
     *
     * @throws IOException .
     *
     */
    int read(byte[] buffer, int off, int count) throws IOException;

    /**
     * Read exactly <tt>count</tt> bytes to the specified buffer.
     *
     * @param buffer where to store the read data
     * @param count how many bytes to read
     * @return bytes read || -1 if  IO problem
     *
     * @throws IOException .
     */
    int readFully(byte[] buffer, int count) throws IOException;

    /**
     * Close this channel.
     * @throws IOException .
     */
    void close() throws IOException;
}
