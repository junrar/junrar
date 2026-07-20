/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 01.06.2007
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
package com.github.junrar.unpack.decode;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Compress {
    public static final int CODEBUFSIZE         = 0x4000;
    public static final int MAXWINSIZE          = 0x400000;
    public static final int MAXWINMASK          = (MAXWINSIZE - 1);

    public static final int LOW_DIST_REP_COUNT  = 16;

    public static final int NC                  = 299;  /* alphabet = {0, 1, 2, ..., NC - 1} */
    public static final int DC                  = 60;
    public static final int LDC                 = 17;
    public static final int RC                  = 28;
    public static final int HUFF_TABLE_SIZE     = (NC + DC + RC + LDC);
    public static final int BC                  = 20;

    public static final int NC20                = 298;  /* alphabet = {0, 1, 2, ..., NC - 1} */
    public static final int DC20                = 48;
    public static final int RC20                = 28;
    public static final int BC20                = 19;
    public static final int MC20                = 257;

    // RAR5 sibling-engine alphabets (issue #27, M3.6). Mirrors 8f437ab:compress.hpp; the RAR3
    // NC/DC/... above predate unrar's NC30 rename, so the RAR5 values carry a 5 suffix.
    public static final int NC5                 = 306;  /* literals/length alphabet */
    public static final int DC5                 = 64;   /* distance-slot alphabet   */
    public static final int LDC5                = 16;   /* low-distance alphabet     */
    public static final int RC5                 = 44;   /* repeat-length alphabet    */
    public static final int BC5                 = 20;   /* bit-length (table) alphabet */
    public static final int HUFF_TABLE_SIZE5    = (NC5 + DC5 + RC5 + LDC5);   /* 430 */
    public static final int LARGEST_TABLE_SIZE5 = 306;
    public static final int MAX_QUICK_DECODE_BITS = 10;
}
