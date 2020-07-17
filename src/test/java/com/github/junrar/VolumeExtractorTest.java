package com.github.junrar;

import static org.junit.Assert.assertEquals;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.vfs2.provider.rar.FileSystem;

public class VolumeExtractorTest {
    private FileSystem fileSystem = new FileSystem();
    private static File tempFolder;
    private static String volumesDirName = "volumes";
    private static String firstVolume = "test-documents.part1.rar";

    @BeforeClass
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
        File dir = new File(VolumeExtractorTest.class.getResource("").getPath(), volumesDirName);
        TestCommons.copyRarsToFolder(tempFolder, dir);
    }

    @AfterClass
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void extractionFromvolumedFile() throws RarException, IOException {
        final File rarFileOnTemp = new File(tempFolder, firstVolume);
        final File unpackedDir = new File(tempFolder, "test-documents");
        unpackedDir.delete();
        unpackedDir.mkdir();
        Junrar.extract(new LocalFolderExtractor(unpackedDir, fileSystem), new FileVolumeManager(rarFileOnTemp));

        checkContent(unpackedDir);
    }

    private void checkContent(File unpackedDir) {
        final Map<String, ContentDescription> expected = new HashMap<String, ContentDescription>();
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
            assertEquals(expectedDesc, filedesc);
        }
    }
}
