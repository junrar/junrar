package com.github.junrar.rarfile.rar5;

/**
 * RAR5 block type (unrar {@code HEADER_TYPE}, {@code d861246:headers.hpp:75-79}). Each
 * constant wraps its on-disk wire value (a vint); {@link #findType(long)} returns
 * {@code null} for an unknown value so the caller applies policy (manual &sect;4.6:
 * skip if {@code HFL_SKIPIFUNKNOWN}, else reject). The raw vint stays the stored field on
 * {@link Rar5BaseBlock}; the enum is materialized lazily at parse time.
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to
 * diff against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson,
 * manual &sect;4.6).
 */
public enum Rar5BlockType {

    /** Archive marker (the 8-byte RAR5 signature). unrar {@code HEAD_MARK}. */
    MARK(0x00),
    /** Main archive header. unrar {@code HEAD_MAIN}. */
    MAIN(0x01),
    /** File header. unrar {@code HEAD_FILE}. */
    FILE(0x02),
    /** Service header (CMT, QO, RR, ...). unrar {@code HEAD_SERVICE}. */
    SERVICE(0x03),
    /** Archive encryption header. unrar {@code HEAD_CRYPT}. */
    CRYPT(0x04),
    /** End-of-archive header. unrar {@code HEAD_ENDARC}. */
    ENDARC(0x05);

    private final int value;

    Rar5BlockType(final int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching type, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5BlockType findType(final long value) {
        for (final Rar5BlockType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
