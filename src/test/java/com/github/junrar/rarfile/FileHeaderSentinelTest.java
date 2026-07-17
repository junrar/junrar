package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins T1 (docs/porting/MIGRATION_MANUAL.md SS6): a non-LHD_LARGE header
 * with unpSize == 0xFFFFFFFF must promote fullUnpackSize to INT64MAX,
 * mirroring unrar 3.7.3 arcread.cpp:148-153.
 */
class FileHeaderSentinelTest {

    @Test
    void unpSizeSentinelPromotesFullUnpackSizeToInt64Max() throws CorruptHeaderException {
        byte[] name = "test".getBytes();
        byte[] body = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, // unpSize = 0xFFFFFFFF (sentinel)
            3,                                                  // hostOS = unix
            0, 0, 0, 0,                                         // fileCRC
            0, 0, 0, 0,                                         // fileTime
            0,                                                  // unpVersion
            0,                                                  // unpMethod
            (byte) name.length, 0,                              // nameSize (LE)
            0, 0, 0, 0,                                         // fileAttr
            name[0], name[1], name[2], name[3],                 // fileNameBytes; no LHD_LARGE => no high*Size fields
        };

        // Mirrors ProtectHeaderTest's construction: BaseBlock(headCRC, type=0x74
        // FileHeader, flags=0, headerSize) wrapped in a BlockHeader(packSize).
        byte[] baseBlockBytes = {0, 0, 0x74, 0, 0, 0, 0};
        byte[] blockHeaderBytes = {0, 0, 0, 0};
        BlockHeader blockHeader = new BlockHeader(new BaseBlock(baseBlockBytes), blockHeaderBytes);
        FileHeader header = new FileHeader(blockHeader, body);

        assertThat(header.getUnpSize()).isEqualTo(0xFFFFFFFFL);
        assertThat(header.getFullUnpackSize()).isEqualTo(Long.MAX_VALUE);
    }
}
