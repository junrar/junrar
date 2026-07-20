/*
 *
 * Original author: alpha_lam
 * Creation date: ?
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
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
package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;

public class FileNameDecoder {
    public static int getChar(byte[] name, int pos) {
        return name[pos] & 0xff;
    }

    /**
     * Ports unrar 7.2.7 (d861246) {@code encname.cpp EncodeFileName::Decode}: every read of the
     * encoded stream is bounds-checked before the access. Where the upstream C++ silently
     * {@code break}s on a truncated encoded stream, junrar surfaces the corruption as a typed
     * {@link CorruptHeaderException} (MIGRATION_MANUAL &sect;4.7: junrar throws where unrar
     * tolerates a short read). The RLE back-copy is bounded by the ANSI name length
     * {@code nameSize} (C++ {@code NameSize}), not the whole field.
     *
     * @param name     full LHD name field: ANSI name bytes {@code [0, nameSize)}, then the
     *                 encoded stream starting at {@code encPos}
     * @param nameSize length of the ANSI name (C++ {@code NameSize}) -- the RLE copy source bound
     * @param encPos   start offset of the encoded stream (just past the NUL separator)
     */
    public static String decode(byte[] name, int nameSize, int encPos) throws CorruptHeaderException {
        int decPos = 0;
        int flags = 0;
        int flagBits = 0;
        int low;
        int highByte = encPos < name.length ? getChar(name, encPos++) : 0;
        StringBuilder buf = new StringBuilder();
        while (encPos < name.length) {
            if (flagBits == 0) {
                flags = getChar(name, encPos++);
                flagBits = 8;
            }
            switch (flags >>> 6) {
                case 0:
                    if (encPos >= name.length) {
                        throw truncated();
                    }
                    buf.append((char) getChar(name, encPos++));
                    ++decPos;
                    break;
                case 1:
                    if (encPos >= name.length) {
                        throw truncated();
                    }
                    buf.append((char) (getChar(name, encPos++) + (highByte << 8)));
                    ++decPos;
                    break;
                case 2:
                    if (encPos + 1 >= name.length) {
                        throw truncated();
                    }
                    low = getChar(name, encPos);
                    int high = getChar(name, encPos + 1);
                    buf.append((char) ((high << 8) + low));
                    ++decPos;
                    encPos += 2;
                    break;
                case 3:
                    if (encPos >= name.length) {
                        throw truncated();
                    }
                    int length = getChar(name, encPos++);
                    if ((length & 0x80) != 0) {
                        if (encPos >= name.length) {
                            throw truncated();
                        }
                        int correction = getChar(name, encPos++);
                        for (length = (length & 0x7f) + 2; length > 0 && decPos < nameSize; length--, decPos++) {
                            low = (getChar(name, decPos) + correction) & 0xff;
                            buf.append((char) ((highByte << 8) + low));
                        }
                    } else {
                        for (length += 2; length > 0 && decPos < nameSize; length--, decPos++) {
                            buf.append((char) getChar(name, decPos));
                        }
                    }
                    break;
            }
            flags = (flags << 2) & 0xff;
            flagBits -= 2;
        }
        return buf.toString();
    }

    private static CorruptHeaderException truncated() {
        return new CorruptHeaderException("Truncated encoded file name");
    }
}
