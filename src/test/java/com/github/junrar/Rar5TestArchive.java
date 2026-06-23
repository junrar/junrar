package com.github.junrar;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

public enum Rar5TestArchive {

    STORED("rar5.rar", buildRar5ExpectedFiles()),
    LEVEL0("rar5-level0.rar", buildRar5LevelExpectedFiles()),
    LEVEL1("rar5-level1.rar", buildRar5LevelExpectedFiles()),
    LEVEL2("rar5-level2.rar", buildRar5LevelExpectedFiles()),
    LEVEL3("rar5-level3.rar", buildRar5LevelExpectedFiles()),
    LEVEL4("rar5-level4.rar", buildRar5LevelExpectedFiles()),
    LEVEL5("rar5-level5.rar", buildRar5LevelExpectedFiles()),
    SOLID("solid/rar5-solid.rar", buildSolidFiles()),
    LEVEL1_VER1("rar5-v1.rar", List.of(
            new ExpectedFile("big.bin", (Path file) -> {
                assertThat(Files.size(file)).isEqualTo(5368709120L);

                CRC32 crc = new CRC32();
                try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, bytesRead);
                    }
                }
                assertThat(crc.getValue()).isEqualTo(423114947L);
            })
    )),
    LARGE_DICT("rar5-largedict.rar", List.of(
            new ExpectedFile("file.bin", (Path file) -> {
                assertThat(Files.size(file)).isEqualTo(8L * 1024 * 1024);

                CRC32 crc = new CRC32();
                try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, bytesRead);
                    }
                }
                assertThat(crc.getValue()).isEqualTo(450018373L);
            })
    ));

    public static final class ExpectedFile {
        private final String name;
        private final byte[] content;
        private final FileVerifier verifier;

        public ExpectedFile(String name, String content) {
            this.name = name;
            this.content = content.getBytes(StandardCharsets.UTF_8);
            this.verifier = null;
        }

        public ExpectedFile(String name, FileVerifier verifier) {
            this.name = name;
            this.content = null;
            this.verifier = verifier;
        }

        public void verify(Path extractionDir) throws Exception {
            Path extractedFile = extractionDir.resolve(name);
            assertThat(extractedFile).exists();

            if (verifier != null) {
                verifier.verify(extractedFile);
            } else {
                assertThat(Files.readAllBytes(extractedFile)).isEqualTo(content);
            }
        }

        @FunctionalInterface
        public interface FileVerifier {
            void verify(Path extractedFile) throws Exception;
        }
    }

    private final String resourcePath;
    private final List<ExpectedFile> expectedFiles;

    Rar5TestArchive(String resourcePath, List<ExpectedFile> expectedFiles) {
        this.resourcePath = resourcePath;
        this.expectedFiles = expectedFiles;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public List<ExpectedFile> getExpectedFiles() {
        return Collections.unmodifiableList(expectedFiles);
    }

    private static List<ExpectedFile> buildSolidFiles() {
        List<ExpectedFile> files = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            files.add(new ExpectedFile("file" + i + ".txt", "file" + i + "\n"));
        }
        return files;
    }

    private static List<ExpectedFile> buildRar5ExpectedFiles() {
        return Arrays.asList(
                new ExpectedFile("FILE1.TXT", "file1\r\n"),
                new ExpectedFile("FILE2.TXT", "file2\r\n")
        );
    }

    private static List<ExpectedFile> buildRar5LevelExpectedFiles() {
        StringBuilder content1 = new StringBuilder();
        StringBuilder content2 = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            content1.append("FILE1 line ").append(i).append(" with some padding content to make it longer\n");
            content2.append("FILE2 line ").append(i).append(" with some padding content to make it longer\n");
        }
        return Arrays.asList(
                new ExpectedFile("FILE1.TXT", content1.toString()),
                new ExpectedFile("FILE2.TXT", content2.toString())
        );
    }
}
