package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.junrar.exception.RarException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An entry name that {@code sun.jnu.encoding} cannot represent must not abort the extraction with
 * an exception the public API does not declare.
 *
 * <p>{@link File#toPath()} throws {@link java.nio.file.InvalidPathException}, which is unchecked,
 * for such a name. {@code Junrar.extract} declares only {@code RarException} and {@code
 * IOException}, so a caller honouring the signature cannot catch it.
 *
 * <p>This only reproduces where the JVM file name encoding is not UTF-8, which is the case in a
 * container without {@code LANG} set: {@code sun.jnu.encoding} is then {@code ANSI_X3.4-1968}. It
 * is not affected by JEP 400, which sets {@code file.encoding} rather than {@code sun.jnu.encoding}.
 * On a UTF-8 host the test cannot fail and is skipped.
 */
public class NonRepresentableNameTest {

    private File tempFolder;

    @BeforeEach
    public void setUp() throws IOException {
        tempFolder = Files.createTempDirectory("junrar-nonrepresentable").toFile();
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void extractionDoesNotThrowUndeclaredExceptionForUnrepresentableName() {
        final String name = "新建文本文档.txt";
        final CharsetEncoder encoder =
                Charset.forName(System.getProperty("sun.jnu.encoding")).newEncoder();
        assumeTrue(
                !encoder.canEncode(name),
                "sun.jnu.encoding can represent the name, so File.toPath cannot fail here");

        final File rarFile =
                new File(
                        getClass()
                                .getResource("password/rar4-only-file-content-encrypted.rar")
                                .getPath());

        final Throwable thrown = catchThrowable(() -> Junrar.extract(rarFile, tempFolder, "test"));

        if (thrown != null) {
            assertThat(thrown)
                    .describedAs("Junrar.extract declares RarException and IOException only")
                    .isInstanceOfAny(RarException.class, IOException.class);
        }
        assertThat(tempFolder.listFiles()).isNotEmpty();
    }
}
