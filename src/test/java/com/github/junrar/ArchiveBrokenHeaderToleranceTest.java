package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A RAR3 header declares its own size, so enumeration can always advance past one whose body it
 * cannot parse -- unrar's {@code ReadHeader15} fixes {@code NextBlockPos} from that declared size
 * before reading a single field, and treats a field-level problem as a warning rather than a stop.
 * junrar must reach the headers behind a broken one just the same, without adopting the mechanism
 * unrar gets it with: {@code RawRead::Get*} returns zero past the end of the buffer, so unrar lists
 * a header too short for its own mandatory fields as a wholly invented entry.
 *
 * <p>A header that cannot be parsed soundly is therefore skipped rather than published: it is
 * recorded in {@link Archive#getHeaderFailures()} and left out of {@link Archive#getHeaders()}
 * entirely, so nothing reaches a caller carrying values that were never on the wire.
 */
class ArchiveBrokenHeaderToleranceTest {

    private static final byte[] MARKER = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00};

    private static final byte FILE_HEADER_TYPE = 0x74;
    private static final byte MAIN_HEADER_TYPE = 0x73;
    private static final int LONG_BLOCK = 0x8000;

    @TempDir Path tempDir;

    /**
     * Control: the fixture below builds archives this parser already accepts, so a failure in any
     * other test here is the parser's and not the fixture's. Passes before and after the change.
     */
    @Test
    void theFixtureBuildsAnArchiveThatListsCleanly() throws Exception {
        byte[] rar = archive(fileHeader("first.txt"), fileHeader("second.txt"));

        try (Archive archive = open(rar)) {
            assertThat(fileNames(archive)).containsExactly("first.txt", "second.txt");
            assertThat(archive.getHeaderFailures()).isEmpty();
        }
    }

    @Test
    void aHeaderTooShortForItsMandatoryFieldsDoesNotHideTheHeadersBehindIt() throws Exception {
        byte[] rar = archive(fileHeader("first.txt"), shortBodyHeader(), fileHeader("third.txt"));

        try (Archive archive = open(rar)) {
            assertThat(fileNames(archive)).containsExactly("first.txt", "third.txt");
            assertThat(archive.getHeaderFailures()).hasSize(1);
        }
    }

    /** The degraded block is not a file entry, so nothing invents a name or a size for it. */
    @Test
    void aDegradedHeaderIsNotReportedAsAFileEntry() throws Exception {
        byte[] rar = archive(shortBodyHeader(), fileHeader("only.txt"));

        try (Archive archive = open(rar)) {
            assertThat(archive.getFileHeaders()).hasSize(1);
            assertThat(archive.getFileHeaders().get(0).getFileName()).isEqualTo("only.txt");
        }
    }

    /**
     * A name size the header declares as zero is no name at all, and a nameless entry is not an
     * entry: an empty name passes the platform's own validity check, so it would otherwise reach
     * {@link Archive#getFileHeaders()} and {@code new File(destDir, "")} would resolve to the
     * destination directory itself. The sibling case -- a declared name the body cannot hold, so
     * the clamp takes it down to nothing -- is pinned by {@code FileHeaderShortBodyTest}.
     */
    @Test
    void aHeaderWithNoRoomForItsNameDoesNotBecomeAnEmptyNamedEntry() throws Exception {
        byte[] rar = archive(fileHeader("first.txt"), namelessHeader(), fileHeader("third.txt"));

        try (Archive archive = open(rar)) {
            assertThat(fileNames(archive)).containsExactly("first.txt", "third.txt");
            assertThat(archive.getHeaderFailures()).hasSize(1);
        }
    }

    /** A name the platform cannot represent is not a usable file entry either. */
    @Test
    void aHeaderWhoseNameIsNotAValidFilenameDegradesRatherThanEndingTheArchive() throws Exception {
        byte[] embeddedNul = {'b', 'a', 'd', 0, 'n', 'a', 'm', 'e'};
        byte[] rar =
                archive(fileHeader("first.txt"), fileHeader(embeddedNul), fileHeader("third.txt"));

        try (Archive archive = open(rar)) {
            assertThat(fileNames(archive)).containsExactly("first.txt", "third.txt");
            assertThat(archive.getHeaderFailures()).hasSize(1);
        }
    }

    /** unrar's {@code default:} arm advances by the declared size; an unknown type is not a stop. */
    @Test
    void anUnknownBlockTypeIsSkippedByItsDeclaredSize() throws Exception {
        byte[] rar = archive(fileHeader("first.txt"), unknownTypeBlock(), fileHeader("third.txt"));

        try (Archive archive = open(rar)) {
            assertThat(fileNames(archive)).containsExactly("first.txt", "third.txt");
        }
    }

    /** The archive ends inside the last header's body; the entries before it still list. */
    @Test
    void aBodyTruncatedByTheEndOfTheFileDoesNotHideTheHeadersBeforeIt() throws Exception {
        byte[] complete = archive(fileHeader("first.txt"), fileHeader("second.txt"));
        byte[] truncated = Arrays.copyOf(complete, complete.length - 6);

        try (Archive archive = open(truncated)) {
            assertThat(fileNames(archive)).contains("first.txt");
        }
    }

    private Archive open(byte[] rar) throws Exception {
        Path file = Files.createTempFile(tempDir, "tolerance", ".rar");
        Files.write(file, rar);
        return new Archive(file.toFile());
    }

    private static List<String> fileNames(Archive archive) {
        return archive.getFileHeaders().stream()
                .map(FileHeader::getFileName)
                .collect(Collectors.toList());
    }

    /** Marker, a main header, then the given blocks back to back. */
    private static byte[] archive(byte[]... blocks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(withHeadCrc(baseBlock(MAIN_HEADER_TYPE, 0, 13), new byte[6]));
        for (byte[] block : blocks) {
            out.write(block);
        }
        return out.toByteArray();
    }

    private static byte[] fileHeader(String name) throws IOException {
        return fileHeader(name.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** A well-formed, dataless FILE header carrying {@code nameBytes} as its name. */
    private static byte[] fileHeader(byte[] nameBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize (the BlockHeader's four bytes)
        writeFixedFields(body, nameBytes.length);
        body.write(nameBytes);
        return block(FILE_HEADER_TYPE, body.toByteArray());
    }

    /** A FILE header whose declared size leaves a body far too short for the fixed fields. */
    private static byte[] shortBodyHeader() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize
        body.write(new byte[6]); // six bytes where twenty-one are mandatory
        return block(FILE_HEADER_TYPE, body.toByteArray());
    }

    /** A FILE header holding every fixed field and declaring a name of length zero. */
    private static byte[] namelessHeader() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize
        writeFixedFields(body, 0);
        return block(FILE_HEADER_TYPE, body.toByteArray());
    }

    /** A block of a type junrar does not know, declaring a size it does fill. */
    private static byte[] unknownTypeBlock() throws IOException {
        return block((byte) 0x6f, new byte[8]);
    }

    /** Frames {@code body} in a base block of the right declared size, with a matching CRC. */
    private static byte[] block(byte type, byte[] body) throws IOException {
        return withHeadCrc(baseBlock(type, LONG_BLOCK, 7 + body.length), body);
    }

    private static byte[] baseBlock(byte type, int flags, int headerSize) {
        byte[] base = new byte[7];
        base[2] = type;
        base[3] = (byte) (flags & 0xff);
        base[4] = (byte) ((flags >>> 8) & 0xff);
        base[5] = (byte) (headerSize & 0xff);
        base[6] = (byte) ((headerSize >>> 8) & 0xff);
        return base;
    }

    /** unrar covers header bytes [2, headSize) with the 16-bit header CRC. */
    private static byte[] withHeadCrc(byte[] base, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base);
        out.write(body);
        byte[] header = out.toByteArray();
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    /** The RAR3 FILE header fields no entry can be missing, up to and including fileAttr. */
    private static void writeFixedFields(ByteArrayOutputStream out, int declaredNameSize) {
        out.write(new byte[4], 0, 4); // unpSize
        out.write(0); // hostOS
        out.write(new byte[4], 0, 4); // fileCRC
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0x4A); // fileTime
        out.write(20); // unpVersion
        out.write(0x30); // unpMethod, store
        out.write(declaredNameSize & 0xff);
        out.write((declaredNameSize >>> 8) & 0xff);
        out.write(new byte[4], 0, 4); // fileAttr
    }
}
