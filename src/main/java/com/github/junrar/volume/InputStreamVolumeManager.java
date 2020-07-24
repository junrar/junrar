package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.VolumeManager;

import java.io.InputStream;

public class InputStreamVolumeManager implements VolumeManager {

    private final InputStream is;

    public InputStreamVolumeManager(final InputStream is) {
        this.is = is;
    }

    @Override
    public Volume nextArchive(final Archive archive, final Volume lastVolume) {
        return new InputStreamVolume(archive, this.is);
    }

}
