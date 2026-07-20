package com.github.junrar;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.2 (issue #23) archive-level tests for the RAR5 read loop through the pre-gate harness
 * ({@link Archive#testOnlyOpenSuppressingV5Gate}). Verifies the loop parses a real RAR5
 * fixture into the shared headers list, that the harness suppresses ONLY the V5 gate (a RAR3
 * archive behaves identically to the public path), and that hostile fixtures reject in
 * bounded time (S1 analog).
 */
class ArchiveRar5FrameworkTest {

    @TempDir
    Path tempDir;

    private static final byte[] MARKER50 = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00};

    private File writeTemp(String name, byte[] bytes) throws Exception {
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private byte[] fixtureBytes(String name) throws Exception {
        return Files.readAllBytes(Paths.get(getClass().getResource(name).toURI()));
    }

    private static List<Rar5BlockType> types(Archive archive) {
        return archive.getHeaders().stream()
            .map(b -> ((Rar5BaseBlock) b).getRar5Type())
            .collect(Collectors.toList());
    }

    @Test
    void rar5FixtureIsParsedIntoTheHeadersList() throws Exception {
        try (Archive archive = Archive.testOnlyOpenSuppressingV5Gate(writeTemp("rar5.rar", fixtureBytes("rar5.rar")))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR50);
            assertThat(types(archive)).containsExactly(
                Rar5BlockType.MAIN, Rar5BlockType.FILE, Rar5BlockType.FILE, Rar5BlockType.ENDARC);
            // A clean fixture: every header CRC verifies.
            assertThat(archive.getHeaders()).noneMatch(BaseBlock::isBrokenHeader);
            // Block file positions (decoded from the 130-byte fixture).
            assertThat(archive.getHeaders().stream().map(BaseBlock::getPositionInFile))
                .containsExactly(8L, 24L, 73L, 122L);
        }
    }

    @Test
    void publicPathStillHitsTheV5Gate() throws Exception {
        final File f = writeTemp("rar5-public.rar", fixtureBytes("rar5.rar"));
        assertThat(catchThrowable(() -> new Archive(f).close()))
            .isExactlyInstanceOf(UnsupportedRarV5Exception.class);
    }

    @Test
    void harnessSuppressesOnlyV5GateRar3IsIdentical() throws Exception {
        final byte[] rar3 = fixtureBytes("test.rar");
        final List<String> viaPublic;
        try (Archive archive = new Archive(writeTemp("t-public.rar", rar3))) {
            viaPublic = archive.getFileHeaders().stream()
                .map(fh -> fh.getFileNameString()).collect(Collectors.toList());
        }
        final List<String> viaHarness;
        try (Archive archive = Archive.testOnlyOpenSuppressingV5Gate(writeTemp("t-harness.rar", rar3))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR15);
            viaHarness = archive.getFileHeaders().stream()
                .map(fh -> fh.getFileNameString()).collect(Collectors.toList());
        }
        assertThat(viaHarness).isNotEmpty().isEqualTo(viaPublic);
    }

    @Test
    void unencryptedBadCrcMarksBrokenButArchiveOpens() throws Exception {
        final byte[] bytes = fixtureBytes("rar5.rar");
        bytes[8] ^= 0xFF; // corrupt the first block's stored CRC32 (offset 8 = after the 8-byte marker)
        try (Archive archive = Archive.testOnlyOpenSuppressingV5Gate(writeTemp("rar5-badcrc.rar", bytes))) {
            assertThat(archive.getHeaders()).isNotEmpty();
            assertThat(archive.getHeaders().get(0).isBrokenHeader()).isTrue();
        }
    }

    @Test
    void truncatedRar5OnPublicPathStillReportsV5NotCorrupt() throws Exception {
        // Regression guard (issue #23): the V5 gate must fire before any parsing, so a
        // truncated/corrupt RAR5 archive surfaces UnsupportedRarV5Exception on the public path
        // -- exactly as pre-M3.2 -- rather than a parse-time CorruptHeaderException. A
        // parse-then-gate order changed the recorded exception for real (truncated) corpus
        // RAR5 archives.
        final byte[] truncated = Arrays.copyOf(fixtureBytes("rar5.rar"), 40);
        assertThat(catchThrowable(() -> new Archive(writeTemp("rar5-trunc-public.rar", truncated)).close()))
            .isExactlyInstanceOf(UnsupportedRarV5Exception.class);
    }

    @Test
    void truncatedMidHeaderThrows() throws Exception {
        final byte[] truncated = Arrays.copyOf(fixtureBytes("rar5.rar"), 40); // cuts into the 2nd block's body
        assertThat(catchThrowable(() ->
            Archive.testOnlyOpenSuppressingV5Gate(writeTemp("rar5-trunc.rar", truncated)).close()))
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
        assertThat(catchThrowable(() ->
            Archive.testOnlyOpenSuppressingV5Gate(writeTemp("rar5-huge.rar", bytes)).close()))
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }
}
