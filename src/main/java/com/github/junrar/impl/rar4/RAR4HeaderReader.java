package com.github.junrar.impl.rar4;

import com.github.junrar.crypt.Rijndael;
import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.InitDeciphererFailedException;
import com.github.junrar.exception.MainHeaderNullException;
import com.github.junrar.exception.NotRarArchiveException;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.HeaderReader;
import com.github.junrar.io.RawDataIo;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.AVHeader;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.BlockHeader;
import com.github.junrar.rarfile.CommentHeader;
import com.github.junrar.rarfile.EAHeader;
import com.github.junrar.rarfile.EndArcHeader;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.MacInfoHeader;
import com.github.junrar.rarfile.MainHeader;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.ProtectHeader;
import com.github.junrar.rarfile.SignHeader;
import com.github.junrar.rarfile.SubBlockHeader;
import com.github.junrar.rarfile.SubBlockHeaderType;
import com.github.junrar.rarfile.UnixOwnersHeader;
import com.github.junrar.rarfile.UnrarHeadertype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads headers from RAR4 (version 4.x) archives.
 * The MarkHeader is provided via constructor to avoid duplicate parsing.
 */
public class RAR4HeaderReader implements HeaderReader {

    private static final Logger logger = LoggerFactory.getLogger(RAR4HeaderReader.class);

    private final List<BaseBlock> headers = new ArrayList<>();

