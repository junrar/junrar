package com.github.junrar;

import java.io.File;

public class InvalidExtractionPath extends IllegalStateException{


    private File badFile;

    public InvalidExtractionPath(File badFile){
        super("Rar contains entry with invalid path: '" + badFile.getAbsolutePath() + "'");
        this.badFile = badFile;
    }


    public File getBadFile() {
        return badFile;
    }
}
