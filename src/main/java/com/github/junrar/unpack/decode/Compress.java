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
    public static final int CODEBUFSIZE = 0x4000;
    public static final int MAXWINSIZE = 0x400000;
    public static final int MAXWINMASK = (MAXWINSIZE - 1);

    public static final int LOW_DIST_REP_COUNT = 16;

    public static final int NC = 299; /* alphabet = {0, 1, 2, ..., NC - 1} */
    public static final int DC = 60;
    public static final int LDC = 17;
    public static final int RC = 28;
    public static final int HUFF_TABLE_SIZE = (NC + DC + RC + LDC);
    public static final int BC = 20;

    public static final int NC20 = 298; /* alphabet = {0, 1, 2, ..., NC - 1} */
    public static final int DC20 = 48;
    public static final int RC20 = 28;
    public static final int BC20 = 19;
    public static final int MC20 = 257;

    // RAR5 sibling-engine alphabets (issue #27, M3.6). Mirrors 8f437ab:compress.hpp; the RAR3
    // NC/DC/... above predate unrar's NC30 rename, so the RAR5 values carry a 5 suffix.
    public static final int NC5 = 306; /* literals/length alphabet */
    public static final int DC5 = 64; /* distance-slot alphabet   */
    public static final int LDC5 = 16; /* low-distance alphabet     */
    public static final int RC5 = 44; /* repeat-length alphabet    */
    public static final int BC5 = 20; /* bit-length (table) alphabet */
    public static final int HUFF_TABLE_SIZE5 = (NC5 + DC5 + RC5 + LDC5); /* 430 */
    public static final int LARGEST_TABLE_SIZE5 = 306;

    // RAR7 extended distances (issue #34, M4.2). d861246:compress.hpp:21-30 split the single DC
    // into DCB ("base distance codes up to 4 GB") and DCX ("extended distance codes up to 1 TB"),
    // with one HUFF_TABLE_SIZE per variant; DC5/HUFF_TABLE_SIZE5 above are that DCB pair, kept
    // under their M3.6 names. Which pair a block uses is Unpack5's extraDist flag, i.e. whether
    // the entry is version 70 (d861246:unpack.cpp:184, unpack50.cpp:647,710).
    public static final int DCX5 = 80; /* extended distance-slot alphabet */
    public static final int HUFF_TABLE_SIZE5X = (NC5 + DCX5 + RC5 + LDC5); /* 446 */
    public static final int MAX_QUICK_DECODE_BITS = 10;

    // Match-length ceilings (compress.hpp:10,17) — the write-out reserve margin the decode loop
    // keeps between the window pointer and the write border.
    public static final int MAX_LZ_MATCH = 0x1001;
    public static final int MAX_INC_LZ_MATCH = MAX_LZ_MATCH + 3; /* 0x1004 */
    // Preferred write-block cap (unpack.hpp:28): bound the per-flush size instead of huge windows.
    public static final int UNPACK_MAX_WRITE = 0x400000;

    // RAR5 filter types (compress.hpp enum FilterType, M3.8 issue #29). Only DELTA/E8/E8E9/ARM
    // are ever applied by RAR5 (d861246:unpack50.cpp:427-485, the 4-type census); FILTER_NONE
    // marks a processed slot in the filter queue.
    public static final int FILTER_DELTA = 0;
    public static final int FILTER_E8 = 1;
    public static final int FILTER_E8E9 = 2;
    public static final int FILTER_ARM = 3;
    public static final int FILTER_NONE = 8;
    // Filter limits (unpack.hpp:9,24): queue flood cap and single-block memory cap.
    public static final int MAX_UNPACK_FILTERS = 8192;
    public static final int MAX_FILTER_BLOCK_SIZE = 0x400000;
}
