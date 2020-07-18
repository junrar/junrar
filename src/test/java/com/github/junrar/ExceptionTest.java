package com.github.junrar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.junrar.exception.RarException;

public class ExceptionTest {

    private File tempDir;

    @Before
    public void createTempDir() throws IOException {
        tempDir = TestCommons.createTempDir();
    }

    @After
    public void cleanupTempDir() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test(expected = RarException.class)
    public void extractRarV5() throws Exception {
        InputStream stream = null;
        try {
            stream = getClass().getResource("abnormal/rar5.rar").openStream();
            Junrar.extract(stream, tempDir);
        } catch (RarException e) {
            assertEquals(RarException.RarExceptionType.unsupportedRarArchive, e.getType());
            throw e;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    @Test(expected = RarException.class)
    public void extractCorruptFile() throws Exception {
        InputStream stream = null;
        try {
            stream = getClass().getResource("abnormal/badRarArchive.rar").openStream();
            Junrar.extract(stream, tempDir);
        } catch (RarException e) {
            assertEquals(RarException.RarExceptionType.unsupportedRarArchive, e.getType());
            throw e;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    @Test
    public void ifIsFileInsteadOfDir_ThrowException() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempDir);
        try {
            Junrar.extract(rarFileOnTemp, rarFileOnTemp);
        } catch (final IllegalArgumentException e) {
            assertEquals("the destination must exist and point to a directory: " + rarFileOnTemp.getAbsolutePath(), e.getMessage());
            return;
        }
        fail();
    }
}
