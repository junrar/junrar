package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamVolume implements Volume {

    private final Archive archive;
    private final InputStream inputStream;
    private final int position;

    public InputStreamVolume(final Archive archive, final InputStream inputStream, final int position) {
        this.archive = archive;
        this.inputStream = inputStream;
        this.position = position;
    }

    @Override
    public SeekableReadOnlyByteChannel getChannel() {
        return new SeekableReadOnlyInputStream(this.inputStream);
    }

    @Override
    public long getLength() {
        try {
            return inputStream.available();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public Archive getArchive() {
        return this.archive;
    }

    public int getPosition() {
        return position;
    }
}
