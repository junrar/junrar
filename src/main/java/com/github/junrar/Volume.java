package com.github.junrar;

import java.io.IOException;

import com.github.junrar.io.IReadOnlyAccess;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 * 
 */
public interface Volume {
	/**
	 * @throws IOException .
	 * 
	 * @return IReadOnlyAccess the access
	 */
	IReadOnlyAccess getReadOnlyAccess() throws IOException;

	/**
	 * @return the data length
	 */
	long getLength();
	
	/**
	 * @return the archive this volume belongs to
	 */
	Archive getArchive();
}
