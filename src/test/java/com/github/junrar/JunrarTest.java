package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.volume.FileVolumeManager;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
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

        Junrar.extract(rarFileOnTemp, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        String barFileContent = FileUtils.readFileToString(new File(fooDir, "bar.txt"), Charset.defaultCharset());

        assertThat(fooDir).exists();
        assertThat(barFileContent).isEqualTo("baz\n");
    }

    @Test
    public void extractionFromFileHappyDayAndValidateExtractedFiles() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        final List<File> extractedFiles = Junrar.extract(rarFileOnTemp, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        File barFile = new File(fooDir, "bar.txt");

        assertThat(extractedFiles.get(0)).isEqualTo(barFile);
        assertThat(extractedFiles.get(1)).isEqualTo(fooDir);
    }


    @Test
    public void extractionFromFileWithVolumeManagerAndExtractorHappyDay() throws RarException, IOException {
        final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

        Junrar.extract(new LocalFolderExtractor(tempFolder), new FileVolumeManager(rarFileOnTemp));

        final File fooDir = new File(tempFolder, "foo");
        String barFileContent = FileUtils.readFileToString(new File(fooDir, "bar.txt"), Charset.defaultCharset());

        assertThat(fooDir).exists();
        assertThat(barFileContent).isEqualTo("baz\n");
    }

    @Test
    public void extractionFromStreamHappyDay() throws IOException, RarException {
        final InputStream resourceAsStream = JunrarTest.class.getResourceAsStream(TestCommons.SIMPLE_RAR_RESOURCE_PATH);
        Junrar.extract(resourceAsStream, tempFolder);

        final File fooDir = new File(tempFolder, "foo");
        String barFileContent = FileUtils.readFileToString(new File(fooDir, "bar.txt"), Charset.defaultCharset());

        assertThat(fooDir).exists();
        assertThat(barFileContent).isEqualTo("baz\n");
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
