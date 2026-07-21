package com.github.junrar.rarfile;

/**
 * Known versions of the rar file format.
 */
public enum RARVersion {

    OLD,
    V4,
    V5;

    /**
     * Checks if the version passed is the old rar format.
     * @param version Version to check if it is the old format.
     * @return <code>true</code> if the format is the old format. Otherwise false.
     */
    public static boolean isOldFormat(final RARVersion version) {
        return version == OLD;
    }
}
