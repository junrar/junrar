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
    public static void extractArchive(String archive, String destination) throws IOException, RarException {
        Junrar.extract(archive, destination);
    }
}
