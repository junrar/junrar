package com.github.junrar.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.junrar.Junrar;
import com.github.junrar.TestCommons;
import com.github.junrar.exception.RarException;

public class ExtractionTests {
	
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
}
