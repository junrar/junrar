package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.MissingNextVolumeException;
import com.github.junrar.exception.RarException;
import com.github.junrar.volume.InputStreamVolumeManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class VolumeExtractorTest {
    private static File tempFolder;

    @BeforeEach
    public void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterEach
    public void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @ParameterizedTest
    @MethodSource("volumeArgs")
    public void extractionFromVolumedFile(String ressourceDir, String firstVolume)
            throws RarException, IOException {
        File dir = new File(getClass().getResource(ressourceDir).getPath());
        TestCommons.copyRarsToFolder(tempFolder, dir);

        final File rarFileOnTemp = new File(tempFolder, firstVolume);
        final File unpackedDir = new File(tempFolder, "test-documents");
        unpackedDir.delete();
        unpackedDir.mkdir();

        Junrar.extract(rarFileOnTemp, unpackedDir);

        checkContent(unpackedDir);
    }

    @ParameterizedTest
    @MethodSource("volumeArgs")
    public void extractionFromVolumedStream(String ressourceDir, String firstVolume)
            throws Exception {
        File dir = new File(getClass().getResource(ressourceDir).getPath());
        TestCommons.copyRarsToFolder(tempFolder, dir);

        List<File> files =
                Arrays.stream(tempFolder.listFiles())
                        .filter(File::isFile)
                        .sorted()
                        .collect(Collectors.toList());

        List<InputStream> streams = new ArrayList<>();
        for (File f : files) {
            streams.add(new FileInputStream(f));
        }

        final File unpackedDir = new File(tempFolder, "test-documents");
        unpackedDir.delete();
        unpackedDir.mkdir();

        Junrar.extract(new InputStreamVolumeManager(streams), unpackedDir);

        checkContent(unpackedDir);
    }

    /**
     * A first volume whose file name holds no volume number has no next volume to open. The
     * name used to be derived from the whole path, so 41/archive.rar continued from
     * 42/archive.rar and 43/archive.rar in the sibling directories.
     */
    @Test
    public void nextVolumeIsNotTakenFromASiblingDirectory() throws IOException {
        final File dir = new File(getClass().getResource("volumes/new-part").getPath());
        final File first = new File(tempFolder, "41/archive.rar");
        FileUtils.copyFile(new File(dir, "test-documents.part1.rar"), first);
        FileUtils.copyFile(
                new File(dir, "test-documents.part2.rar"), new File(tempFolder, "42/archive.rar"));
        FileUtils.copyFile(
                new File(dir, "test-documents.part3.rar"), new File(tempFolder, "43/archive.rar"));
        final File unpackedDir = new File(tempFolder, "test-documents");
        unpackedDir.mkdir();

        final Throwable thrown = catchThrowable(() -> Junrar.extract(first, unpackedDir));

        assertThat(thrown).isInstanceOf(MissingNextVolumeException.class);
    }

    private static Stream<Arguments> volumeArgs() {
        return Stream.of(
                Arguments.of("volumes/new-numbers", "test-documents.000.rar"),
                Arguments.of("volumes/new-part", "test-documents.part1.rar"));
    }

    private static void checkContent(File unpackedDir) {
        final Map<String, ContentDescription> expected = new HashMap<>();
        expected.put("testEXCEL.xls", new ContentDescription("testEXCEL.xls", 13824));
        expected.put("testHTML.html", new ContentDescription("testHTML.html", 167));
        expected.put("testOpenOffice2.odt", new ContentDescription("testOpenOffice2.odt", 26448));
        expected.put("testPDF.pdf", new ContentDescription("testPDF.pdf", 34824));
        expected.put("testPPT.ppt", new ContentDescription("testPPT.ppt", 16384));
        expected.put("testRTF.rtf", new ContentDescription("testRTF.rtf", 3410));
        expected.put("testTXT.txt", new ContentDescription("testTXT.txt", 49));
        expected.put("testWORD.doc", new ContentDescription("testWORD.doc", 19456));
        expected.put("testXML.xml", new ContentDescription("testXML.xml", 766));

        // file sort differ from os,so use the hash to do comparison
        String[] fileNames = unpackedDir.list();
        for (String fileName : fileNames) {
            File file = new File(unpackedDir, fileName);
            ContentDescription filedesc = new ContentDescription(file.getName(), file.length());
            ContentDescription expectedDesc = expected.get(fileName);
            assertThat(filedesc).isEqualTo(expectedDesc);
        }
    }
}
