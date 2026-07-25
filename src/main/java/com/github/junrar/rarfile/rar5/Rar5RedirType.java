package com.github.junrar.rarfile.rar5;

/**
 * RAR5 file-system redirection type (unrar {@code FILE_SYSTEM_REDIRECT},
 * {@code d861246:headers.hpp:107-110}: "We also use these values in extra field, so do not
 * modify them."). Each constant wraps its on-disk wire value (a vint); {@link #findType(long)}
 * returns {@code null} for an unknown value (manual &sect;4.6).
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to diff
 * against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson, manual
 * &sect;4.6).
 */
public enum Rar5RedirType {
    NONE(0),
    UNIX_SYMLINK(1),
    WIN_SYMLINK(2),
    JUNCTION(3),
    HARDLINK(4),
    FILE_COPY(5);

    private final long value;

    Rar5RedirType(final long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching redirection type, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5RedirType findType(final long value) {
        for (final Rar5RedirType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
