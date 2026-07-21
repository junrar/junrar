package com.github.junrar.bugfixes;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/*
Test cases for https://github.com/junrar/junrar/issues/88
 */
public class GitHub88EmptyFile {

    private static final String[] results = {"foo", "", "bar"};

    @Test
    public void testCorruptExtendedTimeData() throws Exception {
        try (Archive archive = new Archive(new File(getClass().getResource("gh-88-empty.rar").toURI()))) {
            FileHeader fileHeader;
            int i = 0;
            while ((fileHeader = archive.nextFileHeader()) != null) {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOUtils.copy(archive.getInputStream(fileHeader), outputStream);
                assertThat(outputStream.toString()).isEqualTo(results[i++]);
            }
            assertThat(i).isEqualTo(results.length);
        }
    }
}
