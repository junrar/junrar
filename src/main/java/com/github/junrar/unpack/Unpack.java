/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
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
package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.rarfile.CompressionMethod;
import com.github.junrar.unpack.decode.Compress;

import java.io.IOException;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public final class Unpack extends Unpack29 {

    public Unpack(ComprDataIO DataIO) {
        unpIO = DataIO;
        window = null;
        suspended = false;
        unpAllBuf = false;
        unpSomeRead = false;
    }

    public void init(byte[] window) {
        if (window == null) {
            this.window = new byte[Compress.MAXWINSIZE];
        } else {
            this.window = window;
        }
        inAddr = 0;
        unpInitData(false);
    }

    public void doUnpack(int method, boolean solid) throws IOException,
            RarException {
        if (unpIO.getSubHeader().getCompressionMethod() == CompressionMethod.STORED) {
            unstoreFile();
            return;
        }
        switch (method) {
            case 15: // rar 1.5 compression
                unpack15(solid);
                break;
            case 20: // rar 2.x compression
            case 26: // files larger than 2GB
                unpack20(solid);
                break;
            case 29: // rar 3.x compression
            case 36: // alternative hash
                unpack29(solid);
                break;
            case 50: // RAR5 compression (VER_PACK5)
            case 70: // RAR7 compression (VER_PACK7)
                throw new UnsupportedRarV5Exception();
            default:
                break;
        }
    }

    private void unstoreFile() throws IOException, RarException {
        byte[] buffer = new byte[0x10000];
        while (true) {
            int code = unpIO.unpRead(buffer, 0, (int) Math.min(buffer.length,
                    destUnpSize));
            if (code == 0 || code == -1) {
                break;
            }
            code = code < destUnpSize ? code : (int) destUnpSize;
            unpIO.unpWrite(buffer, 0, code);
            if (destUnpSize >= 0) {
                destUnpSize -= code;
            }
        }

    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

}
