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
            Arguments.of("abnormal/loop3.rar", CorruptHeaderException.class)
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

        assertThat(thrown).isInstanceOf(CrcErrorException.class);
    }
}
