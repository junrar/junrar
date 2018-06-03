package com.github.junrar.impl;

import java.io.IOException;
import java.io.InputStream;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.VolumeManager;

public class InputStreamVolumeManager implements VolumeManager {

	private final InputStream is;

	public InputStreamVolumeManager(final InputStream is) {
		this.is = is;
	}

	@Override
	public Volume nextArchive(final Archive archive, final Volume lastVolume) throws IOException {
		return new InputStreamVolume(archive, this.is);
	}

}
