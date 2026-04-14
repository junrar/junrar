package com.github.junrar.impl.rar5;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.impl.HeaderReader;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.MarkHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder reader for RAR5 (version 5.x) archives.
 * RAR5 format is not yet supported; this implementation throws
 * UnsupportedRarV5Exception when attempting to read headers.
 */
public class RAR5HeaderReader implements HeaderReader {

    /**
     * Creates a RAR5HeaderReader with the pre-parsed MarkHeader.
     *
     * @param markHeader the MarkHeader parsed by HeaderReaderFactory
     */
    public RAR5HeaderReader(MarkHeader markHeader) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readHeaders(SeekableReadOnlyByteChannel channel, long fileLength, String password)
            throws IOException, RarException {
        throw new UnsupportedRarV5Exception();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<BaseBlock> getHeaders() {
        return new ArrayList<>();
    }
}
