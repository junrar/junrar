package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.volume.FileVolumeManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * This test will have the rar file which will be extracted and the extracted content in the same directory. This directory is newly setup and deleted for every test method
 * to ensure all resources are released after the extraction.
 */
public class ResourceReleasedTest {

    private File rar4TestFile;

    private File rar5TestFile;

    private File extractDir;

    @BeforeEach
    public void setup() throws IOException {
        extractDir = TestCommons.createTempDir();
        rar5TestFile = new File(extractDir, "test5.rar");
        FileUtils.writeByteArrayToFile(rar5TestFile, IOUtils.toByteArray(getClass().getResource("rar5.rar").openStream()));
        rar4TestFile = new File(extractDir, "test4.rar");
        FileUtils.writeByteArrayToFile(rar4TestFile, IOUtils.toByteArray(getClass().getResource("rar4.rar").openStream()));
    }

    @AfterEach
    public void cleanup() throws IOException {
        FileUtils.cleanDirectory(extractDir);
    }

    @Test
    public void extractRar5FromFile() {
        Throwable thrown = catchThrowable(() -> Junrar.extract(rar5TestFile, extractDir));

        assertThat(thrown).isInstanceOf(RarException.class);
    }

    @Test
    public void extractRar5FromInputStream() throws IOException {
        final InputStream input = new FileInputStream(rar5TestFile);

        Throwable thrown = catchThrowable(() -> Junrar.extract(input, extractDir));

        assertThat(thrown).isInstanceOf(RarException.class);

        input.close();
    }

    @Test
    public void extractRar5FromString() {
        Throwable thrown = catchThrowable(() -> Junrar.extract(rar5TestFile.getAbsolutePath(), extractDir.getAbsolutePath()));

        assertThat(thrown).isInstanceOf(RarException.class);
    }

    @Test
    public void extractRar5FromVolumeManager() {
        final ExtractDestination extractDestination = new LocalFolderExtractor(extractDir);
        final VolumeManager volumeManager = new FileVolumeManager(rar5TestFile);

        Throwable thrown = catchThrowable(() -> Junrar.extract(extractDestination, volumeManager));

        assertThat(thrown).isInstanceOf(RarException.class);
    }

    @Test
    public void extractRar4FromFile() throws IOException, RarException {
        Junrar.extract(rar4TestFile, extractDir);
    }

    @Test
    public void extractRar4FromInputStream() throws IOException, RarException {
        try (InputStream input = new FileInputStream(rar4TestFile)) {
            Junrar.extract(input, extractDir);
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
