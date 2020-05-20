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

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.RarException.RarExceptionType;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.impl.InputStreamVolumeManager;
import com.github.junrar.io.IReadOnlyAccess;
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
import com.github.junrar.rarfile.UnixOwnersHeader;
import com.github.junrar.rarfile.UnrarHeadertype;
import com.github.junrar.unpack.ComprDataIO;
import com.github.junrar.unpack.Unpack;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
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
import java.util.Set;

/**
 * The Main Rar Class; represents a rar Archive
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class Archive implements Closeable, Iterable<FileHeader> {

    private static final Log logger = LogFactory.getLog(Archive.class);

    private static int MAX_HEADER_SIZE = 20971520; //20MB

    private IReadOnlyAccess rof;

    private final UnrarCallback unrarCallback;

    private final ComprDataIO dataIO;

    private final List<BaseBlock> headers = new ArrayList<BaseBlock>();

    private MarkHeader markHead = null;

    private MainHeader newMhd = null;

    private Unpack unpack;

    private int currentHeaderIndex;

    /** Size of packed data in current file. */
    private long totalPackedSize = 0L;

    /** Number of bytes of compressed data read from current file. */
    private long totalPackedRead = 0L;

    private VolumeManager volumeManager;
    private Volume volume;

    private FileHeader nextFileHeader;

    public Archive(final VolumeManager volumeManager) throws RarException, IOException {
        this(volumeManager, null);
    }

    /**
     * create a new archive object using the given {@link VolumeManager}
     *
     * @param volumeManager
     *            the the {@link VolumeManager} that will provide volume stream
     *            data
     * @param unrarCallback
     *          gets feedback on the extraction progress
     *
     * @throws RarException .
     * @throws IOException .
     */
    public Archive(
        final VolumeManager volumeManager,
        final UnrarCallback unrarCallback
    ) throws RarException, IOException {

        this.volumeManager = volumeManager;
        this.unrarCallback = unrarCallback;

        try {
            setVolume(this.volumeManager.nextArchive(this, null));
        } catch (IOException e) {
            try {
                close();
            } catch (IOException e1) {
                logger.error("Failed to close the archive after an internal error!");
            }
            throw e;
        } catch (RarException e) {
            try {
                close();
            } catch (IOException e1) {
                logger.error("Failed to close the archive after an internal error!");
            }
            throw e;
        }
        this.dataIO = new ComprDataIO(this);
    }

    public Archive(
        final File firstVolume,
        final UnrarCallback unrarCallback
    ) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), unrarCallback);
    }

    // public File getFile() {
    // return file;
    // }
    //
    // void setFile(File file) throws IOException {
    // this.file = file;
    // setFile(new ReadOnlyAccessFile(file), file.length());
    // }

    public Archive(final InputStream rarAsStream) throws RarException, IOException {
        this(new InputStreamVolumeManager(rarAsStream), null);
    }

    private void setFile(final IReadOnlyAccess file, final long length) throws IOException, RarException {
        this.totalPackedSize = 0L;
        this.totalPackedRead = 0L;
        close();
        this.rof = file;
        try {
            readHeaders(length);
        } catch (final Exception e) {
            logger.warn("exception in archive constructor maybe file is encrypted, corrupt or support not yet implemented", e);
            // Rethrow unsupportedRarException
            if (e instanceof RarException && ((RarException) e).getType() == RarExceptionType.unsupportedRarArchive) {
                throw (RarException) e;
            }
            // ignore exceptions to allow extraction of working files in
            // corrupt archive
        }
        // Calculate size of packed data
        for (final BaseBlock block : this.headers) {
            if (block.getHeaderType() == UnrarHeadertype.FileHeader) {
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

    public IReadOnlyAccess getRof() {
        return this.rof;
    }

    /**
     * Gets all of the headers in the archive.
     *
     * @return returns the headers.
     */
    public List<BaseBlock> getHeaders() {
        return new ArrayList<BaseBlock>(this.headers);
    }

    /**
     * @return returns all file headers of the archive
     */
    public List<FileHeader> getFileHeaders() {
        final List<FileHeader> list = new ArrayList<FileHeader>();
        for (final BaseBlock block : this.headers) {
            if (block.getHeaderType().equals(UnrarHeadertype.FileHeader)) {
                list.add((FileHeader) block);
            }
        }
        return list;
    }

    public FileHeader nextFileHeader() {
        final int n = this.headers.size();
        while (this.currentHeaderIndex < n) {
            final BaseBlock block = this.headers.get(this.currentHeaderIndex++);
            if (block.getHeaderType() == UnrarHeadertype.FileHeader) {
                return (FileHeader) block;
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
            throw new RarException(RarExceptionType.mainHeaderNull);
        }
    }

    /**
     * Read the headers of the archive
     *
     * @param fileLength
     *            Length of file.
     * @throws RarException
     */
    private void readHeaders(final long fileLength) throws IOException, RarException {
        this.markHead = null;
        this.newMhd = null;
        this.headers.clear();
        this.currentHeaderIndex = 0;
        int toRead = 0;
        //keep track of positions already processed for
        //more robustness against corrupt files
        final Set<Long> processedPositions = new HashSet<Long>();
        while (true) {
            int size = 0;
            long newpos = 0;
            final byte[] baseBlockBuffer = safelyAllocate(BaseBlock.BaseBlockSize, MAX_HEADER_SIZE);

            final long position = this.rof.getPosition();

            // Weird, but is trying to read beyond the end of the file
            if (position >= fileLength) {
                break;
            }

            // logger.info("\n--------reading header--------");
            size = this.rof.readFully(baseBlockBuffer, BaseBlock.BaseBlockSize);
            if (size == 0) {
                break;
            }
            final BaseBlock block = new BaseBlock(baseBlockBuffer);

            block.setPositionInFile(position);

            switch (block.getHeaderType()) {

                case MarkHeader:
                    this.markHead = new MarkHeader(block);
                    if (!this.markHead.isSignature()) {
                        if (markHead.getVersion() == RARVersion.V5) {
                            logger.warn("Support for rar version 5 is not yet implemented!");
                            throw new RarException(RarExceptionType.unsupportedRarArchive);
                        } else {
                            throw new RarException(RarException.RarExceptionType.badRarArchive);
                        }
                    }
                    this.headers.add(this.markHead);
                    // markHead.print();
                    break;

                case MainHeader:
                    toRead = block.hasEncryptVersion() ? MainHeader.mainHeaderSizeWithEnc
                            : MainHeader.mainHeaderSize;
                    final byte[] mainbuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    this.rof.readFully(mainbuff, toRead);
                    final MainHeader mainhead = new MainHeader(block, mainbuff);
                    this.headers.add(mainhead);
                    this.newMhd = mainhead;
                    if (this.newMhd.isEncrypted()) {
                        throw new RarException(
                                RarExceptionType.rarEncryptedException);
                    }
                    // mainhead.print();
                    break;

                case SignHeader:
                    toRead = SignHeader.signHeaderSize;
                    final byte[] signBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    this.rof.readFully(signBuff, toRead);
                    final SignHeader signHead = new SignHeader(block, signBuff);
                    this.headers.add(signHead);
                    // logger.info("HeaderType: SignHeader");

                    break;

                case AvHeader:
                    toRead = AVHeader.avHeaderSize;
                    final byte[] avBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    this.rof.readFully(avBuff, toRead);
                    final AVHeader avHead = new AVHeader(block, avBuff);
                    this.headers.add(avHead);
                    // logger.info("headertype: AVHeader");
                    break;

                case CommHeader:
                    toRead = CommentHeader.commentHeaderSize;
                    final byte[] commBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    this.rof.readFully(commBuff, toRead);
                    final CommentHeader commHead = new CommentHeader(block, commBuff);
                    this.headers.add(commHead);
                    // logger.info("method: "+commHead.getUnpMethod()+"; 0x"+
                    // Integer.toHexString(commHead.getUnpMethod()));
                    newpos = commHead.getPositionInFile()
                            + commHead.getHeaderSize();
                    if (processedPositions.contains(newpos)) {
                        throw new RarException(RarExceptionType.badRarArchive);
                    }
                    processedPositions.add(newpos);
                    this.rof.setPosition(newpos);

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
                        this.rof.readFully(endArchBuff, toRead);
                        endArcHead = new EndArcHeader(block, endArchBuff);
                        // logger.info("HeaderType: endarch\ndatacrc:"+
                        // endArcHead.getArchiveDataCRC());
                    } else {
                        // logger.info("HeaderType: endarch - no Data");
                        endArcHead = new EndArcHeader(block, null);
                    }
                    this.headers.add(endArcHead);
                    // logger.info("\n--------end header--------");
                    return;

                default:
                    final byte[] blockHeaderBuffer = safelyAllocate(BlockHeader.blockHeaderSize, MAX_HEADER_SIZE);
                    this.rof.readFully(blockHeaderBuffer, BlockHeader.blockHeaderSize);
                    final BlockHeader blockHead = new BlockHeader(block,
                            blockHeaderBuffer);

                    switch (blockHead.getHeaderType()) {
                        case NewSubHeader:
                        case FileHeader:
                            toRead = blockHead.getHeaderSize()
                                    - BlockHeader.BaseBlockSize
                                    - BlockHeader.blockHeaderSize;
                            final byte[] fileHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                            this.rof.readFully(fileHeaderBuffer, toRead);

                            final FileHeader fh = new FileHeader(blockHead, fileHeaderBuffer);
                            this.headers.add(fh);
                            newpos = fh.getPositionInFile() + fh.getHeaderSize()
                                    + fh.getFullPackSize();
                            if (processedPositions.contains(newpos)) {
                                throw new RarException(RarExceptionType.badRarArchive);
                            }
                            processedPositions.add(newpos);
                            this.rof.setPosition(newpos);
                            break;

                        case ProtectHeader:
                            toRead = blockHead.getHeaderSize()
                                    - BlockHeader.BaseBlockSize
                                    - BlockHeader.blockHeaderSize;
                            final byte[] protectHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                            this.rof.readFully(protectHeaderBuffer, toRead);
                            final ProtectHeader ph = new ProtectHeader(blockHead,
                                    protectHeaderBuffer);
                            newpos = ph.getPositionInFile() + ph.getHeaderSize()
                                    + ph.getDataSize();
                            if (processedPositions.contains(newpos)) {
                                throw new RarException(RarExceptionType.badRarArchive);
                            }
                            processedPositions.add(newpos);
                            this.rof.setPosition(newpos);
                            break;

                        case SubHeader: {
                            final byte[] subHeadbuffer = safelyAllocate(SubBlockHeader.SubBlockHeaderSize, MAX_HEADER_SIZE);
                            this.rof.readFully(subHeadbuffer,
                                    SubBlockHeader.SubBlockHeaderSize);
                            final SubBlockHeader subHead = new SubBlockHeader(blockHead,
                                    subHeadbuffer);
                            subHead.print();
                            switch (subHead.getSubType()) {
                                case MAC_HEAD: {
                                    final byte[] macHeaderbuffer = safelyAllocate(MacInfoHeader.MacInfoHeaderSize, MAX_HEADER_SIZE);
                                    this.rof.readFully(macHeaderbuffer,
                                            MacInfoHeader.MacInfoHeaderSize);
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
                                    this.rof.readFully(eaHeaderBuffer, EAHeader.EAHeaderSize);
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
                                    toRead = subHead.getHeaderSize();
                                    toRead -= BaseBlock.BaseBlockSize;
                                    toRead -= BlockHeader.blockHeaderSize;
                                    toRead -= SubBlockHeader.SubBlockHeaderSize;
                                    final byte[] uoHeaderBuffer = safelyAllocate(toRead, MAX_HEADER_SIZE);
                                    this.rof.readFully(uoHeaderBuffer, toRead);
                                    final UnixOwnersHeader uoHeader = new UnixOwnersHeader(
                                            subHead, uoHeaderBuffer);
                                    uoHeader.print();
                                    this.headers.add(uoHeader);
                                    break;
                                default:
                                    break;
                            }

                            break;
                        }
                        default:
                            logger.warn("Unknown Header");
                            throw new RarException(RarExceptionType.notRarArchive);

                    }
            }
            // logger.info("\n--------end header--------");
        }
    }

    private static byte[] safelyAllocate(final long len, final int maxSize) throws RarException {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxsize must be >= 0");
        }
        if (len < 0 || len > maxSize) {
            throw new RarException(RarExceptionType.badRarArchive);
        }
        return new byte[(int) len];
    }

    /**
     * Extract the file specified by the given header and write it to the
     * supplied output stream
     *
     * @param hd
     *            the header to be extracted
     * @param os
     *            the outputstream
     * @throws RarException .
     */
    public void extractFile(final FileHeader hd, final OutputStream os) throws RarException {
        if (!this.headers.contains(hd)) {
            throw new RarException(RarExceptionType.headerNotInArchive);
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
     * Returns an {@link InputStream} that will allow to read the file and
     * stream it. Please note that this method will create a new Thread and an a
     * pair of Pipe streams.
     *
     * @param hd the header to be extracted
     * @return inputstream
     * @throws RarException .
     * @throws IOException  if any IO error occur
     */
    public InputStream getInputStream(final FileHeader hd) throws RarException,
            IOException {
        final PipedInputStream in = new PipedInputStream(32 * 1024);
        final PipedOutputStream out = new PipedOutputStream(in);

        // creates a new thread that will write data to the pipe. Data will be
        // available in another InputStream, connected to the OutputStream.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    extractFile(hd, out);
                } catch (final RarException e) {
                } finally {
                    try {
                        out.close();
                    } catch (final IOException e) {
                    }
                }
            }
        }).start();

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
                throw new RarException(RarExceptionType.crcError);
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

    /** Close the underlying compressed file. */
    @Override
    public void close() throws IOException {
        if (this.rof != null) {
            this.rof.close();
            this.rof = null;
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
     * @param volumeManager
     *            the volumeManager to set
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

    /**
     * @param volume
     *            the volume to set
     * @throws IOException .
     * @throws RarException .
     */
    public void setVolume(final Volume volume) throws IOException, RarException {
        this.volume = volume;
        setFile(volume.getReadOnlyAccess(), volume.getLength());
    }

    @Override
    public Iterator<FileHeader> iterator() {
        return new Iterator<FileHeader>() {

            @Override
            public FileHeader next() {
                FileHeader next;
                if (Archive.this.nextFileHeader != null) {
                    next =  Archive.this.nextFileHeader;
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
