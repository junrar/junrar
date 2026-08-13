package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.exception.CorruptHeaderException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * A RAR3 FILE header may set {@code LHD_EXTTIME} and then end before the extended-time fields it
 * announces. Reading such a field must neither run past the header nor invent a value for it: the
 * field stays absent and the header is marked broken, the same way a header-CRC mismatch is
 * recorded (issue #12), so listing still works and extraction refuses the entry.
 */
class FileHeaderExtTimeBoundsTest {

    private static final int FILE_TIME_DOS = 0x4A000000;

    /** Extended-time flags claim a ctime whose 4-byte DOS time is not in the buffer. */
    @Test
    void truncatedExtendedTimeValueLeavesTheTimeAbsent() throws CorruptHeaderException {
        FileHeader header = fileHeaderWithExtTimeFlags((short) 0x0800);

        assertThat(header.getCreationTime()).isNull();
        assertThat(header.isBrokenHeader()).isTrue();
    }

    /**
     * Extended-time flags claim three mtime remainder bytes that are not in the buffer. The
     * seconds come from the header's own file time, so they survive; only the sub-second
     * remainder is lost.
     */
    @Test
    void truncatedExtendedTimeRemainderKeepsTheSecondsAndMarksTheHeaderBroken()
            throws CorruptHeaderException {
        FileHeader header = fileHeaderWithExtTimeFlags((short) 0xB000);

        assertThat(header.getLastModifiedTime())
                .isEqualTo(
                        FileTime.from(
                                Instant.ofEpochSecond(
                                        TimeUnit.MILLISECONDS.toSeconds(
                                                FileHeader.getDateDos(FILE_TIME_DOS)))));
        assertThat(header.isBrokenHeader()).isTrue();
    }

    /**
     * Builds a FILE header body that ends immediately after the 2-byte extended-time flags.
     */
    private static FileHeader fileHeaderWithExtTimeFlags(short extTimeFlags)
            throws CorruptHeaderException {
        byte[] baseBlock = new byte[7];
        baseBlock[2] = 0x74; // FileHeader
        short flags = (short) (0x8000 | 0x1000); // LONG_BLOCK | LHD_EXTTIME
        baseBlock[3] = (byte) (flags & 0xff);
        baseBlock[4] = (byte) ((flags >>> 8) & 0xff);
        baseBlock[5] = 40; // headerSize, not used by the body parse below

        BlockHeader blockHeader =
                new BlockHeader(new BaseBlock(baseBlock), new byte[] {0, 0, 0, 0});

        byte[] body =
                new byte[] {
                    0,
                    0,
                    0,
                    0, // unpSize
                    0, // hostOS
                    0,
                    0,
                    0,
                    0, // fileCRC
                    0,
                    0,
                    0,
                    (byte) (FILE_TIME_DOS >>> 24), // fileTime
                    20,
                    0x30, // unpVersion, store
                    1,
                    0, // nameSize
                    0,
                    0,
                    0,
                    0, // fileAttr
                    'a', // name
                    (byte) (extTimeFlags & 0xff),
                    (byte) ((extTimeFlags >>> 8) & 0xff)
                };

        return new FileHeader(blockHeader, body);
    }
}
