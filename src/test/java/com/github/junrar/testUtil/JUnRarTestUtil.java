/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: EW
 * Creation date: 26.09.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 * 
 * 
 * the unrar licence applies to all junrar source and binary distributions 
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;" 
 */
package com.github.junrar.testUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.RarException.RarExceptionType;
import com.github.junrar.io.ReadOnlyAccessFile;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.testutil.ExtractArchive;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class JUnRarTestUtil
{
	private static Log logger = LogFactory.getLog(JUnRarTestUtil.class.getName());
	/**
	 * @param args
	 */
	private static List<String> successfulFiles = new ArrayList<String>();
	
	private static List<String> errorFiles = new ArrayList<String>();
	
	private static List<String> unsupportedFiles = new ArrayList<String>();
	
	private static  File tempFolder;
	private static boolean errorsOcurred;
	
	public static void main(String[] args)
	{
		if(args.length!=1){
			System.out.println("JUnRar TestUtil\n usage: java -jar unrar-test.jar <directory with test files>");
			return;
		}else{
			File file = new File(args[0]);
			if(file.exists()){
				if(file.isDirectory()){
					recurseDirectory(file);
				}else{
					testFile(file);
				}
			}
		}
		printSummary();

	}
	
	@BeforeClass
	public static void setupFunctionalTests() throws IOException{
		tempFolder = File.createTempFile("FOOOOOOO", "BAAAARRRR");
		tempFolder.delete();
		tempFolder.mkdir();
	}
	
	@Test
	public void unrarFile_FileContentsShouldMatchExpected() throws IOException{
		InputStream resourceAsStream = JUnRarTestUtil.class.getResourceAsStream("test.rar");
		File testRar = new File(tempFolder,"test.rar");
        FileUtils.writeByteArrayToFile(testRar, IOUtils.toByteArray(resourceAsStream));
        String[] args = new String[]{tempFolder.getAbsolutePath()};
        JUnRarTestUtil.errorsOcurred = false;
        ExtractArchive.main(new String[]{testRar.getAbsolutePath(), tempFolder.getAbsolutePath() });
		JUnRarTestUtil.main(args);
		File fooDir = new File(tempFolder,"foo");
		assertTrue(fooDir.exists());
		File barFile = new File(fooDir,"bar.txt");
		assertTrue(fooDir.exists());
		String barTxtContents = FileUtils.readFileToString(barFile);
		assertEquals("baz\n", barTxtContents);
		if(errorsOcurred){
			fail("Test failed, see output for details...");
		}
	}
	
	@AfterClass
	public static void tearDownFunctionalTests() throws IOException{
		FileUtils.deleteDirectory(tempFolder);
	}

	private static void printSummary()
	{
		System.out.println("\n\n\nSuccessfully tested archives:\n");
		for(String sf:successfulFiles){
			System.out.println(sf);
		}
		System.out.println("");
		System.out.println("Unsupported archives:\n");
		for(String uf: unsupportedFiles){
			System.out.println(uf);
		}
		System.out.println("");
		System.out.println("Failed archives:");
		for(String ff: errorFiles){
			System.out.println(ff);
		}
		System.out.println("");
		System.out.println("\n\n\nSummary\n");
		System.out.println("tested:\t\t"+(successfulFiles.size()+unsupportedFiles.size()+errorFiles.size()));
		System.out.println("successful:\t"+successfulFiles.size());
		System.out.println("unsupported:\t"+unsupportedFiles.size());
		System.out.println("failed:\t\t"+errorFiles.size());
	}

	private static void testFile(File file)
	{
		if(file==null || !file.exists()){
			String errorMessage = "error file " +file + " does not exist";
			error(errorMessage);
			return;
		}
		logger.info(">>>>>> testing archive: "+file);
		String s = file.toString();
		s = s.substring(s.length()-3);
		if(s.equalsIgnoreCase("rar")){
			System.out.println(file.toString());
			ReadOnlyAccessFile readFile = null;
			Archive arc = null;
			try {
//				readFile = new ReadOnlyAccessFile(file);
				try {
					arc = new Archive(file);
				} catch (RarException e) {
					error("archive consturctor error",e);
					errorFiles.add(file.toString());
					return;
				}
				if(arc != null){
					if(arc.isEncrypted()){
						logger.warn("archive is encrypted cannot extreact");
						unsupportedFiles.add(file.toString());
						return;
					}
					List<FileHeader> files = arc.getFileHeaders();
					for(FileHeader fh : files)
					{
						if(fh.isEncrypted()){
							logger.warn("file is encrypted cannot extract: "+fh.getFileNameString());
							unsupportedFiles.add(file.toString());
							arc.close();
							return;
						}
						logger.info("extracting file: "+fh.getFileNameString());
						if(fh.isFileHeader() && fh.isUnicode()){
							logger.info("unicode name: "+fh.getFileNameW());
						}
						logger.info("start: "+new Date());
						
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						
						
						try {
							arc.extractFile(fh, os);
						} catch (RarException e) {
							if(e.getType().equals(RarExceptionType.notImplementedYet)){
								error("error extracting unsupported file: "+fh.getFileNameString(),e);
								unsupportedFiles.add(file.toString());
								return;
							}
							error("error extracting file: "+fh.getFileNameString(),e);
							errorFiles.add(file.toString());
							arc.close();
							return;
						}finally{
							os.close();
						}
						
						logger.info("end: "+new Date());
					}
				}
				arc.close();
				logger.info("successfully tested archive: "+file);
				successfulFiles.add(file.toString());
			} catch (Exception e) {
				error("file: "+file+ " extraction error - does the file exist?"+e );
				errorFiles.add(file.toString());
			} finally{
				if(readFile!=null){
					try {
						readFile.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
		}
	}

	private static void error(String errorMessage, Exception e) {
		JUnRarTestUtil.errorsOcurred = true;
		logger.error(errorMessage,e);
	}

	private static void error(String errorMessage) {
		JUnRarTestUtil.errorsOcurred = true;
		logger.error(errorMessage);
	}
	
	private static void recurseDirectory(File file)
	{
		if(file==null||!file.exists()){
			return;
		}
		if(file.isDirectory()){
			File[] files = file.listFiles();
			if(files == null){
				return;
			}
			for(File f: files){
				recurseDirectory(f);
				f = null;
			}
		}else{
			testFile(file);
			file=null;
		}
		
	}

	

}
