package com.github.junrar.impl.rar5;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.impl.HeaderReader;
import com.github.junrar.io.Raw;
import com.github.junrar.io.RawDataIo;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rar5.header.HeaderType;
import com.github.junrar.rar5.header.RAR5EndArcHeader;
import com.github.junrar.rar5.header.RAR5FileHeader;
import com.github.junrar.rar5.header.RAR5MainHeader;
import com.github.junrar.rar5.Rar5Constants;
import com.github.junrar.rar5.header.extra.Rar5ExtraParser;
import com.github.junrar.rar5.header.extra.Rar5ExtraRecord;
import com.github.junrar.rar5.io.VInt;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.MarkHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_ALGO_MASK;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_DICT_MASK;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_DICT_SHIFT;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_METHOD_MASK;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_METHOD_SHIFT;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_RAR5_COMPAT;
import static com.github.junrar.rar5.header.RAR5FileHeader.FCI_SOLID;
import static com.github.junrar.rar5.Rar5Constants.FHFL_CRC32;
import static com.github.junrar.rar5.Rar5Constants.FHFL_UTIME;

public class RAR5HeaderReader implements HeaderReader {

    private static final Logger logger = LoggerFactory.getLogger(RAR5HeaderReader.class);
    private static final int CRC32_SIZE = 4;

    private final List<BaseBlock> headers = new ArrayList<>();

    public RAR5HeaderReader(MarkHeader markHeader) {
        headers.add(markHeader);
    }

    @Override
    public byte[] safelyAllocate(final long len) throws RarException {
        if (len < 0 || len > Rar5Constants.MAX_HEADER_SIZE_RAR5) {
            throw new BadRarArchiveException();
        }
        return new byte[(int) len];
    }

