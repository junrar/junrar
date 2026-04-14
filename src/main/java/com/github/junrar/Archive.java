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

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.HeaderNotInArchiveException;
import com.github.junrar.exception.MainHeaderNullException;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarEncryptedException;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.github.junrar.impl.HeaderReader;
import com.github.junrar.impl.HeaderReaderFactory;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.MainHeader;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.UnrarHeadertype;
import com.github.junrar.unpack.ComprDataIO;
import com.github.junrar.unpack.Unpack;
import com.github.junrar.volume.FileVolumeManager;
import com.github.junrar.volume.InputStreamVolumeManager;
import com.github.junrar.volume.Volume;
import com.github.junrar.volume.VolumeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
     * @throws Exception
     */
    private void readHeaders(final long fileLength) throws Exception {
        this.markHead = null;
        this.newMhd = null;
        this.headers.clear();
        this.currentHeaderIndex = 0;

        HeaderReader reader = null;
        try {
            reader = HeaderReaderFactory.create(channel);
            reader.readHeaders(channel, fileLength, password);
        } finally {
            if (reader != null) {
                this.headers.addAll(reader.getHeaders());
            }

            for (final BaseBlock header : this.headers) {
                if (header instanceof MarkHeader) {
                    this.markHead = (MarkHeader) header;
                } else if (header instanceof MainHeader) {
                    this.newMhd = (MainHeader) header;
                }
            }
        }

        if (this.markHead == null) {
            throw new CorruptHeaderException();
        } else if (this.newMhd == null) {
            throw new CorruptHeaderException();
        }
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
