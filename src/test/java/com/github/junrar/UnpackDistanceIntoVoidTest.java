package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * RED coverage for M1.4 (issue #18) FirstWinDone distance-into-void handling across the
 * three legacy engines (v15 / v20 / v29). Each fixture (generate_m14_void_fixtures.py +
 * m14-void-fixtures.manifest.md, next to the fixtures) drives a <em>first-window void</em>:
 * a match whose distance exceeds the current output position {@code UnpPtr} while the first
 * window pass has not completed. unrar 7.0.3+ (commit 5faaa45) zero-fills such a run
 * deterministically (unpackinline.cpp {@code CopyString} / unpack15.cpp
 * {@code CopyString15}: {@code !FirstWinDone && Distance>UnpPtr}). junrar's copyString
 * else-branch instead wraps the source pointer with {@code & MAXWINMASK}; once that pointer
 * increments past window index 0 it reads the member's OWN earlier literals -- bytes unrar
 * never emits.
 *
 * <p>Oracle = UnRAR 7.23 ({@code unrar x -kb}, keep-broken; each member fails the data-CRC
 * check as expected for a corrupt archive, and the broken member is kept). Captured output:
 * <ul>
 *   <li>{@code void-dist-v20.rar} / {@code void-dist-v29.rar}: member is exactly
 *       {@code 41 41 41 00 00 00} ("AAA" + zero-filled length-3 void).</li>
 *   <li>{@code void-dist-v15.rar}: member {@code BOATMO~1.WAV} is 56464 bytes; the length-5
 *       void at output offset 34759 is {@code 00 00 00 00 00}.</li>
 * </ul>
 *
 * <p>On current junrar each assertion FAILS for the intended reason -- junrar leaks the
 * non-zero wrap-copy ({@code 41 41 41 00 41 41} for v20/v29; {@code 00 52 49 46 46} =
 * NUL+"RIFF" for v15) where the oracle has zeros. Once the FirstWinDone guard lands, junrar
 * zero-fills identically and every assertion passes. Extraction still ends in a typed
 * {@link RarException} (data CRC), which is caught; the point of the test is byte parity of
 * the produced output, not the exception.
 */
public class UnpackDistanceIntoVoidTest {

    @Test
    @Timeout(5)
    public void v20FirstWindowVoidZeroFillsLikeUnrar() throws Exception {
        // UnRAR 7.23 `unrar x -kb`: AAA + zero-filled length-3 void.
        assertThat(extractFirstMember("/com/github/junrar/abnormal/void-dist-v20.rar"))
                .as("v20 first-window void must zero-fill (unrar oracle), not leak window bytes")
                .containsExactly(0x41, 0x41, 0x41, 0x00, 0x00, 0x00);
    }

    @Test
    @Timeout(5)
    public void v29FirstWindowVoidZeroFillsLikeUnrar() throws Exception {
        assertThat(extractFirstMember("/com/github/junrar/abnormal/void-dist-v29.rar"))
                .as("v29 first-window void must zero-fill (unrar oracle), not leak window bytes")
                .containsExactly(0x41, 0x41, 0x41, 0x00, 0x00, 0x00);
    }

    @Test
    @Timeout(5)
    public void v15FirstWindowVoidZeroFillsLikeUnrar() throws Exception {
        byte[] out = extractFirstMember("/com/github/junrar/abnormal/void-dist-v15.rar");
        // unrar keeps a 56464-byte broken member; the decode path is bitstream-driven and
        // identical between junrar and unrar, so the produced length must match.
        assertThat(out).as("v15 produced length must match unrar oracle").hasSize(56464);
        // The length-5 first-window void begins at output offset 34759 (shortLZ Length==14
        // match patched to distance = UnpPtr+1). unrar zero-fills it; junrar currently leaks
        // window[0..3] = 'R','I','F','F'.
        byte[] voidRegion = new byte[5];
        System.arraycopy(out, 34759, voidRegion, 0, 5);
        assertThat(voidRegion)
                .as(
                        "v15 first-window void at offset 34759 must zero-fill (unrar oracle), "
                                + "not leak the WAV's own 'RIFF' header bytes")
                .containsExactly(0x00, 0x00, 0x00, 0x00, 0x00);
    }

    /** Extract the first member through the public Archive API, returning bytes produced
     *  before the (expected) data-CRC failure of the deliberately corrupt member. */
    private byte[] extractFirstMember(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource);
                Archive archive = new Archive(in)) {
            assertThat(in).as("fixture resource %s", resource).isNotNull();
            FileHeader header = archive.nextFileHeader();
            assertThat(header).as("first member of %s", resource).isNotNull();
            ByteArrayOutputStream produced = new ByteArrayOutputStream();
            Throwable thrown = catchThrowable(() -> archive.extractFile(header, produced));
            // Corrupt member: junrar surfaces a typed RarException after writing the bytes.
            if (thrown != null) {
                assertThat(thrown).isInstanceOf(RarException.class);
            }
            return produced.toByteArray();
        }
    }
}
