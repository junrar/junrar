/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 29.05.2007
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
package com.github.junrar.crc;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class RarCRC {

    private RarCRC() {
    }

    /**
     * Computes the legacy 16-bit checksum used by RAR 1.5 archives.
     * <p>
     * This is not a standard CRC-16 algorithm. It is a simple rotating-add
     * checksum equivalent to the {@code OldCRC16} function in the original
     * unrar source code. For each byte, it adds the byte value to the
     * accumulator and then rotates the result left by 1 bit:
     * <pre>
     *   crc = crc + byte;
     *   crc = (crc &lt;&lt; 1) | (crc &gt;&gt;&gt; 15);
     * </pre>
     * <p>
     * Only used for RAR 1.5 (method 15) archives. RAR 2.0 and later use
     * standard CRC-32 (polynomial 0xEDB88320).
     *
     * @param startCrc initial CRC value (typically 0 for RAR 1.5)
     * @param data     data to compute the checksum over
     * @param count    number of bytes to process
     * @return the 16-bit rotating-add checksum
     */
    public static short checkOldCrc(short startCrc, byte[] data, int count) {
        int n = Math.min(data.length, count);
        for (int i = 0; i < n; i++) {
            startCrc = (short) ((short) (startCrc + (short) (data[i] & 0x00ff)) & -1);
            startCrc = (short) (((startCrc << 1) | (startCrc >>> 15)) & -1);
        }
        return (startCrc);
    }

}
