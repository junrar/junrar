/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.junrar.bugfixes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class GitHub81InfiniteLoopsTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testLoops() throws Exception {
        for (int i = 1; i <= 3; i++) {
            File f = new File(getClass().getResource("gh81-infinite-loop" + i + ".rar").toURI());
            try (Archive archive = new Archive(f)) {
                for (FileHeader header : archive.getFileHeaders()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (InputStream is = archive.getInputStream(header)) {
                        IOUtils.copy(is, bos);
                    }
                }
            }
        }
    }
}
