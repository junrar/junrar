package com.github.junrar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.github.junrar.testUtil.JUnRarTestUtil;

public class TestCommons {

	public static File createTempDir() throws IOException {
		final File tmp = File.createTempFile("FOOOOOOO", "BAAAARRRR");
		tmp.delete();
		tmp.mkdir();
		return tmp;
	}

	public static File writeTestRarToFolder(File tmp) throws IOException {
		InputStream resourceAsStream = JUnRarTestUtil.class.getResourceAsStream("test.rar");
		File testRar = new File(tmp,"test.rar");
	    FileUtils.writeByteArrayToFile(testRar, IOUtils.toByteArray(resourceAsStream));
		return testRar;
	}

}
