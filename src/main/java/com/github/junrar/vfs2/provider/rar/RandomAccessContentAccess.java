package com.github.junrar.vfs2.provider.rar;

import java.io.IOException;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.RandomAccessContent;
import org.apache.commons.vfs2.util.RandomAccessMode;

import com.github.junrar.io.IReadOnlyAccess;


/**
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 * 
 */
public class RandomAccessContentAccess implements IReadOnlyAccess {
	private final RandomAccessContent rac;

	public RandomAccessContentAccess(RandomAccessContent rac) {
		this.rac = rac;
	}

	public RandomAccessContentAccess(FileObject file)
			throws FileSystemException {
		this(file.getContent().getRandomAccessContent(RandomAccessMode.READ));
	}

	public long getPosition() throws IOException {
		return rac.getFilePointer();
	}

	public void setPosition(long pos) throws IOException {
		rac.seek(pos);
	}

	public int read() throws IOException {
		return rac.readByte();
	}

	public int read(byte[] buffer, int off, int count) throws IOException {
		return rac.getInputStream().read(buffer, off, count);
	}

	public int readFully(byte[] buffer, int count) throws IOException {
		return rac.getInputStream().read(buffer, 0, count);
	}

	public void close() throws IOException {
		rac.close();
	}
}
