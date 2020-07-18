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
package com.github.junrar.testUtil;

import com.github.junrar.Archive;
import com.github.junrar.Junrar;
import com.github.junrar.TestCommons;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


public class SimpleTest {

    @Test
    public void testTikaDocs() throws Exception {
        String[] expected = {"testEXCEL.xls", "13824",
            "testHTML.html", "167",
            "testOpenOffice2.odt", "26448",
            "testPDF.pdf", "34824",
            "testPPT.ppt", "16384",
            "testRTF.rtf", "3410",
            "testTXT.txt", "49",
            "testWORD.doc", "19456",
            "testXML.xml", "766"};


        File f = new File(getClass().getResource("test-documents.rar").toURI());
        try (Archive archive = new Archive(new FileVolumeManager(f))) {
            FileHeader fileHeader = archive.nextFileHeader();
            int i = 0;
            while (fileHeader != null) {
                assertThat(fileHeader.getFileNameString()).contains(expected[i++]);
                assertThat(fileHeader.getUnpSize()).isEqualTo(Long.parseLong(expected[i++]));
                fileHeader = archive.nextFileHeader();
            }
        }
    }

    @Test
    public void nullMainHeaderFile_throwsRarException() throws URISyntaxException, IOException {
        final File tempDir = TestCommons.createTempDir();
        final File f = new File(getClass().getResource("test-mainHeaderNull.rar").toURI());

        Throwable thrown = catchThrowable(() -> Junrar.extract(f, tempDir));

        assertThat(thrown).isInstanceOf(RarException.class);
    }

    @Test
    public void nullMainHeaderInputStream_throwsRarException() throws IOException {
        final File tempDir = TestCommons.createTempDir();
        InputStream stream = getClass().getResource("test-mainHeaderNull.rar").openStream();

        Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir));

        assertThat(thrown).isInstanceOf(RarException.class);

        stream.close();
    }
}
