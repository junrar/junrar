package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFolderExtractorTest {
    private static File tempFolder;

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");

        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops/");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException2() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "parent-dir.rar");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempFolder);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            File expectedInvalidPath = new File(tempFolder.getParentFile().getParentFile(), "tmp");
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath.toString());
        }
    }
}
