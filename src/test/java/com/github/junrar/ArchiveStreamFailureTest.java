package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @TempDir Path tempDir;

    @Test
    void aMemberFailingItsCrcDoesNotEndInANormalEof() throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getResource(FIXTURE).toURI()));
        assertThat(bytes[DATA_OFFSET]).isEqualTo((byte) 'u');
        bytes[DATA_OFFSET] = 'U';
        Path file = Files.write(tempDir.resolve("crc-mismatch.rar"), bytes);

        try (Archive archive = new Archive(file.toFile())) {
            FileHeader header = archive.getFileHeaders().get(0);
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            try (InputStream in = archive.getInputStream(header)) {
                assertThatThrownBy(() -> IOUtils.copy(in, received))
                        .isInstanceOf(IOException.class)
                        .hasCauseInstanceOf(CrcErrorException.class);
            }
            // The bytes produced before the CRC check still arrive; only the EOF changes.
            assertThat(received.size()).isEqualTo(header.getFullUnpackSize());
        }
    }
}
