/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.junrar.vfs2.provider.rar;

import java.io.InputStream;
import java.util.HashSet;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;


/**
 * A file in a RAR file system.
 * 
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public class RARFileObject extends AbstractFileObject implements FileObject {
	/**
	 * The TFile.
	 */
	protected Archive archive;
	protected FileHeader header;
	@SuppressWarnings("unused")
	private final RARFileSystem fs;

	private final HashSet<String> children = new HashSet<String>();

	protected RARFileObject(AbstractFileName name, Archive archive,
			FileHeader header, RARFileSystem fs) throws FileSystemException {
		super(name, fs);
		this.fs = fs;
		this.archive = archive;
		this.header = header;
		archive.getMainHeader().isFirstVolume();
	}

	@Override
	public boolean doIsWriteable() throws FileSystemException {
		return false;
	}

	@Override
	protected FileType doGetType() {
		if (header == null || header.isDirectory()) {
			return FileType.FOLDER;
		} else {
			return FileType.FILE;
		}
	}

	@Override
	protected String[] doListChildren() {
		try {
			if (!getType().hasChildren()) {
				return null;
			}
		} catch (FileSystemException e) {
			// should not happen as the type has already been cached.
			throw new RuntimeException(e);
		}
		return children.toArray(new String[children.size()]);
	}

	@Override
	protected long doGetContentSize() {
		return header.getFullUnpackSize();
	}

	@Override
	protected long doGetLastModifiedTime() throws Exception {
		return header.getMTime().getTime();
	}

	@Override
	protected InputStream doGetInputStream() throws Exception {
		if (!getType().hasContent()) {
			throw new FileSystemException("vfs.provider/read-not-file.error",
					getName());
		}
		return archive.getInputStream(header);
	}

	/**
	 * Attaches a child.
	 * 
	 * @param childName
	 *            The name of the child.
	 */
	public void attachChild(FileName childName) {
		children.add(childName.getBaseName());
	}

	/**
	 * @param header
	 */
	public void setHeader(FileHeader header) {
		this.header = header;
	}
}
