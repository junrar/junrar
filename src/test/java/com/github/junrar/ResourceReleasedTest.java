package com.github.junrar;

import com.github.junrar.exception.RarException;
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
    public void extractRar5FromFile() throws IOException, RarException {
        // RAR5 headers are parsed and extraction is attempted
        Junrar.extract(rar5TestFile, extractDir);

        // Verify extracted files exist and have correct sizes
        // (unrar l shows: FILE1.TXT 7 bytes, FILE2.TXT 7 bytes)
        final File file1 = new File(extractDir, "FILE1.TXT");
        final File file2 = new File(extractDir, "FILE2.TXT");
        assertThat(file1).exists();
        assertThat(file2).exists();
        assertThat(file1.length()).isEqualTo(7);
        assertThat(file2.length()).isEqualTo(7);

        // Verify content matches expected: "file1\r\n" and "file2\r\n"
        assertThat(java.nio.file.Files.readString(file1.toPath())).isEqualTo("file1\r\n");
        assertThat(java.nio.file.Files.readString(file2.toPath())).isEqualTo("file2\r\n");
    }

    @Test
    public void extractRar5FromInputStream() throws IOException, RarException {
        try (InputStream input = new FileInputStream(rar5TestFile)) {
            Junrar.extract(input, extractDir);
        }
    }

    @Test
    public void extractRar5FromString() throws IOException, RarException {
        Junrar.extract(rar5TestFile.getAbsolutePath(), extractDir.getAbsolutePath());
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
}
