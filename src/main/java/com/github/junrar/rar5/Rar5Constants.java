package com.github.junrar.rar5;

public final class Rar5Constants {

    private Rar5Constants() {
    }

    public static final int SIZEOF_MARKHEAD5 = 8;
    public static final int SIZEOF_SHORTBLOCKHEAD5 = 7;

    public static final int HEAD_MAIN = 1;
    public static final int HEAD_FILE = 2;
    public static final int HEAD_SERVICE = 3;
    public static final int HEAD_CRYPT = 4;
    public static final int HEAD_ENDARC = 5;

    public static final int HFL_EXTRA = 0x0001;
    public static final int HFL_DATA = 0x0002;
    public static final int HFL_SKIPIFUNKNOWN = 0x0004;
    public static final int HFL_SPLITBEFORE = 0x0008;
    public static final int HFL_SPLITAFTER = 0x0010;
    public static final int HFL_CHILD = 0x0020;
    public static final int HFL_INHERITED = 0x0040;

    public static final int MHFL_VOLUME = 0x0001;
    public static final int MHFL_VOLNUMBER = 0x0002;
    public static final int MHFL_SOLID = 0x0004;
    public static final int MHFL_PROTECT = 0x0008;
    public static final int MHFL_LOCK = 0x0010;

    public static final int FHFL_DIRECTORY = 0x0001;
    public static final int FHFL_UTIME = 0x0002;
    public static final int FHFL_CRC32 = 0x0004;
    public static final int FHFL_UNPUNKNOWN = 0x0008;

    public static final int EHFL_NEXTVOLUME = 0x0001;

    public static final int CHFL_CRYPT_PSWCHECK = 0x0001;

    public static final int MHEXTRA_LOCATOR = 0x01;
    public static final int MHEXTRA_METADATA = 0x02;

    public static final int MHEXTRA_LOCATOR_QLIST = 0x01;
    public static final int MHEXTRA_LOCATOR_RR = 0x02;

    public static final int MHEXTRA_METADATA_NAME = 0x01;
    public static final int MHEXTRA_METADATA_CTIME = 0x02;
    public static final int MHEXTRA_METADATA_UNIXTIME = 0x04;
    public static final int MHEXTRA_METADATA_UNIX_NS = 0x08;

    public static final int FHEXTRA_CRYPT = 0x01;
    public static final int FHEXTRA_HASH = 0x02;
    public static final int FHEXTRA_HTIME = 0x03;
    public static final int FHEXTRA_VERSION = 0x04;
    public static final int FHEXTRA_REDIR = 0x05;
    public static final int FHEXTRA_UOWNER = 0x06;
    public static final int FHEXTRA_SUBDATA = 0x07;

    public static final int FHEXTRA_HASH_BLAKE2 = 0x00;

    public static final int FHEXTRA_HTIME_UNIXTIME = 0x01;
    public static final int FHEXTRA_HTIME_MTIME = 0x02;
    public static final int FHEXTRA_HTIME_CTIME = 0x04;
    public static final int FHEXTRA_HTIME_ATIME = 0x08;
    public static final int FHEXTRA_HTIME_UNIX_NS = 0x10;

    public static final int FHEXTRA_CRYPT_PSWCHECK = 0x01;
    public static final int FHEXTRA_CRYPT_HASHMAC = 0x02;

    public static final int HOST5_WINDOWS = 0;
    public static final int HOST5_UNIX = 1;

    public static final int VER_PACK5 = 50;
    public static final int VER_PACK7 = 70;

    public static final int MAX_HEADER_SIZE_RAR5 = 2 * 1024 * 1024;
}
