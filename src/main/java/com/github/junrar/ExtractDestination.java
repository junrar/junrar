package com.github.junrar;

import java.io.File;
import java.io.IOException;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public interface ExtractDestination {

    File createDirectory(FileHeader fileHeader);

    File extract(
        Archive arch,
        FileHeader fileHeader
    ) throws RarException, IOException;
}
