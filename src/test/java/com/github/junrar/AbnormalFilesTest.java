package com.github.junrar;

import com.github.junrar.exception.RarException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
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
    public void extractFile(Map.Entry<String, RarException.RarExceptionType> fileAndResult) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(fileAndResult.getKey())) {
            Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir));
            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(((RarException) thrown).getType()).isEqualTo(fileAndResult.getValue());
        }
    }

    private static Stream<Map.Entry<String, RarException.RarExceptionType>> provideFilesAndExpectedExceptionType() {
        Map<String, RarException.RarExceptionType> map = new HashMap<>();

        map.put("abnormal/corrupt-header.rar", RarException.RarExceptionType.corruptHeader);

        return map.entrySet().stream();
    }
}
