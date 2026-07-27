package com.github.junrar.exception;

/**
 * Thrown when the archive signature names a RAR format this library cannot read: a future
 * format version byte ({@code 0x02}..{@code 0x04}) that the library predates (unrar
 * {@code RARFMT_FUTURE}, {@code d861246:archive.cpp:122,178-181}: "so we can return a
 * sensible warning in case we'll want to change the archive format sometimes in the
 * future"), or the ancient RAR 1.4 format (marker {@code 52 45 7e 5e}, unrar
 * {@code RARFMT14}), whose pre-{@code BaseBlock} header layout junrar does not implement
 * (issue #293). Distinct from {@link BadRarArchiveException} (no valid signature at all).
 */
public class UnsupportedRarVersionException extends RarException {
    public UnsupportedRarVersionException(Throwable cause) {
        super(cause);
    }

    public UnsupportedRarVersionException() {}
}
