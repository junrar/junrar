package com.github.junrar;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


public class AbnormalFilesTest {

    private File tempDir;

    @BeforeEach
    public void createTempDir() throws IOException {
        tempDir = TestCommons.createTempDir();
    }

    @AfterEach
    public void cleanupTempDir() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFile(String filePath, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> Junrar.extract(file, tempDir));

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFromStream(String filePath, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir));

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractFileByArchive(String filePath, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> {
            Archive archive = new Archive(file);
            while (true) {
                FileHeader fileHeader = archive.nextFileHeader();
                if (fileHeader == null) {
                    break;
                }
                archive.extractFile(fileHeader, NullOutputStream.INSTANCE);
            }
        });

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideFilesAndExpectedExceptionType")
    public void extractStreamByArchive(String filePath, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> {
                Archive archive = new Archive(stream);
                while (true) {
                    FileHeader fileHeader = archive.nextFileHeader();
                    if (fileHeader == null) {
                        break;
                    }
                    archive.extractFile(fileHeader, NullOutputStream.INSTANCE);
                }
            });

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    private static Stream<Arguments> provideFilesAndExpectedExceptionType() {
        return Stream.of(
            Arguments.of("abnormal/corrupt-header.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/mainHeaderNull.rar", BadRarArchiveException.class),
            Arguments.of("abnormal/loop.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop1.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop2.rar", CorruptHeaderException.class),
            Arguments.of("abnormal/loop3.rar", CorruptHeaderException.class),
            // P0.7 / issue #12: the archive OPENS and lists fine (record + continue,
            // unencrypted FILE header -- FileHeader.isBrokenHeader() is true, see
            // HeaderCrcVerificationTest), but *extracting* the broken-header entry is
            // junrar's one conscious, narrower-scoped divergence from unrar's own
            // "warn and let the data CRC decide" tolerance: it throws
            // CorruptHeaderException instead.
            Arguments.of("abnormal/bad-header-crc.rar", CorruptHeaderException.class),
            // Issue #38 item 2 (P0.7 Finding A): an LHD_COMMENT FILE header whose
            // narrow (processed-only) CRC matches but whose full-buffer CRC (covering
            // the appended comment tail) does not -- see HeaderCrcVerificationTest for
            // the direct open-time isBrokenHeader()/hasComment() assertions.
            Arguments.of("abnormal/lhd-comment-dual-crc.rar", CorruptHeaderException.class)
        );
    }

    // P0.7 / issue #12: encrypted-headers archive with a corrupted FILE header CRC --
    // fatal at open (unrar: decrypt succeeds, CRC still fails to match -> stop), so this
    // needs the correct password to even reach the CRC check and can't share the
    // no-password method source above. Same four-surface shape (Junrar.extract from File
    // and InputStream; manual Archive loop from File and InputStream).

    @ParameterizedTest
    @MethodSource("provideEncryptedFilesAndExpectedExceptionType")
    public void extractEncryptedFile(String filePath, String password, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> Junrar.extract(file, tempDir, password));

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideEncryptedFilesAndExpectedExceptionType")
    public void extractEncryptedFromStream(String filePath, String password, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> Junrar.extract(stream, tempDir, password));

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    @ParameterizedTest
    @MethodSource("provideEncryptedFilesAndExpectedExceptionType")
    public void extractEncryptedFileByArchive(String filePath, String password, Class<?> expectedException) throws Exception {
        File file = new File(getClass().getResource(filePath).toURI());

        Throwable thrown = catchThrowable(() -> {
            try (Archive archive = new Archive(file, password)) {
                // Construction itself must throw; nothing further to do.
            }
        });

        assertThat(thrown).isInstanceOf(RarException.class);
        assertThat(thrown).isExactlyInstanceOf(expectedException);
    }

    @ParameterizedTest
    @MethodSource("provideEncryptedFilesAndExpectedExceptionType")
    public void extractEncryptedStreamByArchive(String filePath, String password, Class<?> expectedException) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(filePath)) {
            Throwable thrown = catchThrowable(() -> {
                try (Archive archive = new Archive(stream, password)) {
                    // Construction itself must throw; nothing further to do.
                }
            });

            assertThat(thrown).isInstanceOf(RarException.class);
            assertThat(thrown).isExactlyInstanceOf(expectedException);
        }
    }

    private static Stream<Arguments> provideEncryptedFilesAndExpectedExceptionType() {
        return Stream.of(
            Arguments.of("abnormal/bad-crc-enc-headers.rar", "secret", CorruptHeaderException.class)
        );
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void VM_JMP_maxOpCount_bypass() throws RarException, IOException {
        /*
         * The PoC RAR4 archive contains one RAR3/m5-compressed file. Its compressed stream decodes to:
         * - a VM filter block (`Number=257`);
         * - VM code bytes `40 40`, which `RarVM.prepare()` parses as one `VM_JMP`; `prepare()` then appends `VM_RET`, so `codeSize = 2`;
         * - eight decompressed bytes `04 00 00 00 02 00 00 00`, copied into the VM memory by `rarVM.setMemory()`.
         *
         * This makes `getOperand()` read `mem[0..3]` as address `4`, and `getValue(false, mem, 4)` read `mem[4..7]` as jump target `2`.
         * Since `2 >= codeSize`, `setIP(2)` takes the early-return path and the VM loops forever.
         */
        File file = TestCommons.writeResourceToFolder(tempDir, "as-vm-1-trigger.rar");

        Throwable thrown = catchThrowable(() -> {
            Junrar.extract(file, tempDir);
        });

        // Upstream asserts CrcErrorException here, because its interpreter breaks out of the
        // filter part-way and produces bytes that fail the checksum. This branch has no
        // interpreter to break out of: M2.2 (16d03472) deleted it, so `setIP`, `ExecuteCode`,
        // `getOperand` and `decodeArg` do not exist and the runaway loop is unreachable by
        // construction rather than by a guard. An unrecognized VM filter is a no-op, matching
        // unrar >= 5.5.1, which dropped the generic interpreter the same way -- and
        // `unrar 7.23` tests this very PoC `All OK` (rc=0). So the oracle-correct outcome on
        // this branch is a clean extraction, and asserting upstream's exception would pin
        // upstream's architecture rather than the format.
        //
        // The invariant upstream's fix actually protects is termination, and the @Timeout above
        // is what pins it: this must not hang.
        assertThat(thrown).as("an unrecognized VM filter is a no-op, as in unrar >= 5.5.1").isNull();
    }
}
