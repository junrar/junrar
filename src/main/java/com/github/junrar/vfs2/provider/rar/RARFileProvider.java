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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractLayeredFileProvider;
import org.apache.commons.vfs2.provider.FileProvider;
import org.apache.commons.vfs2.provider.LayeredFileName;

/**
 * A file system provider for RAR files. Provides read-only file systems.
 * 
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
public class RARFileProvider extends AbstractLayeredFileProvider implements
		FileProvider {
	/** The list of capabilities this provider supports */
	protected static final Collection<Capability> capabilities = Collections
			.unmodifiableCollection(Arrays.asList(new Capability[] {
					Capability.GET_LAST_MODIFIED,
					Capability.GET_TYPE,
					Capability.LIST_CHILDREN,
					Capability.READ_CONTENT,
					Capability.URI,
					Capability.COMPRESS,
					Capability.VIRTUAL
				}));

	public RARFileProvider() {
		super();
	}

	/**
	 * Creates a layered file system. This method is called if the file system
	 * is not cached.
	 * 
	 * @param scheme
	 *            The URI scheme.
	 * @param file
	 *            The file to create the file system on top of.
	 * @return The file system.
	 */
	@Override
	protected FileSystem doCreateFileSystem(final String scheme,
			final FileObject file, final FileSystemOptions fileSystemOptions)
			throws FileSystemException {
		final AbstractFileName rootName = new LayeredFileName(scheme,
				file.getName(), FileName.ROOT_PATH, FileType.FOLDER);
		return new RARFileSystem(rootName, file, fileSystemOptions);
	}

	public Collection<Capability> getCapabilities() {
		return capabilities;
	}
}
