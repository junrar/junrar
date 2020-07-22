package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.junrar.exception.UnsupportedRarEncryptedException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.rarfile.FileHeader;

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
    }

    @Test
    public void givenEncryptedRar4File_whenCreatingArchive_thenUnsupportedRarEncryptedExceptionIsThrown() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar4-encrypted-junrar.rar")) {
            Throwable thrown = catchThrowable(() -> new Archive(is));

            assertThat(thrown).isExactlyInstanceOf(UnsupportedRarEncryptedException.class);
        }
    }

    @Test
    public void givenPasswordProtectedRar4File_whenCreatingArchive_then() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar4-password-junrar.rar")) {
            try (Archive archive = new Archive(is)) {
                List<FileHeader> fileHeaders = archive.getFileHeaders();
                assertThat(fileHeaders).hasSize(1);

                FileHeader fileHeader = fileHeaders.get(0);
                assertThat(fileHeader.isEncrypted()).isTrue();
                assertThat(fileHeader.getFileNameString()).isEqualTo("file1.txt");
            }
        }
    }

    @Test
    public void givenEncryptedRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar5-encrypted-junrar.rar")) {
            Throwable thrown = catchThrowable(() -> new Archive(is));

            assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
        }
    }

    @Test
    public void givenPasswordProtectedRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("password/rar5-password-junrar.rar")) {
            Throwable thrown = catchThrowable(() -> new Archive(is));

            assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
        }
    }
}
