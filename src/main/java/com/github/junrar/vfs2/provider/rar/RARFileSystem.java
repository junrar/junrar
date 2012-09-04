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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.apache.commons.vfs2.provider.UriParser;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;


/**
 * A read-only file system for RAR files.
 * 
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public class RARFileSystem extends AbstractFileSystem implements FileSystem {
	private final FileObject parentLayer;

	private Archive archive;
	private Map<String, FileHeader> files = new HashMap<String, FileHeader>();

	public RARFileSystem(final AbstractFileName rootName,
			final FileObject parentLayer,
			final FileSystemOptions fileSystemOptions)
			throws FileSystemException {
		super(rootName, parentLayer, fileSystemOptions);
		this.parentLayer = parentLayer;
	}

	@Override
	public void init() throws FileSystemException {
		super.init();

		try {
			try {
				archive = new Archive(new VFSVolumeManager(parentLayer));
				// Build the index
				List<RARFileObject> strongRef = new ArrayList<RARFileObject>(
						100);
				for (final FileHeader header : archive.getFileHeaders()) {
					AbstractFileName name = (AbstractFileName) getFileSystemManager()
							.resolveName(
									getRootName(),
									UriParser.encode(header.getFileNameString()));

					// Create the file
					RARFileObject fileObj;
					if (header.isDirectory() && getFileFromCache(name) != null) {
						fileObj = (RARFileObject) getFileFromCache(name);
						fileObj.setHeader(header);
						continue;
					}

					fileObj = createRARFileObject(name, header);
					putFileToCache(fileObj);
					strongRef.add(fileObj);
					fileObj.holdObject(strongRef);

					// Make sure all ancestors exist
					RARFileObject parent;
					for (AbstractFileName parentName = (AbstractFileName) name
							.getParent(); parentName != null; fileObj = parent, parentName = (AbstractFileName) parentName
							.getParent()) {
						// Locate the parent
						parent = (RARFileObject) getFileFromCache(parentName);
						if (parent == null) {
							parent = createRARFileObject(parentName, null);
							putFileToCache(parent);
							strongRef.add(parent);
							parent.holdObject(strongRef);
						}

						// Attach child to parent
						parent.attachChild(fileObj.getName());
					}
				}

			} catch (RarException e) {
				throw new FileSystemException(e);
			} catch (IOException e) {
				throw new FileSystemException(e);
			}
		} finally {
			// closeCommunicationLink();
		}
	}

	protected RARFileObject createRARFileObject(final AbstractFileName name,
			final FileHeader header) throws FileSystemException {
		return new RARFileObject(name, archive, header, this);
	}

	@Override
	protected void doCloseCommunicationLink() {
		try {
			archive.close();
		} catch (FileSystemException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the capabilities of this file system.
	 */
	@Override
	protected void addCapabilities(final Collection<Capability> caps) {
		caps.addAll(RARFileProvider.capabilities);
	}

	/**
	 * Creates a file object.
	 */
	@Override
	protected FileObject createFile(final AbstractFileName name)
			throws FileSystemException {
		String path = name.getPath().substring(1);
		if (path.length() == 0) {
			return new RARFileObject(name, archive, null, this);
		} else if (files.containsKey(name.getPath())) {
			return new RARFileObject(name, archive, files.get(name.getPath()),
					this);
		}
		return null;
	}

	/**
	 * will be called after all file-objects closed their streams.
	 */
	protected void notifyAllStreamsClosed() {
		closeCommunicationLink();
	}
}
