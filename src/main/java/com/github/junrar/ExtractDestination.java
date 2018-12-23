package com.github.junrar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public interface ExtractDestination {

    public File createDirectory(FileHeader fileHeader);

    public File extract(
        final Archive arch,
        final FileHeader fileHeader
    ) throws FileNotFoundException, RarException, IOException;
}
