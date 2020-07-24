package com.github.junrar;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This class will test archives that are encrypted or password protected.
 * <p>
 * Encrypted archives are password protected, but also encrypt the list of files,
 * so you need the password to list the content.
 * <p>
 * You can list the content of a password protected archive, but you cannot extract
 * without the password.
 */
public class EncryptedAchiveExtractionTest {
    private static File tempFolder;

    @BeforeEach
    public void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterEach
    public void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void onlyFileContentEncryptedRar4File() throws Exception {
        File rarFile = new File(EncryptedAchiveExtractionTest.class.getResource("password/rar4-only-file-content-encrypted.rar").getPath());
        Junrar.extract(rarFile, tempFolder, "test");

        File unpackFile = new File(tempFolder, "新建文本文档.txt");
        assertThat(unpackFile.exists());
        assertThat(unpackFile.length() == 10);
    }

}
