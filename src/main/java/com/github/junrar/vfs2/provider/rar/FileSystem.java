package com.github.junrar.vfs2.provider.rar;

import java.io.File;
import java.io.IOException;

public class FileSystem {

    public void mkdir(File dir) {
        dir.mkdir();
    }

    public void createNewFile(File file) throws IOException {
        file.createNewFile();
    }
}
