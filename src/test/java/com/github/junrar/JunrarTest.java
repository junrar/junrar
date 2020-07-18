package com.github.junrar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.github.junrar.vfs2.provider.rar.FileSystem;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.testUtil.JUnRarTestUtil;

public class JunrarTest {
    private static File tempFolder;
    private FileSystem fileSystem = new FileSystem();

    @Before
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @After
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void extractionFromFileHappyDay() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        Junrar.extract(rarFileOnTemp, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        assertTrue(fooDir.exists());
        assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir, "bar.txt")));
    }

    @Test
    public void extractionFromFileHappyDayAndValidateExtractedFiles() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        final List<File> extractedFiles = Junrar.extract(rarFileOnTemp, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        assertEquals(new File(fooDir, "bar.txt"), extractedFiles.get(0));
        assertEquals(fooDir, extractedFiles.get(1));
    }


    @Test
    public void extractionFromFileWithVolumeManagerAndExtractorHappyDay() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        Junrar.extract(new LocalFolderExtractor(tempFolder, fileSystem), new FileVolumeManager(rarFileOnTemp));

        final File fooDir = new File(tempFolder, "foo");
        assertTrue(fooDir.exists());
        assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir, "bar.txt")));
    }

    @Test
    public void extractionFromStreamHappyDay() throws IOException, RarException {
        final InputStream resourceAsStream = JUnRarTestUtil.class.getResourceAsStream(TestCommons.SIMPLE_RAR_RESOURCE_PATH);
        Junrar.extract(resourceAsStream, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        assertTrue(fooDir.exists());
        assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir, "bar.txt")));
    }

    @Test
    public void listContents() throws IOException, RarException {
        final File testDocuments = TestCommons.writeResourceToFolder(tempFolder, "test-documents.rar");
        final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);

        final ContentDescription[] expected = {
                c("test-documents\\testEXCEL.xls", 13824),
                c("test-documents\\testHTML.html", 167),
                c("test-documents\\testOpenOffice2.odt", 26448),
                c("test-documents\\testPDF.pdf", 34824),
                c("test-documents\\testPPT.ppt", 16384),
                c("test-documents\\testRTF.rtf", 3410),
                c("test-documents\\testTXT.txt", 49),
                c("test-documents\\testWORD.doc", 19456),
                c("test-documents\\testXML.xml", 766)
        };

        assertArrayEquals(expected, contentDescriptions.toArray());
    }

    @Test
    public void extractRarV4() throws Exception {
        InputStream stream = null;
        try {
            final File rarV4 = TestCommons.writeResourceToFolder(tempFolder, "rar4.rar");
            stream = new FileInputStream(rarV4);
            Junrar.extract(stream, tempFolder);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        final File file1 = new File(tempFolder, "FILE1.TXT");
        final File file2 = new File(tempFolder, "FILE2.TXT");
        assertTrue(file1.exists());
        assertEquals(7, file1.length());
        assertTrue(file2.exists());
        assertEquals(7, file2.length());
    }

    private static ContentDescription c(final String name, final long size) {
        return new ContentDescription(name, size);
    }
}
