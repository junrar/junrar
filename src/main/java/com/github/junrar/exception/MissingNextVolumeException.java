package com.github.junrar.exception;

/**
 * A split entry needs its next volume and the {@link com.github.junrar.volume.VolumeManager}
 * could not supply it (RAR5 multi-volume, M3.9 issue #30; unrar {@code UIERROR_MISSINGVOL},
 * {@code 8f437ab:volume.cpp:124}). RAR3/RAR4 keep their historical untyped behavior.
 */
public class MissingNextVolumeException extends RarException {
    public MissingNextVolumeException() {
        super();
    }

    public MissingNextVolumeException(final String message) {
        super(message);
    }

    public MissingNextVolumeException(final Throwable cause) {
        super(cause);
    }
}
