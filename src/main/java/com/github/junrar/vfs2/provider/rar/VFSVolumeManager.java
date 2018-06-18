package com.github.junrar.vfs2.provider.rar;

import java.io.IOException;

import org.apache.commons.vfs2.FileObject;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.VolumeManager;
import com.github.junrar.util.VolumeHelper;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public class VFSVolumeManager implements VolumeManager {

	private final FileObject firstVolume;

	public VFSVolumeManager(final FileObject firstVolume) {
		this.firstVolume = firstVolume;
	}

	@Override
	public Volume nextArchive(final Archive archive, final Volume last) throws IOException {
		if (last == null) return new VFSVolume(archive, this.firstVolume);

		final VFSVolume vfsVolume = (VFSVolume) last;
		final boolean oldNumbering = !archive.getMainHeader().isNewNumbering()
				|| archive.isOldFormat();
		final String nextName = VolumeHelper.nextVolumeName(vfsVolume.getFile()
				.getName().getBaseName(), oldNumbering);
		final FileObject nextVolumeFile = this.firstVolume.getParent().resolveFile(
				nextName);

		return new VFSVolume(archive, nextVolumeFile);
	}
}
