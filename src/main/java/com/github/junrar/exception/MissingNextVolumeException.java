package com.github.junrar.exception;

/**
 * A split entry needs its next volume and the {@link com.github.junrar.volume.VolumeManager}
 * could not supply it (unrar {@code UIERROR_MISSINGVOL}, {@code 8f437ab:volume.cpp:124}, where
 * an exhausted volume search sets {@code RARX_OPEN} rather than ending the stream quietly).
 * Thrown for every format: RAR5 since M3.9 (issue #30), and RAR1.4/RAR3/RAR4 since the RAR 1.4
 * multi-volume work (issue #293) routed their shared volume switch through the same signal.
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
