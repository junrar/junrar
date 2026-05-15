package com.github.junrar.impl.rar4;

import com.github.junrar.impl.HeaderReader;
import com.github.junrar.impl.HeaderReaderFactory;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.EndArcHeader;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.HostSystem;
import com.github.junrar.rarfile.MainHeader;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.RARVersion;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RAR4HeaderReaderTest {

    @Test
    @DefaultTimeZone("UTC")
    void readHeaders() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/rar4.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers.size()).isEqualTo(5);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            final MarkHeader markHeader = (MarkHeader) headers.get(0);
            assertThat(markHeader.getVersion()).isEqualTo(RARVersion.V4);
            assertThat(markHeader.getFlags()).isEqualTo((short) 0x1A21);
            assertThat(markHeader.getHeaderSize(false)).isEqualTo((short) 7);
            assertThat(headers.get(1)).isInstanceOf(MainHeader.class);
            final MainHeader mainHeader = (MainHeader) headers.get(1);
            assertThat(mainHeader.getHeaderSize(false)).isEqualTo((short) 13);
            assertThat(headers.get(2)).isInstanceOf(FileHeader.class);
            final FileHeader file1 = (FileHeader) headers.get(2);
            assertFileInfo(file1, "FILE1.TXT", 7, "7A197DBA", (byte) 48, FileTime.from(
                    ZonedDateTime.of(2010, 11, 3, 0, 27, 28, 0, ZoneOffset.UTC)
                            .toInstant()
            ), HostSystem.win32, false);

            assertThat(headers.get(3)).isInstanceOf(FileHeader.class);
            final FileHeader file2 = (FileHeader) headers.get(3);
            assertFileInfo(file2, "FILE2.TXT", 7, "785FC3E3", (byte) 48, FileTime.from(
                    ZonedDateTime.of(2010, 11, 3, 0, 27, 34, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.win32, false);

            assertThat(headers.get(4)).isInstanceOf(EndArcHeader.class);
            assertThat(((EndArcHeader) headers.get(4)).getVolumeNumber()).isZero();
        }
    }

    @Test
    @DefaultTimeZone("UTC")
    void readHeaderSolid() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/solid/rar4-solid.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers.size()).isEqualTo(12);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            final MarkHeader markHeader = (MarkHeader) headers.get(0);
            assertThat(markHeader.getVersion()).isEqualTo(RARVersion.V4);
            assertThat(markHeader.getFlags()).isEqualTo((short) 0x1A21);
            assertThat(markHeader.getHeaderSize(false)).isEqualTo((short) 7);
            assertThat(headers.get(1)).isInstanceOf(MainHeader.class);
            final MainHeader mainHeader = (MainHeader) headers.get(1);
            assertThat(mainHeader.getHeaderSize(false)).isEqualTo((short) 13);

            assertThat(headers.get(2)).isInstanceOf(FileHeader.class);
            final FileHeader file1 = (FileHeader) headers.get(2);
            assertFileInfo(file1, "file1.txt", 6, "E229F704", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false);

            assertThat(headers.get(3)).isInstanceOf(FileHeader.class);
            final FileHeader file2 = (FileHeader) headers.get(3);
            assertFileInfo(file2, "file2.txt", 6, "C904A4C7", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file3 = (FileHeader) headers.get(4);
            assertFileInfo(file3, "file3.txt", 6, "D01F9586", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file4 = (FileHeader) headers.get(5);
            assertFileInfo(file4, "file4.txt", 6, "9F5E0341", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file5 = (FileHeader) headers.get(6);
            assertFileInfo(file5, "file5.txt", 6, "86453200", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file6 = (FileHeader) headers.get(7);
            assertFileInfo(file6, "file6.txt", 6, "AD6861C3", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file7 = (FileHeader) headers.get(8);
            assertFileInfo(file7, "file7.txt", 6, "B4735082", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file8 = (FileHeader) headers.get(9);
            assertFileInfo(file8, "file8.txt", 6, "33EB4C4D", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            final FileHeader file9 = (FileHeader) headers.get(10);
            assertFileInfo(file9, "file9.txt", 6, "2AF07D0C", (byte) 51, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 21, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true);

            assertThat(headers.get(11)).isInstanceOf(EndArcHeader.class);
            assertThat(((EndArcHeader) headers.get(11)).getVolumeNumber()).isZero();
        }
    }

    private void assertFileInfo(FileHeader fileHeader, String fileName, long unpSize, String crc, byte compressionMethod,
                                FileTime lastModifiedTime, HostSystem hostOs, boolean solid) {
        assertThat(fileHeader.getFileName()).isEqualTo(fileName);
        assertThat(fileHeader.getUnpSize()).isEqualTo(unpSize);
        assertThat(fileHeader.getFileCRC()).isEqualTo(crc);
        assertThat(fileHeader.getUnpMethod()).isEqualTo(compressionMethod);
        assertThat(fileHeader.getLastModifiedTime()).isEqualTo(lastModifiedTime);
        assertThat(fileHeader.getHostOS()).isEqualTo(hostOs);
        assertThat(fileHeader.isSolid()).isEqualTo(solid);
    }

}
