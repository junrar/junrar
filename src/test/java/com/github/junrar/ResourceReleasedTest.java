package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This test will have the rar file which will be extracted and the extracted content in the same directory. This directory is newly setup and deleted for every test method
 * to ensure all resources are released after the extraction.
 */
public class ResourceReleasedTest {

    private File rar4TestFile;

    private File rar5TestFile;

    private File extractDir;

    @Before
    public void setup() throws IOException {
        extractDir = TestCommons.createTempDir();
        rar5TestFile = new File(extractDir, "test5.rar");
        FileUtils.writeByteArrayToFile(rar5TestFile, IOUtils.toByteArray(getClass().getResource("rar5.rar").openStream()));
        rar4TestFile = new File(extractDir, "test4.rar");
        FileUtils.writeByteArrayToFile(rar4TestFile, IOUtils.toByteArray(getClass().getResource("rar4.rar").openStream()));
    }

    @After
    public void cleanup() throws IOException {
        FileUtils.cleanDirectory(extractDir);
    }

    @Test(expected = RarException.class)
    public void extractRar5FromFile() throws IOException, RarException {
        Junrar.extract(rar5TestFile, extractDir);
    }

    @Test(expected = RarException.class)
    public void extractRar5FromInputStream() throws IOException, RarException {
        InputStream input = null;
        try {
            input = new FileInputStream(rar5TestFile);
            Junrar.extract(input, extractDir);
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    @Test(expected = RarException.class)
    public void extractRar5FromString() throws IOException, RarException {
        Junrar.extract(rar5TestFile.getAbsolutePath(), extractDir.getAbsolutePath());
    }

    @Test(expected = RarException.class)
    public void extractRar5FromVolumeManager() throws IOException, RarException {
        final ExtractDestination extractDestination = new LocalFolderExtractor(extractDir);
        final VolumeManager volumeManager = new FileVolumeManager(rar5TestFile);
        Junrar.extract(extractDestination, volumeManager);
    }

    @Test
    public void extractRar4FromFile() throws IOException, RarException {
        Junrar.extract(rar4TestFile, extractDir);
    }

    @Test
    public void extractRar4FromInputStream() throws IOException, RarException {
        InputStream input = null;
        try {
            input = new FileInputStream(rar4TestFile);
            Junrar.extract(input, extractDir);
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    @Test
    public void extractRar4FromString() throws IOException, RarException {
        Junrar.extract(rar4TestFile.getAbsolutePath(), extractDir.getAbsolutePath());
    }

    @Test
    public void extractRar4FromVolumeManager() throws IOException, RarException {
        final ExtractDestination extractDestination = new LocalFolderExtractor(extractDir);
        final VolumeManager volumeManager = new FileVolumeManager(rar4TestFile);
        Junrar.extract(extractDestination, volumeManager);
    }
}
