package com.github.junrar.testutil;

import java.io.File;

import org.apache.commons.logging.LogFactory;

/**
 * extract an archive to the given location
 * 
 * @author edmund wagner
 * 
 */
public class ExtractArchive {

	public static void main(String[] args) {
		if (args.length == 2) {
			extractArchive(args[0], args[1]);
		} else {
			System.out.println("usage: java -jar extractArchive.jar <thearchive> <the destination directory>");
		}
	}
	
	public static void extractArchive(String archive, String destination) {
		if (archive == null || destination == null) {
			throw new RuntimeException("archive and destination must me set");
		}
		File arch = new File(archive);
		if (!arch.exists()) {
			throw new RuntimeException("the archive does not exit: " + archive);
		}
		File dest = new File(destination);
		if (!dest.exists() || !dest.isDirectory()) {
			throw new RuntimeException("the destination must exist and point to a directory: " + destination);
		}
		ExtractArchive.extractArchive(arch, dest);
	}
	
	public static void extractArchive(File archive, File destination){
		com.github.junrar.extract.ExtractArchive extractArchive = new com.github.junrar.extract.ExtractArchive();
		extractArchive.setLogger(LogFactory.getLog(ExtractArchive.class.getName()));
		extractArchive.extractArchive(archive, destination);
	}
}
