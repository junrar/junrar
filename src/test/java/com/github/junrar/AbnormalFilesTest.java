package com.github.junrar;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


public class AbnormalFilesTest {

    private File tempDir;

    @BeforeEach
    public void createTempDir() throws IOException {
        tempDir = TestCommons.createTempDir();
    }

    @AfterEach
    public void cleanupTempDir() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFile(String filePath, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> Junrar.extract(file, tempDir));

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFromStream(String filePath, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir));

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFileByArchive(String filePath, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> {
            Archive archive = new Archive(file);
            while (true) {
                FileHeader fileHeader = archive.nextFileHeader();
                if (fileHeader == null) {
                    break;
                }
                archive.extractFile(fileHeader, NullOutputStream.INSTANCE);
            }
        });

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractStreamByArchive(String filePath, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> {
                Archive archive = new Archive(stream);
                while (true) {
                    FileHeader fileHeader = archive.nextFileHeader();
                    if (fileHeader == null) {
                        break;
                    }
                    archive.extractFile(fileHeader, NullOutputStream.INSTANCE);
                }
            });

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    private static Stream<Arguments> provideFilesAndExpectedExceptionType() {
        return Stream.of(
            Arguments.of("abnormal/corrupt-header.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/mainHeaderNull.rar", BadRarArchiveException.class),
            Arguments.of("abnormal/loop.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop1.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop2.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop3.rar", CorruptHeaderException.class)
        );
    }
}
