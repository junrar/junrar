package com.github.junrar;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.exception.UnsupportedRarVersionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.1 signature dispatch, SFX-stub scan, and hostile-scan bound. The V5 extraction gate
 * stays in force: a RAR5 marker is <em>recognized</em> (format detected, SFX stub skipped)
 * but still throws {@link UnsupportedRarV5Exception} during header parsing.
 */
class ArchiveSignatureDispatchTest {

    @TempDir
    Path tempDir;

    private static final byte[] MARKER15 = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00};
    private static final byte[] MARKER50 = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00};

    private File writeTemp(String name, byte[] bytes) throws Exception {
        Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private byte[] fixtureBytes(String name) throws Exception {
        return Files.readAllBytes(Paths.get(getClass().getResource(name).toURI()));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** An arbitrary SFX stub with no embedded RAR signature (no 0x52 byte). */
    private static byte[] sfxStub(int size) {
        byte[] stub = new byte[size];
        java.util.Arrays.fill(stub, (byte) 0xAA);
        stub[0] = 0x4D; // 'M'
        stub[1] = 0x5A; // 'Z'
        return stub;
    }

    @Test
    void version00DetectsRar15() throws Exception {
        try (Archive archive = new Archive(writeTemp("v00.rar", MARKER15))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR15);
        }
    }

    @Test
    void version01HitsV5GateButIsRecognized() throws Exception {
        Throwable thrown = catchThrowable(() -> new Archive(writeTemp("v01.rar", MARKER50)).close());
        assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0x02, 0x03, 0x04})
    void futureVersionThrowsUnsupportedRarVersion(int version) throws Exception {
        byte[] marker = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, (byte) version, 0x00};
        Throwable thrown = catchThrowable(() -> new Archive(writeTemp("future.rar", marker)).close());
        assertThat(thrown).isExactlyInstanceOf(UnsupportedRarVersionException.class);
    }

    @Test
    void sfxStubBeforeRar4MarkerOpensAndLists() throws Exception {
        byte[] sfx = concat(sfxStub(2048), fixtureBytes("test.rar"));
        try (Archive archive = new Archive(writeTemp("sfx-rar4.rar", sfx))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR15);
            assertThat(archive.getFileHeaders()).isNotEmpty();
        }
    }

    @Test
    void sfxStubBeforeRar5MarkerIsRecognizedButHitsV5Gate() throws Exception {
        byte[] sfx = concat(sfxStub(2048), fixtureBytes("rar5.rar"));
        Throwable thrown = catchThrowable(() -> new Archive(writeTemp("sfx-rar5.rar", sfx)).close());
        assertThat(thrown).isExactlyInstanceOf(UnsupportedRarV5Exception.class);
    }

    @Test
    @Timeout(15)
    void signatureFreeGarbageBeyondBoundIsRejected() throws Exception {
        byte[] garbage = new byte[MAXSFXSIZE_PLUS];
        java.util.Arrays.fill(garbage, (byte) 0x01); // no 0x52, so no signature anywhere
        Throwable thrown = catchThrowable(() -> new Archive(writeTemp("garbage.rar", garbage)).close());
        assertThat(thrown).isExactlyInstanceOf(BadRarArchiveException.class);
    }

    /** MAXSFXSIZE (4 MB) + slack, to prove the scan is bounded and terminates. */
    private static final int MAXSFXSIZE_PLUS = 0x400000 + 4096;
}
