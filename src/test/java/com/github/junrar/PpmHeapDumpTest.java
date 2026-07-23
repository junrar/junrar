package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.unpack.Unpack;
import com.github.junrar.unpack.ppm.ModelPPM;
import com.github.junrar.unpack.ppm.SubAllocator;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Byte-diff oracle for the live PPMd heap produced by the P0.5 fixture.
 *
 * <p>The dump is the historical allocator span {@code [1, heapEnd)}. The
 * compressed golden is generated from the committed fixture only; it is not
 * an extraction-output oracle.</p>
 */
public class PpmHeapDumpTest {

    private static final String FIXTURE = "/com/github/junrar/ppm/legit-maxmb63.rar";
    private static final String GOLDEN_RESOURCE = "/com/github/junrar/ppm/legit-maxmb63-heap.gz";
    private static final String GOLDEN_PROPERTY = "ppm.heap.golden";
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final Field ARCHIVE_UNPACK = privateField(Archive.class, "unpack");
    private static final Field UNPACK_PPM = privateField(Unpack.class, "ppm");

    @Test
    @Tag("check")
    void givenLegitPpmFixture_whenExtractedTwice_thenHeapDumpsMatchGolden(@TempDir Path tempDir)
            throws Exception {
        Path first = tempDir.resolve("legit-maxmb63-heap-1.bin");
        Path second = tempDir.resolve("legit-maxmb63-heap-2.bin");
        dumpHeap(first);
        dumpHeap(second);

        long consecutiveMismatch = mismatch(first, second);
        assertThat(consecutiveMismatch)
                .as(
                        "consecutive PPMd heap dumps differ at byte offset %d (%s vs %s)",
                        consecutiveMismatch, first, second)
                .isEqualTo(-1L);

        try (InputStream golden =
                new GZIPInputStream(
                        new BufferedInputStream(resource(GOLDEN_RESOURCE), BUFFER_SIZE))) {
            long goldenMismatch = mismatch(first, golden);
            assertThat(goldenMismatch)
                    .as(
                            "PPMd heap dump differs from committed %s at byte offset %d; run"
                                + " ./gradlew generatePpmHeapDump only for an intentional oracle"
                                + " update",
                            GOLDEN_RESOURCE, goldenMismatch)
                    .isEqualTo(-1L);
        }
    }

    @Test
    @Tag("generate")
    void generatePpmHeapGolden() throws Exception {
        String output = System.getProperty(GOLDEN_PROPERTY);
        assertThat(output)
                .as("Gradle must provide -D%s pointing at the committed golden", GOLDEN_PROPERTY)
                .isNotBlank();
        Path golden = Paths.get(output);
        Path parent = golden.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream compressed =
                new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(golden)))) {
            extractAndDumpHeap(compressed);
        }
    }

    private static void dumpHeap(Path output) throws Exception {
        try (OutputStream dump = new BufferedOutputStream(Files.newOutputStream(output))) {
            extractAndDumpHeap(dump);
        }
    }

    private static void extractAndDumpHeap(OutputStream dump) throws Exception {
        try (InputStream fixture = resource(FIXTURE);
                Archive archive = new Archive(fixture)) {
            List<FileHeader> headers = archive.getFileHeaders();
            assertThat(headers).as("PPMd fixture file headers").hasSize(1);
            archive.extractFile(headers.get(0), discardOutput());

            Unpack unpack = (Unpack) ARCHIVE_UNPACK.get(archive);
            assertThat(unpack).as("Archive.unpack after extraction").isNotNull();
            ModelPPM ppm = (ModelPPM) UNPACK_PPM.get(unpack);
            assertThat(ppm).as("Unpack.ppm after PPMd extraction").isNotNull();
            SubAllocator allocator = ppm.getSubAlloc();
            byte[] heap = allocator.getHeap();
            int heapEnd = allocator.getHeapEnd();
            assertThat(heap).as("PPMd heap after extraction").isNotNull();
            assertThat(heapEnd).as("PPMd heapEnd").isBetween(2, heap.length);
            for (int offset = 1; offset < heapEnd; offset += BUFFER_SIZE) {
                dump.write(heap, offset, Math.min(BUFFER_SIZE, heapEnd - offset));
            }
        }
    }

    private static OutputStream discardOutput() {
        return new OutputStream() {
            @Override
            public void write(int b) {
                // The extracted bytes are only needed to drive the live heap.
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                // Avoid retaining the fixture's output while dumping the heap.
            }
        };
    }

    private static InputStream resource(String name) throws IOException {
        InputStream stream = PpmHeapDumpTest.class.getResourceAsStream(name);
        if (stream == null) {
            throw new IOException("Missing committed PPMd resource: " + name);
        }
        return stream;
    }

    private static long mismatch(Path actual, InputStream expected) throws IOException {
        try (InputStream input =
                new BufferedInputStream(Files.newInputStream(actual), BUFFER_SIZE)) {
            byte[] actualBytes = new byte[BUFFER_SIZE];
            byte[] expectedBytes = new byte[BUFFER_SIZE];
            long offset = 0;
            while (true) {
                int actualRead = readChunk(input, actualBytes);
                int expectedRead = readChunk(expected, expectedBytes);
                if (actualRead != expectedRead) {
                    return offset + Math.min(Math.max(actualRead, 0), Math.max(expectedRead, 0));
                }
                if (actualRead < 0) {
                    return -1;
                }
                for (int i = 0; i < actualRead; i++) {
                    if (actualBytes[i] != expectedBytes[i]) {
                        return offset + i;
                    }
                }
                offset += actualRead;
            }
        }
    }

    private static int readChunk(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) {
                return offset == 0 ? -1 : offset;
            }
            if (count == 0) {
                continue;
            }
            offset += count;
        }
        return offset;
    }

    private static Field privateField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Java 8 stand-in for {@code Files.mismatch} (test sources compile at release 8). */
    private static long mismatch(Path a, Path b) throws IOException {
        byte[] x = Files.readAllBytes(a);
        byte[] y = Files.readAllBytes(b);
        int n = Math.min(x.length, y.length);
        for (int i = 0; i < n; i++) {
            if (x[i] != y[i]) {
                return i;
            }
        }
        return x.length == y.length ? -1L : n;
    }
}
