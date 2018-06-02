package com.github.junrar;

import java.io.IOException;

/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 *
 */
public interface VolumeManager {
	public Volume nextArchive(Archive archive, Volume lastVolume) throws IOException;
}
