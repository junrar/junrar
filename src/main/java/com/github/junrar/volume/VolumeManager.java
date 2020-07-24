package com.github.junrar.volume;

import com.github.junrar.Archive;

import java.io.IOException;

/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public interface VolumeManager {
    Volume nextArchive(Archive archive, Volume lastVolume) throws IOException;
}
