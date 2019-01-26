package com.github.junrar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.github.junrar.vfs2.provider.rar.FileSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public class LocalFolderExtractor implements ExtractDestination{

	private File folderDestination;
	private FileSystem fileSystem;
	private static final Log logger = LogFactory.getLog(LocalFolderExtractor.class);

	public LocalFolderExtractor(final File destination, final FileSystem fileSystem) {
		this.folderDestination = destination;
		this.fileSystem = fileSystem;
	}
	
	@Override
	public File createDirectory(final FileHeader fh) {
		String fileName = null;
		if (fh.isDirectory()) {
			if (fh.isUnicode()) {
				fileName = fh.getFileNameW();
			} else {
				fileName = fh.getFileNameString();
			}
		}

		if (fileName == null) {
			return null;
		}

		File f = new File(folderDestination, fileName);
		try {
			if (!f.getCanonicalPath().startsWith(folderDestination.getCanonicalPath())) {
				throw new IllegalStateException("Detected path traversal! Stop extracting.");
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		return f;
	}
	
	@Override
	public File extract(
		final Archive arch, 
		final FileHeader fileHeader
	) throws RarException, IOException {
		final File f = createFile(fileHeader, folderDestination);
		OutputStream stream = null;
		try {
			stream = new FileOutputStream(f);
			arch.extractFile(fileHeader, stream);
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
		return f;
	}
	
	private File createFile(final FileHeader fh, final File destination) throws IOException {
		File f;
		String name;
		if (fh.isFileHeader() && fh.isUnicode()) {
			name = fh.getFileNameW();
		} else {
			name = fh.getFileNameString();
		}
		f = new File(destination, name);
		if (!f.getCanonicalPath().startsWith(destination.getCanonicalPath())) {
			throw new IllegalStateException("Detected path traversal! Stop extracting.");
		}
		if (!f.exists()) {
			try {
				f = makeFile(destination, name);
			} catch (final IOException e) {
				logger.error("error creating the new file: " + f.getName(), e);
			}
		}
		return f;
	}
	
	private File makeFile(final File destination, final String name) throws IOException {
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
				File dir = new File(destination, path);
				fileSystem.mkdir(dir);
			}
			path = path + File.separator + dirs[dirs.length - 1];
			final File f = new File(destination, path);
			fileSystem.createNewFile(f);
			return f;
		} else {
			return null;
		}
	}
	

}
