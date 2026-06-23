package com.github.junrar.impl.rar5;

import com.github.junrar.impl.HeaderReader;
import com.github.junrar.impl.HeaderReaderFactory;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.io.SeekableReadOnlyInputStream;
import com.github.junrar.rar5.Rar5Constants;
import com.github.junrar.rar5.header.RAR5EndArcHeader;
import com.github.junrar.rar5.header.RAR5FileHeader;
import com.github.junrar.rar5.header.RAR5MainHeader;
import com.github.junrar.rar5.header.extra.Rar5FileCryptRecord;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.HostSystem;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.RARVersion;
import com.github.junrar.rarfile.UnrarHeadertype;
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
            assertFileInfo(file1, "FILE1.TXT", 7, "7A197DBA", (byte) 0, 131072, FileTime.from(
                    ZonedDateTime.of(2010, 11, 2, 23, 27, 28, 0, ZoneOffset.UTC)
                            .toInstant()
            ), HostSystem.win32, false, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file2 = (RAR5FileHeader) headers.get(3);
            assertFileInfo(file2, "FILE2.TXT", 7, "785FC3E3", (byte) 0, 131072, FileTime.from(
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
            assertFileInfo(file1, "file1.txt", 6, "E229F704", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file2 = (RAR5FileHeader) headers.get(3);
            assertFileInfo(file2, "file2.txt", 6, "C904A4C7", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file3 = (RAR5FileHeader) headers.get(4);
            assertFileInfo(file3, "file3.txt", 6, "D01F9586", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file4 = (RAR5FileHeader) headers.get(5);
            assertFileInfo(file4, "file4.txt", 6, "9F5E0341", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file5 = (RAR5FileHeader) headers.get(6);
            assertFileInfo(file5, "file5.txt", 6, "86453200", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file6 = (RAR5FileHeader) headers.get(7);
            assertFileInfo(file6, "file6.txt", 6, "AD6861C3", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file7 = (RAR5FileHeader) headers.get(8);
            assertFileInfo(file7, "file7.txt", 6, "B4735082", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file8 = (RAR5FileHeader) headers.get(9);
            assertFileInfo(file8, "file8.txt", 6, "33EB4C4D", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            final RAR5FileHeader file9 = (RAR5FileHeader) headers.get(10);
            assertFileInfo(file9, "file9.txt", 6, "2AF07D0C", (byte) 3, 1048576L, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, true, new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));

            assertThat(headers.get(11)).isInstanceOf(RAR5EndArcHeader.class);
            assertThat(((RAR5EndArcHeader) headers.get(11)).isNextVolume()).isFalse();
        }
    }

    @Test
    void readHeaderRar7Compression() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/rar5-v1.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers).hasSize(5);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);

            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader bigBin = (RAR5FileHeader) headers.get(2);
            assertThat(bigBin.getUnpSize()).isEqualTo(5368709120L);
            assertThat(bigBin.getFileCRC()).isEqualTo("193838C3");
            assertThat(bigBin.getUnpMethod()).isEqualTo((byte) 1);
            assertThat(bigBin.getUnpVersion()).isEqualTo((byte) Rar5Constants.VER_PACK7);
            assertThat(bigBin.getDictionarySize()).isEqualTo(5368709120L);
            assertThat(bigBin.getHostOS()).isEqualTo(HostSystem.unix);
            assertThat(bigBin.isSolid()).isFalse();

            // Quick Open service block
            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader quickOpen = (RAR5FileHeader) headers.get(3);
            assertThat(quickOpen.getFileName()).isEqualTo("QO");
            assertThat(quickOpen.getUnpSize()).isEqualTo(63L);
            assertThat(quickOpen.getPackSize()).isEqualTo(63L);
            assertThat(quickOpen.getHeaderType()).isEqualTo(UnrarHeadertype.NewSubHeader);

            assertThat(headers.get(4)).isInstanceOf(RAR5EndArcHeader.class);
        }
    }

    @Test
    void readHeadersBlake2() throws Exception {
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/rar5-blake2.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers).hasSize(5);

            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            final MarkHeader markHeader = (MarkHeader) headers.get(0);
            assertThat(markHeader.getVersion()).isEqualTo(RARVersion.V5);
            assertThat(markHeader.getFlags()).isEqualTo((short) 0x1A21);
            assertThat(markHeader.getHeaderSize(false)).isEqualTo((short) 263);

            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);
            final RAR5MainHeader mainHeader = (RAR5MainHeader) headers.get(1);
            assertThat(mainHeader.getHeaderSize(false)).isEqualTo((short) 10);

            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file1 = (RAR5FileHeader) headers.get(2);
            assertFileInfo(file1, "FILE1.TXT", 7,
                    "fc30504f38dd39d30fa68ea2a702b8fd79756a4330b28c7d36e44c7139827721",
                    (byte) 0, 131072, FileTime.from(
                    ZonedDateTime.of(2010, 11, 2, 23, 27, 28, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ)));
            assertThat(file1.hasFileCrc32()).isFalse();

            assertThat(headers.get(3)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file2 = (RAR5FileHeader) headers.get(3);
            assertFileInfo(file2, "FILE2.TXT", 7,
                    "09b37c45def427a4a6c84b9cb899825426caa0e74b7109252101d26938166a00",
                    (byte) 0, 131072, FileTime.from(
                    ZonedDateTime.of(2010, 11, 2, 23, 27, 34, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ)));
            assertThat(file2.hasFileCrc32()).isFalse();

            assertThat(headers.get(4)).isInstanceOf(RAR5EndArcHeader.class);
            assertThat(((RAR5EndArcHeader) headers.get(4)).isNextVolume()).isFalse();
        }
    }

    @Test
    void readHeadersFileContentEncrypted() throws Exception {
        // Headers are plaintext; the file header carries an FHEXTRA_CRYPT record.
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/password/rar5-password-junrar.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), null);

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers).hasSize(4);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);
            assertThat(((RAR5MainHeader) headers.get(1)).isEncrypted()).isFalse();

            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file1 = (RAR5FileHeader) headers.get(2);
            // Values verified against `unrar lta -pjunrar` (this fixture lists the
            // checksum as "CRC32 MAC", i.e. the HashMAC-transformed CRC32).
            assertFileInfo(file1, "file1.txt", 6, "EBD3E719", (byte) 3, 131072, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));
            assertThat(file1.getPackSize()).isEqualTo(32);
            assertThat(file1.getUnpVersion()).isEqualTo((byte) 50);
            assertThat(file1.isEncrypted()).isTrue();

            final Rar5FileCryptRecord crypt = file1.getCryptRecord();
            assertThat(crypt).isNotNull();
            assertThat(crypt.getKdfCount()).isEqualTo(15);
            assertThat(crypt.getSalt()).hasSize(16);
            assertThat(crypt.getIv()).hasSize(16);
            assertThat(crypt.hasPswCheck()).isTrue();
            assertThat(crypt.hasHashMac()).isTrue();

            assertThat(headers.get(3)).isInstanceOf(RAR5EndArcHeader.class);
        }
    }

    @Test
    void readHeadersHeaderEncrypted() throws Exception {
        // The HEAD_CRYPT block decrypts every subsequent header with the password.
        Path f = Paths.get("").resolve("src/test/resources/com/github/junrar/password/rar5-encrypted-junrar.rar");
        try (InputStream in = Files.newInputStream(f)) {
            final SeekableReadOnlyByteChannel channel = new SeekableReadOnlyInputStream(in);
            final HeaderReader headerReader = HeaderReaderFactory.create(channel);
            headerReader.readHeaders(channel, Files.size(f), "junrar");

            final List<BaseBlock> headers = headerReader.getHeaders();
            assertThat(headers).hasSize(4);
            assertThat(headers.get(0)).isInstanceOf(MarkHeader.class);
            assertThat(headers.get(1)).isInstanceOf(RAR5MainHeader.class);
            assertThat(((RAR5MainHeader) headers.get(1)).isEncrypted()).isTrue();

            assertThat(headers.get(2)).isInstanceOf(RAR5FileHeader.class);
            final RAR5FileHeader file1 = (RAR5FileHeader) headers.get(2);
            // Values verified against `unrar lta -pjunrar` (this fixture lists a plain
            // "CRC32" — no HashMAC — unlike the file-content-only fixture).
            assertFileInfo(file1, "file1.txt", 6, "E229F704", (byte) 3, 131072, FileTime.from(
                    ZonedDateTime.of(2020, 7, 19, 13, 23, 47, 0, ZoneOffset.UTC).toInstant()
            ), HostSystem.unix, false, new HashSet<>(Arrays.asList(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)));
            assertThat(file1.getPackSize()).isEqualTo(32);
            assertThat(file1.getUnpVersion()).isEqualTo((byte) 50);
            assertThat(file1.isEncrypted()).isTrue();

            final Rar5FileCryptRecord crypt = file1.getCryptRecord();
            assertThat(crypt).isNotNull();
            assertThat(crypt.getKdfCount()).isEqualTo(15);
            assertThat(crypt.getSalt()).hasSize(16);
            assertThat(crypt.getIv()).hasSize(16);
            assertThat(crypt.hasHashMac()).isFalse();

            assertThat(headers.get(3)).isInstanceOf(RAR5EndArcHeader.class);
        }
    }

    private void assertFileInfo(RAR5FileHeader fileHeader, String fileName, long unpSize, String crc,
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
