package com.github.junrar;


import com.github.junrar.volume.Volume;

/**
 * @author alban
 */
public interface UnrarCallback {

    /**
     * @param nextVolume ,
     *
     * @return {@code true} if the next volume is ready to be processed,
     *         {@code false} otherwise.
     */
    boolean isNextVolumeReady(Volume nextVolume);

    /**
     * This method is invoked each time the progress of the current
     * volume changes.
     *
     * @param current .
     * @param total .
     *
     */
    void volumeProgressChanged(long current, long total);
}
