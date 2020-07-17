package com.github.junrar;

import com.github.junrar.exception.RarException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class RarVersionTest {

    private File tempDir;

    @BeforeEach
    public void createTempDir() throws IOException {
        tempDir = TestCommons.createTempDir();
    }

    @AfterEach
    public void cleanupTempDir() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void extractRarV4() throws Exception {
        InputStream stream = null;
        try {
            stream = getClass().getResource("rar4.rar").openStream();
            Junrar.extract(stream, tempDir);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        final File file1 = new File(tempDir, "FILE1.TXT");
        final File file2 = new File(tempDir, "FILE2.TXT");
        assertTrue(file1.exists());
        assertEquals(7, file1.length());
        assertTrue(file2.exists());
        assertEquals(7, file2.length());
    }

    @Test
    public void extractRarV5() throws Exception {
        InputStream stream = null;
        try {
            stream = getClass().getResource("rar5.rar").openStream();
            Junrar.extract(stream, tempDir);
        } catch (RarException e) {
            assertEquals(RarException.RarExceptionType.unsupportedRarArchive, e.getType());
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
