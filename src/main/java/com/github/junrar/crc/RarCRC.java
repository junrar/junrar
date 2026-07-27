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

import java.util.zip.CRC32;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class RarCRC {

    private RarCRC() {}

    /**
     * Computes the RAR3 16-bit header-CRC check (P0.7, issue #12; unrar
     * {@code GetCRC15}, {@code d861246:rawread.cpp}): standard CRC-32 over
     * {@code header[offset, offset + length)}, masked to the low 16 bits.
     * <p>
     * unrar computes this as {@code ~CRC32raw(...) & 0xffff}, where
     * {@code CRC32raw} is unrar's own pre-final-invert running accumulator
     * ({@code crc.cpp}); bitwise-NOT of a pre-invert CRC-32 accumulator is
     * exactly the standard (post-invert) CRC-32 value, which is what
     * {@link CRC32#getValue()} already returns -- no extra inversion needed
     * here (verified against real {@code rar}-produced archives at three
     * independent header types before this method was written).
     *
     * @param header the raw header bytes, including the 2-byte headCRC field
     *               itself at the front (NOT covered by the checksum)
     * @param offset start offset within {@code header} (2, to skip headCRC)
     * @param length number of bytes to cover, non-positive means "none"
     * @return the 16-bit header CRC
     */
    public static short computeHeaderCrc16(
            final byte[] header, final int offset, final int length) {
        if (length <= 0) {
            return 0;
        }
        final CRC32 crc32 = new CRC32();
        crc32.update(header, offset, length);
        return (short) (crc32.getValue() & 0xffffL);
    }

    /**
     * Computes the RAR5 full 32-bit header CRC (M3.2, issue #23; unrar {@code GetCRC50},
     * {@code d861246:rawread.cpp:185-190}): standard CRC-32 over
     * {@code header[offset, offset + length)}. Unlike the RAR3 16-bit check this keeps the
     * whole 32-bit width. The caller feeds the header bytes past the 4-byte stored CRC
     * (i.e. {@code offset == 4}, {@code length == HeaderSize - 4}) and compares the result
     * to the little-endian stored value.
     *
     * @param header the raw header bytes, including the 4-byte headCRC field at the front
     *               (NOT covered by the checksum)
     * @param offset start offset within {@code header} (4, to skip the stored CRC)
     * @param length number of bytes to cover
     * @return the 32-bit header CRC
     */
    public static int computeHeaderCrc32(final byte[] header, final int offset, final int length) {
        final CRC32 crc32 = new CRC32();
        crc32.update(header, offset, length);
        return (int) crc32.getValue();
    }

    /**
     * Computes the legacy 16-bit checksum used by RAR 1.4 archives (unrar {@code Checksum14},
     * {@code d861246:crc.cpp:155-164}).
     * <p>
     * This is not a standard CRC-16 algorithm. It is a simple rotating-add
     * checksum equivalent to the {@code Checksum14} function in the original
     * unrar source code. For each byte, it adds the byte value to the
     * accumulator (masked to 16 bits) and then rotates the result left by 1 bit:
     * <pre>
     *   crc = (crc + byte) &amp; 0xffff;
     *   crc = ((crc &lt;&lt; 1) | (crc &gt;&gt;&gt; 15)) &amp; 0xffff;
     * </pre>
     * <p>
     * Only used for RAR 1.4 (unrar {@code HASH_RAR14}) archives. RAR 1.5 and later use
     * standard CRC-32 (polynomial 0xEDB88320).
     *
     * @param startCrc initial CRC value (typically 0 for RAR 1.4)
     * @param data     data to compute the checksum over, from index 0
     * @param count    number of bytes to process
     * @return the 16-bit rotating-add checksum
     */
    public static short checkOldCrc(final short startCrc, final byte[] data, final int count) {
        return checkOldCrc(startCrc, data, 0, count);
    }

    /**
     * {@link #checkOldCrc(short, byte[], int)}, but starting at {@code offset} instead of index
     * 0 (P2 fix round 3, issue #293). {@code ComprDataIO#unpWrite}'s old-format branch used to
     * call the 3-arg overload directly with a nonzero {@code offset} silently dropped -- correct
     * for the common case ({@code offset==0}, e.g. every RAR3+ archive and a RAR 1.4 archive's
     * FIRST flush of a fresh decode window), but wrong for a SOLID RAR 1.4 entry's flush, which
     * starts wherever the shared circular window pointer ({@code Unpack15}'s {@code wrPtr})
     * already sits after the previous entry -- always hashing {@code data[0, count)} instead of
     * {@code data[offset, offset+count)} corrupted the checksum (while leaving the actual output
     * bytes, written via {@code outputStream.write(addr, offset, count)}, correct) for exactly
     * the case P2 is the first code path to exercise. The 3-arg overload above now delegates
     * here with {@code offset=0}, so both share one implementation.
     *
     * @param startCrc initial CRC value (typically 0 for RAR 1.4)
     * @param data     data to compute the checksum over
     * @param offset   start offset within {@code data}
     * @param count    number of bytes to process
     * @return the 16-bit rotating-add checksum
     */
    public static short checkOldCrc(
            final short startCrc, final byte[] data, final int offset, final int count) {
        // P2 fix round (issue #293): the accumulator lives in an `int` masked to 16 bits
        // (`& 0xffff`) after every step, per crc.cpp:155-164, replacing a `short` accumulator
        // whose `>>> 15` sign-extended through `int` promotion once bit 15 was set, corrupting
        // the rotate for the majority of real (non-trivial) inputs.
        int crc = startCrc & 0xffff;
        final int n = Math.min(data.length - offset, count);
        for (int i = 0; i < n; i++) {
            crc = (crc + (data[offset + i] & 0xff)) & 0xffff;
            crc = ((crc << 1) | (crc >>> 15)) & 0xffff;
        }
        return (short) crc;
    }
}
