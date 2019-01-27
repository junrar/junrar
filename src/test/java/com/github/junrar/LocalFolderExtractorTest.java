package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.vfs2.provider.rar.FileSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFolderExtractorTest {

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException, RarException {
        File file = new File("/foo");
        FileSystem mockFileSystem = mock(FileSystem.class);
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file, mockFileSystem);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileNameW()).thenReturn("../../ops");

        try {
            localFolderExtractor.extract(archive, fileHeader);
        } catch (IllegalStateException e) {
            Assert.assertEquals("Rar contains file with invalid path: '/../ops'", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown 'java.lang.IllegalStateException: Detected path traversal! Stop extracting.'");
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException() throws IOException, RarException {
        File file = new File("/foo");
        FileSystem mockFileSystem = mock(FileSystem.class);
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file, mockFileSystem);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileNameW()).thenReturn("../../ops/");

        try {
            localFolderExtractor.createDirectory(fileHeader);
        } catch (IllegalStateException e) {
            Assert.assertEquals("Rar contains invalid path: '/../ops'", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown 'java.lang.IllegalStateException: Detected path traversal! Stop extracting.'");
    }

}
