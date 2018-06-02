package com.github.junrar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public class Junrar {

	private static Log logger = LogFactory.getLog(Junrar.class.getName());

	public static void extract(final String rarPath, final String destinationPath) throws IOException, RarException {
		if (rarPath == null || destinationPath == null) {
			throw new RuntimeException("archive and destination must me set");
		}
		final File arch = new File(rarPath);
		final File dest = new File(destinationPath);
		extract(arch, dest);
	}

	public static void extract(final File rar, final File destinationFolder) throws RarException, IOException {
		validateRarPath(rar);
		validateDestinationPath(destinationFolder);
		extractFileTo(rar, destinationFolder);
	}

	public static void extract(final InputStream resourceAsStream, final File destinationFolder) throws RarException, IOException {
		validateDestinationPath(destinationFolder);
		final Archive arch = createArchiveOrThrowException(logger, resourceAsStream);
		extractArchiveTo(arch, destinationFolder);
	}

	public static List<ContentDescription> getContentsDescription(final File rar) throws RarException, IOException {
		validateRarPath(rar);

		final Archive arch = createArchiveOrThrowException(logger, rar);

		final List<ContentDescription> contents = new ArrayList<ContentDescription>();
		try{
			if (arch.isEncrypted()) {
				logger.warn("archive is encrypted cannot extract");
				return new ArrayList<ContentDescription>();
			}
			for(final FileHeader fileHeader : arch ) {
				contents.add(new ContentDescription(fileHeader.getFileNameString(), fileHeader.getUnpSize()));
			}
		}finally {
			arch.close();
		}
		return contents;
	}

	private static Archive createArchiveOrThrowException(final Log logger, final InputStream rarAsStream) throws RarException, IOException {
		try {
			return new Archive(rarAsStream);
		} catch (final RarException e) {
			logger.error(e);
			throw e;
		} catch (final IOException e1) {
			logger.error(e1);
			throw e1;
		}
	}

	private static Archive createArchiveOrThrowException(final Log logger, final File archive) throws RarException, IOException {
		try {
			return new Archive(archive);
		} catch (final RarException e) {
			logger.error(e);
			throw e;
		} catch (final IOException e1) {
			logger.error(e1);
			throw e1;
		}
	}

	private static void validateDestinationPath(final File destinationFolder) {
		if (destinationFolder == null) {
			throw new RuntimeException("archive and destination must me set");
		}
		if (!destinationFolder.exists() || !destinationFolder.isDirectory()) {
			throw new IllegalArgumentException("the destination must exist and point to a directory: " + destinationFolder);
		}
	}

	private static void validateRarPath(final File rar) {
		if (rar == null) {
			throw new RuntimeException("archive and destination must me set");
		}
		if (!rar.exists()) {
			throw new IllegalArgumentException("the archive does not exit: " + rar);
		}
		if(!rar.isFile()) {
			throw new IllegalArgumentException("First argument should be a file but was "+rar.getAbsolutePath());
		}
	}

	private static void extractFileTo(final File file, final File destination) throws RarException, IOException {
		final Archive archive = createArchiveOrThrowException(logger, file);
		extractArchiveTo(archive, destination);
	}

	private static void extractArchiveTo(final Archive arch, final File destination) throws IOException, RarException {
		if (arch.isEncrypted()) {
			logger.warn("archive is encrypted cannot extract");
			arch.close();
			return;
		}

		try{
			for(final FileHeader fh : arch ) {
				try {
					tryToExtract(logger, destination, arch, fh);
				} catch (final IOException e) {
					logger.error("error extracting the file", e);
					throw e;
				} catch (final RarException e) {
					logger.error("error extraction the file", e);
					throw e;
				}
			}
		}finally {
			arch.close();
		}
	}

	private static void tryToExtract(final Log logger,final File destination, final Archive arch, final FileHeader fileHeader) throws IOException, RarException {
		final String fileNameString = fileHeader.getFileNameString();
		if (fileHeader.isEncrypted()) {
			logger.warn("file is encrypted cannot extract: "+ fileNameString);
			return;
		}
		logger.info("extracting: " + fileNameString);
		if (fileHeader.isDirectory()) {
			createDirectory(fileHeader, destination);
		} else {
			extract(logger, arch, destination, fileHeader);
		}
	}

	private static void extract(final Log logger, final Archive arch, final File destination, final FileHeader fileHeader) throws FileNotFoundException, RarException, IOException {
		final File f = createFile(logger, fileHeader, destination);
		final OutputStream stream = new FileOutputStream(f);
		arch.extractFile(fileHeader, stream);
		stream.close();
	}

	private static File createFile(final Log logger, final FileHeader fh, final File destination) {
		File f = null;
		String name = null;
		if (fh.isFileHeader() && fh.isUnicode()) {
			name = fh.getFileNameW();
		} else {
			name = fh.getFileNameString();
		}
		f = new File(destination, name);
		if (!f.exists()) {
			try {
				f = makeFile(destination, name);
			} catch (final IOException e) {
				logger.error("error creating the new file: " + f.getName(), e);
			}
		}
		return f;
	}

	private static File makeFile(final File destination, final String name) throws IOException {
		final String[] dirs = name.split("\\\\");
		if (dirs == null) {
			return null;
		}
		String path = "";
		final int size = dirs.length;
		if (size == 1) {
			return new File(destination, name);
		} else if (size > 1) {
			for (int i = 0; i < dirs.length - 1; i++) {
				path = path + File.separator + dirs[i];
				new File(destination, path).mkdir();
			}
			path = path + File.separator + dirs[dirs.length - 1];
			final File f = new File(destination, path);
			f.createNewFile();
			return f;
		} else {
			return null;
		}
	}

	private static void createDirectory(final FileHeader fh, final File destination) {
		File f = null;
		if (fh.isDirectory() && fh.isUnicode()) {
			f = new File(destination, fh.getFileNameW());
			if (!f.exists()) {
				makeDirectory(destination, fh.getFileNameW());
			}
		} else if (fh.isDirectory() && !fh.isUnicode()) {
			f = new File(destination, fh.getFileNameString());
			if (!f.exists()) {
				makeDirectory(destination, fh.getFileNameString());
			}
		}
	}

	private static void makeDirectory(final File destination, final String fileName) {
		final String[] dirs = fileName.split("\\\\");
		if (dirs == null) {
			return;
		}
		String path = "";
		for (final String dir : dirs) {
			path = path + File.separator + dir;
			new File(destination, path).mkdir();
		}

	}

}
