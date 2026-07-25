package com.github.junrar.regression;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.volume.FileVolume;
import com.github.junrar.volume.FileVolumeManager;
import com.github.junrar.volume.Volume;
import com.github.junrar.volume.VolumeManager;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Rewrites a corpus of RAR archives into a payload-free corpus that drives the parser down
 * exactly the same paths, so the result can be published alongside the code without carrying
 * anyone else's compressed content.
 *
 * <p>The rule is: <em>zero every byte the parser never read</em>. A byte that was not read
 * during the reference parse cannot have influenced that parse, so overwriting it cannot
 * change what a later parse does — the second parse reads the same offsets, finds the same
 * values there, and follows the identical path. Header bytes, block offsets, declared sizes,
 * stored checksums and the file length are all preserved because the parser reads them.
 *
 * <p>Reads are recorded at the {@link SeekableReadOnlyByteChannel} seam, which is
 * conservative: a read that fetches more than the parser ends up using still marks those
 * bytes as read, so they survive. That costs a few unnecessary bytes and cannot cost
 * correctness.
 *
 * <p>Ranges are accumulated across the whole corpus before anything is zeroed, so a file read
 * as a continuation volume of some other archive keeps the bytes that other parse needed.
 *
 * <p>Usage: {@code ./gradlew stripCorpus -PcorpusIn=<dir> -PcorpusOut=<dir>}. The output is
 * only trustworthy once {@code regressionTest} run against it reproduces the run against the
 * original corpus — including its failures. See {@code CONTRIBUTING.md}.
 */
public final class CorpusStripper {

    private CorpusStripper() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("usage: CorpusStripper <inDir> <outDir>");
        final Path in = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path out = Paths.get(args[1]).toAbsolutePath().normalize();

        final List<Path> inputs = listArchives(in);
        System.out.println("corpus: " + inputs.size() + " files under " + in);

        final Map<Path, Reads> reads = new HashMap<>();
        final Map<Path, String> before = new HashMap<>();
        for (final Path file : inputs) {
            before.put(file, parse(file, reads));
        }
        System.out.println("pass 1: parsed, read ranges recorded");

        long total = 0;
        long zeroed = 0;
        for (final Path file : inputs) {
            final Path target = out.resolve(in.relativize(file));
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            total += Files.size(target);
            zeroed += zeroUnread(target, reads.get(file), Files.size(target));
        }
        System.out.println(
                "pass 2: "
                        + zeroed
                        + " of "
                        + total
                        + " bytes zeroed ("
                        + (total == 0 ? 0 : 100 * zeroed / total)
                        + "%)");

