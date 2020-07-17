package com.github.junrar;

import com.github.junrar.exception.RarException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


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
        try (InputStream stream = getClass().getResourceAsStream("rar4.rar")) {
            Junrar.extract(stream, tempDir);
        }
        final File file1 = new File(tempDir, "FILE1.TXT");
        final File file2 = new File(tempDir, "FILE2.TXT");

        assertThat(file1).exists();
        assertThat(file1.length()).isEqualTo(7);
        assertThat(file2).exists();
        assertThat(file2.length()).isEqualTo(7);
    }

    @Test
    public void extractRarV5() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("rar5.rar")) {
            Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir));
            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(((RarException) thrown).getType()).isEqualTo(RarException.RarExceptionType.unsupportedRarArchive);
        }
    }
}
