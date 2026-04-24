package com.github.junrar.rar5.header.extra;

/**
 * Base interface for RAR5 extra area records.
 *
 * <p>Each extra area record has a type (vint) and size (vint for the record
 * size starting from the Type field). Records are parsed from the end of
 * the block header backwards.
 */
public interface Rar5ExtraRecord {

    /**
     * @return the record type value
     */
    long getRecordType();
}
