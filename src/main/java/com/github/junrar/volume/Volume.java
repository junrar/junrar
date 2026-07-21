package com.github.junrar.volume;

import com.github.junrar.Archive;
import com.github.junrar.io.SeekableReadOnlyByteChannel;

import java.io.IOException;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 *
 */
public interface Volume {
    /**
     * @return SeekableReadOnlyByteChannel the channel
     * @throws IOException .
     */
    SeekableReadOnlyByteChannel getChannel() throws IOException;

    /**
     * @return the data length
     */
    long getLength();

    /**
     * @return the archive this volume belongs to
     */
    Archive getArchive();
}