    /**
     * Creates a RAR4HeaderReader with the pre-parsed MarkHeader.
     *
     * @param markHeader the MarkHeader parsed by HeaderReaderFactory
     */
    public RAR4HeaderReader(final MarkHeader markHeader) {
        this.headers.add(markHeader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readHeaders(final SeekableReadOnlyByteChannel channel, final long fileLength, final String password)
            throws IOException, RarException {
        MainHeader newMhd = null;
        Set<Long> processedPositions = new HashSet<>();

        while (true) {
            RawDataIo rawData = new RawDataIo(channel);
            final byte[] baseBlockBuffer = new byte[BaseBlock.BaseBlockSize];

            if (newMhd != null && newMhd.isEncrypted()) {
                byte[] salt = new byte[8];
                rawData.readFully(salt, 8);
                try {
                    Cipher cipher = Rijndael.buildDecipherer(password, salt);
                    rawData.setCipher(cipher);
                } catch (Exception e) {
                    throw new InitDeciphererFailedException(e);
                }
            }

            final long position = channel.getPosition();
            // Weird, but is trying to read beyond the end of the file
            if (position >= fileLength) {
                break;
            }

            final int size = rawData.readFully(baseBlockBuffer, baseBlockBuffer.length);
            if (size == 0) {
                break;
            }

            final BaseBlock block = new BaseBlock(baseBlockBuffer);
            block.setPositionInFile(position);

            final UnrarHeadertype headerType = block.getHeaderType();
            if (headerType == null) {
                throw new CorruptHeaderException("Unknown header type at position: " + position);
            }

            switch (headerType) {
                case MainHeader:
                    final int toRead = block.hasEncryptVersion() ? MainHeader.mainHeaderSizeWithEnc : MainHeader.mainHeaderSize;
                    final byte[] mainbuff = safelyAllocate(toRead);
                    rawData.readFully(mainbuff, mainbuff.length);
                    MainHeader mainhead = new MainHeader(block, mainbuff);
                    headers.add(mainhead);
                    newMhd = mainhead;
                    break;

                case SignHeader:
                    byte[] signBuff = safelyAllocate(SignHeader.signHeaderSize);
                    rawData.readFully(signBuff, signBuff.length);
                    SignHeader signHead = new SignHeader(block, signBuff);
                    headers.add(signHead);
                    break;

                case AvHeader:
                    byte[] avBuff = safelyAllocate(AVHeader.avHeaderSize);
                    rawData.readFully(avBuff, avBuff.length);
                    AVHeader avHead = new AVHeader(block, avBuff);
                    headers.add(avHead);
                    break;

                case CommHeader:
                    byte[] commBuff = safelyAllocate(CommentHeader.commentHeaderSize);
                    rawData.readFully(commBuff, commBuff.length);
                    CommentHeader commHead = new CommentHeader(block, commBuff);
                    headers.add(commHead);

                    long newpos = commHead.getPositionInFile() + commHead.getHeaderSize(isEncrypted(newMhd));
                    channel.setPosition(newpos);

                    if (processedPositions.contains(newpos)) {
                        throw new BadRarArchiveException();
                    }
                    processedPositions.add(newpos);
                    break;

                case EndArcHeader:
                    int endArcSize = 0;
                    if (block.hasArchiveDataCRC()) {
                        endArcSize += EndArcHeader.endArcArchiveDataCrcSize;
                    }
                    if (block.hasVolumeNumber()) {
                        endArcSize += EndArcHeader.endArcVolumeNumberSize;
                    }
                    EndArcHeader endArcHead;
                    if (endArcSize > 0) {
                        final byte[] endArchBuff = safelyAllocate(endArcSize);
                        rawData.readFully(endArchBuff, endArchBuff.length);
                        endArcHead = new EndArcHeader(block, endArchBuff);
                    } else {
                        endArcHead = new EndArcHeader(block, null);
                    }
                    if (!newMhd.isMultiVolume() && !endArcHead.isValid()) {
                        throw new CorruptHeaderException("Invalid End Archive Header");
                    }
                    headers.add(endArcHead);
                    return;

                default:
                    final byte[] blockHeaderBuffer = safelyAllocate(BlockHeader.blockHeaderSize);
                    rawData.readFully(blockHeaderBuffer, blockHeaderBuffer.length);
                    BlockHeader blockHead = new BlockHeader(block, blockHeaderBuffer);

                    switch (blockHead.getHeaderType()) {
                        case FileHeader:
                        case NewSubHeader:
                            final long fileHeaderSize = blockHead.getHeaderSize(false)
                                    - BaseBlock.BaseBlockSize
                                    - BlockHeader.blockHeaderSize;
                            final byte[] fileHeaderBuffer = safelyAllocate(fileHeaderSize);
                            try {
                                rawData.readFully(fileHeaderBuffer, fileHeaderBuffer.length);
                            } catch (EOFException e) {
                                throw new CorruptHeaderException("Unexpected end of file");
                            }
                            FileHeader fileHeader = new FileHeader(blockHead, fileHeaderBuffer);
                            headers.add(fileHeader);
                            final long filePos = fileHeader.getPositionInFile()
                                    + fileHeader.getHeaderSize(isEncrypted(newMhd))
                                    + fileHeader.getFullPackSize();
                            channel.setPosition(filePos);

                            if (processedPositions.contains(filePos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(filePos);
                            break;
                        case ProtectHeader:
                            final long protHeaderSize = blockHead.getHeaderSize(false)
                                    - BaseBlock.BaseBlockSize
                                    - BlockHeader.blockHeaderSize;
                            final byte[] protHeaderBuffer = safelyAllocate(protHeaderSize);
                            rawData.readFully(protHeaderBuffer, protHeaderBuffer.length);
                            ProtectHeader ph = new ProtectHeader(blockHead, protHeaderBuffer);
                            headers.add(ph);
                            long protPos = ph.getPositionInFile()
                                    + ph.getHeaderSize(isEncrypted(newMhd))
                                    + ph.getDataSize();
                            channel.setPosition(protPos);

                            if (processedPositions.contains(protPos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(protPos);
                            break;
                        case SubHeader:
                            final byte[] subHeadbuffer = safelyAllocate(SubBlockHeader.SubBlockHeaderSize);
                            rawData.readFully(subHeadbuffer, subHeadbuffer.length);
                            final SubBlockHeader subHead = new SubBlockHeader(blockHead, subHeadbuffer);
                            subHead.print();
                            SubBlockHeaderType subType = subHead.getSubType();
                            if (subType == null) {
                                break;
                            }
                            switch (subType) {
                                case MAC_HEAD:
                                    final byte[] macHeaderBuffer = safelyAllocate((int) MacInfoHeader.MacInfoHeaderSize);
                                    rawData.readFully(macHeaderBuffer, macHeaderBuffer.length);
                                    MacInfoHeader macHeader = new MacInfoHeader(subHead, macHeaderBuffer);
                                    macHeader.print();
                                    headers.add(macHeader);
                                    break;
                                // TODO implement other subheaders
                                case BEEA_HEAD:
                                    break;
                                case EA_HEAD:
                                    final byte[] eaHeaderBuffer = safelyAllocate((int) EAHeader.EAHeaderSize);
                                    rawData.readFully(eaHeaderBuffer, eaHeaderBuffer.length);
                                    EAHeader eaHeader = new EAHeader(subHead, eaHeaderBuffer);
                                    eaHeader.print();
                                    headers.add(eaHeader);
                                    break;
                                case NTACL_HEAD:
                                case STREAM_HEAD:
                                    break;
                                case UO_HEAD:
                                    long uoToRead = subHead.getHeaderSize(false);
                                    uoToRead -= BaseBlock.BaseBlockSize;
                                    uoToRead -= BlockHeader.blockHeaderSize;
                                    uoToRead -= SubBlockHeader.SubBlockHeaderSize;
                                    byte[] uoHeaderBuffer = safelyAllocate(uoToRead);
                                    rawData.readFully(uoHeaderBuffer, uoHeaderBuffer.length);
                                    UnixOwnersHeader uoHeader = new UnixOwnersHeader(subHead, uoHeaderBuffer);
                                    uoHeader.print();
                                    headers.add(uoHeader);
                                    break;
                                default:
                                    break;
                            }
                            // Always seek past the full subblock (header + packed data) so that
                            // partially-handled subtypes (e.g. MAC_HEAD, EA_HEAD) don't leave the
                            // channel positioned mid-block, corrupting all subsequent header reads.
                            long subNewPos = subHead.getPositionInFile()
                                    + subHead.getHeaderSize(isEncrypted(newMhd))
                                    + subHead.getDataSize();
                            channel.setPosition(subNewPos);

                            if (processedPositions.contains(subNewPos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(subNewPos);
                            break;
                        default:
                            logger.warn("Unknown Header");
                            throw new NotRarArchiveException();
                    }

            }
        }
    }

    private boolean isEncrypted(final MainHeader newMhd) throws RarException {
        if (newMhd != null) {
            return newMhd.isEncrypted();
        } else {
            throw new MainHeaderNullException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BaseBlock> getHeaders() {
        return headers;
    }
}
