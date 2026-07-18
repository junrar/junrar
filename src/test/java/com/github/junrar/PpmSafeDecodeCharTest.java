package com.github.junrar;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Defensive public-surface coverage for nested PPM decode errors. */
public class PpmSafeDecodeCharTest {

    private static final String FIXTURE = "/com/github/junrar/ppm/hostile-ppm-decodechar.rar";
    private static final String[] PS_FIXTURES = {
        "/com/github/junrar/ppm/hostile-ppm-ps-collect.rar",
        "/com/github/junrar/ppm/hostile-ppm-ps-count.rar",
        "/com/github/junrar/ppm/hostile-ppm-ps-mask.rar"
    };
    private static final String EXPECTED = "/com/github/junrar/ppm/legit-maxmb63-expected.bin";

    @Test
    @Timeout(5)
    public void hostileNestedPpmDecodeErrorIsBoundedOnJunrarFileSurface() throws Exception {
        assertSurface(this::extractWithJunrarFile);
    }

    @Test
    @Timeout(5)
    public void hostileNestedPpmDecodeErrorIsBoundedOnJunrarStreamSurface() throws Exception {
        assertSurface(this::extractWithJunrarStream);
    }

    @Test
    @Timeout(5)
    public void hostileNestedPpmDecodeErrorIsBoundedOnArchiveFileSurface() throws Exception {
        assertSurface(this::extractWithArchiveFile);
    }

    @Test
    @Timeout(5)
    public void hostileNestedPpmDecodeErrorIsBoundedOnArchiveStreamSurface() throws Exception {
        assertSurface(this::extractWithArchiveStream);
    }

    @Test
    @Timeout(5)
    public void hostilePpmPsBoundsAreBoundedOnJunrarFileSurface() throws Exception {
        for (String fixture : PS_FIXTURES) {
            assertSurface(fixture, this::extractWithJunrarFile);
        }
    }

    @Test
    @Timeout(5)
    public void hostilePpmPsBoundsAreBoundedOnJunrarStreamSurface() throws Exception {
        for (String fixture : PS_FIXTURES) {
            assertSurface(fixture, this::extractWithJunrarStream);
        }
    }

    @Test
    @Timeout(5)
    public void hostilePpmPsBoundsAreBoundedOnArchiveFileSurface() throws Exception {
        for (String fixture : PS_FIXTURES) {
            assertSurface(fixture, this::extractWithArchiveFile);
        }
    }

    @Test
    @Timeout(5)
    public void hostilePpmPsBoundsAreBoundedOnArchiveStreamSurface() throws Exception {
        for (String fixture : PS_FIXTURES) {
            assertSurface(fixture, this::extractWithArchiveStream);
        }
    }

    private void assertSurface(Extraction extraction) throws Exception {
        assertSurface(FIXTURE, extraction);
    }

    private void assertSurface(String fixture, Extraction extraction) throws Exception {
        SurfaceResult result = runSurface(fixture, extraction);
        if (result.thrown == null) {
            assertThat(result.bytes).isEqualTo(readResource(EXPECTED));
        } else {
            assertThat(result.thrown)
                .as("hostile PPM input must fail with a typed RarException")
                .isInstanceOf(RarException.class);
        }
    }

    private SurfaceResult runSurface(Extraction extraction) throws Exception {
        return runSurface(FIXTURE, extraction);
    }

    private SurfaceResult runSurface(String fixture, Extraction extraction) throws Exception {
        Path root = Files.createTempDirectory("junrar-ppm-safe-");
        Path archive = root.resolve("hostile.rar");
        Path destination = root.resolve("out");
        Files.createDirectories(destination);
        Files.write(archive, readResource(fixture));
        try {
            AtomicReference<byte[]> extracted = new AtomicReference<>();
            Throwable thrown = catchThrowable(() -> extracted.set(extraction.extract(archive, destination)));
            return thrown == null
                ? SurfaceResult.success(extracted.get())
                : SurfaceResult.failure(thrown);
        } finally {
            delete(root.toFile());
        }
    }

    private byte[] extractWithJunrarFile(Path archive, Path destination) throws Exception {
        Junrar.extract(archive.toFile(), destination.toFile());
        return readExtracted(destination);
    }

    private byte[] extractWithJunrarStream(Path archive, Path destination) throws Exception {
        try (InputStream input = Files.newInputStream(archive)) {
            Junrar.extract(input, destination.toFile());
        }
        return readExtracted(destination);
    }

    private byte[] extractWithArchiveFile(Path archive, Path destination) throws Exception {
        try (Archive input = new Archive(archive.toFile())) {
            return extractSoleEntry(input);
        }
    }

    private byte[] extractWithArchiveStream(Path archive, Path destination) throws Exception {
        try (InputStream stream = Files.newInputStream(archive);
             Archive input = new Archive(stream)) {
            return extractSoleEntry(input);
        }
    }

    private byte[] extractSoleEntry(Archive archive) throws Exception {
        FileHeader fileHeader = archive.nextFileHeader();
        assertThat(fileHeader).isNotNull();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            archive.extractFile(fileHeader, output);
            return output.toByteArray();
        }
    }

    private byte[] readExtracted(Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(destination)) {
            List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            assertThat(files).hasSize(1);
            return Files.readAllBytes(files.get(0));
        }
    }

    private byte[] readResource(String resource) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(input).as("resource %s", resource).isNotNull();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        file.delete();
    }

    @FunctionalInterface
    private interface Extraction {
        byte[] extract(Path archive, Path destination) throws Exception;
    }

    private static final class SurfaceResult {
        private final byte[] bytes;
        private final Throwable thrown;

        private SurfaceResult(byte[] bytes, Throwable thrown) {
            this.bytes = bytes;
            this.thrown = thrown;
        }

        private static SurfaceResult success(byte[] bytes) {
            return new SurfaceResult(bytes, null);
        }

        private static SurfaceResult failure(Throwable thrown) {
            return new SurfaceResult(null, thrown);
        }
    }
}
