package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.vfs2.provider.rar.FileSystem;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFolderExtractorTest {

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        FileSystem mockFileSystem = mock(FileSystem.class);
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file, mockFileSystem);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileNameW()).thenReturn("../../ops");


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
        FileSystem mockFileSystem = mock(FileSystem.class);
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file, mockFileSystem);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileNameW()).thenReturn("../../ops/");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

}