        final List<String> diverged = new ArrayList<>();
        for (final Path file : inputs) {
            final Path target = out.resolve(in.relativize(file));
            final String after = parse(target, new HashMap<>());
            if (!before.get(file).equals(after)) {
                diverged.add(
                        in.relativize(file)
                                + "\n  before: "
                                + before.get(file)
                                + "\n  after:  "
                                + after);
            }
        }
        System.out.println(
                "pass 3: "
                        + (inputs.size() - diverged.size())
                        + "/"
                        + inputs.size()
                        + " identical, "
                        + diverged.size()
                        + " diverged");
        diverged.forEach(System.out::println);
        if (!diverged.isEmpty())
            throw new IllegalStateException("stripping changed parser behaviour");
    }

    private static List<Path> listArchives(final Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /** Opens the archive through a read-recording channel and returns its regression record. */
    private static String parse(final Path file, final Map<Path, Reads> reads) {
        final VolumeManager manager = new RecordingVolumeManager(file.toFile(), reads);
        ArchiveRecord record;
        try (Archive archive = new Archive(manager, null, null)) {
            record = ArchiveRecord.fromArchive(archive);
        } catch (final RarException e) {
            record = ArchiveRecord.fromException(e, file);
        } catch (final Exception e) {
            return "NON-RAR-FAILURE:" + e.getClass().getSimpleName();
        }
        return record.toString();
    }

    private static long zeroUnread(final Path file, final Reads read, final long length)
            throws IOException {
        final List<long[]> keep = read == null ? new ArrayList<>() : read.merged();
        long zeroed = 0;
        final byte[] zeros = new byte[64 * 1024];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            long cursor = 0;
            for (final long[] range : keep) {
                zeroed += zero(raf, cursor, Math.min(range[0], length), zeros);
                cursor = Math.max(cursor, range[1]);
            }
            zeroed += zero(raf, cursor, length, zeros);
        }
        return zeroed;
    }

    private static long zero(
            final RandomAccessFile raf, final long from, final long to, final byte[] zeros)
            throws IOException {
        if (to <= from) return 0;
        raf.seek(from);
        long left = to - from;
        while (left > 0) {
            final int n = (int) Math.min(zeros.length, left);
            raf.write(zeros, 0, n);
            left -= n;
        }
        return to - from;
    }

    /** Byte ranges touched by the parser, in no particular order until {@link #merged()}. */
    private static final class Reads {
        private final List<long[]> ranges = new ArrayList<>();

        void add(final long start, final long count) {
            if (count > 0) ranges.add(new long[] {start, start + count});
        }

        List<long[]> merged() {
            ranges.sort((a, b) -> Long.compare(a[0], b[0]));
            final List<long[]> merged = new ArrayList<>();
            for (final long[] range : ranges) {
                if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                    final long[] last = merged.get(merged.size() - 1);
                    last[1] = Math.max(last[1], range[1]);
                } else {
                    merged.add(new long[] {range[0], range[1]});
                }
            }
            return merged;
        }
    }

    /** Hands out {@link RecordingChannel}s over the volumes {@link FileVolumeManager} resolves. */
    private static final class RecordingVolumeManager implements VolumeManager {
        private final FileVolumeManager delegate;
        private final Map<Path, Reads> reads;

        RecordingVolumeManager(final File firstVolume, final Map<Path, Reads> reads) {
            this.delegate = new FileVolumeManager(firstVolume);
            this.reads = reads;
        }

        @Override
        public Volume nextVolume(final Archive archive, final Volume last) throws IOException {
            final Volume volume = delegate.nextVolume(archive, last);
            return new RecordingVolume((FileVolume) volume, reads);
        }
    }

    private static final class RecordingVolume implements Volume {
        private final FileVolume delegate;
        private final Map<Path, Reads> reads;

        RecordingVolume(final FileVolume delegate, final Map<Path, Reads> reads) {
            this.delegate = delegate;
            this.reads = reads;
        }

        @Override
        public SeekableReadOnlyByteChannel getChannel() throws IOException {
            final Path path = delegate.getFile().toPath().toAbsolutePath().normalize();
            return new RecordingChannel(
                    delegate.getChannel(), reads.computeIfAbsent(path, p -> new Reads()));
        }

        @Override
        public long getLength() {
            return delegate.getLength();
        }

        @Override
        public Archive getArchive() {
            return delegate.getArchive();
        }
    }

    private static final class RecordingChannel implements SeekableReadOnlyByteChannel {
        private final SeekableReadOnlyByteChannel delegate;
        private final Reads reads;

        RecordingChannel(final SeekableReadOnlyByteChannel delegate, final Reads reads) {
            this.delegate = delegate;
            this.reads = reads;
        }

        @Override
        public long getPosition() throws IOException {
            return delegate.getPosition();
        }

        @Override
        public void setPosition(final long pos) throws IOException {
            delegate.setPosition(pos);
        }

        @Override
        public int read() throws IOException {
            final long at = delegate.getPosition();
            final int value = delegate.read();
            if (value >= 0) reads.add(at, 1);
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int off, final int count) throws IOException {
            final long at = delegate.getPosition();
            final int read = delegate.read(buffer, off, count);
            if (read > 0) reads.add(at, read);
            return read;
        }

        @Override
        public int readFully(final byte[] buffer, final int count) throws IOException {
            final long at = delegate.getPosition();
            try {
                return delegate.readFully(buffer, count);
            } finally {
                reads.add(at, Math.max(0, delegate.getPosition() - at));
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
