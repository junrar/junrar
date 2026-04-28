package com.github.junrar.impl.rar5;

import com.github.junrar.impl.HeaderReader;
import com.github.junrar.impl.HeaderReaderFactory;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;
import com.github.junrar.rar5.header.RAR5EndArcHeader;
import com.github.junrar.rar5.header.RAR5FileHeader;
import com.github.junrar.rar5.header.RAR5MainHeader;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.HostSystem;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.RARVersion;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RAR5HeaderReaderTest {

    @Test
    void readHeaders() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/rar5.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers.size()).isEqualTo(5);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            final MarkHeader markHeader = (MarkHeader) headers.get(0);
            assertThat(markHeader.getVersion()).isEqualTo(RARVersion.V5);
            assertThat(markHeader.getFlags()).isEqualTo((short) 0x1A21);
            assertThat(markHeader.getHeaderSize(false)).isEqualTo((short) 263);
            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);
            final RAR5MainHeader mainHeader = (RAR5MainHeader) headers.get(1);
            assertThat(mainHeader.getHeaderSize(false)).isEqualTo((short) 11);
            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file1 = (RAR5FileHeader) headers.get(2);
            assertFileInfo(file1, "FILE1.TXT", 7, 2048490938, (byte) 0, 131072, FileTime.from(
                    ZonedDateTime.of(2010, 11, 2, 23, 27, 28, 0, ZoneOffset.UTC)
                            .toInstant()
            ), HostSystem.win32, false, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file2 = (RAR5FileHeader) headers.get(3);
            assertFileInfo(file2, "FILE2.TXT", 7, 2019541987, (byte) 0, 131072, FileTime.from(
                    ZonedDateTime.of(2010, 11, 2, 23, 27, 34, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.win32, false, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(4)).isInstanceOf(RAR5EndArcHeader.class);
            assertThat(((RAR5EndArcHeader) headers.get(4)).isNextVolume()).isFalse();
        }
    }

    @Test
    void readHeaderSolid() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/solid/rar5-solid.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers.size()).isEqualTo(12);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            final MarkHeader markHeader = (MarkHeader) headers.get(0);
            assertThat(markHeader.getVersion()).isEqualTo(RARVersion.V5);
            assertThat(markHeader.getFlags()).isEqualTo((short) 0x1A21);
            assertThat(markHeader.getHeaderSize(false)).isEqualTo((short) 263);
            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);
            final RAR5MainHeader mainHeader = (RAR5MainHeader) headers.get(1);
            assertThat(mainHeader.getHeaderSize(false)).isEqualTo((short) 11);

            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file1 = (RAR5FileHeader) headers.get(2);
            assertFileInfo(file1, "file1.txt", 6, -500566268, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file2 = (RAR5FileHeader) headers.get(3);
            assertFileInfo(file2, "file2.txt", 6, -922442553, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file3 = (RAR5FileHeader) headers.get(4);
            assertFileInfo(file3, "file3.txt", 6, -803236474, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file4 = (RAR5FileHeader) headers.get(5);
            assertFileInfo(file4, "file4.txt", 6, -1621228735, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file5 = (RAR5FileHeader) headers.get(6);
            assertFileInfo(file5, "file5.txt", 6, -2042285568, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file6 = (RAR5FileHeader) headers.get(7);
            assertFileInfo(file6, "file6.txt", 6, -1385668157, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file7 = (RAR5FileHeader) headers.get(8);
            assertFileInfo(file7, "file7.txt", 6, -1267511166, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file8 = (RAR5FileHeader) headers.get(9);
            assertFileInfo(file8, "file8.txt", 6, 871058509, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file9 = (RAR5FileHeader) headers.get(10);
            assertFileInfo(file9, "file9.txt", 6, 720403724, (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(11)).isInstanceOf(RAR5EndArcHeader.class);
            assertThat(((RAR5EndArcHeader) headers.get(11)).isNextVolume()).isFalse();
        }
    }

    private void assertFileInfo(RAR5FileHeader fileHeader, String fileName, long unpSize, int crc,
                                byte compressionMethod, long dictionarySize, FileTime lastModifiedTime,
                                HostSystem hostOs, boolean solid, Set<PosixFilePermission> filePermissions) {
        assertThat(fileHeader.getFileName()).isEqualTo(fileName);
        assertThat(fileHeader.getUnpSize()).isEqualTo(unpSize);
        assertThat(fileHeader.getFileCRC()).isEqualTo(crc);
        assertThat(fileHeader.getUnpMethod()).isEqualTo(compressionMethod);
        assertThat(fileHeader.getDictionarySize()).isEqualTo(dictionarySize);
        assertThat(fileHeader.getLastModifiedTime()).isEqualTo(lastModifiedTime);
        assertThat(fileHeader.getHostOS()).isEqualTo(hostOs);
        assertThat(fileHeader.isSolid()).isEqualTo(solid);
        assertThat(fileHeader.getFlags()).isEqualTo(solid ? BaseBlock.LHD_SOLID : (short) 0);
        assertThat(fileHeader.getPermissions()).containsExactlyInAnyOrderElementsOf(filePermissions);
    }

}
