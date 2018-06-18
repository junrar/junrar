package com.github.junrar.testutil;

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

	public static void main(String[] args) throws IOException, RarException {
		if (args.length == 2) {
			extractArchive(args[0], args[1]);
		} else {
			System.out.println("usage: java -jar extractArchive.jar <thearchive> <the destination directory>");
		}
	}
	
	@Deprecated
	public static void extractArchive(String archive, String destination) throws IOException, RarException {
		Junrar.extract(archive, destination);
	}
}
