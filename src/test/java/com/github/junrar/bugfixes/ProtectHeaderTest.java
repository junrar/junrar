package com.github.junrar.bugfixes;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for archives containing a legacy RAR3 recovery-record
 * ("protect") block (HEAD_TYPE=0x78) followed by a normal file member.
 * <p>
 * Pins no-go row C7 (docs/porting/PARITY_PLAN.md SS3 P0.2): the ProtectHeader
 * seek in {@code Archive}'s header loop must land past both the block's own
 * header AND its declared recovery-record payload, else the following file
 * header is parsed from the wrong offset.
 */
public class ProtectHeaderTest {

    @Test
    public void fileHeaderAfterProtectBlockParsesCorrectly() throws Exception {
        File f = new File(getClass().getResource("protect-header-seek.rar").toURI());
        try (Archive archive = new Archive(f)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);

            FileHeader fileHeader = fileHeaders.get(0);
            assertThat(fileHeader.getFileName()).isEqualTo("after-protect.txt");
            assertThat(fileHeader.getFullUnpackSize()).isEqualTo(10L);
            assertThat(fileHeader.getFullPackSize()).isEqualTo(10L);
        }
    }
}
