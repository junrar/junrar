package com.github.junrar;

import com.github.junrar.Rar5TestArchive.ExpectedFile;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Rar5ExtractionTest {

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @EnumSource(Rar5TestArchive.class)
    void extractViaJunrarFile(Rar5TestArchive archive) throws Exception {
        Path rarPath = copyResourceToTempDir(archive);
        Junrar.extract(rarPath.toFile(), tempDir.toFile());
        assertExtractedFiles(archive);
    }

    @ParameterizedTest
    @EnumSource(Rar5TestArchive.class)
    void extractViaJunrarStream(Rar5TestArchive archive) throws Exception {
        try (InputStream in = open(archive)) {
            Junrar.extract(in, tempDir.toFile());
        }
        assertExtractedFiles(archive);
    }

    @ParameterizedTest
    @EnumSource(Rar5TestArchive.class)
    void extractViaArchiveFile(Rar5TestArchive archive) throws Exception {
        Path rarPath = copyResourceToTempDir(archive);
        try (Archive a = new Archive(rarPath.toFile())) {
            extractToDir(a);
        }
        assertExtractedFiles(archive);
    }

    @ParameterizedTest
    @EnumSource(Rar5TestArchive.class)
    void extractViaArchiveStream(Rar5TestArchive archive) throws Exception {
        try (Archive a = new Archive(open(archive))) {
            extractToDir(a);
        }
        assertExtractedFiles(archive);
    }

    @ParameterizedTest
    @EnumSource(Rar5TestArchive.class)
    void extractViaArchiveGetInputStream(Rar5TestArchive archive) throws Exception {
        try (Archive a = new Archive(open(archive))) {
            for (FileHeader hd : a.getFileHeaders()) {
                Path target = tempDir.resolve(hd.getFileName());
                try (InputStream fis = a.getInputStream(hd)) {
                    Files.copy(fis, target);
                }
            }
        }
        assertExtractedFiles(archive);
    }

    private Path copyResourceToTempDir(Rar5TestArchive archive) throws IOException {
        Path target = tempDir.resolve(Paths.get(archive.getResourcePath()).getFileName());
        try (InputStream in = open(archive)) {
            Files.copy(in, target);
        }
        return target;
    }

    private void extractToDir(Archive archive) throws Exception {
        FileHeader hd;
        while ((hd = archive.nextFileHeader()) != null) {
            Path target = tempDir.resolve(hd.getFileName());
            try (OutputStream os = Files.newOutputStream(target)) {
                archive.extractFile(hd, os);
            }
        }
    }

    private void assertExtractedFiles(Rar5TestArchive archive) throws Exception {
        for (ExpectedFile ef : archive.getExpectedFiles()) {
            ef.verify(tempDir);
        }
    }

    private InputStream open(Rar5TestArchive archive) {
        return getClass().getResourceAsStream(archive.getResourcePath());
    }
}
