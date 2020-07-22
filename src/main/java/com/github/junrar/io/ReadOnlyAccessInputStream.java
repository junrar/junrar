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

import com.github.junrar.rarfile.FileHeader;

import gnu.crypto.cipher.Rijndael;

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
    private Rijndael rin = new Rijndael();
    private byte[] AESKey = new byte[16];
    private byte[] AESInit = new byte[16];

    public ReadOnlyAccessInputStream(IReadOnlyAccess file, FileHeader hd, long startPos, long endPos)
            throws IOException {
        super();
        this.file = file;
        this.hd = hd;
        if (hd.isEncrypted()) {
            file.initAES(rin, hd.getSalt(), AESInit, AESKey);
        }
        this.startPos = startPos;
        curPos = startPos;
        this.endPos = endPos;
        file.setPosition(curPos);
    }

    @Override
    public int read() throws IOException {
        if (curPos == endPos) {
            return -1;
        } else {
            int b = 0;
            if (hd.isEncrypted()) {
                byte[] bx = new byte[1];
                this.deRead(bx, 0, bx.length);
                b = bx[0];
            } else {
                b = file.read();
            }

            curPos++;
            return b;
        }
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
            // byte[] bx = new byte[(int)Math.min(len, endPos - curPos)];
            bytesRead = this.deRead(b, off, (int) Math.min(len, endPos - curPos));
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

//
//    public void close() throws IOException {
//        file.close();
//    }
    private int deRead(byte[] b, int off, int len) throws IOException {
        int cs = data.size();
        int sizeToRead = len - cs;

        if (sizeToRead > 0) {
            int alignedSize = sizeToRead + ((~sizeToRead + 1) & 0xf);
            for (int i = 0; i < alignedSize / 16; i++) {
                // long ax = System.currentTimeMillis();
                byte[] tr = new byte[16];
                file.readFully(tr, 0, 16);

                // decrypt & add to data list
                byte[] out = new byte[16];
                this.rin.decryptBlock(tr, 0, out, 0);
                for (int j = 0; j < out.length; j++) {
                    this.data.add((byte) (out[j] ^ AESInit[j % 16])); // 32:114, 33:101
                }

                for (int j = 0; j < AESInit.length; j++) {
                    AESInit[j] = tr[j];
                }
                // System.out.println(System.currentTimeMillis()-ax);
            }
        }

        for (int i = 0; i < len; i++) {
            b[off + i] = data.poll();
        }
        return len;
    }
}
