package com.github.junrar.rarfile.rar5;

/**
 * RAR5 MAIN header extra-record type (unrar {@code MHEXTRA_*},
 * {@code d861246:headers5.hpp:67-68}). Each constant wraps its on-disk wire value (a vint);
 * {@link #findType(long)} returns {@code null} for an unknown value so the caller skips the
 * record (manual &sect;4.6).
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to diff
 * against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson, manual
 * &sect;4.6).
 */
public enum Rar5MainExtraType {

    /** Position of quick list and other blocks. unrar {@code MHEXTRA_LOCATOR}. */
    LOCATOR(1),
    /** Archive metadata. unrar {@code MHEXTRA_METADATA}. */
    METADATA(2);

    private final long value;

    Rar5MainExtraType(final long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching extra-record type, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5MainExtraType findType(final long value) {
        for (final Rar5MainExtraType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
