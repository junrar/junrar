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
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static java.util.Calendar.FEBRUARY;
import static java.util.Calendar.MARCH;
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
        try (Archive archive = new Archive(f)) {
            assertThat(archive.isPasswordProtected()).isFalse();
            assertThat(archive.isEncrypted()).isFalse();

            for (int i = 0; i < expected.length; i += 2) {
                FileHeader fileHeader = archive.nextFileHeader();
                assertThat(fileHeader).isNotNull();
                assertThat(fileHeader.getFileName()).contains(expected[i]);
                assertThat(fileHeader.getUnpSize()).isEqualTo(Long.parseLong(expected[i + 1]));
                assertThat(fileHeader.getFullUnpackSize()).isEqualTo(fileHeader.getUnpSize());
            }

            assertThat(archive.nextFileHeader()).isNull();
        }
    }

    @ValueSource(strings = {
        "audio/BoatModernEnglish-audio-text-unpack30.rar",  // special audio/text compression enabled, RAR 2.9
        "audio/BoatModernEnglish-audio-text-unpack20.rar",  // special audio/text compression enabled, RAR 2.0
        "audio/BoatModernEnglish-regular-unpack30.rar",     // special audio/text compression disabled, RAR 2.9
        "audio/BoatModernEnglish-regular-unpack20.rar",     // special audio/text compression disabled, RAR 2.0
        "audio/BoatModernEnglish-regular-unpack15-dos.rar", // special audio/text compression disabled, RAR 1.5 DOS
        "audio/BoatModernEnglish-regular-unpack15-win.rar"  // special audio/text compression disabled, RAR 1.5 Windows
    })
    @ParameterizedTest
    public void testAudioDecompression(String fileName) throws Exception {
        File f = new File(getClass().getResource(fileName).toURI());
        try (Archive archive = new Archive(f)) {
            assertThat(archive.isPasswordProtected()).isFalse();
            assertThat(archive.isEncrypted()).isFalse();

            FileHeader fileHeader = archive.nextFileHeader();
            boolean isDos = fileName.endsWith("-dos.rar");
            if (isDos) {
                assertThat(fileHeader.getHostOS()).isEqualTo(HostSystem.msdos);
                assertThat(fileHeader.getFileName()).isEqualTo("BOATMO~1.WAV");
            } else {
                assertThat(fileHeader.getHostOS()).isEqualTo(HostSystem.win32);
                assertThat(fileHeader.getFileName()).isEqualTo("BoatModernEnglish.wav");
            }
            assertThat(fileHeader.getUnpSize()).isEqualTo(56464);
            assertThat(fileHeader.getFullUnpackSize()).isEqualTo(fileHeader.getUnpSize());
            byte[] audioData;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeader, baos);
                audioData = baos.toByteArray();
            }
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (InputStream is = getClass().getResource("audio/BoatModernEnglish.wav").openStream()) {
                    IOUtils.copy(is, baos);
                }
                byte[] expectedAudioData = baos.toByteArray();
                assertThat(audioData).containsExactly(expectedAudioData);
            }

            fileHeader = archive.nextFileHeader();
            if (isDos) {
                assertThat(fileHeader.getHostOS()).isEqualTo(HostSystem.msdos);
                assertThat(fileHeader.getFileName()).isEqualTo("LICENSE.TXT");
            } else {
                assertThat(fileHeader.getHostOS()).isEqualTo(HostSystem.win32);
                assertThat(fileHeader.getFileName()).isEqualTo("LICENSE.txt");
            }
            assertThat(fileHeader.getUnpSize()).isEqualTo(107);
            assertThat(fileHeader.getFullUnpackSize()).isEqualTo(fileHeader.getUnpSize());
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeader, baos);
                assertThat(baos.toString()).isEqualTo("UofG Language Modules, CC BY-SA 4.0 "
                    + "<https://creativecommons.org/licenses/by-sa/4.0>, via Wikimedia Commons");
            }

            assertThat(archive.nextFileHeader()).isNull();
        }
    }

    /*
     The file is shifted by 1-hour because it was created in Europe/Amsterdam.
     Times in a RAR file are stored in the MS-DOS style, so the fields are always the same but the resulting timestamp is not.

     Original timestamps:
     MTime: 2022-02-23T09:24:19.191543300Z
     CTime: 2022-02-23T09:34:59.759754700Z
     ATime: 2022-03-02T17:45:18.694091100Z

     Ensure the fields remain constant across timezones.
     */
    @Nested
    class ExtendedTimeTest {
        @Test
        @DefaultTimeZone("America/Los_Angeles")
        public void testArchiveExtTimes_LosAngeles() throws Exception {
            assertThat(TimeZone.getDefault()).isEqualTo(TimeZone.getTimeZone("America/Los_Angeles"));
            testArchiveExtTimes();
        }

        @Test
        @DefaultTimeZone("America/Sao_Paulo")
        public void testArchiveExtTimes_SaoPaulo() throws Exception {
            assertThat(TimeZone.getDefault()).isEqualTo(TimeZone.getTimeZone("America/Sao_Paulo"));
            testArchiveExtTimes();
        }

        @Test
        @DefaultTimeZone("Europe/Amsterdam")
        public void testArchiveExtTimes_Amsterdam() throws Exception {
            assertThat(TimeZone.getDefault()).isEqualTo(TimeZone.getTimeZone("Europe/Amsterdam"));
            testArchiveExtTimes();
        }

        @Test
        @DefaultTimeZone("Asia/Kolkata")
        public void testArchiveExtTimes_Kolkata() throws Exception {
            assertThat(TimeZone.getDefault()).isEqualTo(TimeZone.getTimeZone("Asia/Kolkata"));
            testArchiveExtTimes();
        }

        private void testArchiveExtTimes() throws IOException, RarException {
            try (InputStream is = getClass().getResourceAsStream("rar4-ext_time.rar")) {
                try (Archive archive = new Archive(is)) {
                    assertThat(archive.getMainHeader().isSolid()).isFalse();

                    FileHeader fileHeader = archive.getFileHeaders().stream()
                        .filter(FileHeader::isFileHeader)
                        .findFirst()
                        .orElse(null);
                    assertThat(fileHeader).isNotNull();
                    assertThat(fileHeader.getFileName()).isEqualTo("files\\test\\short-text.txt");
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        archive.extractFile(fileHeader, baos);
                        assertThat(baos.toString()).isEqualTo("Short text for example");
                    }
                    assertThat(fileHeader.getMTime()).isEqualTo(toDate(2022, FEBRUARY, 23, 10, 24, 19, 191));
                    assertThat(fileHeader.getLastModifiedTime()).isEqualTo(toFileTime(fileHeader.getMTime(), 543300));
                    assertThat(fileHeader.getCTime()).isEqualTo(toDate(2022, FEBRUARY, 23, 10, 34, 59, 759));
                    assertThat(fileHeader.getCreationTime()).isEqualTo(toFileTime(fileHeader.getCTime(), 754700));
                    assertThat(fileHeader.getATime()).isEqualTo(toDate(2022, MARCH, 2, 18, 45, 18, 694));
                    assertThat(fileHeader.getLastAccessTime()).isEqualTo(toFileTime(fileHeader.getATime(), 91100));
                }
            }
        }

        private Date toDate(int year, int month, int day, int hour, int minute, int second, int millis) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minute, second);
            calendar.set(Calendar.MILLISECOND, millis);
            return calendar.getTime();
        }

        private FileTime toFileTime(Date date, long nanos) {
            return FileTime.from(Instant.ofEpochMilli(date.getTime()).plus(nanos, ChronoUnit.NANOS));
        }
    }

    @Nested
    class Solid {
        @Test
        public void givenSolidRar4File_whenExtractingInOrder_thenExtractionIsDone() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("solid/rar4-solid.rar")) {
                try (Archive archive = new Archive(is)) {
                    assertThat(archive.getMainHeader().isSolid()).isTrue();

                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(9);

                    for (int i = 0; i < fileHeaders.size(); i++) {
                        int index = i + 1;
                        FileHeader fileHeader = fileHeaders.get(i);
                        assertThat(fileHeader.getFileName()).isEqualTo("file" + index + ".txt");

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
                    assertThat(archive.getMainHeader().isSolid()).isTrue();

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

    /**
     * This class will test archives that are encrypted or password protected.
     * <p>
     * Encrypted archives are password protected, but also encrypt the list of files,
     * so you need the password to list the content.
     * <p>
     * You can list the content of a password protected archive, but you cannot extract
     * without the password.
     */
    @Nested
    class PasswordProtected {
        @Test
        public void givenEncryptedRar4File_whenCreatingArchiveWithPassword_thenItCanExtractContent() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("password/rar4-encrypted-junrar.rar")) {
                try (Archive archive = new Archive(is, "junrar")) {
                    assertThat(archive.isEncrypted()).isTrue();
                    assertThat(archive.isPasswordProtected()).isTrue();
                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(1);

                    FileHeader fileHeader = fileHeaders.get(0);
                    assertThat(fileHeader.isEncrypted()).isTrue();
                    assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        archive.extractFile(fileHeader, baos);
                        assertThat(baos.toString()).isEqualTo("file1\n");
                    }
                }
            }
        }

        @Test
        public void givenPasswordProtectedRar4File_whenCreatingArchive_thenItCanListContent() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("password/rar4-password-junrar.rar")) {
                try (Archive archive = new Archive(is)) {
                    assertThat(archive.isEncrypted()).isFalse();
                    assertThat(archive.isPasswordProtected()).isTrue();
                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(1);

                    FileHeader fileHeader = fileHeaders.get(0);
                    assertThat(fileHeader.isEncrypted()).isTrue();
                    assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");
                }
            }
        }

        @Test
        public void givenPasswordProtectedRar4File_whenCreatingArchiveWithPassword_thenItCanExtractContent() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("password/rar4-password-junrar.rar")) {
                try (Archive archive = new Archive(is, "junrar")) {
                    assertThat(archive.isEncrypted()).isFalse();
                    assertThat(archive.isPasswordProtected()).isTrue();
                    List<FileHeader> fileHeaders = archive.getFileHeaders();
                    assertThat(fileHeaders).hasSize(1);

                    FileHeader fileHeader = fileHeaders.get(0);
                    assertThat(fileHeader.isEncrypted()).isTrue();
                    assertThat(fileHeader.getFileName()).isEqualTo("file1.txt");

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        archive.extractFile(fileHeader, baos);
                        assertThat(baos.toString()).isEqualTo("file1\n");
                    }
                }
            }
        }

        @Test
        public void givenEncryptedRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("password/rar5-encrypted-junrar.rar")) {
                Throwable thrown = catchThrowable(() -> new Archive(is));

                assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
            }
        }

        @Test
        public void givenPasswordProtectedRar5File_whenCreatingArchive_thenUnsupportedRarV5ExceptionIsThrown() throws Exception {
            try (InputStream is = getClass().getResourceAsStream("password/rar5-password-junrar.rar")) {
                Throwable thrown = catchThrowable(() -> new Archive(is));

                assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
            }
        }
    }

    @Nested
    class Unicode {
        @Test
        public void unicodeFileNamesAreDecodedProperly() throws Exception {
            File f = new File(getClass().getResource("unicode.rar").getPath());
            try (Archive archive = new Archive(f)) {
                List<String> names = archive.getFileHeaders().stream()
                    .map(FileHeader::getFileName)
                    .collect(Collectors.toList());

                assertThat(names).containsExactlyInAnyOrder("新建文本文档.txt", "ウニコド.txt");
            }
        }
    }
}
