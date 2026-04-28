/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 22.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression
 * algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar;

import com.github.junrar.crc.Blake2sp;
import com.github.junrar.crypt.Rijndael;
import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.HeaderNotInArchiveException;
import com.github.junrar.exception.InitDeciphererFailedException;
import com.github.junrar.exception.MainHeaderNullException;
import com.github.junrar.exception.NotRarArchiveException;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarEncryptedException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.io.RawDataIo;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rar5.crypt.Rar5Crypto;
import com.github.junrar.rar5.header.Rar5BlockHeader;
import com.github.junrar.rar5.header.Rar5FileHeader;
import com.github.junrar.rar5.io.VInt;
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
import com.github.junrar.rarfile.RARVersion;
import com.github.junrar.rarfile.SignHeader;
import com.github.junrar.rarfile.SubBlockHeader;
import com.github.junrar.rarfile.SubBlockHeaderType;
import com.github.junrar.rarfile.UnixOwnersHeader;
import com.github.junrar.rarfile.UnrarHeadertype;
import com.github.junrar.unpack.ComprDataIO;
import com.github.junrar.unpack.Unpack;
import com.github.junrar.volume.FileVolumeManager;
import com.github.junrar.volume.InputStreamVolumeManager;
import com.github.junrar.volume.Volume;
import com.github.junrar.volume.VolumeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The Main Rar Class; represents a rar Archive
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Archive implements Closeable, Iterable<FileHeader> {

    private static final Logger logger = LoggerFactory.getLogger(Archive.class);

    private static final int MAX_HEADER_SIZE = 20971520; //20MB

    private static final int PIPE_BUFFER_SIZE = getPropertyAs(
        "junrar.extractor.buffer-size",
        Integer::parseInt,
        32 * 1024
    );

    private static final boolean USE_EXECUTOR = getPropertyAs(
        "junrar.extractor.use-executor",
        Boolean::parseBoolean,
        true
    );

    private SeekableReadOnlyByteChannel channel;

    private final UnrarCallback unrarCallback;

    private final ComprDataIO dataIO;

    private final List<BaseBlock> headers = new ArrayList<>();

    private MarkHeader markHead = null;

    private MainHeader newMhd = null;

    private Unpack unpack;

    private int currentHeaderIndex;

    /**
     * Size of packed data in current file.
     */
    private long totalPackedSize = 0L;

    /**
     * Number of bytes of compressed data read from current file.
     */
    private long totalPackedRead = 0L;

    private VolumeManager volumeManager;
    private Volume volume;

    private FileHeader nextFileHeader;
    private String password;

    public Archive(
        final VolumeManager volumeManager,
        final UnrarCallback unrarCallback,
        final String password
    ) throws RarException, IOException {

        this.volumeManager = volumeManager;
        this.unrarCallback = unrarCallback;
        this.password = password;

        try {
            setVolume(this.volumeManager.nextVolume(this, null));
        } catch (IOException | RarException e) {
            try {
                close();
            } catch (IOException e1) {
                logger.error("Failed to close the archive after an internal error!");
            }
            throw e;
        }
        this.dataIO = new ComprDataIO(this);
    }

    public Archive(final File firstVolume) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), null, null);
    }

    public Archive(final File firstVolume, final UnrarCallback unrarCallback) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), unrarCallback, null);
    }

    public Archive(final File firstVolume, final String password) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), null, password);
    }

    public Archive(final File firstVolume, final UnrarCallback unrarCallback, final String password) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), unrarCallback, password);
    }

    public Archive(final InputStream rarAsStream) throws RarException, IOException {
        this(new InputStreamVolumeManager(rarAsStream), null, null);
    }

    public Archive(final InputStream rarAsStream, final UnrarCallback unrarCallback) throws RarException, IOException {
        this(new InputStreamVolumeManager(rarAsStream), unrarCallback, null);
    }

    public Archive(final InputStream rarAsStream, final String password) throws IOException, RarException {
        this(new InputStreamVolumeManager(rarAsStream), null, password);
    }

    public Archive(final InputStream rarAsStream, final UnrarCallback unrarCallback, final String password) throws IOException, RarException {
        this(new InputStreamVolumeManager(rarAsStream), unrarCallback, password);
    }

    /**
     * Solid archive decompressor state for RAR5.
     * In solid archives, the decompressor state (window, distances) persists
     * across files, so we must reuse the same Unpack50 instance.
     */
    private com.github.junrar.rar5.unpack.Unpack50 rar5Unpacker;

    /**
     * RAR5 archive-level encryption header (type 4 CRYPT block).
     * Used for header encryption and password validation.
     */
    private com.github.junrar.rar5.header.Rar5CryptHeader rar5CryptHeader;

    /**
     * Adapts a SeekableReadOnlyByteChannel to an InputStream with a byte limit.
     * Used to feed compressed data from the channel to Unpack50.
     */
    private static final class ChannelInputStream extends java.io.InputStream {
        private final SeekableReadOnlyByteChannel channel;
        private long remaining;
        private final byte[] singleByte = new byte[1];

        ChannelInputStream(final SeekableReadOnlyByteChannel channel, final long byteCount) {
            this.channel = channel;
            this.remaining = byteCount;
        }

        @Override
        public int read() throws java.io.IOException {
            if (remaining <= 0) return -1;
            final int n = channel.read(singleByte, 0, 1);
            if (n <= 0) return -1;
            remaining--;
            return singleByte[0] & 0xFF;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws java.io.IOException {
            if (remaining <= 0) return -1;
            final int toRead = (int) Math.min(len, remaining);
            final int n = channel.read(b, off, toRead);
            if (n > 0) remaining -= n;
            return n;
        }

        @Override
        public int available() {
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }
    }

    private void setChannel(final SeekableReadOnlyByteChannel channel, final long length) throws IOException, RarException {
        this.totalPackedSize = 0L;
        this.totalPackedRead = 0L;
        close();
        this.channel = channel;

        try {
            readHeaders(length);
        } catch (UnsupportedRarEncryptedException | UnsupportedRarV5Exception | CorruptHeaderException | BadRarArchiveException e) {
            logger.warn("exception in archive constructor maybe file is encrypted, corrupt or support not yet implemented", e);
            throw e;
        } catch (final Exception e) {
            logger.warn("exception in archive constructor maybe file is encrypted, corrupt or support not yet implemented", e);
            // ignore exceptions to allow extraction of working files in corrupt archive
        }
        // Calculate size of packed data
        for (final BaseBlock block : this.headers) {
            if (block instanceof Rar5FileHeader) {
                this.totalPackedSize += ((Rar5FileHeader) block).getPackedSize();
            } else if (block instanceof FileHeader) {
                this.totalPackedSize += ((FileHeader) block).getFullPackSize();
            }
        }
        if (this.unrarCallback != null) {
            this.unrarCallback.volumeProgressChanged(this.totalPackedRead,
                this.totalPackedSize);
        }
    }

    public void bytesReadRead(final int count) {
        if (count > 0) {
            this.totalPackedRead += count;
            if (this.unrarCallback != null) {
                this.unrarCallback.volumeProgressChanged(this.totalPackedRead,
                    this.totalPackedSize);
            }
        }
    }

    public SeekableReadOnlyByteChannel getChannel() {
        return this.channel;
    }

    /**
     * Gets all of the headers in the archive.
     *
     * @return returns the headers.
     */
    public List<BaseBlock> getHeaders() {
        return new ArrayList<>(this.headers);
    }

    /**
     * @return returns all file headers of the archive (RAR4 and RAR5)
     */
    public List<FileHeader> getFileHeaders() {
        final List<FileHeader> list = new ArrayList<>();
        for (final BaseBlock block : this.headers) {
            if (block instanceof Rar5FileHeader) {
                list.add((Rar5FileHeader) block);
            } else if (block instanceof FileHeader
                    && block.getHeaderType() != null
                    && block.getHeaderType().equals(UnrarHeadertype.FileHeader)) {
                list.add((FileHeader) block);
            }
        }
        return list;
    }

    public FileHeader nextFileHeader() {
        final int n = this.headers.size();
        while (this.currentHeaderIndex < n) {
            final BaseBlock block = this.headers.get(this.currentHeaderIndex++);
            if (block instanceof Rar5FileHeader) {
                return (Rar5FileHeader) block;
            }
            if (block.getHeaderType() == UnrarHeadertype.FileHeader) {
                return (FileHeader) block;
            }
        }
        return null;
    }

    /**
     * Resets the header iteration position to the beginning.
     */
    public void reset() {
        this.currentHeaderIndex = 0;
        this.nextFileHeader = null;
    }

    /**
     * Returns the next file header entry (RAR4 or RAR5).
     *
     * @return the next file header entry, or null if no more files
     */
    public com.github.junrar.rarfile.FileHeaderEntry nextFileHeaderEntry() {
        final int n = this.headers.size();
        while (this.currentHeaderIndex < n) {
            final BaseBlock block = this.headers.get(this.currentHeaderIndex++);
            if (block instanceof com.github.junrar.rarfile.FileHeaderEntry) {
                return (com.github.junrar.rarfile.FileHeaderEntry) block;
            }
        }
        return null;
    }

    public UnrarCallback getUnrarCallback() {
        return this.unrarCallback;
    }

    /**
     * @return whether the archive is encrypted
     * @throws RarException when the main header is not present
     */
    public boolean isEncrypted() throws RarException {
        if (this.newMhd != null) {
            return this.newMhd.isEncrypted();
        } else {
            throw new MainHeaderNullException();
        }
    }

    /**
     * @return whether the archive content is password protected
     * @throws RarException when the main header is not present
     */
    public boolean isPasswordProtected() throws RarException {
        if (isEncrypted()) return true;
        return getFileHeaders().stream().anyMatch(FileHeader::isEncrypted);
    }

    /**
     * Read the headers of the archive
     *
     * @param fileLength Length of file.
     * @throws IOException, RarException
     */
    private void readHeaders(final long fileLength) throws IOException, RarException {
        this.markHead = null;
        this.newMhd = null;
        this.headers.clear();
        this.currentHeaderIndex = 0;
        int toRead = 0;
        boolean isRar5 = false;
        long rar5Position = 0; // Manual position tracking for RAR5

        //keep track of positions already processed for
        //more robustness against corrupt files
        final Set<Long> processedPositions = new HashSet<>();
        while (true) {
            int size = 0;
            long newpos = 0;
            RawDataIo rawData = new RawDataIo(channel);

            if (isRar5) {
                rar5Position = readRar5Block(rawData, rar5Position);
                if (rar5Position < 0) {
                    break;
                }
                continue;
            }

            final byte[] baseBlockBuffer = safelyAllocate(BaseBlock.BaseBlockSize, MAX_HEADER_SIZE);

            // if header is encrypted,there is a 8-byte salt before each header
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

            final long position = this.channel.getPosition();

            // Weird, but is trying to read beyond the end of the file
            if (position >= fileLength) {
                break;
            }

            // logger.info("\n--------reading header--------");
            size = rawData.readFully(baseBlockBuffer, baseBlockBuffer.length);

            if (size == 0) {
                break;
            }
            final BaseBlock block = new BaseBlock(baseBlockBuffer);

            block.setPositionInFile(position);

            UnrarHeadertype headerType = block.getHeaderType();
            if (headerType == null) {
                logger.warn("unknown block header!");
                throw new CorruptHeaderException();
            }
            switch (headerType) {

                case MarkHeader:
                    this.markHead = new MarkHeader(block);
                    if (!this.markHead.isSignature()) {
                        if (markHead.getVersion() == RARVersion.V5) {
                            isRar5 = true;
                            rar5Position = 8; // RAR5 mark header is 8 bytes
                            // RAR5 signature is 8 bytes but BaseBlock only read 7.
                            // Skip the 8th byte (0x00) to position at the first real block.
                            rawData.read();
                            // Don't increment rar5Position - the 8th byte is part of the mark header
                            // which is already accounted for in the initial position of 8.
                        } else {
                            throw new BadRarArchiveException();
                        }
                    }
                    if (!isRar5 && !markHead.isValid()) {
                        throw new CorruptHeaderException("Invalid Mark Header");
                    }
                    this.headers.add(this.markHead);
                    // markHead.print();
                    break;

                case MainHeader:
                    toRead = block.hasEncryptVersion() ? MainHeader.mainHeaderSizeWithEnc
                        : MainHeader.mainHeaderSize;
                    final byte[] mainbuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    rawData.readFully(mainbuff, mainbuff.length);
                    final MainHeader mainhead = new MainHeader(block, mainbuff);
                    this.headers.add(mainhead);
                    this.newMhd = mainhead;
                    break;

                case SignHeader:
                    toRead = SignHeader.signHeaderSize;
                    final byte[] signBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    rawData.readFully(signBuff, signBuff.length);
                    final SignHeader signHead = new SignHeader(block, signBuff);
                    this.headers.add(signHead);
                    break;

                case AvHeader:
                    toRead = AVHeader.avHeaderSize;
                    final byte[] avBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    rawData.readFully(avBuff, avBuff.length);
                    final AVHeader avHead = new AVHeader(block, avBuff);
                    this.headers.add(avHead);
                    break;

                case CommHeader:
                    toRead = CommentHeader.commentHeaderSize;
                    final byte[] commBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    rawData.readFully(commBuff, commBuff.length);
                    final CommentHeader commHead = new CommentHeader(block, commBuff);
                    this.headers.add(commHead);

                    newpos = commHead.getPositionInFile() + commHead.getHeaderSize(isEncrypted());
                    this.channel.setPosition(newpos);

                    if (processedPositions.contains(newpos)) {
                        throw new BadRarArchiveException();
                    }
                    processedPositions.add(newpos);

                    break;
                case EndArcHeader:

                    toRead = 0;
                    if (block.hasArchiveDataCRC()) {
                        toRead += EndArcHeader.endArcArchiveDataCrcSize;
                    }
                    if (block.hasVolumeNumber()) {
                        toRead += EndArcHeader.endArcVolumeNumberSize;
                    }
                    EndArcHeader endArcHead;
                    if (toRead > 0) {
                        final byte[] endArchBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                        rawData.readFully(endArchBuff, endArchBuff.length);
                        endArcHead = new EndArcHeader(block, endArchBuff);
                    } else {
                        endArcHead = new EndArcHeader(block, null);
                    }
                    if (!this.newMhd.isMultiVolume() && !endArcHead.isValid()) {
                        throw new CorruptHeaderException("Invalid End Archive Header");
                    }
                    this.headers.add(endArcHead);
                    return;

                default:
                    final byte[] blockHeaderBuffer = safelyAllocate(BlockHeader.blockHeaderSize, MAX_HEADER_SIZE);
                    rawData.readFully(blockHeaderBuffer, blockHeaderBuffer.length);
                    final BlockHeader blockHead = new BlockHeader(block,
                        blockHeaderBuffer);

                    switch (blockHead.getHeaderType()) {
                        case NewSubHeader:
                        case FileHeader:
                            toRead = blockHead.getHeaderSize(false)
                                - BlockHeader.BaseBlockSize
                                - BlockHeader.blockHeaderSize;
                            final byte[] fileHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                            try {
                                rawData.readFully(fileHeaderBuffer, fileHeaderBuffer.length);
                            } catch (EOFException e) {
                                throw new CorruptHeaderException("Unexpected end of file");
                            }

                            final FileHeader fh = new FileHeader(blockHead, fileHeaderBuffer);
                            this.headers.add(fh);
                            newpos = fh.getPositionInFile() + fh.getHeaderSize(isEncrypted()) + fh.getFullPackSize();
                            this.channel.setPosition(newpos);

                            if (processedPositions.contains(newpos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(newpos);
                            break;

                        case ProtectHeader:
                            toRead = blockHead.getHeaderSize(false)
                                - BlockHeader.BaseBlockSize
                                - BlockHeader.blockHeaderSize;
                            final byte[] protectHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                            rawData.readFully(protectHeaderBuffer, protectHeaderBuffer.length);
                            final ProtectHeader ph = new ProtectHeader(blockHead, protectHeaderBuffer);
                            newpos = ph.getPositionInFile() + ph.getHeaderSize(isEncrypted()) + ph.getDataSize();
                            this.channel.setPosition(newpos);

                            if (processedPositions.contains(newpos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(newpos);
                            break;

                        case SubHeader: {
                            final byte[] subHeadbuffer = safelyAllocate(SubBlockHeader.SubBlockHeaderSize, MAX_HEADER_SIZE);
                            rawData.readFully(subHeadbuffer, subHeadbuffer.length);
                            final SubBlockHeader subHead = new SubBlockHeader(blockHead,
                                subHeadbuffer);
                            subHead.print();
                            SubBlockHeaderType subType = subHead.getSubType();
                            if (subType == null) break;
                            switch (subType) {
                                case MAC_HEAD: {
                                    final byte[] macHeaderbuffer = safelyAllocate(MacInfoHeader.MacInfoHeaderSize, MAX_HEADER_SIZE);
                                    rawData.readFully(macHeaderbuffer, macHeaderbuffer.length);
                                    final MacInfoHeader macHeader = new MacInfoHeader(subHead,
                                        macHeaderbuffer);
                                    macHeader.print();
                                    this.headers.add(macHeader);

                                    break;
                                }
                                // TODO implement other subheaders
                                case BEEA_HEAD:
                                    break;
                                case EA_HEAD: {
                                    final byte[] eaHeaderBuffer = safelyAllocate(EAHeader.EAHeaderSize, MAX_HEADER_SIZE);
                                    rawData.readFully(eaHeaderBuffer, eaHeaderBuffer.length);
                                    final EAHeader eaHeader = new EAHeader(subHead,
                                        eaHeaderBuffer);
                                    eaHeader.print();
                                    this.headers.add(eaHeader);

                                    break;
                                }
                                case NTACL_HEAD:
                                    break;
                                case STREAM_HEAD:
                                    break;
                                case UO_HEAD:
                                    toRead = subHead.getHeaderSize(false);
                                    toRead -= BaseBlock.BaseBlockSize;
                                    toRead -= BlockHeader.blockHeaderSize;
                                    toRead -= SubBlockHeader.SubBlockHeaderSize;
                                    final byte[] uoHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                                    rawData.readFully(uoHeaderBuffer, uoHeaderBuffer.length);
                                    final UnixOwnersHeader uoHeader = new UnixOwnersHeader(
                                        subHead, uoHeaderBuffer);
                                    uoHeader.print();
                                    this.headers.add(uoHeader);
                                    break;
                                default:
                                    if (isRar5) {
                                        // RAR5 blocks are handled by readRar5Block above
                                        break;
                                    }
                                    break;
                            }

                            // Always seek past the full subblock (header + packed data) so that
                            // partially-handled subtypes (e.g. MAC_HEAD, EA_HEAD) don't leave the
                            // channel positioned mid-block, corrupting all subsequent header reads.
                            newpos = subHead.getPositionInFile() + subHead.getHeaderSize(isEncrypted()) + subHead.getDataSize();
                            this.channel.setPosition(newpos);

                            if (processedPositions.contains(newpos)) {
                                throw new BadRarArchiveException();
                            }
                            processedPositions.add(newpos);

                            break;
                        }
                        default:
                            logger.warn("Unknown Header");
                            throw new NotRarArchiveException();

                    }
            }
            // logger.info("\n--------end header--------");
        }
    }

    /**
     * Reads a RAR5 block header and processes it.
     * RAR5 blocks use a completely different format: CRC32 + vint size + vint type + vint flags.
     *
     * @param rawData the raw data reader
     * @param currentPosition the current file position (tracked manually)
     * @return the new file position after reading this block, or -1 if end of archive
     */
    private long readRar5Block(final RawDataIo rawData, long currentPosition) throws IOException, RarException {
        // Read CRC32 (4 bytes, little-endian)
        final byte[] crcBuf = new byte[4];
        if (rawData.read(crcBuf, 0, 4) < 4) {
            return -1;
        }
        currentPosition += 4;

        // Read header size vint
        long headerSize = 0;
        int shift = 0;
        int sizeBytesRead = 0;
        for (int i = 0; i < 3; i++) {
            final int b = rawData.read();
            if (b < 0) return -1;
            headerSize |= (b & 0x7FL) << shift;
            sizeBytesRead++;
            currentPosition++;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (headerSize <= 0 || headerSize > MAX_HEADER_SIZE) {
            return -1;
        }

        // Read the full header data
        final byte[] headerData = new byte[(int) headerSize];
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

        final int fieldEnd = (int) headerSize - (int) extraSize;
        final Rar5BlockHeader blockHeader = new Rar5BlockHeader(
            0, headerSize, null, flags, extraSize, dataSize, hasExtra, hasData);

        switch ((int) typeValue) {
            case 1: // ARCHIVE
                // Parse archive flags for synthetic main header
                final long archiveFlags = VInt.read(headerData, pos).getValue();
                short mainFlags = 0;
                if ((archiveFlags & 0x0004) != 0) mainFlags |= BaseBlock.MHD_SOLID;
                if ((archiveFlags & 0x0001) != 0) mainFlags |= BaseBlock.MHD_VOLUME;
                if ((archiveFlags & 0x0010) != 0) mainFlags |= BaseBlock.MHD_LOCK;
                if ((archiveFlags & 0x0008) != 0) mainFlags |= BaseBlock.MHD_PROTECT;
                final byte[] mainBlockData = new byte[BaseBlock.BaseBlockSize];
                mainBlockData[2] = 0x73; // RAR4 MainHeader type code
                mainBlockData[3] = (byte) (mainFlags & 0xFF);
                mainBlockData[4] = (byte) ((mainFlags >> 8) & 0xFF);
                mainBlockData[5] = (byte) BaseBlock.BaseBlockSize;
                mainBlockData[6] = 0x00;
                this.newMhd = new MainHeader(new BaseBlock(mainBlockData), new byte[6]);
                this.headers.add(this.newMhd);
                break;

            case 2: // FILE
            case 3: // SERVICE
                try {
                    final Rar5FileHeader fileHeader = Rar5FileHeader.parse(headerData, blockHeader, pos);
                    // Use the manually tracked position for data start
                    fileHeader.setDataPosition(currentPosition);
                    fileHeader.setPackedSize(dataSize);
                    if (typeValue == 2) {
                        this.headers.add(fileHeader);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse RAR5 file header: {}", e.getMessage());
                }
                break;

            case 4: { // CRYPT
                // Parse archive-level encryption header
                final VInt.Result cverRes = VInt.read(headerData, pos);
                int cpos = pos + cverRes.getBytesConsumed();
                final VInt.Result cflagsRes = VInt.read(headerData, cpos);
                final long cflags = cflagsRes.getValue();
                cpos += cflagsRes.getBytesConsumed();
                final int ckdfCount = headerData[cpos] & 0xFF;
                cpos++;
                final byte[] csalt = new byte[16];
                System.arraycopy(headerData, cpos, csalt, 0, 16);
                cpos += 16;
                byte[] ccheckValue = null;
                if ((cflags & 0x0001) != 0) {
                    ccheckValue = new byte[12];
                    System.arraycopy(headerData, cpos, ccheckValue, 0, 12);
                }
                this.rar5CryptHeader = new com.github.junrar.rar5.header.Rar5CryptHeader(
                    0, cflags, ckdfCount, csalt, ccheckValue);
                break;
            }

            case 5: // ENDARC
                return -1;

            default:
                if ((flags & 0x0004) == 0) {
                    logger.warn("Unknown RAR5 block type: {}", typeValue);
                }
                break;
        }

        // Skip data area
        if (hasData && dataSize > 0) {
            skipRar5Data(rawData, dataSize);
            currentPosition += dataSize;
        }

        return currentPosition;
    }

    /**
     * Reads a RAR5 block header size as vint.
     */
    private long readRar5VIntSize(final RawDataIo rawData) throws IOException {
        long value = 0;
        int shift = 0;
        for (int i = 0; i < 3; i++) {
            final int b = rawData.read();
            if (b < 0) return 0;
            value |= (b & 0x7FL) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return value;
    }

    /**
     * Skips RAR5 data area.
     */
    private void skipRar5Data(final RawDataIo rawData, final long dataSize) throws IOException {
        final byte[] buf = new byte[8192];
        long remaining = dataSize;
        while (remaining > 0) {
            final int toRead = (int) Math.min(remaining, buf.length);
            final int read = rawData.read(buf, 0, toRead);
            if (read <= 0) break;
            remaining -= read;
        }
    }

    private static byte[] safelyAllocate(final long len, final int maxSize) throws RarException {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxsize must be >= 0");
        }
        if (len < 0 || len > maxSize) {
            throw new BadRarArchiveException();
        }
        return new byte[(int) len];
    }

    /**
     * Extract the file specified by the given header and write it to the
     * supplied output stream
     *
     * @param hd the header to be extracted
     * @param os the outputstream
     * @throws RarException .
     */
    public void extractFile(final FileHeader hd, final OutputStream os) throws RarException {
        if (!this.headers.contains(hd)) {
            throw new HeaderNotInArchiveException();
        }
        try {
            doExtractFile(hd, os);
        } catch (final Exception e) {
            if (e instanceof RarException) {
                throw (RarException) e;
            } else {
                throw new RarException(e);
            }
        }
    }

    /**
     * Extract a RAR5 file header entry to the given output stream.
     *
     * @param hd the RAR5 file header entry
     * @param os the output stream
     * @throws RarException if extraction fails
     */
    public void extractFile(final com.github.junrar.rarfile.FileHeaderEntry hd,
                            final OutputStream os) throws RarException {
        if (hd instanceof Rar5FileHeader) {
            extractRar5File((Rar5FileHeader) hd, os);
        } else if (hd instanceof FileHeader) {
            extractFile((FileHeader) hd, os);
        } else {
            throw new RarException("Unsupported header type: " + hd.getClass().getName());
        }
    }

    /**
     * Extracts a RAR5 file using the Unpack50 decompressor.
     * For solid archives, reuses the same Unpack50 instance to preserve
     * decompressor state (window, distances) across files.
     */
    private void extractRar5File(final Rar5FileHeader hd, final OutputStream os) throws RarException {
        try {
            final long dataStart = hd.getDataPosition();
            final long packedSize = hd.getPackedSize();

            if (dataStart < 0 || packedSize <= 0) {
                return;
            }

            // For stored files (method 0), read raw data directly from channel
            if (hd.getCompressionMethod() == 0) {
                channel.setPosition(dataStart);
                final byte[] buf = new byte[8192];
                long remaining = packedSize;
                final java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
                while (remaining > 0) {
                    final int toRead = (int) Math.min(remaining, buf.length);
                    final int read = channel.read(buf, 0, toRead);
                    if (read <= 0) break;
                    os.write(buf, 0, read);
                    crc32.update(buf, 0, read);
                    remaining -= read;
                }

                if (hd.getDataCrc32() != null) {
                    long actualCrc = crc32.getValue();
                    long expectedCrc = hd.getDataCrc32() & 0xFFFFFFFFL;

                    final com.github.junrar.rar5.header.extra.Rar5FileCryptRecord cryptRecord = hd.getCryptRecord();
                    if (cryptRecord != null && cryptRecord.hasHashMac()) {
                        try {
                            final Rar5Crypto.DerivedKeys keys = Rar5Crypto.deriveKeys(
                                password, cryptRecord.getSalt(), cryptRecord.getKdfCount());
                            actualCrc = Rar5Crypto.convertCrc32ToMac((int) actualCrc, keys.getHashKey()) & 0xFFFFFFFFL;
                        } catch (java.security.GeneralSecurityException e) {
                            throw new RarException(e);
                        }
                    }

                    if (actualCrc != expectedCrc) {
                        throw new com.github.junrar.exception.CrcErrorException();
                    }
                }
                return;
            }

            // Check if this is a solid archive
            final boolean isSolid = newMhd != null && newMhd.isSolid();

            // Create or reuse unpacker (singleton for solid archives)
            if (rar5Unpacker == null) {
                rar5Unpacker = new com.github.junrar.rar5.unpack.Unpack50();
            }

            // For non-solid files, reset state. For solid files, preserve state.
            if (!isSolid) {
                rar5Unpacker.resetState(false);
            }

            // Handle encryption
            final com.github.junrar.rar5.header.extra.Rar5FileCryptRecord cryptRecord = hd.getCryptRecord();
            final java.io.InputStream compressedStream;
            if (cryptRecord != null) {
                // File is encrypted - derive keys and decrypt
                if (password == null || password.isEmpty()) {
                    throw new com.github.junrar.exception.UnsupportedRarEncryptedException();
                }

                final Rar5Crypto.DerivedKeys keys = Rar5Crypto.deriveKeys(
                    password, cryptRecord.getSalt(), cryptRecord.getKdfCount());

                // Validate password if check value is present
                if (cryptRecord.hasPswCheck() && cryptRecord.getPswCheck() != null) {
                    if (!Rar5Crypto.validatePassword(keys.getCheckValue(), cryptRecord.getPswCheck())) {
                        throw new com.github.junrar.exception.UnsupportedRarEncryptedException();
                    }
                }

                // Stream decryption with 32KB chunks (matching UnRAR)
                channel.setPosition(dataStart);
                final java.io.InputStream encryptedStream = new ChannelInputStream(channel, packedSize);
                compressedStream = new com.github.junrar.rar5.crypt.DecryptingInputStream(
                    encryptedStream, keys.getEncryptionKey(), cryptRecord.getIv());
            } else {
                // Non-encrypted: stream directly from channel
                channel.setPosition(dataStart);
                compressedStream = new ChannelInputStream(channel, packedSize);
            }

            // Wrap output stream for CRC32 tracking when CRC is present in header
            final java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
            final Blake2sp blake2sp = hd.getHashRecord() != null && hd.getHashRecord().isBlake2()
                ? new Blake2sp() : null;

            java.io.OutputStream crcOut = hd.getDataCrc32() != null
                ? new java.util.zip.CheckedOutputStream(os, crc32)
                : os;

            if (blake2sp != null) {
                final java.io.OutputStream blakeOut = crcOut;
                crcOut = new java.io.OutputStream() {
                    @Override
                    public void write(final int b) throws java.io.IOException {
                        blakeOut.write(b);
                        blake2sp.update(new byte[]{(byte) b});
                    }

                    @Override
                    public void write(final byte[] b, final int off, final int len) throws java.io.IOException {
                        blakeOut.write(b, off, len);
                        blake2sp.update(b, off, len);
                    }

                    @Override
                    public void flush() throws java.io.IOException {
                        blakeOut.flush();
                    }

                    @Override
                    public void close() {
                        // Don't close the underlying stream
                    }
                };
            }

            rar5Unpacker.setMaxWinSize(hd.getDictionarySize());
            rar5Unpacker.setExpectedSize(hd.getFullUnpackSize());
            rar5Unpacker.setOutputStream(crcOut);

            rar5Unpacker.doUnpack(compressedStream);

            // Verify CRC32 when present (primary verification for RAR archives)
            // Blake2sp verification is also available when both hash record and CRC32 are present
            if (hd.getDataCrc32() != null) {
                long actualCrc = crc32.getValue();
                long expectedCrc = hd.getDataCrc32() & 0xFFFFFFFFL;

                // When HashMAC flag is set, the stored CRC is HMAC-SHA256 transformed
                if (cryptRecord != null && cryptRecord.hasHashMac()) {
                    try {
                        final Rar5Crypto.DerivedKeys keys = Rar5Crypto.deriveKeys(
                            password, cryptRecord.getSalt(), cryptRecord.getKdfCount());
                        actualCrc = Rar5Crypto.convertCrc32ToMac((int) actualCrc, keys.getHashKey()) & 0xFFFFFFFFL;
                    } catch (java.security.GeneralSecurityException e) {
                        throw new RarException(e);
                    }
                }

                if (actualCrc != expectedCrc) {
                    throw new com.github.junrar.exception.CrcErrorException();
                }

                if (blake2sp != null) {
                    final byte[] actualHash = blake2sp.digest();
                    final byte[] expectedHash = hd.getHashRecord().getDigest();
                    if (expectedHash != null && actualHash.length == expectedHash.length) {
                        boolean hashMatch = true;
                        for (int i = 0; i < actualHash.length; i++) {
                            if (actualHash[i] != expectedHash[i]) {
                                hashMatch = false;
                                break;
                            }
                        }
                        if (!hashMatch) {
                            throw new com.github.junrar.exception.CrcErrorException();
                        }
                    }
                }
            }
        } catch (final Exception e) {
            if (e instanceof RarException) {
                throw (RarException) e;
            } else {
                throw new RarException(e);
            }
        }
    }

    /**
     * Decrypts RAR5 compressed data blocks using AES-256-CBC.
     *
    /**
     * Class to ensure the lazy initialization of the {@link ThreadPoolExecutor} upon first usage.<br><br>
     * <p>
     * Using a cached thread pool executor is more efficient and creating a new thread for each extraction.
     * The total number of threads will only increase if there are tasks on its queue and all current threads are busy.
     * If there are available threads, those will be reused instead of a new one being created.
     * <br><br>
     * <p>
     * Configuration options:
     * <ul>
     * <li>To avoid the possibility of too many simultaneous active threads being started, the maximum
     * number of threads can be configured through the {@code junrar.extractor.max-threads} system property.
     * The default maximum number of threads is unbounded.</li>
     * <li>The keep alive time can be configured through the {@code junrar.extractor.thread-keep-alive-seconds} system property.
     * The default is 5s.</li>
     * </ul>
     */
    private static final class ExtractorExecutorHolder {
        private ExtractorExecutorHolder() {
        }

        private static final AtomicLong threadIndex = new AtomicLong();

        /**
         * Equivalent to {@link java.util.concurrent.Executors#newCachedThreadPool()}, but customizable through system properties.
         */
        private static final ExecutorService cachedExecutorService = new ThreadPoolExecutor(
            0, getMaxThreads(),
            getThreadKeepAlive(), TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "junrar-extractor-" + threadIndex.getAndIncrement());
                t.setDaemon(true);
                return t;
            });

        private static int getMaxThreads() {
            return getPropertyAs("junrar.extractor.max-threads", Integer::parseInt, Integer.MAX_VALUE);
        }

        private static int getThreadKeepAlive() {
            return getPropertyAs("junrar.extractor.thread-keep-alive-seconds", Integer::parseInt, 5);
        }
    }

    private static <T> T getPropertyAs(String key, Function<String, T> function, T defaultValue) {
        Objects.requireNonNull(defaultValue, "default value must not be null");
        try {
            String integerString = System.getProperty(key);
            if (integerString != null && !integerString.isEmpty()) {
                return function.apply(integerString);
            }
        } catch (SecurityException | NumberFormatException e) {
            logger.error(
                "Could not parse the System Property '{}' into an '{}'. Defaulting to '{}'",
                key,
                defaultValue.getClass().getTypeName(),
                defaultValue,
                e
            );
        }
        return defaultValue;
    }

    /**
     * An empty {@link InputStream}.
     */
    private static final class EmptyInputStream extends InputStream {

        @Override
        public int available() {
            return 0;
        }

        @Override
        public int read() {
            return -1;
        }
    }

    /**
     * Returns an {@link InputStream} that will allow to read the file and stream it. <br>
     * Please note that this method will create a pair of Pipe streams and either: <br>
     *
     * <ul>
     *     <li>delegate the work to a {@link ThreadPoolExecutor}, via {@link ExtractorExecutorHolder}; or</li>
     *     <li>delegate the work to a newly created thread on each call</li>
     * </ul>
     * <p>
     * You can choose which strategy to use by setting the {@code junrar.extractor.use-executor} system property.<br>
     * Defaults to using the {@link ThreadPoolExecutor}.
     *
     * @param hd the header to be extracted
     * @return an {@link InputStream} from which you can read the uncompressed bytes
     * @throws IOException if any I/O error occur
     * @see ExtractorExecutorHolder
     */
    public InputStream getInputStream(final FileHeader hd) throws IOException {
        // If the file is empty, return an empty InputStream
        // This saves adding a task on the executor that will effectively do nothing
        if (hd.getFullUnpackSize() <= 0) {
            return new EmptyInputStream();
        }

        // Small optimization to prevent the creation of large buffers for very small files
        // Never allocate more than needed, but ensure the buffer will be at least 1-byte long
        final int bufferSize = (int) Math.max(Math.min(hd.getFullUnpackSize(), PIPE_BUFFER_SIZE), 1);

        final PipedInputStream in = new PipedInputStream(bufferSize);
        final PipedOutputStream out = new PipedOutputStream(in);

        // Data will be available in another InputStream, connected to the OutputStream
        // Delegates execution to the cached executor service.
        Runnable r = () -> {
            try {
                extractFile(hd, out);
            } catch (final RarException ignored) {
            } finally {
                try {
                    out.close();
                } catch (final IOException ignored) {
                }
            }
        };
        if (USE_EXECUTOR) {
            ExtractorExecutorHolder.cachedExecutorService.submit(r);
        } else {
            new Thread(r).start();
        }

        return in;
    }

    private void doExtractFile(FileHeader hd, final OutputStream os)
        throws RarException, IOException {
        this.dataIO.init(os);
        this.dataIO.init(hd);
        this.dataIO.setUnpFileCRC(this.isOldFormat() ? 0 : 0xffFFffFF);
        if (this.unpack == null) {
            this.unpack = new Unpack(this.dataIO);
        }
        if (!hd.isSolid()) {
            this.unpack.init(null);
        }
        this.unpack.setDestSize(hd.getFullUnpackSize());
        try {
            this.unpack.doUnpack(hd.getUnpVersion(), hd.isSolid());
            // Verify file CRC
            hd = this.dataIO.getSubHeader();
            final long actualCRC = hd.isSplitAfter() ? ~this.dataIO.getPackedCRC()
                : ~this.dataIO.getUnpFileCRC();
            final int expectedCRC = hd.getFileCRC();
            if (actualCRC != expectedCRC) {
                throw new CrcErrorException();
            }
            // if (!hd.isSplitAfter()) {
            // // Verify file CRC
            // if(~dataIO.getUnpFileCRC() != hd.getFileCRC()){
            // throw new RarException(RarExceptionType.crcError);
            // }
            // }
        } catch (final Exception e) {
            this.unpack.cleanUp();
            if (e instanceof RarException) {
                // throw new RarException((RarException)e);
                throw (RarException) e;
            } else {
                throw new RarException(e);
            }
        }
    }

    /**
     * @return returns the main header of this archive
     */
    public MainHeader getMainHeader() {
        return this.newMhd;
    }

    /**
     * @return whether the archive is old format
     */
    public boolean isOldFormat() {
        return this.markHead.isOldFormat();
    }

    /**
     * Close the underlying compressed file.
     */
    @Override
    public void close() throws IOException {
        if (this.channel != null) {
            this.channel.close();
            this.channel = null;
        }
        if (this.unpack != null) {
            this.unpack.cleanUp();
        }
    }

    /**
     * @return the volumeManager
     */
    public VolumeManager getVolumeManager() {
        return this.volumeManager;
    }

    /**
     * @param volumeManager the volumeManager to set
     */
    public void setVolumeManager(final VolumeManager volumeManager) {
        this.volumeManager = volumeManager;
    }

    /**
     * @return the volume
     */
    public Volume getVolume() {
        return this.volume;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    /**
     * @param volume the volume to set
     * @throws IOException  .
     * @throws RarException .
     */
    public void setVolume(final Volume volume) throws IOException, RarException {
        this.volume = volume;
        setChannel(volume.getChannel(), volume.getLength());
    }

    @Override
    public Iterator<FileHeader> iterator() {
        return new Iterator<FileHeader>() {

            @Override
            public FileHeader next() {
                FileHeader next;
                if (Archive.this.nextFileHeader != null) {
                    next = Archive.this.nextFileHeader;
                } else {
                    next = nextFileHeader();
                }
                return next;
            }

            @Override
            public boolean hasNext() {
                Archive.this.nextFileHeader = nextFileHeader();
                return Archive.this.nextFileHeader != null;
            }
        };
    }

}
