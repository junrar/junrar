package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5MainHeader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * M3.2 (issue #23) archive-level tests for the RAR5 read loop, on the public {@code Archive}
 * path since the M3.11 gate lift. Verifies the loop parses a real RAR5 fixture into the
 * shared headers list and that hostile fixtures reject in bounded time (S1 analog).
 */
class ArchiveRar5FrameworkTest {

    @TempDir Path tempDir;

    private static final byte[] MARKER50 = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00};

    private File writeTemp(String name, byte[] bytes) throws Exception {
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private byte[] fixtureBytes(String name) throws Exception {
        return Files.readAllBytes(Paths.get(getClass().getResource(name).toURI()));
    }

    /**
     * M3.3 (issue #24) parses MAIN/FILE/SERVICE/CRYPT/ENDARC into typed subclasses ({@link
     * com.github.junrar.rarfile.rar5.Rar5MainHeader}, the unified {@link FileHeader}), so a
     * single {@code Rar5BlockType} cast no longer works for every header in the list -- dispatch
     * on the concrete Java type instead (manual &sect;7d: "keep Rar5BlockType via a per-instance
     * check").
     */
    private static List<String> types(Archive archive) {
        return archive.getHeaders().stream()
                .map(
                        b ->
                                b instanceof Rar5BaseBlock
                                        ? ((Rar5BaseBlock) b).getRar5Type().name()
                                        : b.getHeaderType().name())
                .collect(Collectors.toList());
    }

    @Test
    void rar5FixtureIsParsedIntoTheHeadersList() throws Exception {
        try (Archive archive = new Archive(writeTemp("rar5.rar", fixtureBytes("rar5.rar")))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR50);
            assertThat(types(archive))
                    .containsExactly("MAIN", "FileHeader", "FileHeader", "ENDARC");
            // A clean fixture: every header CRC verifies.
            assertThat(archive.getHeaders()).noneMatch(BaseBlock::isBrokenHeader);
            // Block file positions (decoded from the 130-byte fixture).
            assertThat(archive.getHeaders().stream().map(BaseBlock::getPositionInFile))
                    .containsExactly(8L, 24L, 73L, 122L);

            // Archive-level listing asserts (manual &sect;8 ground truth).
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files)
                    .extracting(FileHeader::getFileName)
                    .containsExactly("FILE1.TXT", "FILE2.TXT");
            assertThat(files).extracting(FileHeader::getFullUnpackSize).containsExactly(7L, 7L);
            final FileHeader file1 = files.get(0);
            assertThat(file1.getFileCRC()).isEqualTo(0x7a197dba);
            assertThat(file1.getLastModifiedTime()).isNotNull();
        }
    }

    /**
     * M3.3 (issue #24) real-fixture row (manual &sect;7e), cross-checked against the unrar 7.23
     * oracle: {@code unrar l -v src/test/resources/com/github/junrar/solid/rar5-solid.rar}
     * reports {@code Details: RAR 5, solid} and 9 entries {@code file1.txt}..{@code file9.txt},
     * 6 bytes each.
     */
    @Test
    void solidFixtureMainHeaderIsSolidAndListsNineFiles() throws Exception {
        try (Archive archive =
                new Archive(writeTemp("rar5-solid.rar", fixtureBytes("solid/rar5-solid.rar")))) {
            final Rar5MainHeader main = (Rar5MainHeader) archive.getHeaders().get(0);
            assertThat(main.isSolid()).isTrue();

            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files)
                    .extracting(FileHeader::getFileName)
                    .containsExactly(
                            "file1.txt",
                            "file2.txt",
                            "file3.txt",
                            "file4.txt",
                            "file5.txt",
                            "file6.txt",
                            "file7.txt",
                            "file8.txt",
                            "file9.txt");
            assertThat(files).extracting(FileHeader::getFullUnpackSize).containsOnly(6L);
        }
    }

    /**
     * M3.3 (issue #24) real-fixture row (manual &sect;7e), cross-checked against the unrar 7.23
     * oracle: {@code unrar l -v src/test/resources/com/github/junrar/password/rar5-password-junrar.rar}
     * lists {@code file1.txt} marked {@code *} (per-file encrypted data; the archive headers
     * themselves are not encrypted, so this parses without a password).
     */
    @Test
    void passwordFixtureFileEntryCarriesRar5CryptFacts() throws Exception {
        try (Archive archive =
                new Archive(
                        writeTemp(
                                "rar5-password.rar",
                                fixtureBytes("password/rar5-password-junrar.rar")))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(1);
            final FileHeader fh = files.get(0);
            assertThat(fh.getFileName()).isEqualTo("file1.txt");
            assertThat(fh.isEncrypted()).isTrue();
            assertThat(fh.getSalt16()).hasSize(16);
            assertThat(fh.getLg2Count()).isGreaterThan(0);
        }
    }

    @Test
    void unencryptedBadCrcMarksBrokenButArchiveOpens() throws Exception {
        final byte[] bytes = fixtureBytes("rar5.rar");
        bytes[8] ^=
                0xFF; // corrupt the first block's stored CRC32 (offset 8 = after the 8-byte marker)
        try (Archive archive = new Archive(writeTemp("rar5-badcrc.rar", bytes))) {
            assertThat(archive.getHeaders()).isNotEmpty();
            assertThat(archive.getHeaders().get(0).isBrokenHeader()).isTrue();
        }
    }

    @Test
    void truncatedMidHeaderThrows() throws Exception {
        // Post-M3.11: with the V5 gate lifted, a truncated RAR5 archive surfaces the
        // parse-time CorruptHeaderException on the public path (the pre-lift gate-pinning
        // twin of this test asserted UnsupportedRarV5Exception; retired with the gate).
        final byte[] truncated =
                Arrays.copyOf(fixtureBytes("rar5.rar"), 40); // cuts into the 2nd block's body
        assertThat(
                        catchThrowable(
                                () -> new Archive(writeTemp("rar5-trunc.rar", truncated)).close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    @Timeout(15)
    void oversizedHeaderRejectsInBoundedTimeWithoutAllocation() throws Exception {
        // Marker + a block whose 3-byte size vint declares 0x1FFFFF -> HeaderSize > 2 MB. The
        // bound rejects before any tail allocation, so this is a CorruptHeaderException (not the
        // BadRarArchiveException safelyAllocate would raise), and it returns immediately (S1).
        final byte[] bytes = new byte[MARKER50.length + 7];
        System.arraycopy(MARKER50, 0, bytes, 0, MARKER50.length);
        // CRC(4) then size vint 0xFF 0xFF 0x7F.
        bytes[MARKER50.length + 4] = (byte) 0xFF;
        bytes[MARKER50.length + 5] = (byte) 0xFF;
        bytes[MARKER50.length + 6] = (byte) 0x7F;
        assertThat(catchThrowable(() -> new Archive(writeTemp("rar5-huge.rar", bytes)).close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    @Timeout(15)
    void negativeDataSizeVintRejectsInsteadOfSeekingBackward() throws Exception {
        // A CRC-valid block with HFL_DATA whose DataSize vint decodes to a negative long
        // (bit 63 set: the 10-byte 0x80*9,0x01 encoding = Long.MIN_VALUE). The seek target
        // position + consumed + DataSize is then <= the current position; unrar rejects
        // NextBlockPos <= CurBlockPos as a broken header (arcread.cpp). junrar must reject with
        // CorruptHeaderException rather than seek backward -- which RandomAccessFile.seek turns
        // into a raw IOException ("Negative seek offset") -- or spin. Valid CRC isolates this
        // from the CRC record-vs-fatal split.
        final byte[] content = new byte[13]; // HeadSize(1) + Type(1) + Flags(1) + DataSize(10)
        int p = 0;
        content[p++] = 0x0C; // HeadSize vint = 12 (bytes following this field)
        content[p++] = 0x70; // Type = unknown wire value (skippable)
        content[p++] = 0x06; // Flags = HFL_DATA | HFL_SKIPIFUNKNOWN
        for (int i = 0; i < 9; i++) {
            content[p++] = (byte) 0x80; // 9 continuation bytes, payload 0
        }
        content[p] = 0x01; // final byte: bit 63 -> DataSize = Long.MIN_VALUE
        final byte[] header = new byte[4 + content.length];
        System.arraycopy(content, 0, header, 4, content.length);
        final int crc = RarCRC.computeHeaderCrc32(header, 4, header.length - 4);
        header[0] = (byte) crc;
        header[1] = (byte) (crc >>> 8);
        header[2] = (byte) (crc >>> 16);
        header[3] = (byte) (crc >>> 24);
        final byte[] bytes = new byte[MARKER50.length + header.length];
        System.arraycopy(MARKER50, 0, bytes, 0, MARKER50.length);
        System.arraycopy(header, 0, bytes, MARKER50.length, header.length);
        assertThat(catchThrowable(() -> new Archive(writeTemp("rar5-negdata.rar", bytes)).close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }
}
