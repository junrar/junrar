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
	 * @deprecated  As of release 1.0.2, replaced by {@link #Junrar.extract(File archive, File destination)}
	 */
	@Deprecated public void extractArchive(File archive, File destination) throws RarException, IOException {
		Junrar.extract(archive, destination);	
	}
}
