package com.github.junrar.rar5.header;

import com.github.junrar.exception.CorruptHeaderException;

/**
 * Enumeration of RAR5 block header types.
 */
public enum HeaderType {

    MARK(0),
    ARCHIVE(1),
    FILE(2),
    SERVICE(3),
    CRYPT(4),
    ENDARC(5);

    private final int value;

    HeaderType(final int value) {
        this.value = value;
    }

    /**
     * @return the numeric value of this header type
     */
    public int getValue() {
        return value;
    }

    /**
     * Looks up a HeaderType by its numeric value.
     *
     * @param value the numeric header type value
     * @return the corresponding HeaderType
     * @throws CorruptHeaderException if the value is no valid header type
     */
    public static HeaderType fromValue(final long value) throws CorruptHeaderException {
        for (final HeaderType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new CorruptHeaderException("unknown header type: " + value);
    }
}
