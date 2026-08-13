package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.junrar.exception.CorruptHeaderException;
import org.junit.jupiter.api.Test;

/**
 * Pins T2 (docs/porting/MIGRATION_MANUAL.md SS6): the ProtectHeader field
 * layout must match unrar 3.7.3 headers.hpp:242-249 (Version, RecSectors,
 * TotalBlocks, Mark[8]) rather than double-reading position 0.
 * <p>
 * Byte values mirror src/test/resources/com/github/junrar/bugfixes/
 * generate_protect_header_seek_fixture.py's PROTECT_* constants.
 */
class ProtectHeaderTest {

    @Test
    void parsesFieldsPerUnrarLayout() throws CorruptHeaderException {
        byte[] body = {
            0x05, // Version
            (byte) 0xE8,
            0x03, // RecSectors = 1000 (LE)
            0x4E,
            0x61,
            (byte) 0xBC,
            0x00, // TotalBlocks = 12345678 (LE)
            'P',
            'R',
            'O',
            'T',
            'M',
            'A',
            'R',
            'K', // Mark[8]
        };
        assertThat(body).hasSize(ProtectHeader.protectHeaderSize);

        // Mirrors Archive.java's ProtectHeader construction: BaseBlock(headCRC,
        // type=0x78, flags, headerSize) wrapped in a BlockHeader(dataSize).
        byte[] baseBlockBytes = {0, 0, 0x78, 0, 0, 0, 0};
        byte[] blockHeaderBytes = {0, 0, 0, 0};
        BlockHeader blockHeader = new BlockHeader(new BaseBlock(baseBlockBytes), blockHeaderBytes);
        ProtectHeader header = new ProtectHeader(blockHeader, body);

        assertThat(header.getVersion()).isEqualTo((byte) 5);
        assertThat(header.getRecSectors()).isEqualTo((short) 1000);
        assertThat(header.getTotalBlocks()).isEqualTo(12345678);
        assertThat(header.getMark()).isEqualTo("PROTMARK".getBytes());
    }

    /**
     * The buffer is sized from the header's own declared size, so a crafted PROTECT block can be
     * shorter than the fixed layout above. Reading it must not run past the buffer, and the
     * fields it never held must not be stood in for either: {@code mark} is a {@code byte[8]}
     * and {@code version} a {@code byte}, neither of which can say "not there", so a zero mark
     * would be indistinguishable from a genuine one. The header is rejected instead, and
     * {@code Archive} skips the block and reports it through {@code getHeaderFailures()}.
     */
    @Test
    void headerShorterThanItsFixedLayoutIsRejected() {
        byte[] baseBlockBytes = {0, 0, 0x78, 0, 0, 0, 0};
        BlockHeader blockHeader = new BlockHeader(new BaseBlock(baseBlockBytes), new byte[4]);

        assertThatThrownBy(() -> new ProtectHeader(blockHeader, new byte[] {0x05, (byte) 0xE8}))
                .isInstanceOf(CorruptHeaderException.class);
    }
}