    @Override
    public void readHeaders(SeekableReadOnlyByteChannel channel, long fileLength, String password)
            throws IOException, RarException {
        // We only read 7 bytes for the MarkHeader. Next header starts at 8 bytes.
        long currentPosition = Rar5Constants.SIZEOF_MARKHEAD5;

        while (currentPosition < fileLength) {
            RawDataIo rawData = new RawDataIo(channel);
            rawData.setPosition(currentPosition);

            // Read CRC32 (4 bytes, little endian)
            final byte[] crc32Buffer = new byte[CRC32_SIZE];
            int read = rawData.readFully(crc32Buffer, CRC32_SIZE);
            if (read < CRC32_SIZE) {
                break;
            }
            final long headerCrc32 = Raw.readIntLittleEndianAsLong(crc32Buffer, 0);
            currentPosition += CRC32_SIZE;

            // Read header size vint (manual loop: must read from channel before full header buffer exists)
            long headerSize = 0;
            int shift = 0;
            for (int i = 0; i < 3; i++) {
                final int b = rawData.read();
                if (b < 0) {
                    return;
                }
                headerSize |= (b & 0x7FL) << shift;
                currentPosition++;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            if (headerSize <= 0 || headerSize > Rar5Constants.MAX_HEADER_SIZE_RAR5) {
                throw new CorruptHeaderException("Invalid header size: " + headerSize + " at position " + currentPosition);
            }

            // Read the full header data
            final byte[] headerData = safelyAllocate(headerSize);
            rawData.readFully(headerData, (int) headerSize);
            currentPosition += headerSize;

            int pos = 0;

            // Header type (vint)
            final VInt.Result typeResult = VInt.read(headerData, pos);
            final long typeValue = typeResult.getValue();
            pos += typeResult.getBytesConsumed();

            // Header flags (vint)
            final VInt.Result flagsResult = VInt.read(headerData, pos);
            final long flags = flagsResult.getValue();
            pos += flagsResult.getBytesConsumed();

            final boolean hasExtra = (flags & 0x0001) != 0;
            final boolean hasData = (flags & 0x0002) != 0;

            long extraSize = 0;
            if (hasExtra) {
                final VInt.Result extraResult = VInt.read(headerData, pos);
                extraSize = extraResult.getValue();
                pos += extraResult.getBytesConsumed();
            }

            long dataSize = 0;
            if (hasData) {
                final VInt.Result dataResult = VInt.read(headerData, pos);
                dataSize = dataResult.getValue();
                pos += dataResult.getBytesConsumed();
            }

            final HeaderType headerType = HeaderType.fromValue(typeValue);
            switch (headerType) {
                case ARCHIVE:
                    // Parse archive flags for main header
                    final VInt.Result archiveFlagsResult = VInt.read(headerData, pos);
                    final long archiveFlags = archiveFlagsResult.getValue();
                    pos += archiveFlagsResult.getBytesConsumed();

                    final RAR5MainHeader mainHeader = new RAR5MainHeader((int) archiveFlags, (short) headerSize);
                    mainHeader.setHeaderCrc32(headerCrc32);

                    // Read volume number if present (archive flag 0x0002)
                    if ((archiveFlags & 0x0002) != 0) {
                        final VInt.Result volNumResult = VInt.read(headerData, pos);
                        mainHeader.setVolumeNumber((int) volNumResult.getValue());
                        mainHeader.setVolumeNumberPresent(true);
                        pos += volNumResult.getBytesConsumed();
                    }

                    // Parse extra area if present
                    if (hasExtra && extraSize > 0) {
                        parseMainHeaderExtraArea(mainHeader, headerData, pos, (int) extraSize);
                    }

                    this.headers.add(mainHeader);
                    break;
                case FILE:
                case SERVICE:
                    // Read file flags (vint)
                    final VInt.Result fileFlagsResult = VInt.read(headerData, pos);
                    final long fileFlags = fileFlagsResult.getValue();
                    pos += fileFlagsResult.getBytesConsumed();

                    // Read unpacked size (vint)
                    final VInt.Result unpSizeResult = VInt.read(headerData, pos);
                    final long unpackedSize = unpSizeResult.getValue();
                    pos += unpSizeResult.getBytesConsumed();

                    // Read attributes (vint)
                    final VInt.Result attrResult = VInt.read(headerData, pos);
                    final long attributes = attrResult.getValue();
                    pos += attrResult.getBytesConsumed();

                    // Read optional mtime (uint32, Unix time)
                    FileTime mtime = null;
                    if ((fileFlags & FHFL_UTIME) != 0) {
                        long mtimeValue = Raw.readIntLittleEndianAsLong(headerData, pos);
                        pos += 4;
                        // Unix timestamp (seconds since 1970-01-01 UTC) - store as UTC
                        mtime = FileTime.from(java.time.Instant.ofEpochSecond(mtimeValue));
                    }

                    // Read optional CRC32
                    Long dataCrc32 = null;
                    if ((fileFlags & FHFL_CRC32) != 0) {
                        dataCrc32 = Raw.readIntLittleEndianAsLong(headerData, pos);
                        pos += 4;
                    }

                    // Read compression info (vint)
                    final VInt.Result compResult = VInt.read(headerData, pos);
                    final long compressionInfo = compResult.getValue();
                    pos += compResult.getBytesConsumed();

                    final int algorithmVersion = (int) (compressionInfo & FCI_ALGO_MASK);
                    // Translate raw algo field to the pack version number used by the C++ reference
                    // (VER_PACK5 = 50, VER_PACK7 = 70) so that getUnpVersion() carries the same meaning
                    // as in RAR4 headers: "minimum version required to unpack".
                    final int unpackVersion = algorithmVersion == 0 ? Rar5Constants.VER_PACK5
                            : algorithmVersion == 1 ? Rar5Constants.VER_PACK7
                            : 0;
                    final int compressionMethod = (int) ((compressionInfo & FCI_METHOD_MASK) >>> FCI_METHOD_SHIFT);
                    final boolean isSolid = (compressionInfo & FCI_SOLID) != 0;

                    // Dictionary size: 128KB * 2^N
                    final int dictBits = (int) ((compressionInfo & FCI_DICT_MASK) >>> FCI_DICT_SHIFT);
                    long dictionarySize = 0;
                    if (algorithmVersion == 0) {
                        // RAR5: dictBits 0-15, max 128KB * 2^15 = 4GB
                        if (dictBits <= 15) {
                            dictionarySize = 0x20000L << dictBits;
                        }
                    } else if (algorithmVersion == 1) {
                        // RAR7: dictBits 0-31, with fraction
                        final int dictFrac = (int) ((compressionInfo & 0xF8000) >>> 15);
                        if (dictBits <= 31) {
                            dictionarySize = 0x20000L << dictBits;
                            dictionarySize += dictionarySize / 32 * dictFrac;
                        }
                        // RAR7 with RAR5 compat flag
                        if ((compressionInfo & FCI_RAR5_COMPAT) != 0) {
                            // Treat as RAR5 for dictionary size purposes
                        }
                    }

                    // Read host OS (vint)
                    final VInt.Result hostResult = VInt.read(headerData, pos);
                    final int hostOS = (int) hostResult.getValue();
                    pos += hostResult.getBytesConsumed();

                    // Read name length (vint)
                    final VInt.Result nameLenResult = VInt.read(headerData, pos);
                    final long nameLength = nameLenResult.getValue();
                    pos += nameLenResult.getBytesConsumed();

                    // Read file name (always UTF-8)
                    String fileName = "";
                    if (nameLength > 0 && nameLength <= Integer.MAX_VALUE) {
                        fileName = new String(headerData, pos, (int) nameLength, StandardCharsets.UTF_8);
                        pos += (int) nameLength;
                    }

                    // Parse extra area records (always at the end of the header data)
                    final List<Rar5ExtraRecord> extraRecords;
                    if (hasExtra && extraSize > 0) {
                        // Seek from end of header to guarantee correct position even if
                        // future optional fields appear between the name and extra area.
                        final int extraStart = (int) headerSize - (int) extraSize;
                        extraRecords = Rar5ExtraParser.parse(headerData, extraStart, (int) extraSize);
                    } else {
                        extraRecords = Collections.emptyList();
                    }

                    RAR5FileHeader fileHeader = new RAR5FileHeader(fileFlags, unpackedSize, attributes, mtime,
                            dataCrc32, compressionInfo, hostOS, fileName,
                            unpackVersion, compressionMethod, dictionarySize,
                            isSolid, extraRecords);
                    this.headers.add(fileHeader);
                    break;
                case CRYPT:
                    throw new UnsupportedRarV5Exception();
                case ENDARC: {
                    final VInt.Result endFlagsResult = VInt.read(headerData, pos);
                    final RAR5EndArcHeader endArcHeader = new RAR5EndArcHeader(
                            headerCrc32, headerSize, flags, (int) endFlagsResult.getValue());
                    this.headers.add(endArcHeader);
                    return;
                }
                default:
                    if ((flags & 0x0004) == 0) {
                        logger.warn("Unknown RAR5 block type: {}", typeValue);
                    }
                    break;
            }

            // Skip data area
            if (hasData && dataSize > 0) {
                // Next iteration will skip the channel to the current position. Skipping the data with it.
                currentPosition += dataSize;
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<BaseBlock> getHeaders() {
        return headers;
    }

    private void parseMainHeaderExtraArea(RAR5MainHeader mainHeader, byte[] headerData, int startPos, int extraSize) {
        int pos = startPos;
        final int endPos = startPos + extraSize;

        while (pos < endPos) {
            final VInt.Result recordSizeResult = VInt.read(headerData, pos);
            final int recordSize = (int) recordSizeResult.getValue();
            pos += recordSizeResult.getBytesConsumed();

            final VInt.Result recordTypeResult = VInt.read(headerData, pos);
            final int recordType = (int) recordTypeResult.getValue();
            pos += recordTypeResult.getBytesConsumed();

            switch (recordType) {
                case 0x01:
                    parseLocatorRecord(mainHeader, headerData, pos, recordSize);
                    break;
                case 0x02:
                    parseMetadataRecord(mainHeader, headerData, pos, recordSize);
                    break;
                default:
                    logger.warn("Unknown main header extra record type: {}", recordType);
                    break;
            }

            pos += recordSize - recordTypeResult.getBytesConsumed();
        }
    }

    private void parseLocatorRecord(RAR5MainHeader mainHeader, byte[] headerData, int startPos, int recordSize) {
        int pos = startPos;
        final int recordEnd = startPos + recordSize - 1;

        final VInt.Result flagsResult = VInt.read(headerData, pos);
        final int locatorFlags = (int) flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        mainHeader.setLocator(true);

        if ((locatorFlags & 0x0001) != 0 && pos < recordEnd) {
            final VInt.Result qOpenResult = VInt.read(headerData, pos);
            mainHeader.setQOpenOffset(qOpenResult.getValue());
            pos += qOpenResult.getBytesConsumed();
        }

        if ((locatorFlags & 0x0002) != 0 && pos < recordEnd) {
            final VInt.Result rrResult = VInt.read(headerData, pos);
            mainHeader.setRrOffset(rrResult.getValue());
        }
    }

    private void parseMetadataRecord(RAR5MainHeader mainHeader, byte[] headerData, int startPos, int recordSize) {
        int pos = startPos;
        final int recordEnd = startPos + recordSize - 1;

        final VInt.Result flagsResult = VInt.read(headerData, pos);
        final int metadataFlags = (int) flagsResult.getValue();
        pos += flagsResult.getBytesConsumed();

        if ((metadataFlags & 0x0001) != 0 && pos < recordEnd) {
            final VInt.Result nameLenResult = VInt.read(headerData, pos);
            final int nameLen = (int) nameLenResult.getValue();
            pos += nameLenResult.getBytesConsumed();

            if (nameLen > 0 && nameLen < recordEnd - pos) {
                String name = new String(headerData, pos, nameLen, StandardCharsets.UTF_8);
                int nullPos = name.indexOf('\0');
                if (nullPos >= 0) {
                    name = name.substring(0, nullPos);
                }
                mainHeader.setOrigName(name);
            }
            pos += nameLen;
        }

        if ((metadataFlags & 0x0002) != 0 && pos < recordEnd) {
            if ((metadataFlags & 0x0004) != 0) {
                if ((metadataFlags & 0x0008) != 0) {
                    long time = Raw.readLongLittleEndian(headerData, pos);
                    mainHeader.setOrigTime(FileTime.from(time, TimeUnit.NANOSECONDS));
                } else {
                    long time = Raw.readIntLittleEndianAsLong(headerData, pos);
                    mainHeader.setOrigTime(FileTime.from(time, TimeUnit.SECONDS));
                }
            } else {
                long time = Raw.readLongLittleEndian(headerData, pos);
                long windowsTime = time - 116444736000000000L;
                long millis = windowsTime / 10000;
                mainHeader.setOrigTime(FileTime.fromMillis(millis));
            }
        }
    }
}
