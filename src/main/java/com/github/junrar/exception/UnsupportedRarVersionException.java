package com.github.junrar.exception;

/**
 * Thrown when the archive signature carries a future RAR format version byte
 * ({@code 0x02}..{@code 0x04}) that this library predates (unrar {@code RARFMT_FUTURE},
 * {@code d861246:archive.cpp:122,178-181}: "so we can return a sensible warning in case
 * we'll want to change the archive format sometimes in the future"). Distinct from
 * {@link UnsupportedRarV5Exception} (a known-but-not-yet-implemented version) and from
 * {@link BadRarArchiveException} (no valid signature at all).
 */
public class UnsupportedRarVersionException extends RarException {
    public UnsupportedRarVersionException(Throwable cause) {
        super(cause);
    }

    public UnsupportedRarVersionException() {
    }
}
