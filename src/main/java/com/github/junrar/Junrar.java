package com.github.junrar;

import java.io.File;
import java.io.IOException;

import com.github.junrar.exception.RarException;
import com.github.junrar.extract.ExtractArchive;

public class Junrar {
	
	public static void extract(final File rar, final File destinationFolder) throws RarException, IOException {
		if(!rar.isFile()) {
			throw new IllegalArgumentException("First argument should be a file but was "+rar.getAbsolutePath());
		}
		if(!destinationFolder.isDirectory()) {
			throw new IllegalArgumentException("Second argument should be a dierctory but was "+destinationFolder.getAbsolutePath());
		}
		ExtractArchive extractArchive = new ExtractArchive();  
		extractArchive.extractArchive(rar, destinationFolder);  
	}	

}
