package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.VolumeManager;
import com.github.junrar.util.VolumeHelper;

import java.io.File;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public class FileVolumeManager implements VolumeManager {
    private final File firstVolume;

    public FileVolumeManager(final File firstVolume) {
        this.firstVolume = firstVolume;
    }

    @Override
    public Volume nextArchive(final Archive archive, final Volume last) {
        if (last == null) return new FileVolume(archive, this.firstVolume);

        final FileVolume lastFileVolume = (FileVolume) last;
        final boolean oldNumbering = !archive.getMainHeader().isNewNumbering()
            || archive.isOldFormat();
        final String nextName = VolumeHelper.nextVolumeName(lastFileVolume.getFile().getAbsolutePath(), oldNumbering);
        final File nextVolume = new File(nextName);

        return new FileVolume(archive, nextVolume);
    }
}
