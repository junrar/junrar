package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.io.Raw;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * GHSA-frq4-6xmx-hm4g: {@link Archive#getInputStream} extracts on a worker thread and used to
 * discard the worker's failure, so a member that {@link Archive#extractFile} rejects streamed
 * its bytes and then a normal EOF. The stream must instead end in an {@code IOException} carrying
 * the original cause.
 */
class ArchiveStreamFailureTest {

    private static final String FIXTURE = "/com/github/junrar/unicode/rar3-ansi-name.rar";

    /** Offset of the first stored data byte; the header's file CRC is left as it was. */
    private static final int DATA_OFFSET = 60;

    private static final String EMPTY_FIXTURE = "/com/github/junrar/bugfixes/gh-88-empty.rar";

    /** Offset and size of the RAR3 file header of {@code empty.txt}, the fixture's 2nd member. */
    private static final int EMPTY_HEADER = 0x43;

    private static final int EMPTY_HEADER_SIZE = 46;

    @TempDir Path tempDir;

    @Test
    void aMemberFailingItsCrcDoesNotEndInANormalEof() throws Exception {
        try (Archive archive = new Archive(crcMismatch().toFile())) {
            try (InputStream in = archive.getInputStream(archive.getFileHeaders().get(0))) {
                assertThatThrownBy(() -> IOUtils.copy(in, new ByteArrayOutputStream()))
                        .isInstanceOf(IOException.class)
                        .hasCauseInstanceOf(CrcErrorException.class);
            }
        }
    }

    /**
     * Reading exactly the declared size never asks for EOF, so the read reaching that size must
     * already report the failure; close() is not a channel callers are guaranteed to check.
     */
    @Test
    void readingExactlyTheDeclaredSizeReportsTheFailure() throws Exception {
        try (Archive archive = new Archive(crcMismatch().toFile())) {
            FileHeader header = archive.getFileHeaders().get(0);
            int size = (int) header.getFullUnpackSize();
            try (InputStream in = archive.getInputStream(header)) {
                assertThatThrownBy(() -> new DataInputStream(in).readFully(new byte[size]))
                        .isInstanceOf(IOException.class)
                        .hasCauseInstanceOf(CrcErrorException.class);
            }
            try (InputStream in = archive.getInputStream(header)) {
                for (int i = 1; i < size; i++) {
                    assertThat(in.read()).isNotNegative();
                }
                assertThatThrownBy(in::read)
                        .isInstanceOf(IOException.class)
                        .hasCauseInstanceOf(CrcErrorException.class);
            }
        }
    }

    /** The fixture's single member with one stored data byte changed and its file CRC kept. */
    private Path crcMismatch() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource(FIXTURE).toURI()));
        assertThat(bytes[DATA_OFFSET]).isEqualTo((byte) 'u');
        bytes[DATA_OFFSET] = 'U';
        return Files.write(tempDir.resolve("crc-mismatch.rar"), bytes);
    }

    /** An empty member is still extracted and checked, not answered with an empty stream. */
    @Test
    void anEmptyMemberFailingItsCrcDoesNotEndInANormalEof() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource(EMPTY_FIXTURE).toURI()));
        assertThat(new String(bytes, EMPTY_HEADER + 32, 9, StandardCharsets.US_ASCII))
                .isEqualTo("empty.txt");
        // Give empty.txt a nonzero file CRC, then re-seal its header so only the data check fails.
        bytes[EMPTY_HEADER + 16] = 1;
        short crc = RarCRC.computeHeaderCrc16(bytes, EMPTY_HEADER + 2, EMPTY_HEADER_SIZE - 2);
        bytes[EMPTY_HEADER] = (byte) crc;
        bytes[EMPTY_HEADER + 1] = (byte) (crc >>> 8);
        Path file = Files.write(tempDir.resolve("empty-crc-mismatch.rar"), bytes);

        try (Archive archive = new Archive(file.toFile())) {
            FileHeader header = archive.getFileHeaders().get(1);
            assertThat(header.getFullUnpackSize()).isZero();
            try (InputStream in = archive.getInputStream(header)) {
                assertThatThrownBy(in::read)
                        .isInstanceOf(IOException.class)
                        .hasCauseInstanceOf(CrcErrorException.class);
            }
        }
    }

    /**
     * Output may exceed the declared size: a RAR5 filter block is written whole (unrar does the
     * same and tests such an archive "All OK"). Reading must deliver it however it is read; the
     * look-ahead at the declared size used to reject it for byte-by-byte readers only.
     */
    @Test
    void outputPastTheDeclaredSizeIsDeliveredToEveryReader() throws Exception {
        final File file = declaredOneByteShort("/com/github/junrar/rar5filters/rar5-delta.rar");
        try (Archive archive = new Archive(file)) {
            final FileHeader header = archive.getFileHeaders().get(0);
            final ByteArrayOutputStream expected = new ByteArrayOutputStream();
            archive.extractFile(header, expected);
            assertThat((long) expected.size()).isGreaterThan(header.getFullUnpackSize());

            final ByteArrayOutputStream byByte = new ByteArrayOutputStream();
            try (InputStream in = archive.getInputStream(header)) {
                for (int c; (c = in.read()) != -1; ) {
                    byByte.write(c);
                }
            }
            assertThat(byByte.toByteArray()).isEqualTo(expected.toByteArray());
        }
    }

    /** Copy of a RAR5 archive whose first file header declares one byte less than it unpacks. */
    private File declaredOneByteShort(final String resource) throws Exception {
        final Path original = Paths.get(getClass().getResource(resource).toURI());
        final long position;
        try (Archive archive = new Archive(original.toFile())) {
            position = archive.getFileHeaders().get(0).getPositionInFile();
        }
        final byte[] bytes = Files.readAllBytes(original);
        final int start = (int) position;
        final int length =
                Rar5BaseBlock.checkHeaderSize(
                        Arrays.copyOfRange(bytes, start, start + Rar5BaseBlock.FIRST_READ_SIZE));
        final byte[] header = Arrays.copyOfRange(bytes, start, start + length);
        int pos = Rar5BaseBlock.parse(header, false).getFieldsOffset();
        while ((header[pos++] & 0x80) != 0) {} // file flags
        final int sizeStart = pos;
        long size = 0;
        for (int shift = 0; ; shift += 7) {
            size |= (long) (header[pos] & 0x7f) << shift;
            if ((header[pos++] & 0x80) == 0) {
                break;
            }
        }
        size--;
        for (int i = sizeStart; i < pos; i++) { // same width, so the header length is unchanged
            header[i] = (byte) ((size & 0x7f) | (i < pos - 1 ? 0x80 : 0));
            size >>>= 7;
        }
        assertThat(size).isZero();
        Raw.writeIntLittleEndian(
                header, 0, RarCRC.computeHeaderCrc32(header, 4, header.length - 4));
        System.arraycopy(header, 0, bytes, start, length);
        return Files.write(tempDir.resolve("declared-short.rar"), bytes).toFile();
    }
}
