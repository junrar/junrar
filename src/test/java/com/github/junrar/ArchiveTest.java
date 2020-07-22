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
package com.github.junrar;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


public class ArchiveTest {

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


        File f = new File(getClass().getResource("tika-documents.rar").toURI());
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

    @Nested
    class Solid {
        @Test
        public void givenSolidRar4File_whenExtractingInOrder_thenExtractionIsDone() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("solid/rar4-solid.rar")) {
                try (Archive archive = new Archive(is)) {
                    assertThat(archive.getMainHeader().isSolid());

                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(9);

                    for (int i = 0; i < fileHeaders.size(); i++) {
                        int index = i + 1;
                        FileHeader fileHeader = fileHeaders.get(i);
                        assertThat(fileHeader.getFileNameString()).isEqualTo("file" + index + ".txt");

                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            archive.extractFile(fileHeaders.get(i), baos);
                            assertThat(baos.toString()).isEqualTo("file" + index + "\n");
                        }
                    }
                }
            }
        }

        @Test
        public void givenSolidRar4File_whenExtractingOutOfOrder_thenExceptionIsThrown() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("solid/rar4-solid.rar")) {
                try (Archive archive = new Archive(is)) {
                    assertThat(archive.getMainHeader().isSolid());

                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(9);

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        Throwable thrown = catchThrowable(() -> archive.extractFile(fileHeaders.get(4), baos));

                        assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
                    }
                }
            }
        }

        @Test
        public void givenSolidRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("solid/rar5-solid.rar")) {
                Throwable thrown = catchThrowable(() -> new Archive(is));

                assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
            }
        }
    }
}
