package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedDictionarySizeException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Resolves no-go row S8 (docs/porting/PARITY_PLAN.md P0.5;
 * docs/porting/MIGRATION_MANUAL.md ss7): junrar clamped the archive-supplied
 * PPM MaxMB to 1, diverging from unrar's "honor MaxMB+1 MB verbatim" policy
 * (d861246:model.cpp DecodeInit) and truncating legitimate PPMd decoding once
 * the model outgrows the clamped 2 MB. ModelPPM.decodeInit now honors the
 * header byte with no additional cap; the 256 MB format-inherent ceiling
 * (MaxMB is one byte, 0-255) is bounded only once a shared resource budget
 * lands (P0.8's ArchiveOptions.maxDictionarySize).
 * <p>
 * Fixture provenance: ppm/generate_maxmb_fixtures.py.
 */
public class PpmMaxMbTest {

    private static final String LEGIT_FIXTURE = "ppm/legit-maxmb63.rar";
    private static final String LEGIT_EXPECTED = "ppm/legit-maxmb63-expected.bin";
    private static final String PRESERVED_FIXTURE = "ppm/preserved-maxmb0.rar";
    private static final String PRESERVED_EXPECTED = "ppm/preserved-maxmb0-expected.bin";
    private static final String HOSTILE_FIXTURE = "ppm/hostile-maxmb255.rar";

    @Test
    public void givenLegitMaxMb63Archive_whenExtracting_thenByteIdenticalToRealUnrarOracle()
            throws Exception {
        byte[] expected = readResource(LEGIT_EXPECTED);
        byte[] extracted = extractSoleEntry(LEGIT_FIXTURE);
        assertThat(extracted).isEqualTo(expected);
    }

    @Test
    public void
            givenLegitMaxMb63Archive_whenDefaultArchiveOptions_thenByteIdenticalToRealUnrarOracle()
                    throws Exception {
        byte[] expected = readResource(LEGIT_EXPECTED);
        ArchiveOptions options = ArchiveOptions.builder().build();
        try (InputStream is = getClass().getResourceAsStream(LEGIT_FIXTURE);
                Archive archive = new Archive(is, options)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeaders.get(0), baos);
                assertThat(baos.toByteArray()).isEqualTo(expected);
            }
        }
    }

    @Test
    public void
            givenLegitMaxMb63Archive_whenMaxDictionarySizeBelowRequirement_thenUnsupportedDictionarySizeException()
                    throws Exception {
        // MaxMB=63 in the fixture => PPMd needs (63+1) MB = 64 MB of suballocator memory.
        long requiredBytes = 64L * 1024 * 1024;

        // Before-state: the same fixture extracts fine under the default (unconstrained) budget.
        assertThat(extractSoleEntry(LEGIT_FIXTURE)).isEqualTo(readResource(LEGIT_EXPECTED));

        // After: a budget one byte short of the requirement refuses the same fixture.
        ArchiveOptions options =
                ArchiveOptions.builder().maxDictionarySize(requiredBytes - 1).build();
        try (InputStream is = getClass().getResourceAsStream(LEGIT_FIXTURE);
                Archive archive = new Archive(is, options)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);
            FileHeader fileHeader = fileHeaders.get(0);

            Throwable thrown =
                    catchThrowable(
                            () -> {
                                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    archive.extractFile(fileHeader, baos);
                                }
                            });

            assertThat(thrown).isExactlyInstanceOf(UnsupportedDictionarySizeException.class);
        }
    }

    @Test
    public void givenPreservedMaxMb0Archive_whenExtracting_thenByteIdenticalAsBeforeTheFix()
            throws Exception {
        byte[] expected = readResource(PRESERVED_EXPECTED);
        byte[] extracted = extractSoleEntry(PRESERVED_FIXTURE);
        assertThat(extracted).isEqualTo(expected);
    }

    @Test
    @Timeout(60)
    public void givenHostileMaxMb255Archive_whenExtracting_thenBoundedNoOomAiiobeOrHang()
            throws Exception {
        byte[] expected = readResource(LEGIT_EXPECTED);
        try (InputStream is = getClass().getResourceAsStream(HOSTILE_FIXTURE);
                Archive archive = new Archive(is)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);
            FileHeader fileHeader = fileHeaders.get(0);

            AtomicReference<byte[]> extractedRef = new AtomicReference<>();
            Throwable thrown =
                    catchThrowable(
                            () -> {
                                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    archive.extractFile(fileHeader, baos);
                                    extractedRef.set(baos.toByteArray());
                                }
                            });

            if (thrown != null) {
                assertThat(thrown).isInstanceOf(RarException.class);
            } else {
                assertThat(extractedRef.get()).isEqualTo(expected);
            }
        }
    }

    private byte[] extractSoleEntry(String fixture) throws IOException, RarException {
        try (InputStream is = getClass().getResourceAsStream(fixture);
                Archive archive = new Archive(is)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                archive.extractFile(fileHeaders.get(0), baos);
                return baos.toByteArray();
            }
        }
    }

    private byte[] readResource(String resource) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }
}
