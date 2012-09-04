/*
 * This file is part of seedbox <github.com/seedbox>.
 *
 * seedbox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * seedbox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with seedbox.  If not, see <http://www.gnu.org/licenses/>.
 */
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

	/**
	 * @param rac
	 */
	public RandomAccessContentAccess(RandomAccessContent rac) {
		this.rac = rac;
	}

	/**
	 * @param rac
	 * @throws FileSystemException
	 */
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
