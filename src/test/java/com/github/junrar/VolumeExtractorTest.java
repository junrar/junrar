package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VolumeExtractorTest {
    private static File tempFolder;
    private static final String volumesDirName = "volumes";
    private static final String firstVolume = "test-documents.part1.rar";

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
        File dir = new File(VolumeExtractorTest.class.getResource(volumesDirName).getPath());
        TestCommons.copyRarsToFolder(tempFolder, dir);
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void extractionFromvolumedFile() throws RarException, IOException {
        final File rarFileOnTemp = new File(tempFolder, firstVolume);
        final File unpackedDir = new File(tempFolder, "test-documents");
        unpackedDir.delete();
        unpackedDir.mkdir();
        Junrar.extract(new LocalFolderExtractor(unpackedDir), new FileVolumeManager(rarFileOnTemp));

        checkContent(unpackedDir);
    }

    private void checkContent(File unpackedDir) {
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
