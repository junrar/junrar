package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.MainHeader;

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
    public Volume nextVolume(final Archive archive, final Volume last) {
        if (last == null) return new FileVolume(archive, this.firstVolume);

        final FileVolume lastFileVolume = (FileVolume) last;
        // RAR5 has no RAR3 main header (getMainHeader() is null) and knows only the
        // .partN "new numbering" scheme (M3.9, issue #30; unrar-delta-map §2.9).
        final MainHeader mainHeader = archive.getMainHeader();
        final boolean oldNumbering = mainHeader != null
            && (!mainHeader.isNewNumbering() || archive.isOldFormat());
        final String nextName = VolumeHelper.nextVolumeName(lastFileVolume.getFile().getAbsolutePath(), oldNumbering);
        final File nextVolume = new File(nextName);

        return new FileVolume(archive, nextVolume);
    }
}
