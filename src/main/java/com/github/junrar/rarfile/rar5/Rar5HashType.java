package com.github.junrar.rarfile.rar5;

/**
 * RAR5 file-hash type, the {@code Type} field of a FHEXTRA_HASH record (unrar
 * {@code FHEXTRA_HASH_*}, {@code d861246:headers5.hpp:88-89}: only Blake2sp is defined). Each
 * constant wraps its on-disk wire value (a vint); {@link #findType(long)} returns {@code null}
 * for an unknown value (manual &sect;4.6).
 * <p>
 * The {@code findX}-returning-null lookup is junrar-only code with no C++ counterpart to diff
 * against, so it carries an {@code @EnumSource} round-trip test (the C11 lesson, manual
 * &sect;4.6).
 */
public enum Rar5HashType {

    /** Blake2sp, 32-byte digest. unrar {@code FHEXTRA_HASH_BLAKE2}. */
    BLAKE2(0);

    private final long value;

    Rar5HashType(final long value) {
        this.value = value;
    }

    public long getValue() {
        return this.value;
    }

    /**
     * @param value the raw wire value read as a vint
     * @return the matching hash type, or {@code null} if no constant wraps {@code value}
     */
    public static Rar5HashType findType(final long value) {
        for (final Rar5HashType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
