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

import com.github.junrar.crc.RarCRC;
import com.github.junrar.crypt.Rar5Crypt;
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
import com.github.junrar.exception.UnsupportedRarVersionException;
import com.github.junrar.exception.WrongPasswordException;
import com.github.junrar.io.Raw;
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
import com.github.junrar.rarfile.RARVersion;
import com.github.junrar.rarfile.Rar5FileHeaderReader;
import com.github.junrar.rarfile.SignHeader;
import com.github.junrar.rarfile.SubBlockHeader;
import com.github.junrar.rarfile.SubBlockHeaderType;
import com.github.junrar.rarfile.UnixOwnersHeader;
import com.github.junrar.rarfile.UnrarHeadertype;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BlockType;
import com.github.junrar.rarfile.rar5.Rar5MainHeader;
import com.github.junrar.unpack.ComprDataIO;
import com.github.junrar.unpack.Unpack;
import com.github.junrar.unpack.Unpack5;
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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * SFX-stub search bound (unrar {@code MAXSFXSIZE}, {@code d861246:rardefs.hpp} = 4 MB):
     * the RAR marker may sit behind up to this many bytes of self-extractor code.
     */
    private static final int MAXSFXSIZE = 0x400000; // 4 MB

    /** RAR5 marker length (unrar {@code SIZEOF_MARKHEAD5}, {@code d861246:headers5.hpp:4}). */
    private static final int SIZEOF_MARKHEAD5 = 8;

    // Signature classification (unrar RARFORMAT, d861246:archive.cpp:100-126).
    private static final int SIG_NONE = 0;
    private static final int SIG_RAR15 = 1;
    private static final int SIG_RAR50 = 2;
    private static final int SIG_FUTURE = 3;

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

    private RarFormat format = null;

    /**
     * True once a RAR5 HEAD_CRYPT block has been parsed on the current volume (M3.4): every
     * subsequent header is AES-256-CBC encrypted. Reset per volume in {@link #resetChannel}.
     */
    private boolean rar5HeadersEncrypted = false;

    private MainHeader newMhd = null;

    private Unpack unpack;

    /** RAR5 (method 0x50) decode engine — one per archive, sibling of {@link #unpack} (M3.7). */
    private Unpack5 unpack5;

    private int currentHeaderIndex;

    /**
     * Tracks the highest file index processed in the current solid stream.
     */
    private int lastProcessedFileIndex = -1;

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
    private char[] passwordChars;
    private final long maxDictionarySize;

    /**
     * M3.2 pre-gate test harness ({@link #testOnlyOpenSuppressingV5Gate}): suppresses the
     * RAR5 extraction gate so {@link #readHeadersRar5} can parse RAR5 headers into the list
     * instead of throwing {@link UnsupportedRarV5Exception}. Always {@code false} on every
     * public constructor path; deleted with the gate at M3.11.
     */
    private final boolean suppressV5Gate;

    /**
     * @see #Archive(VolumeManager, ArchiveOptions)
     */
    public Archive(
        final VolumeManager volumeManager,
        final ArchiveOptions options
    ) throws RarException, IOException {
        this(volumeManager, options, false);
    }

    /**
     * Canonical constructor: every other {@code Archive} constructor delegates here,
     * directly or transitively. See {@link ArchiveOptions} for the password/resource
     * configuration contract (password hygiene, {@code maxDictionarySize} budget).
     *
     * @param suppressV5Gate M3.2 test-only: see {@link #suppressV5Gate}.
     */
    Archive(
        final VolumeManager volumeManager,
        final ArchiveOptions options,
        final boolean suppressV5Gate
    ) throws RarException, IOException {

        this.suppressV5Gate = suppressV5Gate;
        this.volumeManager = volumeManager;
        this.unrarCallback = options.getUnrarCallback();
        this.passwordChars = options.getPassword();
        this.maxDictionarySize = options.getMaxDictionarySize();

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

    /**
     * @see #Archive(VolumeManager, ArchiveOptions)
     * @see ArchiveOptions#builder()
     */
    public Archive(
        final VolumeManager volumeManager,
        final UnrarCallback unrarCallback,
        final String password
    ) throws RarException, IOException {
        this(volumeManager, ArchiveOptions.builder().unrarCallback(unrarCallback).password(password).build());
    }

    public Archive(final File firstVolume) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), null, null);
    }

    public Archive(final File firstVolume, final UnrarCallback unrarCallback) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), unrarCallback, null);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #Archive(File, ArchiveOptions)} with a {@code char[]} password via
     * {@link ArchiveOptions#builder()}.
     */
    public Archive(final File firstVolume, final String password) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), null, password);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer {@link ArchiveOptions}
     * (via {@link ArchiveOptions#builder()}) with a {@code char[]} password.
     */
    public Archive(final File firstVolume, final UnrarCallback unrarCallback, final String password) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), unrarCallback, password);
    }

    public Archive(final File firstVolume, final ArchiveOptions options) throws RarException, IOException {
        this(new FileVolumeManager(firstVolume), options);
    }

    public Archive(final InputStream rarAsStream) throws RarException, IOException {
        this(new InputStreamVolumeManager(rarAsStream), null, null);
    }

    public Archive(final InputStream rarAsStream, final UnrarCallback unrarCallback) throws RarException, IOException {
        this(new InputStreamVolumeManager(rarAsStream), unrarCallback, null);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer
     * {@link #Archive(InputStream, ArchiveOptions)} with a {@code char[]} password via
     * {@link ArchiveOptions#builder()}.
     */
    public Archive(final InputStream rarAsStream, final String password) throws IOException, RarException {
        this(new InputStreamVolumeManager(rarAsStream), null, password);
    }

    /**
     * A {@code String} password cannot be wiped from memory; prefer {@link ArchiveOptions}
     * (via {@link ArchiveOptions#builder()}) with a {@code char[]} password.
     */
    public Archive(final InputStream rarAsStream, final UnrarCallback unrarCallback, final String password) throws IOException, RarException {
        this(new InputStreamVolumeManager(rarAsStream), unrarCallback, password);
    }

    public Archive(final InputStream rarAsStream, final ArchiveOptions options) throws IOException, RarException {
        this(new InputStreamVolumeManager(rarAsStream), options);
    }

    /**
     * M3.2 pre-gate test harness (issue #23): opens {@code firstVolume} with the RAR5
     * extraction gate suppressed, so a RAR5 archive parses its headers instead of throwing
     * {@link UnsupportedRarV5Exception}. Package-private on purpose -- the tests live in
     * {@code com.github.junrar}, so no system property or production-reachable flag is
     * needed. It suppresses <em>only</em> the V5 throw; every other failure mode (corrupt
     * header, bad archive) behaves exactly as on the public path. Deleted with the gate at
     * M3.11.
     */
    static Archive testOnlyOpenSuppressingV5Gate(final File firstVolume) throws RarException, IOException {
        return testOnlyOpenSuppressingV5Gate(firstVolume, ArchiveOptions.builder().build());
    }

    /**
     * M3.7 test-only hook: the current backing capacity of the RAR5 decode window, i.e. the bytes
     * actually allocated (never the header-declared window size). Proves the growth-capped,
     * never-header-eager allocation policy at the archive level (review B-S3): a 1 GB-claim archive
     * that writes only kilobytes keeps a kilobyte-scale window. {@code -1} if no RAR5 file was
     * extracted. Deleted with the pre-gate harness at M3.11.
     */
    int testOnlyLastUnpack5WindowCapacity() {
        return (this.unpack5 == null || this.unpack5.window() == null) ? -1 : this.unpack5.window().capacity();
    }

    /** @see #testOnlyOpenSuppressingV5Gate(File) */
    static Archive testOnlyOpenSuppressingV5Gate(final File firstVolume, final ArchiveOptions options) throws RarException, IOException {
        return new Archive(new FileVolumeManager(firstVolume), options, true);
    }

    private void setChannel(final SeekableReadOnlyByteChannel channel, final long length) throws IOException, RarException {
        this.totalPackedSize = 0L;
        this.totalPackedRead = 0L;
        resetChannel();
        this.channel = channel;
        try {
            readHeaders(length);
        } catch (UnsupportedRarEncryptedException | UnsupportedRarV5Exception | UnsupportedRarVersionException
                 | CorruptHeaderException | BadRarArchiveException | WrongPasswordException e) {
            logger.warn("exception in archive constructor maybe file is encrypted, corrupt or support not yet implemented", e);
            throw e;
        } catch (final Exception e) {
            logger.warn("exception in archive constructor maybe file is encrypted, corrupt or support not yet implemented", e);
            // ignore exceptions to allow extraction of working files in corrupt archive
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
     * @return returns all file headers of the archive
     */
    public List<FileHeader> getFileHeaders() {
        final List<FileHeader> list = new ArrayList<>();
        for (final BaseBlock block : this.headers) {
            // UnrarHeadertype.equals(BaseBlock) rather than the reverse (M3.3, issue #24): a
            // RAR5 MAIN/CRYPT/ENDARC block's inherited RAR3 headerType byte is never set (those
            // facts live on Rar5MainHeader's getRar5Type() instead), so getHeaderType() is null
            // for them -- null.equals(...) would NPE, UnrarHeadertype.FileHeader.equals(null)
            // is just false.
            if (UnrarHeadertype.FileHeader.equals(block.getHeaderType())) {
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
        if (this.format == RarFormat.RAR50) {
            // RAR5 has no RAR3 MainHeader; header encryption is tracked by the HEAD_CRYPT block.
            return this.rar5HeadersEncrypted;
        }
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
     * @return the detected {@link RarFormat} of the current volume, or {@code null} if the
     *         archive has not been (successfully) opened. Post-open this is always
     *         {@link RarFormat#RAR15} until the V5 extraction gate is lifted (RAR5 archives
     *         throw {@link UnsupportedRarV5Exception} during header parsing).
     */
    public RarFormat getFormat() {
        return this.format;
    }

    /**
     * unrar {@code Archive::IsArchive}/{@code IsSignature} ({@code d861246:archive.cpp
     * :100-190}): locate the RAR marker, tolerating an SFX stub of up to {@link #MAXSFXSIZE}
     * bytes ahead of it, classify the format from the signature's version byte, and seek the
     * channel to the marker so {@link #readHeaders} reads it next. The V5 extraction gate is
     * unaffected -- a RAR5 marker still throws {@link UnsupportedRarV5Exception} once the
     * header loop parses it.
     *
     * @param fileLength the current volume's length (bounds the SFX scan).
     * @return the detected {@link RarFormat}.
     * @throws UnsupportedRarVersionException for a future RAR format (version byte 2..4).
     * @throws BadRarArchiveException         if no signature is found within the bound.
     */
    private RarFormat detectFormatAndSeek(final long fileLength) throws IOException, RarException {
        // Fast path: signature at offset 0 (the common, non-SFX case). Avoids the 4 MB scan.
        this.channel.setPosition(0);
        final byte[] head = new byte[BaseBlock.BaseBlockSize];
        final int headRead = fill(head, 0, head.length);
        final int type0 = signatureType(head, 0, headRead);
        if (type0 != SIG_NONE) {
            this.channel.setPosition(0);
            return toFormat(type0);
        }

        // SFX path: scan for the marker within the first MAXSFXSIZE bytes, starting at
        // offset 1 (offset 0 was just ruled out). Bounded read/alloc -- a signature-free
        // file reads at most MAXSFXSIZE bytes then fails (no unbounded scan).
        final int scanLen = (int) Math.min(Math.max(fileLength - 1, 0), MAXSFXSIZE);
        final byte[] buffer = new byte[scanLen];
        this.channel.setPosition(1);
        final int filled = fill(buffer, 0, scanLen);
        for (int i = 0; i < filled; i++) {
            if ((buffer[i] & 0xff) != 0x52) {
                continue;
            }
            final int type = signatureType(buffer, i, filled - i);
            if (type != SIG_NONE) {
                this.channel.setPosition(1L + i);
                return toFormat(type);
            }
        }
        throw new BadRarArchiveException();
    }

    /**
     * Reads up to {@code len} bytes into {@code buffer} at {@code offset}, looping until the
     * request is satisfied or the channel is exhausted (a single {@code read} may return
     * fewer bytes than asked).
     *
     * @return the number of bytes actually read.
     */
    private int fill(final byte[] buffer, final int offset, final int len) throws IOException {
        int filled = 0;
        while (filled < len) {
            final int n = this.channel.read(buffer, offset + filled, len - filled);
            if (n <= 0) {
                break;
            }
            filled += n;
        }
        return filled;
    }

    private RarFormat toFormat(final int signatureType) throws UnsupportedRarVersionException {
        switch (signatureType) {
            case SIG_RAR50:
                return RarFormat.RAR50;
            case SIG_FUTURE:
                throw new UnsupportedRarVersionException();
            default:
                return RarFormat.RAR15;
        }
    }

    /**
     * unrar {@code Archive::IsSignature} ({@code d861246:archive.cpp:100-126}): classify the
     * bytes at {@code d[off..off+len)} as a RAR marker. Returns one of {@link #SIG_NONE},
     * {@link #SIG_RAR15}, {@link #SIG_RAR50}, {@link #SIG_FUTURE}. {@code len} is the number
     * of readable bytes from {@code off}, so short buffers never read past their content.
     */
    private static int signatureType(final byte[] d, final int off, final int len) {
        if (len < 1 || (d[off] & 0xff) != 0x52) {
            return SIG_NONE;
        }
        // Old RAR 1.4 marker: 52 45 7e 5e (RARFMT14 -- part of the classic family).
        if (len >= 4 && (d[off + 1] & 0xff) == 0x45 && (d[off + 2] & 0xff) == 0x7e
            && (d[off + 3] & 0xff) == 0x5e) {
            return SIG_RAR15;
        }
        // Modern marker: 52 61 72 21 1a 07 + version byte.
        if (len >= 7 && (d[off + 1] & 0xff) == 0x61 && (d[off + 2] & 0xff) == 0x72
            && (d[off + 3] & 0xff) == 0x21 && (d[off + 4] & 0xff) == 0x1a
            && (d[off + 5] & 0xff) == 0x07) {
            final int version = d[off + 6] & 0xff;
            if (version == 0) {
                return SIG_RAR15;
            }
            if (version == 1) {
                return SIG_RAR50;
            }
            if (version > 1 && version < 5) {
                return SIG_FUTURE;
            }
        }
        return SIG_NONE;
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

        // Locate the marker (skipping any SFX stub) and classify the format before the
        // header loop; also seeks the channel to the marker.
        this.format = detectFormatAndSeek(fileLength);

        // RAR5 headers are vint-encoded and incompatible with the RAR3 BaseBlock loop below;
        // dispatch to the dedicated RAR5 framework (M3.2, issue #23). The V5 extraction gate
        // moves with the branch -- readHeadersRar5 throws UnsupportedRarV5Exception once the
        // headers are parsed, unless the M3.2 test harness suppresses it.
        if (this.format == RarFormat.RAR50) {
            readHeadersRar5(fileLength);
            return;
        }

        //keep track of positions already processed for
        //more robustness against corrupt files
        final Set<Long> processedPositions = new HashSet<>();
        while (true) {
            int size = 0;
            long newpos = 0;
            RawDataIo rawData = new RawDataIo(channel);
            final byte[] baseBlockBuffer = safelyAllocate(BaseBlock.BaseBlockSize, MAX_HEADER_SIZE);

            // if header is encrypted,there is a 8-byte salt before each header
            if (newMhd != null && newMhd.isEncrypted()) {
                byte[] salt = new byte[8];
                rawData.readFully(salt, 8);
                try {
                    Cipher cipher = Rijndael.buildDecipherer(passwordAsString(), salt);
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
                            logger.warn("Support for rar version 5 is not yet implemented!");
                            throw new UnsupportedRarV5Exception();
                        } else {
                            throw new BadRarArchiveException();
                        }
                    }
                    if (!markHead.isValid()) {
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
                    verifyHeaderCrc(mainhead, fileLength, baseBlockBuffer, mainbuff);
                    this.headers.add(mainhead);
                    this.newMhd = mainhead;
                    break;

                case SignHeader:
                    // Upstream-exempt (HEAD3_SIGN): no header-CRC verification at all.
                    toRead = SignHeader.signHeaderSize;
                    final byte[] signBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                    rawData.readFully(signBuff, signBuff.length);
                    final SignHeader signHead = new SignHeader(block, signBuff);
                    this.headers.add(signHead);
                    break;

                case AvHeader:
                    // Upstream-exempt (HEAD3_AV): "Old AV header does not have header
                    // CRC properly set" (d861246:arcread.cpp:514-523).
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
                    verifyHeaderCrc(commHead, fileLength, baseBlockBuffer, commBuff);
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
                    final byte[] endArchBuff;
                    if (toRead > 0) {
                        endArchBuff = safelyAllocate(toRead, MAX_HEADER_SIZE);
                        rawData.readFully(endArchBuff, endArchBuff.length);
                        endArcHead = new EndArcHeader(block, endArchBuff);
                    } else {
                        endArchBuff = new byte[0];
                        endArcHead = new EndArcHeader(block, null);
                    }
                    if (!this.newMhd.isMultiVolume() && !endArcHead.isValid()) {
                        throw new CorruptHeaderException("Invalid End Archive Header");
                    }
                    verifyHeaderCrc(endArcHead, fileLength, baseBlockBuffer, endArchBuff);
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
                            // unrar CRCProcessedOnly (d861246:arcread.cpp:268,430-431):
                            // an old-style embedded comment (LHD_COMMENT) on a genuine
                            // FILE header leaves unparsed trailing bytes past
                            // getParsedLength() -- cover only the processed prefix then.
                            // NEWSUB_HEAD always consumes its whole buffer as subData,
                            // so the distinction is moot there; always use the full
                            // buffer.
                            final int fileHeaderCoverage = (fh.isFileHeader() && fh.hasComment())
                                ? BaseBlock.BaseBlockSize + BlockHeader.blockHeaderSize + fh.getParsedLength()
                                : -1;
                            verifyHeaderCrc(fh, fileLength, fileHeaderCoverage, baseBlockBuffer, blockHeaderBuffer, fileHeaderBuffer);
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
                            verifyHeaderCrc(ph, fileLength, baseBlockBuffer, blockHeaderBuffer, protectHeaderBuffer);
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
                                    verifyHeaderCrc(macHeader, fileLength, baseBlockBuffer, blockHeaderBuffer, subHeadbuffer, macHeaderbuffer);
                                    this.headers.add(macHeader);

                                    break;
                                }
                                // TODO implement other subheaders
                                case BEEA_HEAD:
                                    // Not parsed (junrar never reads BEEA_HEAD's payload,
                                    // unlike unrar) -- no CRC check: our read stops short
                                    // of unrar's true coverage, so a partial-buffer check
                                    // would false-positive against a real archive.
                                    break;
                                case EA_HEAD: {
                                    final byte[] eaHeaderBuffer = safelyAllocate(EAHeader.EAHeaderSize, MAX_HEADER_SIZE);
                                    rawData.readFully(eaHeaderBuffer, eaHeaderBuffer.length);
                                    final EAHeader eaHeader = new EAHeader(subHead,
                                        eaHeaderBuffer);
                                    eaHeader.print();
                                    verifyHeaderCrc(eaHeader, fileLength, baseBlockBuffer, blockHeaderBuffer, subHeadbuffer, eaHeaderBuffer);
                                    this.headers.add(eaHeader);

                                    break;
                                }
                                case NTACL_HEAD:
                                    // Same rationale as BEEA_HEAD: not parsed here.
                                    break;
                                case STREAM_HEAD:
                                    // Same rationale as BEEA_HEAD: not parsed here.
                                    break;
                                case UO_HEAD:
                                    // Upstream-exempt (HEAD3_OLDSERVICE + UO_HEAD): "Old
                                    // Unix owners header didn't include string fields into
                                    // header size, but included them into CRC, so it
                                    // couldn't be verified with generic approach here"
                                    // (d861246:arcread.cpp:514-523).
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
     * RAR5 header-read loop (M3.2, issue #23; unrar {@code ReadHeader50},
     * {@code 8f437ab:arcread.cpp:555-...}, boundary + CRC at {@code d861246:arcread.cpp
     * :634-707}). Consumes the 8-byte RAR5 marker, then eagerly parses each block
     * ({@code CRC32 | vint HeadSize | vint Type | vint Flags [| vint ExtraSize][| vint
     * DataSize]}) into the shared {@code headers} list, seeking past each block's packed
     * data and guarding against a repeated stream position (S1/C6 parity, no-go S1). Stops
     * at the end-of-archive block, exactly as the RAR3 loop's {@code EndArcHeader} case
     * terminates.
     * <p>
     * The buffer for each block is capped at {@link Rar5BaseBlock#MAX_HEADER_SIZE_RAR5} (not
     * the generic {@link #MAX_HEADER_SIZE}); the 2 MB / 3-byte-size-vint bound is enforced by
     * {@link Rar5BaseBlock#checkHeaderSize} <em>before</em> that allocation.
     * <p>
     * The V5 extraction gate lives here now (the RAR3 loop's gate at the {@code MarkHeader}
     * case is unreachable for a RAR5 archive, which branches away before it). The gate is
     * checked <em>before</em> any parsing so production behavior is byte-identical to
     * pre-M3.2: every RAR5 archive -- valid, truncated or corrupt -- surfaces
     * {@link UnsupportedRarV5Exception}, never a parse-time {@link CorruptHeaderException}.
     * The parse loop below runs only under the M3.2 test harness ({@link #suppressV5Gate}),
     * which the M3.3-M3.10 archive-level acceptance lines drive; the corpus flips at M3.11.
     */
    private void readHeadersRar5(final long fileLength) throws IOException, RarException {
        if (!this.suppressV5Gate) {
            logger.warn("Support for rar version 5 is not yet implemented!");
            throw new UnsupportedRarV5Exception();
        }

        // detectFormatAndSeek left the channel at the marker; consume and re-validate it.
        final byte[] marker = new byte[SIZEOF_MARKHEAD5];
        if (fill(marker, 0, SIZEOF_MARKHEAD5) < SIZEOF_MARKHEAD5
            || signatureType(marker, 0, SIZEOF_MARKHEAD5) != SIG_RAR50) {
            throw new CorruptHeaderException("Invalid RAR5 marker");
        }

        final Set<Long> processedPositions = new HashSet<>();
        // Non-null once a HEAD_CRYPT block is parsed: every subsequent header is then AES-256-CBC
        // encrypted with its own prepended 16-byte IV (unrar arcread.cpp:559-631).
        Rar5HeaderCrypt crypto = null;
        while (true) {
            final long position = this.channel.getPosition();
            if (position >= fileLength) {
                break;
            }

            final boolean encrypted = crypto != null;
            final byte[] headerBuffer;
            if (encrypted) {
                headerBuffer = readEncryptedRar5Header(crypto);
                if (headerBuffer == null) {
                    break;
                }
            } else {
                final byte[] first = new byte[Rar5BaseBlock.FIRST_READ_SIZE];
                final int got = fill(first, 0, Rar5BaseBlock.FIRST_READ_SIZE);
                if (got == 0) {
                    break;
                }
                if (got < Rar5BaseBlock.FIRST_READ_SIZE) {
                    throw new CorruptHeaderException("Truncated RAR5 block header");
                }
                final int headerSize = Rar5BaseBlock.checkHeaderSize(first);
                headerBuffer = safelyAllocate(headerSize, Rar5BaseBlock.MAX_HEADER_SIZE_RAR5);
                System.arraycopy(first, 0, headerBuffer, 0, Rar5BaseBlock.FIRST_READ_SIZE);
                final int rest = headerSize - Rar5BaseBlock.FIRST_READ_SIZE;
                if (fill(headerBuffer, Rar5BaseBlock.FIRST_READ_SIZE, rest) < rest) {
                    throw new CorruptHeaderException("Truncated RAR5 header body");
                }
            }
            final int headerSize = headerBuffer.length;

            if (encrypted) {
                // The decrypted header's CRC is the only wrong-password signal under CBC (unrar
                // maps a mismatch to FailedHeaderDecryption, arcread.cpp:691-696) -- verify it
                // here so a wrong key surfaces as WrongPasswordException, while a CRC-valid but
                // structurally broken header still falls through to CorruptHeaderException below.
                final int storedCrc = Raw.readIntLittleEndian(headerBuffer, 0);
                final int computedCrc = RarCRC.computeHeaderCrc32(headerBuffer, 4, headerBuffer.length - 4);
                if (storedCrc != computedCrc) {
                    throw new WrongPasswordException("RAR5 encrypted header CRC mismatch (wrong password or corrupt header)");
                }
            }
            // CRC already validated for the encrypted path, still record-vs-fatal for the
            // unencrypted path -- pass encrypted=false either way (a wrong key never reaches here).
            final Rar5BaseBlock base = Rar5BaseBlock.parse(headerBuffer, false);
            final Rar5BlockType rar5Type = base.getRar5Type();
            // Captured before dispatch so the seek math below is unaffected by which typed
            // subclass (if any) the block turns into (M3.3, issue #24).
            final long rar5DataSize = base.getDataSize();

            final BaseBlock block;
            if (rar5Type == Rar5BlockType.MAIN || rar5Type == Rar5BlockType.CRYPT || rar5Type == Rar5BlockType.ENDARC) {
                block = Rar5MainHeader.from(base, headerBuffer);
            } else if (rar5Type == Rar5BlockType.FILE || rar5Type == Rar5BlockType.SERVICE) {
                block = Rar5FileHeaderReader.read(base, headerBuffer);
            } else {
                block = base;
            }
            block.setPositionInFile(position);
            this.headers.add(block);

            // The HEAD_CRYPT block itself is plaintext; header encryption starts with the NEXT
            // block (unrar sets Encrypted=true only after reading it, arcread.cpp:758).
            if (rar5Type == Rar5BlockType.CRYPT && block instanceof Rar5MainHeader) {
                crypto = Rar5HeaderCrypt.from((Rar5MainHeader) block);
                this.rar5HeadersEncrypted = true;
            }

            // Encrypted headers consume the 16-byte IV plus the header padded up to the AES block
            // size (unrar FullHeaderSize, archive.cpp:292-302 -- align-16 on BOTH paths, manual 4.12).
            final long consumed = encrypted ? (Rar5Crypt.SIZE_INITV + alignTo16(headerSize)) : headerSize;
            // The packed data starts right after the on-disk header; record it for the extractor
            // (M3.7) so encrypted-header entries locate their data past the IV + AES padding too.
            if (block instanceof FileHeader) {
                ((FileHeader) block).setRar5DataStartOffset(position + consumed);
            }
            final long newpos = position + consumed + rar5DataSize;
            // consumed is always positive, so newpos <= position only when the DataSize vint is
            // negative-as-signed (>= 2^63) or overflows the sum -- a hostile pointer that would
            // seek backward and spin the loop. unrar rejects NextBlockPos <= CurBlockPos as a
            // broken header (arcread.cpp); do the same before touching the channel.
            if (newpos <= position) {
                throw new CorruptHeaderException("RAR5 block does not advance (corrupt DataSize)");
            }
            this.channel.setPosition(newpos);
            if (processedPositions.contains(newpos)) {
                throw new BadRarArchiveException();
            }
            processedPositions.add(newpos);

            if (rar5Type == Rar5BlockType.ENDARC) {
                break;
            }
        }
    }

    private static int alignTo16(final int size) {
        return (size + 15) & ~15;
    }

    /**
     * The archive-level RAR5 header-encryption facts carried from the HEAD_CRYPT block to every
     * subsequent encrypted header (M3.4). The KDF salt/count are the same for the whole archive;
     * only the per-header IV varies, so the derived key is served from {@link Rar5Crypt}'s cache.
     */
    private static final class Rar5HeaderCrypt {
        final byte[] salt16;
        final int lg2Count;
        final boolean usePswCheck;
        final byte[] pswCheck;

        private Rar5HeaderCrypt(final byte[] salt16, final int lg2Count, final boolean usePswCheck, final byte[] pswCheck) {
            this.salt16 = salt16;
            this.lg2Count = lg2Count;
            this.usePswCheck = usePswCheck;
            this.pswCheck = pswCheck;
        }

        static Rar5HeaderCrypt from(final Rar5MainHeader h) {
            return new Rar5HeaderCrypt(h.getSalt16(), h.getLg2Count(), h.isUsePswCheck(), h.getPswCheck());
        }
    }

    /**
     * Reads and AES-256-CBC-decrypts one RAR5 header (unrar {@code ReadHeader50}'s decrypt path,
     * {@code arcread.cpp:559-631}): consume the 16-byte IV, verify the password check value
     * against the HEAD_CRYPT block, then decrypt through {@link RawDataIo} (which rounds reads up
     * to the AES block boundary, manual &sect;4.12). The returned buffer is exactly HeadSize bytes.
     *
     * @return the decrypted header buffer, or {@code null} at a clean end of channel
     * @throws WrongPasswordException on a missing password or a password-check mismatch
     */
    private byte[] readEncryptedRar5Header(final Rar5HeaderCrypt crypto) throws IOException, RarException {
        if (this.passwordChars == null) {
            // unrar's own passwordless -hp open reports "Incorrect password" (probed 2026-07-17).
            throw new WrongPasswordException("Missing password for header-encrypted RAR5 archive");
        }
        final byte[] iv = new byte[Rar5Crypt.SIZE_INITV];
        final int ivGot = fill(iv, 0, iv.length);
        if (ivGot == 0) {
            return null;
        }
        if (ivGot < iv.length) {
            throw new CorruptHeaderException("Truncated RAR5 header IV");
        }

        final byte[] pwdUtf8 = Rar5Crypt.passwordToUtf8(this.passwordChars);
        final Rar5Crypt.Kdf kdf;
        final Cipher cipher;
        try {
            kdf = Rar5Crypt.deriveKey(pwdUtf8, crypto.salt16, crypto.lg2Count);
            cipher = Rar5Crypt.buildDecipherer(kdf.aesKey, iv);
        } catch (final GeneralSecurityException e) {
            throw new InitDeciphererFailedException(e);
        } finally {
            Arrays.fill(pwdUtf8, (byte) 0);
        }
        if (crypto.usePswCheck && !Arrays.equals(kdf.pswCheck, crypto.pswCheck)) {
            throw new WrongPasswordException("RAR5 password check failed");
        }

        final RawDataIo rawData = new RawDataIo(this.channel);
        rawData.setCipher(cipher);
        final byte[] first = new byte[Rar5BaseBlock.FIRST_READ_SIZE];
        if (rawData.readFully(first, first.length) < first.length) {
            throw new CorruptHeaderException("Truncated RAR5 encrypted header");
        }
        final int headerSize = Rar5BaseBlock.checkHeaderSize(first);
        final byte[] headerBuffer = safelyAllocate(headerSize, Rar5BaseBlock.MAX_HEADER_SIZE_RAR5);
        System.arraycopy(first, 0, headerBuffer, 0, Rar5BaseBlock.FIRST_READ_SIZE);
        final int rest = headerSize - Rar5BaseBlock.FIRST_READ_SIZE;
        if (rest > 0) {
            final byte[] tail = new byte[rest];
            if (rawData.readFully(tail, rest) < rest) {
                throw new CorruptHeaderException("Truncated RAR5 encrypted header body");
            }
            System.arraycopy(tail, 0, headerBuffer, Rar5BaseBlock.FIRST_READ_SIZE, rest);
        }
        return headerBuffer;
    }

    /**
     * unrar RAR3 header-CRC verification (P0.7, issue #12; {@code d861246:arcread.cpp
     * :514-545}, math = {@code GetCRC15} at {@code d861246:rawread.cpp}): full coverage
     * of every byte in {@code segments}. See
     * {@link #verifyHeaderCrc(BaseBlock, long, int, byte[]...)}.
     */
    private void verifyHeaderCrc(final BaseBlock block, final long fileLength, final byte[]... segments) throws IOException, CorruptHeaderException {
        verifyHeaderCrc(block, fileLength, -1, segments);
    }

    /**
     * unrar RAR3 header-CRC verification (P0.7, issue #12; {@code d861246:arcread.cpp
     * :431-445} file-header check, {@code :514-545} generic check + exemptions;
     * {@code d861246:rawread.cpp GetCRC15}): 16-bit CRC over header bytes
     * [2, coverageLength). A mismatch is <em>recorded</em> on {@code block}
     * ({@link BaseBlock#setBrokenHeader}) and logged; unencrypted headers keep parsing
     * (upstream "warn and continue" tolerance). Encrypted headers are fatal at open
     * (upstream: decrypt succeeds, CRC still fails to match -> stop). EndArc headers
     * with {@code EARC_REVSPACE} get the trailing-7-zero-bytes recovery check before
     * being marked broken.
     * <p>
     * Callers skip this method entirely for upstream-exempt types (SIGN, AV, old-Unix-
     * owner sub-blocks -- "Old AV header does not have header CRC properly set") and for
     * sub-block types junrar does not fully parse (BEEA_HEAD/NTACL_HEAD/STREAM_HEAD --
     * a partial read would false-positive against a real archive's true, fuller-coverage
     * CRC) and for {@link MarkHeader} (no CRC concept, its own {@code isSignature}/
     * {@code isValid} checks the magic bytes instead).
     * <p>
     * Divergence (conscious, issue #12): unrar warns and lets the file data CRC decide
     * at extract time for a broken FILE/NEWSUB header; junrar instead throws
     * {@link CorruptHeaderException} when a {@link BaseBlock#isBrokenHeader()} entry is
     * extracted (see {@link #doExtractFile}) -- junrar has no CLI warning channel, and
     * silent garbage extraction would be worse.
     *
     * @param block          the freshly parsed header (its own headCRC is compared)
     * @param fileLength     the archive's total length (EndArc REVSPACE recovery seeks
     *                       to {@code fileLength - 7})
     * @param coverageLength number of header bytes to feed the CRC (counted from the
     *                       start of {@code segments}, concatenated), or a negative
     *                       value to cover every byte in {@code segments}
     * @param segments       the raw byte buffers that make up this header, in read
     *                       order (base block, block header, type-specific)
     */
    private void verifyHeaderCrc(final BaseBlock block, final long fileLength, final int coverageLength, final byte[]... segments) throws IOException, CorruptHeaderException {
        int totalLength = 0;
        for (final byte[] segment : segments) {
            totalLength += segment.length;
        }
        final byte[] header = new byte[totalLength];
        int pos = 0;
        for (final byte[] segment : segments) {
            System.arraycopy(segment, 0, header, pos, segment.length);
            pos += segment.length;
        }
        final int coverage = coverageLength >= 0 ? coverageLength : totalLength;
        final short expected = RarCRC.computeHeaderCrc16(header, 2, coverage - 2);
        if (block.getHeadCRC() == expected) {
            return;
        }
        if (block.getHeaderType() == UnrarHeadertype.EndArcHeader
            && block.hasRevSpace()
            && isEndArcRevSpaceRecovered(fileLength)) {
            return;
        }
        block.setBrokenHeader(true);
        logger.warn("Header CRC mismatch for {} at position {}", block.getHeaderType(), block.getPositionInFile());
        if (this.newMhd != null && this.newMhd.isEncrypted()) {
            throw new CorruptHeaderException("Header CRC mismatch on encrypted header at position " + block.getPositionInFile());
        }
    }

    /**
     * unrar's ENDARC {@code EARC_REVSPACE} recovery ({@code d861246:arcread.cpp
     * :525-535}): a REV-recovery-volume archive's last 7 bytes can legitimately be zero
     * (REV files stash their own metadata there), which would otherwise fail the generic
     * header-CRC check; treat that specific shape as recovered, not broken.
     */
    private boolean isEndArcRevSpaceRecovered(final long fileLength) throws IOException {
        if (fileLength < 7) {
            return false;
        }
        final long savedPosition = this.channel.getPosition();
        try {
            this.channel.setPosition(fileLength - 7);
            for (int i = 0; i < 7; i++) {
                if (this.channel.read() != 0) {
                    return false;
                }
            }
            return true;
        } finally {
            this.channel.setPosition(savedPosition);
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
        List<FileHeader> fileHeaders = getFileHeaders();
        int targetIdx = fileHeaders.indexOf(hd);
        if (targetIdx < 0) {
            throw new HeaderNotInArchiveException();
        }
        try {
            // newMhd is the RAR4 main header and is null for RAR5 (its solid flag lives on the
            // Rar5MainHeader / per-file compression info instead), so guard the deref (M3.7).
            boolean isSolidStream = (newMhd != null && newMhd.isSolid()) || hd.isSolid();

            if (isSolidStream) {
                if (targetIdx < lastProcessedFileIndex) {
                    // Reset the dictionary unconditionally: doExtractFile only resets it when
                    // the first file is non-solid, which malformed archives may violate.
                    if (unpack != null) {
                        unpack.init(null);
                    }
                    // RAR5 engine carries the window across a solid set; a rewind must replay from
                    // the start with a fresh window, so drop it (re-created lazily in doExtractFile).
                    this.unpack5 = null;
                    lastProcessedFileIndex = -1;
                }
                for (int i = lastProcessedFileIndex + 1; i < targetIdx; i++) {
                    skipFile(fileHeaders.get(i));
                }
            }
            doExtractFile(hd, os);
            lastProcessedFileIndex = targetIdx;
        } catch (final Exception e) {
            if (e instanceof RarException) {
                throw (RarException) e;
            } else {
                throw new RarException(e);
            }
        }
    }

    private void skipFile(FileHeader skipHd) {
        if (skipHd.getFullUnpackSize() <= 0) {
            return;
        }
        try {
            doExtractFile(skipHd, new NullOutputStream(), true);
        } catch (RarException e) {
            logger.warn("Error skipping file {}: {}", skipHd.getFileName(), e.getMessage());
        } catch (IOException e) {
            logger.warn("IO error skipping file {}: {}", skipHd.getFileName(), e.getMessage());
        }
    }

    private static class NullOutputStream extends OutputStream {
        @Override
        public void write(int b) {
            // No-op
        }
    }

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
        doExtractFile(hd, os, false);
    }

    private void doExtractFile(FileHeader hd, final OutputStream os, boolean skip)
        throws RarException, IOException {
        // P0.7 / issue #12 conscious divergence: unrar warns and lets the file data CRC
        // decide at extract time for a broken FILE/NEWSUB header; junrar has no CLI
        // warning channel, so silent garbage extraction would be worse -- refuse instead.
        if (hd.isBrokenHeader()) {
            throw new CorruptHeaderException("Cannot extract '" + hd.getFileName() + "': header CRC mismatch");
        }
        this.dataIO.init(os);
        this.dataIO.init(hd);
        this.dataIO.setUnpFileCRC(this.isOldFormat() ? 0 : 0xffFFffFF);
        try {
            if (hd.getUnpVersion() == 50) {
                extractRar5(hd);
            } else {
                if (this.unpack == null) {
                    this.unpack = new Unpack(this.dataIO);
                }
                if (!hd.isSolid()) {
                    this.unpack.init(null);
                }
                this.unpack.setDestSize(hd.getFullUnpackSize());
                this.unpack.doUnpack(hd.getUnpVersion(), hd.isSolid());
            }
            if (!skip) {
                // Verify file CRC
                hd = this.dataIO.getSubHeader();
                final long actualCRC = hd.isSplitAfter() ? ~this.dataIO.getPackedCRC()
                    : ~this.dataIO.getUnpFileCRC();
                final int expectedCRC = hd.getFileCRC();
                if (actualCRC != expectedCRC) {
                    throw new CrcErrorException();
                }
            }
            // if (!hd.isSplitAfter()) {
            // // Verify file CRC
            // if(~dataIO.getUnpFileCRC() != hd.getFileCRC()){
            // throw new RarException(RarExceptionType.crcError);
            // }
            // }
        } catch (final Exception e) {
            if (this.unpack != null) {
                this.unpack.cleanUp();
            }
            if (e instanceof RarException) {
                throw (RarException) e;
            } else {
                throw new RarException(e);
            }
        }
    }

    /**
     * Extract one RAR5 (method 0x50) entry through the per-archive {@link Unpack5} engine (M3.7).
     * Stored entries (method 0) copy straight through the CRC/hash seam; compressed entries size
     * the per-archive window from the header dictionary and run the decode loop. A solid entry
     * reuses the same engine so its LZ window continues from the preceding file.
     */
    private void extractRar5(final FileHeader hd) throws IOException, RarException {
        if (this.unpack5 == null) {
            this.unpack5 = new Unpack5(this.dataIO, false);
        }
        this.unpack5.setDestSize(hd.getFullUnpackSize());
        if (hd.getUnpMethod() == 0) {
            this.unpack5.unstore();
        } else {
            this.unpack5.init(hd.getRar5WinSize(), hd.isSolid());
            this.unpack5.unpack5(hd.isSolid());
        }
    }

    /**
     * @return returns the main header of this archive
     */
    public MainHeader getMainHeader() {
        return this.newMhd;
    }

    /**
     * @return whether the archive is old format; always {@code false} for RAR5 ({@code markHead}
     *         is never populated on the {@link #readHeadersRar5} path -- M3.5, issue #26, "old
     *         format" is a RAR 1.3-era wire fact RAR5 cannot have).
     */
    public boolean isOldFormat() {
        return this.markHead != null && this.markHead.isOldFormat();
    }

    /**
     * Tears down the current channel/unpacker without wiping the password, so a
     * volume switch (multi-volume archives) can keep decrypting headers. Internal
     * use only; {@link #close()} is the password-wiping terminal counterpart.
     */
    private void resetChannel() throws IOException {
        if (this.channel != null) {
            this.channel.close();
            this.channel = null;
        }
        if (this.unpack != null) {
            this.unpack.cleanUp();
        }
        this.rar5HeadersEncrypted = false;
    }

    /**
     * Close the underlying compressed file and wipe the internal password copy.
     */
    @Override
    public void close() throws IOException {
        resetChannel();
        if (this.passwordChars != null) {
            Arrays.fill(this.passwordChars, '\0');
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

    /**
     * @return the password as a {@code String}, which cannot be wiped from memory;
     *         prefer keeping the password as {@code char[]} where feasible.
     * @see ArchiveOptions#getPassword()
     */
    public String getPassword() {
        return passwordAsString();
    }

    public void setPassword(String password) {
        this.passwordChars = password == null ? null : password.toCharArray();
    }

    private String passwordAsString() {
        return this.passwordChars == null ? null : new String(this.passwordChars);
    }

    /**
     * @return the extraction dictionary-size budget in bytes ({@link ArchiveOptions#getMaxDictionarySize()}).
     */
    public long getMaxDictionarySize() {
        return this.maxDictionarySize;
    }

    /**
     * Test hook (P0.8 wipe-on-close acceptance row): exposes the live internal
     * array reference, not a copy, so a test can observe it being zeroed by
     * {@link #close()}.
     */
    char[] getPasswordChars() {
        return this.passwordChars;
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
