package com.github.junrar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

public class TestCommons {

    public static String SIMPLE_RAR_RESOURCE_PATH = "test.rar";

    public static File createTempDir() throws IOException {
        final File tmp = File.createTempFile("Junrar", "test");
        tmp.delete();
        tmp.mkdir();
        return tmp;
    }

    public static File writeTestRarToFolder(File tmp) throws IOException {
        return writeResourceToFolder(tmp, SIMPLE_RAR_RESOURCE_PATH);
    }

    public static File writeResourceToFolder(File destination, String resourcePath) throws IOException {
        InputStream resourceAsStream = TestCommons.class.getResourceAsStream(resourcePath);
        File testRar = new File(destination, resourcePath);
        FileUtils.writeByteArrayToFile(testRar, IOUtils.toByteArray(resourceAsStream));
        return testRar;
    }

    public static void copyRarsToFolder(File destination, File srcDir) throws IOException {
        String[] fileNames = srcDir.list();
        for (String fileName : fileNames) {
            File srcFile = Paths.get(srcDir.getPath(), fileName).toFile();
            InputStream resourceAsStream = new FileInputStream(srcFile);
            File destFile = new File(destination, srcFile.getName());
            FileUtils.writeByteArrayToFile(destFile, IOUtils.toByteArray(resourceAsStream));
        }
    }

}
