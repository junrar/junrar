package com.github.junrar.extract;

import java.io.File;
import java.io.IOException;

import com.github.junrar.Junrar;
import com.github.junrar.exception.RarException;

/**
 * extract an archive to the given location
 * 
 * @author edmund wagner
 * 
 */
public class ExtractArchive {
	
	/**
	 * @deprecated  As of release 1.0.2, replaced by { @link #Junrar.extract(final String rarPath, final String destinationPath) }
	 * 
	 * @param archive rar file path
	 * @param destination folder where the files will be extracted
	 * 
	 * @throws IOException .
	 * @throws RarException .
	 */
	@Deprecated 
	public void extractArchive(File archive, File destination) throws RarException, IOException {
		Junrar.extract(archive, destination);	
	}
}
