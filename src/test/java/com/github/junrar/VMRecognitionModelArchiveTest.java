package com.github.junrar;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Acceptance test for M2.1 (issue #20): RAR3 VM programs are now recognized against the
 * 6 canonical standard filters (by length+CRC32), never interpreted. A program that
 * fails recognition -- including one whose single-byte XOR checksum is otherwise valid
 * -- is a no-op filter (FilteredDataSize=0), matching real UnRAR, never fed to a general
 * bytecode interpreter.
 *
 * <p>{@code vm/audio-filter-noncanonical.rar} is {@code
 * audio/BoatModernEnglish-audio-text-unpack30.rar} (a real RAR3/Unpack29 archive whose
 * BoatModernEnglish.wav member carries one canonical 216-byte VMSF_AUDIO filter, CRC32
 * 0xbc85e701) with 2 bytes XOR-flipped inside that filter's VM code so its CRC32 no
 * longer matches any canonical fingerprint while its XorSum check still passes --
 * "checksum valid, fingerprint unrecognized", the only case where the old interpreter
 * and the new recognition model can produce different results. See
 * {@code vm/generate_m21_recognition_fixture.py} next to the fixture for the exact byte
 * offsets, the linear-algebra argument for why the checksum stays valid, and how the
 * expected bytes below were captured from the real UnRAR 7.23 binary.
 *
 * <p>Observable behaviour recorded from UnRAR 7.23: {@code BoatModernEnglish.wav} fails
 * ("checksum error" / "Total errors: 1") after writing exactly 144 bytes -- the AUDIO
 * filter's block covers effectively the whole rest of the file in one shot, and a no-op
 * filter contributes zero bytes for its block, so only the small unfiltered prefix
 * before the block is ever written; the sibling {@code LICENSE.txt} member extracts
 * cleanly.
 *
 * <p>On the pre-M2.1 base commit, RarVM's (now deleted) general bytecode interpreter
 * reinterprets these 216 checksum-valid-but-corrupted bytes as VM instructions and
 * crashes with an {@code ArrayIndexOutOfBoundsException} (RarVM.ExecuteCode -&gt;
 * RarVM.getValue -&gt; Raw.readIntLittleEndian), wrapped in a RarException. After
 * M2.1, junrar must instead write the same 144-byte prefix as UnRAR and fail with a
 * clean {@link CrcErrorException} -- never a raw index/array exception.
 */
class VMRecognitionModelArchiveTest {

    @Test
    void givenChecksumValidButUnrecognizedVmProgram_whenExtracting_thenNoOpMatchesUnrarOracleNotInterpreter()
            throws Exception {
        byte[] expected;
        try (InputStream is = getClass().getResourceAsStream("vm/audio-filter-noncanonical.expected")) {
            assertThat(is).as("expected-bytes resource").isNotNull();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                IOUtils.copy(is, baos);
                expected = baos.toByteArray();
            }
        }
        assertThat(expected).hasSize(144);

        InputStream archiveStream = getClass().getResourceAsStream("vm/audio-filter-noncanonical.rar");
        assertThat(archiveStream).as("archive resource").isNotNull();

        byte[] actual;
        try (Archive archive = new Archive(archiveStream)) {
            FileHeader fileHeader = archive.nextFileHeader();
            assertThat(fileHeader.getFileName()).isEqualTo("BoatModernEnglish.wav");

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Throwable failure = catchThrowable(() -> archive.extractFile(fileHeader, baos));

                // The recognition model fails the member the same clean way UnRAR does
                // (a CRC mismatch on the fully-processed, but now-shorter, output) --
                // never the raw ArrayIndexOutOfBoundsException that the pre-M2.1
                // interpreter throws when it reinterprets this bytecode as instructions.
                assertThat(failure).isInstanceOf(CrcErrorException.class);
                assertThat(failure).hasNoCause();

                actual = baos.toByteArray();
            }
        }

        // Byte-identical to the real UnRAR 7.23 oracle's output for this exact archive.
        assertThat(actual).containsExactly(expected);
    }

    @Test
    void givenChecksumValidButUnrecognizedVmProgram_whenExtractingSiblingMember_thenUnaffected()
            throws Exception {
        InputStream archiveStream = getClass().getResourceAsStream("vm/audio-filter-noncanonical.rar");
        assertThat(archiveStream).as("archive resource").isNotNull();

        try (Archive archive = new Archive(archiveStream)) {
            archive.nextFileHeader(); // BoatModernEnglish.wav -- covered above
            FileHeader license = archive.nextFileHeader();
            assertThat(license.getFileName()).isEqualTo("LICENSE.txt");

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                assertThatCode(() -> archive.extractFile(license, baos)).doesNotThrowAnyException();
                assertThat(baos.toByteArray()).hasSize((int) license.getFullUnpackSize());
            }
        }
    }
}
