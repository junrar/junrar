package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Public-surface coverage for the M1.3 (issue #17) RAR3/RAR1.5 limit guards. Each fixture
 * is a byte-patched real archive that drives one guard through the full extraction
 * pipeline (generate_m13_limit_fixtures.py next to the fixtures records the exact mutation):
 *
 * <ul>
 *   <li>{@code filter-flood.rar} -- RAR3 filter position past the filter table
 *       (unsigned FiltPos guard, unpack30.cpp:383).</li>
 *   <li>{@code channel-flood.rar} -- RAR3 VMSF_DELTA channel count &gt; 1024
 *       (MAX3_UNPACK_CHANNELS guard, rarvm.cpp:203).</li>
 *   <li>{@code flagsplace-oob.rar} -- RAR1.5 GetFlagsBuf FlagsPlace == ChSetC.length and
 *       the LongLZ ChSetB write-back index (unpack15.cpp:412 and :291).</li>
 * </ul>
 *
 * Observable behaviour recorded from UnRAR 7.23 for all three: the corrupt member fails
 * (checksum error / "Total errors: 1") while sibling members extract, and extraction
 * completes immediately. junrar must match that: fail the corrupt member with a typed
 * {@link RarException} within the {@code @Timeout(5)} bound and -- the point of the guards
 * -- never let a raw {@link ArrayIndexOutOfBoundsException}/{@link NegativeArraySizeException}
 * escape as the cause. Without the guards each fixture instead wraps such a raw error
 * (proven on the pre-M1.3 base commit).
 */
public class UnpackLimitHostileArchiveTest {

    private static final String[] FIXTURES = {
        "/com/github/junrar/abnormal/filter-flood.rar",
        "/com/github/junrar/abnormal/channel-flood.rar",
        "/com/github/junrar/abnormal/flagsplace-oob.rar",
    };

    @Test
    @Timeout(5)
    public void hostileLimitsAreBoundedOnJunrarFileSurface() throws Exception {
        for (String fixture : FIXTURES) {
            assertBounded(fixture, this::extractWithJunrarFile);
        }
    }

    @Test
    @Timeout(5)
    public void hostileLimitsAreBoundedOnJunrarStreamSurface() throws Exception {
        for (String fixture : FIXTURES) {
            assertBounded(fixture, this::extractWithJunrarStream);
        }
    }

    @Test
    @Timeout(5)
    public void hostileLimitsAreBoundedOnArchiveFileSurface() throws Exception {
        for (String fixture : FIXTURES) {
            assertBounded(fixture, this::extractWithArchiveFile);
        }
    }

    @Test
    @Timeout(5)
    public void hostileLimitsAreBoundedOnArchiveStreamSurface() throws Exception {
        for (String fixture : FIXTURES) {
            assertBounded(fixture, this::extractWithArchiveStream);
        }
    }

    private void assertBounded(String fixture, Extraction extraction) throws Exception {
        Path root = Files.createTempDirectory("junrar-m13-");
        Path archive = root.resolve("hostile.rar");
        Path destination = root.resolve("out");
        Files.createDirectories(destination);
        Files.write(archive, readResource(fixture));
        try {
            AtomicReference<Throwable> memberFailure = new AtomicReference<>();
            Throwable thrown =
                    catchThrowable(() -> extraction.extract(archive, destination, memberFailure));
            Throwable failure = thrown != null ? thrown : memberFailure.get();
            assertThat(failure)
                    .as(
                            "hostile %s must fail the corrupt member with a typed RarException",
                            fixture)
                    .isInstanceOf(RarException.class);
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                assertThat(cause)
                        .as("%s must not leak a raw bounds/size error (guard defeated)", fixture)
                        .isNotInstanceOf(ArrayIndexOutOfBoundsException.class)
                        .isNotInstanceOf(NegativeArraySizeException.class);
            }
        } finally {
            delete(root);
        }
    }

    private void extractWithJunrarFile(
            Path archive, Path destination, AtomicReference<Throwable> unused) throws Exception {
        Junrar.extract(archive.toFile(), destination.toFile());
    }

    private void extractWithJunrarStream(
            Path archive, Path destination, AtomicReference<Throwable> unused) throws Exception {
        try (InputStream input = Files.newInputStream(archive)) {
            Junrar.extract(input, destination.toFile());
        }
    }

    private void extractWithArchiveFile(
            Path archive, Path destination, AtomicReference<Throwable> memberFailure)
            throws Exception {
        try (Archive input = new Archive(archive.toFile())) {
            drain(input, memberFailure);
        }
    }

    private void extractWithArchiveStream(
            Path archive, Path destination, AtomicReference<Throwable> memberFailure)
            throws Exception {
        try (InputStream stream = Files.newInputStream(archive);
                Archive input = new Archive(stream)) {
            drain(input, memberFailure);
        }
    }

    /** Extract every member; keep the first per-member failure (mirrors UnRAR continuing past a bad entry). */
    private void drain(Archive archive, AtomicReference<Throwable> memberFailure) throws Exception {
        FileHeader header;
        while ((header = archive.nextFileHeader()) != null) {
            final FileHeader current = header;
            Throwable thrown =
                    catchThrowable(() -> archive.extractFile(current, NullOutputStream.INSTANCE));
            if (thrown != null) {
                memberFailure.compareAndSet(null, thrown);
            }
        }
    }

    private byte[] readResource(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as("resource %s", resource).isNotNull();
            return IOUtils.toByteArray(input);
        }
    }

    private void delete(Path path) throws Exception {
        try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @FunctionalInterface
    private interface Extraction {
        void extract(Path archive, Path destination, AtomicReference<Throwable> memberFailure)
                throws Exception;
    }
}
