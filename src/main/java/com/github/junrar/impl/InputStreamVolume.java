package com.github.junrar.impl;

import java.io.IOException;
import java.io.InputStream;

import com.github.junrar.Archive;
import com.github.junrar.Volume;
import com.github.junrar.io.IReadOnlyAccess;
import com.github.junrar.io.InputStreamReadOnlyAccessFile;

public class InputStreamVolume implements Volume {

	private final Archive archive;
	private final InputStream inputStream;

	public InputStreamVolume(final Archive archive, final InputStream inputStream) {
		this.archive = archive;
		this.inputStream = inputStream;
	}

	@Override
	public IReadOnlyAccess getReadOnlyAccess() throws IOException {
		return new InputStreamReadOnlyAccessFile(this.inputStream);
	}

	@Override
	public long getLength() {
		return Long.MAX_VALUE;
	}

	@Override
	public Archive getArchive() {
		return this.archive;
	}

}
