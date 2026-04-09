package com.github.junrar;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;


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
            final Archive archive = new Archive(stream);
            assertThat(archive.getHeaders()).isNotEmpty();

            // Verify file headers match unrar l output:
            // FILE1.TXT  size=7
            // FILE2.TXT  size=7
            int rar5FileCount = 0;
            for (Object block : archive.getHeaders()) {
                if (block instanceof com.github.junrar.rar5.header.Rar5FileHeader) {
                    com.github.junrar.rar5.header.Rar5FileHeader fh =
                        (com.github.junrar.rar5.header.Rar5FileHeader) block;
                    if (fh.getFileName().equals("FILE1.TXT")) {
                        assertThat(fh.getFullUnpackSize()).isEqualTo(7);
                        rar5FileCount++;
                    } else if (fh.getFileName().equals("FILE2.TXT")) {
                        assertThat(fh.getFullUnpackSize()).isEqualTo(7);
                        rar5FileCount++;
                    }
                }
            }
            assertThat(rar5FileCount).isEqualTo(2);
        }
    }

    @Test
    public void extractRarV5Compressed() throws Exception {
        // Non-solid RAR5 archive from ssokolow/rar-test-files
        // Contains: testfile.txt (12 bytes, "Testing 123\n")
        // Note: RAR chose method 0 (stored) for this small file
        File tempDir = java.nio.file.Files.createTempDirectory("junrar-rar5-compressed").toFile();
        try {
            try (InputStream stream = getClass().getResourceAsStream("testfile.rar5.rar")) {
                assertThat(stream).isNotNull();
                Junrar.extract(stream, tempDir);
            }

            File file = new File(tempDir, "testfile.txt");
            assertThat(file).exists();
            assertThat(file.length()).isEqualTo(12);
            assertThat(java.nio.file.Files.readString(file.toPath())).isEqualTo("Testing 123\n");
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"LICENSE.rar", "LICENSE-blake2.rar"})
    public void extractRarV5CompressedLicense(final String fileName) throws Exception {
        // Non-solid compressed RAR5 archive from the project's own LICENSE file
        // Created with: rar a -m3 -s- LICENSE.rar LICENSE
        // Contains: LICENSE (1900 bytes, compressed to 959 bytes)
        File tempDir = java.nio.file.Files.createTempDirectory("junrar-rar5-license").toFile();
        try {
            try (InputStream stream = getClass().getResourceAsStream(fileName)) {
                assertThat(stream).isNotNull();
                Junrar.extract(stream, tempDir);
            }

            File extracted = new File(tempDir, "LICENSE");
            assertThat(extracted).exists();
            assertThat(extracted.length()).isEqualTo(1900);

            // Verify content matches the project's actual LICENSE file
            String expected = java.nio.file.Files.readString(
                java.nio.file.Paths.get("LICENSE"));
            String actual = java.nio.file.Files.readString(extracted.toPath());
            assertThat(actual).isEqualTo(expected);
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void extractRarV5AudioWithDeltaFilter() throws Exception {
        // RAR5 archive containing a WAV audio file that triggers Delta filter application.
        // Created from the RAR3 test archive BoatModernEnglish-audio-text-unpack30.rar
        // by extracting and recompressing as RAR5.
        File tempDir = java.nio.file.Files.createTempDirectory("junrar-rar5-audio").toFile();
        try {
            try (InputStream stream = getClass().getResourceAsStream("audio/rar5-audio-delta.rar")) {
                assertThat(stream).isNotNull();
                Junrar.extract(stream, tempDir);
            }

            // Verify the WAV file was extracted correctly (56464 bytes)
            File wavFile = new File(tempDir, "BoatModernEnglish.wav");
            assertThat(wavFile).exists();
            assertThat(wavFile.length()).isEqualTo(56464);

            // Verify the text file was extracted correctly
            File txtFile = new File(tempDir, "LICENSE.txt");
            assertThat(txtFile).exists();
            assertThat(txtFile.length()).isEqualTo(107);

            // Verify WAV content matches original
            byte[] expectedWav = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/test/resources/com/github/junrar/audio/BoatModernEnglish.wav"));
            byte[] actualWav = java.nio.file.Files.readAllBytes(wavFile.toPath());
            assertThat(actualWav).isEqualTo(expectedWav);
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
        }
    }
}

