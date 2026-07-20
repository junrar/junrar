package com.github.junrar.rarfile.rar5;

/**
 * RAR5 host OS (unrar {@code HOST5_*}, {@code d861246:headers.hpp:93}). Each constant wraps its
 * on-disk wire value (a vint); {@link #findHostOS(long)} returns {@code null} for an unknown
 * value (unrar {@code HSYS_UNKNOWN}) so the caller applies a sane default rather than guessing.
 * The raw vint stays the stored field on the parser; the enum is materialized lazily.
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to diff
 * against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson, manual
 * &sect;4.6).
 */
public enum Rar5HostOS {

    WINDOWS(0),
    UNIX(1);

    private final long value;

    Rar5HostOS(final long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching host OS, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5HostOS findHostOS(final long value) {
        for (final Rar5HostOS hostOS : values()) {
            if (hostOS.value == value) {
                return hostOS;
            }
        }
        return null;
    }
}
