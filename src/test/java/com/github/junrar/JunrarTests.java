package com.github.junrar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.junrar.exception.RarException;

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
	public void extractionHappyDay() throws RarException, IOException {
		File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);
		
		Junrar.extract(rarFileOnTemp, tempFolder);
		
		File fooDir = new File(tempFolder,"foo");
		assertTrue(fooDir.exists());		
		assertEquals("baz\n", FileUtils.readFileToString(new File(fooDir,"bar.txt")));
	}
	
	@Test
	public void ifIsDirInsteadOfFile_ThrowException() throws RarException, IOException {
		try {
			Junrar.extract(tempFolder, tempFolder);
		}catch (IllegalArgumentException e) {
			assertEquals("First argument should be a file but was "+tempFolder.getAbsolutePath(), e.getMessage());
			return;
		}
		fail();		
	}
	
	@Test
	public void ifIsFileInsteadOfDir_ThrowException() throws RarException, IOException {
		File rarFileOnTemp = TestCommons.writeTestRarToFolder(tempFolder);
		try {
			Junrar.extract(rarFileOnTemp, rarFileOnTemp);
		}catch (IllegalArgumentException e) {
			assertEquals("Second argument should be a directory but was "+rarFileOnTemp.getAbsolutePath(), e.getMessage());
			return;
		}
		fail();		
	}
}
