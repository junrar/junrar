package com.github.junrar.rarfile.rar5;

/**
 * RAR5 FILE/SERVICE header extra-record type (unrar {@code FHEXTRA_*},
 * {@code d861246:headers5.hpp:79-86}). Each constant wraps its on-disk wire value (a vint);
 * {@link #findType(long)} returns {@code null} for an unknown value so the caller skips the
 * record (manual &sect;4.6). The raw vint stays available to the caller; the enum is
 * materialized lazily per record.
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to diff
 * against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson, manual
 * &sect;4.6).
 */
public enum Rar5FileExtraType {

    /** Encryption parameters. unrar {@code FHEXTRA_CRYPT}. */
    CRYPT(1),
    /** File hash (Blake2sp). unrar {@code FHEXTRA_HASH}. */
    HASH(2),
    /** High precision file time. unrar {@code FHEXTRA_HTIME}. */
    HTIME(3),
    /** File version information. unrar {@code FHEXTRA_VERSION}. */
    VERSION(4),
    /** File system redirection (links, etc.). unrar {@code FHEXTRA_REDIR}. */
    REDIR(5),
    /** Unix owner and group information. unrar {@code FHEXTRA_UOWNER}. */
    UOWNER(6),
    /** Service header subdata array. unrar {@code FHEXTRA_SUBDATA}. */
    SUBDATA(7);

    private final long value;

    Rar5FileExtraType(final long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching extra-record type, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5FileExtraType findType(final long value) {
        for (final Rar5FileExtraType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
