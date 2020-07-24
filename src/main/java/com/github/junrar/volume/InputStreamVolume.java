package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;

import java.io.InputStream;

public class InputStreamVolume implements Volume {

    private final Archive archive;
    private final InputStream inputStream;

    public InputStreamVolume(final Archive archive, final InputStream inputStream) {
        this.archive = archive;
        this.inputStream = inputStream;
    }

    @Override
    public SeekableReadOnlyByteChannel getChannel() {
        return new SeekableReadOnlyInputStream(this.inputStream);
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
