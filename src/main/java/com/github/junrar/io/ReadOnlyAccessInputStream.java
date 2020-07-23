/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 26.06.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression
 * algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.io;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import com.github.junrar.crypt.Rijndael;
import com.github.junrar.exception.InitDeciphererFailedException;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class ReadOnlyAccessInputStream extends InputStream {

    private IReadOnlyAccess file;

    private long curPos;
    protected final long startPos;
    private final long endPos;
    private FileHeader hd;
    private Queue<Byte> data = new LinkedList<Byte>();
    private Cipher cipher;
    private long readedCount = 0;

    public ReadOnlyAccessInputStream(IReadOnlyAccess file, FileHeader hd, long startPos, long endPos, final String password)
            throws IOException, RarException {
        super();
        this.file = file;
        this.hd = hd;
        this.startPos = startPos;
        curPos = startPos;
        this.endPos = endPos;
        file.setPosition(curPos);

        if (hd.isEncrypted()) {
            try {
                cipher = Rijndael.buildDecipherer(password, hd.getSalt());
            } catch (Exception e) {
                throw new InitDeciphererFailedException(e);
            }
        }
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Not needed,implement it when needed");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (curPos == endPos) {
            return -1;
        }
        int bytesRead = 0;
        if (hd.isEncrypted()) {
            try {
                bytesRead = this.deRead(b, off, (int) Math.min(len, endPos - curPos));
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException("exception happen when decrypting");
                }
            }

        } else {
            bytesRead = file.read(b, off, (int) Math.min(len, endPos - curPos));
        }
        curPos += bytesRead;
        return bytesRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    private int deRead(byte[] b, int off, int len) throws IOException, IllegalBlockSizeException, BadPaddingException {
        int cs = data.size();
        int sizeToRead = len - cs;

        if (sizeToRead > 0) {
            int alignedSize = sizeToRead + ((~sizeToRead + 1) & 0xf);
            byte[] tr = new byte[16];

            for (int i = 0; i < alignedSize / 16; i++) {
                while (true) {
                    byte[] out = null;
                    // reach the end of content
                    if ((startPos + readedCount) == endPos) {
                        out = cipher.doFinal();
                        if (out.length == 0) {
                            throw new IOException("No data to read");
                        }
                    } else {
                        file.readFully(tr, 16);
                        readedCount += 16;
                        out = cipher.update(tr);
                    }

                    if (out != null && out.length > 0) {
                        for (int j = 0; j < out.length; j++) {
                            this.data.add(out[j]);
                        }
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < len; i++) {
            b[off + i] = data.poll();
        }
        return len;
    }
}
