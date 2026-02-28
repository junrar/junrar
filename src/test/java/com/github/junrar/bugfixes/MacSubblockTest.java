package com.github.junrar.bugfixes;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for archives containing MAC_HEAD (0x77 subtype 0x0102) subblocks
 * interleaved between file entries, as produced by Mac OS RAR archivers.
 * <p>
 * The bug: junrar read only the fixed-size MacInfoHeader (8 bytes) from the subblock's
 * packed data but never seeked past the remaining bytes, leaving the channel positioned
 * mid-block. Every subsequent file header was then parsed from the wrong offset, causing
 * a CorruptHeaderException and making the archive appear unreadable.
 */
public class MacSubblockTest {

    @Test
    public void archiveWithMacSubblocksBetweenFilesCanBeListedAndExtracted() throws Exception {
        File f = new File(getClass().getResource("mac-subblock.rar").toURI());
        try (Archive archive = new Archive(f)) {
            assertThat(archive.isPasswordProtected()).isFalse();
            assertThat(archive.isEncrypted()).isFalse();

            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(2);

            FileHeader first = fileHeaders.get(0);
            assertThat(first.getFileName()).isEqualTo("file1.txt");
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                IOUtils.copy(archive.getInputStream(first), baos);
                assertThat(baos.toString()).isEqualTo("hello\n");
            }

            FileHeader second = fileHeaders.get(1);
            assertThat(second.getFileName()).isEqualTo("file2.txt");
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                IOUtils.copy(archive.getInputStream(second), baos);
                assertThat(baos.toString()).isEqualTo("world\n");
            }
        }
    }
}
