package com.github.junrar.rar5.unpack;

import com.github.junrar.unpack.vm.BitInput;

/**
 * RAR5 compressed data block header.
 *
 * <p>Each compressed block starts with:
 * <ul>
 *   <li>Flags byte: TablePresent(bit 7), LastBlockInFile(bit 6), SizeBytes-1(bits 5-4), BlockBitSize-1(bits 2-0)</li>
 *   <li>Checksum byte: 0x5A ^ Flags ^ BlockSize ^ (BlockSize>>8) ^ (BlockSize>>16)</li>
 *   <li>Block size: 1-3 bytes little-endian</li>
 * </ul>
 */
public final class UnpackBlockHeader {

    private boolean tablePresent;
    private boolean lastBlockInFile;
    private int blockSize;
    private int blockBitSize;
    private int blockStart;

    /** @return true if Huffman tables follow this header */
    public boolean isTablePresent() {
        return tablePresent;
    }

    /** @return true if this is the last block in the file */
    public boolean isLastBlockInFile() {
        return lastBlockInFile;
    }

    /** @return the block data size in bytes */
    public int getBlockSize() {
        return blockSize;
    }

    /** @return the number of valid bits in the last byte */
    public int getBlockBitSize() {
        return blockBitSize;
    }

    /** @return the starting byte position of the block data */
    public int getBlockStart() {
        return blockStart;
    }

    /**
     * Reads and parses a block header from the bit input.
     *
     * @param input the bit input positioned at the block header
     * @return true if the header was read successfully
     */
    public boolean read(final BitInput input) {
        if (!input.hasBytes(4)) {
            return false;
        }

        // Align to byte boundary
        input.alignToByte();

        final int flags = input.getbits() >>> 8;
        input.addbits(8);

        tablePresent = (flags & 0x80) != 0;
        lastBlockInFile = (flags & 0x40) != 0;

        final int sizeByteCount = ((flags >>> 3) & 0x03) + 1;
        blockBitSize = (flags & 0x07) + 1;

        if (sizeByteCount > 3) {
            return false;
        }

        if (!input.hasBytes(1 + sizeByteCount)) {
            return false;
        }

        // Read checksum byte — BEFORE block size per C++ code
        final int checksum = input.getbits() >>> 8;
        input.addbits(8);

        // Read block size (little-endian, 1-3 bytes) — AFTER checksum per C++ code
        int blockSizeVal = 0;
        for (int i = 0; i < sizeByteCount; i++) {
            blockSizeVal |= (input.getbits() >>> 8) << (i * 8);
            input.addbits(8);
        }
        this.blockSize = blockSizeVal;

        // Verify checksum (truncated to byte per C++ code)
        final int expectedChecksum = 0x5A ^ flags ^ blockSizeVal ^ (blockSizeVal >>> 8) ^ (blockSizeVal >>> 16);
        if ((checksum & 0xFF) != (expectedChecksum & 0xFF)) {
            return false;
        }

        blockStart = input.getInAddr();
        return true;
    }
}
