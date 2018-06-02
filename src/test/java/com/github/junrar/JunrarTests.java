package com.github.junrar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.junrar.exception.RarException;
import com.github.junrar.testUtil.JUnRarTestUtil;

public class JunrarTests {
	private static File tempFolder;

	@BeforeClass
	public static void setupFunctionalTests() throws IOException{
		tempFolder = TestCommons.createTempDir();
	}

	@AfterClass
	public static void tearDownFunctionalTests() throws IOException{
		FileUtils.deleteDirectory(tempFolder);
	}

	@Test
	public void extractionFromFileHappyDay() throws RarException, IOException {
		final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);

		Junrar.extract(rarFileOnTemp, tempFolder);

		final File fooDir = new File(tempFolder,"foo");
		assertTrue(fooDir.exists());
		assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir,"bar.txt")));
	}

	@Test
	public void extractionFromStreamHappyDay() throws IOException, RarException {
		final InputStream resourceAsStream = JUnRarTestUtil.class.getResourceAsStream(TestCommons.SIMPLE_RAR_RESOURCE_PATH);
		Junrar.extract(resourceAsStream, tempFolder);

		final File fooDir = new File(tempFolder,"foo");
		assertTrue(fooDir.exists());
		assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir,"bar.txt")));
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
	public void ifIsDirInsteadOfFile_ThrowException() throws RarException, IOException {
		try {
			Junrar.extract(tempFolder, tempFolder);
		}catch (final IllegalArgumentException e) {
			assertEquals("First argument should be a file but was "+tempFolder.getAbsolutePath(), e.getMessage());
			return;
		}
		fail();
	}

	@Test
	public void ifIsFileInsteadOfDir_ThrowException() throws RarException, IOException {
		final File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);
		try {
			Junrar.extract(rarFileOnTemp, rarFileOnTemp);
		}catch (final IllegalArgumentException e) {
			assertEquals("the destination must exist and point to a directory: "+rarFileOnTemp.getAbsolutePath(), e.getMessage());
			return;
		}
		fail();
	}

	private static ContentDescription c(final String name, final long size) {
		return new ContentDescription(name, size);
	}
}
