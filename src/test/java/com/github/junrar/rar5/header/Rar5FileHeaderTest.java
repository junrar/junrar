package com.github.junrar.rar5.header;

import com.github.junrar.rar5.io.VInt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rar5FileHeaderTest {

    @Test
    void parseMinimalFileHeader() {
        // flags=0, unpSize=100, attr=0, no mtime, no crc32, compInfo=0, hostOS=0, nameLen=4, name="test"
        final byte[] fieldData = buildFieldData(0, 100, 0, null, null, 0, 0, "test");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);

        final int fieldOffset = 4 + VInt.encodedLength(blockHeader.getHeaderSize()) + 1 + 1; // CRC + size + type + flags
        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.getFileFlags()).isZero();
        assertThat(header.getUnpackedSize()).isEqualTo(100);
        assertThat(header.getAttributes()).isZero();
        assertThat(header.getMtime()).isNull();
        assertThat(header.getDataCrc32()).isNull();
        assertThat(header.getCompressionInfo()).isZero();
        assertThat(header.getCompressionMethod()).isZero();
        assertThat(header.getAlgorithmVersion()).isZero();
        assertThat(header.getDictionarySize()).isEqualTo(128 * 1024);
        assertThat(header.getHostOSRaw()).isZero();
        assertThat(header.getFileName()).isEqualTo("test");
        assertThat(header.isDirectory()).isFalse();
        assertThat(header.hasUnixTime()).isFalse();
        assertThat(header.hasCrc32()).isFalse();
        assertThat(header.isUnknownUnpSize()).isFalse();
        assertThat(header.isSolid()).isFalse();
        assertThat(header.isEncrypted()).isFalse();
        assertThat(header.getExtraRecords()).isEmpty();
    }

    @Test
    void parseDirectoryEntry() {
        final byte[] fieldData = buildFieldData(
            Rar5FileHeader.FHFL_DIRECTORY, 0, 0, null, null, 0, 0, "mydir");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.isDirectory()).isTrue();
        assertThat(header.getFileName()).isEqualTo("mydir");
    }

    @Test
    void parseWithUnixTimeAndCrc32() {
        final long mtime = 1609459200L; // 2021-01-01 00:00:00 UTC
        final long crc32 = 0xDEADBEEFL;
        final byte[] fieldData = buildFieldData(
            Rar5FileHeader.FHFL_UTIME | Rar5FileHeader.FHFL_CRC32,
            500, 0644, mtime, crc32, 0, 1, "data.txt");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.hasUnixTime()).isTrue();
        assertThat(header.getMtime()).isEqualTo(mtime);
        assertThat(header.hasCrc32()).isTrue();
        assertThat(header.getDataCrc32()).isEqualTo(crc32);
        assertThat(fileNameFrom(header)).isEqualTo("data.txt");
    }

    @Test
    void parseCompressionMethod() {
        // method=3 (normal), dictBits=7 (128KB * 2^7 = 16MB)
        final long compInfo = (3 << Rar5FileHeader.FCI_METHOD_SHIFT) | (7 << Rar5FileHeader.FCI_DICT_SHIFT);
        final byte[] fieldData = buildFieldData(0, 1000, 0, null, null, compInfo, 0, "compressed.bin");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.getCompressionMethod()).isEqualTo(3);
        assertThat(header.getDictionarySize()).isEqualTo(16 * 1024 * 1024);
        assertThat(header.getAlgorithmVersion()).isZero();
    }

    @Test
    void parseSolidFile() {
        final long compInfo = Rar5FileHeader.FCI_SOLID | (5 << Rar5FileHeader.FCI_METHOD_SHIFT);
        final byte[] fieldData = buildFieldData(0, 2000, 0, null, null, compInfo, 0, "solid.dat");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.isSolid()).isTrue();
        assertThat(header.getCompressionMethod()).isEqualTo(5);
    }

    @Test
    void parseUnknownUnpSize() {
        final byte[] fieldData = buildFieldData(
            Rar5FileHeader.FHFL_UNPUNKNOWN, 0, 0, null, null, 0, 0, "stream.dat");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.isUnknownUnpSize()).isTrue();
    }

    @Test
    void parseUnixHostOS() {
        final byte[] fieldData = buildFieldData(0, 100, 0755, null, null, 0, Rar5FileHeader.HOST_UNIX, "script.sh");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.getHostOSRaw()).isEqualTo(Rar5FileHeader.HOST_UNIX);
        assertThat(header.getAttributes()).isEqualTo(0755);
    }

    @Test
    void parseLargeUnpackedSize() {
        final long largeSize = 5L * 1024 * 1024 * 1024; // 5 GB
        final byte[] fieldData = buildFieldData(0, largeSize, 0, null, null, 0, 0, "large.bin");
        final Rar5BlockHeader blockHeader = buildBlockHeader(fieldData, false, false);
        final byte[] fullData = assembleBlock(blockHeader, fieldData);
        final int fieldOffset = computeFieldOffset(blockHeader);

        final Rar5FileHeader header = Rar5FileHeader.parse(fullData, blockHeader, fieldOffset);

        assertThat(header.getUnpackedSize()).isEqualTo(largeSize);
    }

    @Test
    void parseNullData() {
        assertThatThrownBy(() -> Rar5FileHeader.parse(null, null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void parseNullBlockHeader() {
        assertThatThrownBy(() -> Rar5FileHeader.parse(new byte[10], null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    private static byte[] buildFieldData(final long flags, final long unpSize, final long attr,
                                         final Long mtime, final Long crc32,
                                         final long compInfo, final int hostOS,
                                         final String name) {
        final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        final int nameLenBytes = VInt.encodedLength(nameBytes.length);

        int size = 0;
        size += VInt.encodedLength(flags);
        size += VInt.encodedLength(unpSize);
        size += VInt.encodedLength(attr);
        if (mtime != null) size += 4;
        if (crc32 != null) size += 4;
        size += VInt.encodedLength(compInfo);
        size += VInt.encodedLength(hostOS);
        size += nameLenBytes;
        size += nameBytes.length;

        final byte[] data = new byte[size];
        int pos = 0;
        pos += VInt.write(flags, data, pos);
        pos += VInt.write(unpSize, data, pos);
        pos += VInt.write(attr, data, pos);
        if (mtime != null) {
            data[pos++] = (byte) (mtime & 0xFF);
            data[pos++] = (byte) ((mtime >> 8) & 0xFF);
            data[pos++] = (byte) ((mtime >> 16) & 0xFF);
            data[pos++] = (byte) ((mtime >> 24) & 0xFF);
        }
        if (crc32 != null) {
            data[pos++] = (byte) (crc32 & 0xFF);
            data[pos++] = (byte) ((crc32 >> 8) & 0xFF);
            data[pos++] = (byte) ((crc32 >> 16) & 0xFF);
            data[pos++] = (byte) ((crc32 >> 24) & 0xFF);
        }
        pos += VInt.write(compInfo, data, pos);
        pos += VInt.write(hostOS, data, pos);
        pos += VInt.write(nameBytes.length, data, pos);
        System.arraycopy(nameBytes, 0, data, pos, nameBytes.length);

        return data;
    }

    private static Rar5BlockHeader buildBlockHeader(final byte[] fieldData,
                                                     final boolean hasExtra, final boolean hasData) {
        long flags = 0;
        if (hasExtra) flags |= BlockFlags.HFL_EXTRA;
        if (hasData) flags |= BlockFlags.HFL_DATA;

        final byte[] headerFields = new byte[2 + fieldData.length];
        headerFields[0] = 0x02; // type = FILE
        headerFields[1] = (byte) flags;
        System.arraycopy(fieldData, 0, headerFields, 2, fieldData.length);

        final CRC32 crc = new CRC32();
        crc.update(headerFields);
        final long crcVal = crc.getValue();

        final int headerSize = headerFields.length;
        final byte[] fullBlock = new byte[4 + VInt.encodedLength(headerSize) + headerSize];
        int pos = 0;
        fullBlock[pos++] = (byte) (crcVal & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 8) & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 16) & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 24) & 0xFF);
        pos += VInt.write(headerSize, fullBlock, pos);
        System.arraycopy(headerFields, 0, fullBlock, pos, headerFields.length);

        return Rar5BlockHeader.parse(fullBlock, 0);
    }

    private static byte[] assembleBlock(final Rar5BlockHeader blockHeader, final byte[] fieldData) {
        long flags = 0;
        if (blockHeader.hasExtra()) flags |= BlockFlags.HFL_EXTRA;
        if (blockHeader.hasData()) flags |= BlockFlags.HFL_DATA;

        final byte[] headerFields = new byte[2 + fieldData.length];
        headerFields[0] = 0x02;
        headerFields[1] = (byte) flags;
        System.arraycopy(fieldData, 0, headerFields, 2, fieldData.length);

        final CRC32 crc = new CRC32();
        crc.update(headerFields);
        final long crcVal = crc.getValue();

        final int headerSize = headerFields.length;
        final byte[] fullBlock = new byte[4 + VInt.encodedLength(headerSize) + headerSize];
        int pos = 0;
        fullBlock[pos++] = (byte) (crcVal & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 8) & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 16) & 0xFF);
        fullBlock[pos++] = (byte) ((crcVal >> 24) & 0xFF);
        pos += VInt.write(headerSize, fullBlock, pos);
        System.arraycopy(headerFields, 0, fullBlock, pos, headerFields.length);

        return fullBlock;
    }

    private static int computeFieldOffset(final Rar5BlockHeader blockHeader) {
        return 4 + VInt.encodedLength(blockHeader.getHeaderSize()) + 1 + 1;
    }

    private static String fileNameFrom(final Rar5FileHeader header) {
        return header.getFileName();
    }
}
