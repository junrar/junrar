package com.github.junrar;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
		String[] fileNames = unpackedDir.list();
		List<ContentDescription> contentDescriptions = new ArrayList<ContentDescription>();
		for (String fileName : fileNames) {
			File file = new File(unpackedDir, fileName);
			contentDescriptions.add(new ContentDescription(file.getName(), file.length()));
		}

		final ContentDescription[] expected = { new ContentDescription("testEXCEL.xls", 13824),
				new ContentDescription("testHTML.html", 167), 
				new ContentDescription("testOpenOffice2.odt", 26448),
				new ContentDescription("testPDF.pdf", 34824), 
				new ContentDescription("testPPT.ppt", 16384),
				new ContentDescription("testRTF.rtf", 3410), 
				new ContentDescription("testTXT.txt", 49),
				new ContentDescription("testWORD.doc", 19456), 
				new ContentDescription("testXML.xml", 766) };

		assertArrayEquals(expected, contentDescriptions.toArray());
	}
}
