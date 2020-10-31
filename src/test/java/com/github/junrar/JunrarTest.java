package com.github.junrar;

import com.github.junrar.exception.RarException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class JunrarTest {
    private static File tempFolder;

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void extractionFromFileHappyDay() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        final List<File> extractedFiles = Junrar.extract(rarFileOnTemp, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        File barFile = new File(fooDir, "bar.txt");
        String barFileContent = FileUtils.readFileToString(barFile, Charset.defaultCharset());

        assertThat(fooDir).exists();
        assertThat(barFileContent).isEqualTo("baz\n");
        assertThat(extractedFiles.get(0)).isEqualTo(barFile);
        assertThat(extractedFiles.get(1)).isEqualTo(fooDir);
    }

    @Test
    public void extractionFromStreamHappyDay() throws IOException, RarException {
        final InputStream resourceAsStream = JunrarTest.class.getResourceAsStream(TestCommons.SIMPLE_RAR_RESOURCE_PATH);

        final List<File> extractedFiles = Junrar.extract(resourceAsStream, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        File barFile = new File(fooDir, "bar.txt");
        String barFileContent = FileUtils.readFileToString(barFile, Charset.defaultCharset());

        assertThat(fooDir).exists();
        assertThat(barFileContent).isEqualTo("baz\n");
        assertThat(extractedFiles.get(0)).isEqualTo(barFile);
        assertThat(extractedFiles.get(1)).isEqualTo(fooDir);
    }

    @Test
    public void listContents() throws IOException, RarException {
        final File testDocuments = TestCommons.writeResourceToFolder(tempFolder, "tika-documents.rar");
        final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(testDocuments);

        final ContentDescription[] expected = {
                new ContentDescription("test-documents\\testEXCEL.xls", 13824),
                new ContentDescription("test-documents\\testHTML.html", 167),
                new ContentDescription("test-documents\\testOpenOffice2.odt", 26448),
                new ContentDescription("test-documents\\testPDF.pdf", 34824),
                new ContentDescription("test-documents\\testPPT.ppt", 16384),
                new ContentDescription("test-documents\\testRTF.rtf", 3410),
                new ContentDescription("test-documents\\testTXT.txt", 49),
                new ContentDescription("test-documents\\testWORD.doc", 19456),
                new ContentDescription("test-documents\\testXML.xml", 766)
        };

        assertThat(contentDescriptions.toArray()).isEqualTo(expected);
    }

    @Test
    public void listContentsFromStream() throws IOException, RarException {
        final File testDocuments = TestCommons.writeResourceToFolder(tempFolder, "tika-documents.rar");
        try (InputStream fi = new FileInputStream(testDocuments)) {
            final List<ContentDescription> contentDescriptions = Junrar.getContentsDescription(fi);
            final ContentDescription[] expected = {
                    new ContentDescription("test-documents\\testEXCEL.xls", 13824),
                    new ContentDescription("test-documents\\testHTML.html", 167),
                    new ContentDescription("test-documents\\testOpenOffice2.odt", 26448),
                    new ContentDescription("test-documents\\testPDF.pdf", 34824),
                    new ContentDescription("test-documents\\testPPT.ppt", 16384),
                    new ContentDescription("test-documents\\testRTF.rtf", 3410),
                    new ContentDescription("test-documents\\testTXT.txt", 49),
                    new ContentDescription("test-documents\\testWORD.doc", 19456),
                    new ContentDescription("test-documents\\testXML.xml", 766)
            };

            assertThat(contentDescriptions.toArray()).isEqualTo(expected);
        }
    }

    @Nested
    class DirectoryTraversal {
        @Test
        public void ifIsDirInsteadOfFile_ThrowException() {
            Throwable thrown = catchThrowable(() -> Junrar.extract(tempFolder, tempFolder));

            assertThat(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("First argument should be a file but was " + tempFolder.getAbsolutePath());
        }

        @Test
        public void ifIsFileInsteadOfDir_ThrowException() throws IOException {
            final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

            Throwable thrown = catchThrowable(() -> Junrar.extract(rarFileOnTemp, rarFileOnTemp));

            assertThat(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("the destination must exist and point to a directory: " + rarFileOnTemp.getAbsolutePath());
        }
    }

    /**
     * This class will test archives that are encrypted or password protected.
     * <p>
     * Encrypted archives are password protected, but also encrypt the list of files,
     * so you need the password to list the content.
     * <p>
     * You can list the content of a password protected archive, but you cannot extract
     * without the password.
     */
    @Nested
    class PasswordProtected {
        @Test
        public void onlyFileContentEncryptedRar4File() throws Exception {
            File rarFile = new File(getClass().getResource("password/rar4-only-file-content-encrypted.rar").getPath());
            Junrar.extract(rarFile, tempFolder, "test");

            File unpackFile = new File(tempFolder, "新建文本文档.txt");
            assertThat(unpackFile.exists());
            assertThat(unpackFile.length() == 10);
        }

        @Test
        public void onlyFileContentEncryptedRar4FileAsStream() throws Exception {
            InputStream rarStream = getClass().getResourceAsStream("password/rar4-only-file-content-encrypted.rar");
            Junrar.extract(rarStream, tempFolder, "test");

            File unpackFile = new File(tempFolder, "新建文本文档.txt");
            assertThat(unpackFile.exists());
            assertThat(unpackFile.length() == 10);
        }

        @Test
        public void headerEncryptedRar4File() throws Exception {
            File rarFile = new File(getClass().getResource("password/rar4-encrypted-junrar.rar").getPath());
            Junrar.extract(rarFile, tempFolder, "junrar");

            File unpackFile = new File(tempFolder, "file1.txt");
            assertThat(unpackFile.exists());
            assertThat(unpackFile.length() == 6);
        }

        @Test
        public void headerEncryptedRar4FileAsStream() throws Exception {
            InputStream rarStream = getClass().getResourceAsStream("password/rar4-encrypted-junrar.rar");
            Junrar.extract(rarStream, tempFolder, "junrar");

            File unpackFile = new File(tempFolder, "file1.txt");
            assertThat(unpackFile.exists());
            assertThat(unpackFile.length() == 6);
        }
    }

}
