package com.github.junrar.rar5.header.extra;

/**
 * File version extra record (FHEXTRA_VERSION = 0x04).
 *
 * <p>Fields:
 * <ul>
 *   <li>Flags (vint): reserved, skip</li>
 *   <li>Version (vint): file version number</li>
 * </ul>
 */
public final class Rar5FileVersionRecord implements Rar5ExtraRecord {

    /** Record type value. */
    public static final long TYPE = 0x04;

    private final long version;

    Rar5FileVersionRecord(final long version) {
        this.version = version;
    }

    @Override
    public long getRecordType() {
        return TYPE;
    }

    /**
     * @return the file version number (0 if not set)
     */
    public long getVersion() {
        return version;
    }

    /**
     * @return true if a version number is present
     */
    public boolean hasVersion() {
        return version != 0;
    }
}
