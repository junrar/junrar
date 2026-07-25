package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins no-go row C1 (docs/porting/MIGRATION_MANUAL.md ss7): a solid RAR v20
 * back-reference reaching before the window start must fail with a typed
 * RarException, never ArrayIndexOutOfBoundsException. Unpack20.CopyString20's
 * destPtr&gt;=0 guard (Unpack20.java:215) routes the out-of-range case into a
 * masked-wrap copy that silently emits garbage instead of throwing; the typed
 * failure surfaces later, as a data-CRC mismatch.
 * <p>
 * Fixture provenance: solid/generate_v20_negative_backref_fixture.py hand-
 * assembles a 2-member solid v20 archive (no RAR 2.x binary was available to
 * produce a genuine one) whose second member's first LZ77 token is a
 * back-reference with distance 2 while the shared window holds only 1 byte
 * written by the first member - see that script for the byte-exact design.
 */
public class Unpack20SolidTest {

    private static final String FIXTURE = "solid/v20-solid-negative-backref.rar";

    @Test
    public void givenSolidV20NegativeBackref_whenExtractingSecondMember_thenCrcErrorNotAIOOBE()
            throws Exception {
        try (InputStream is = getClass().getResourceAsStream(FIXTURE);
                Archive archive = new Archive(is)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(2);

            FileHeader secondMember = fileHeaders.get(1);
            assertThat(secondMember.getUnpVersion()).isEqualTo((byte) 20);
            assertThat(secondMember.isSolid()).isTrue();

            Throwable thrown =
                    catchThrowable(
                            () -> {
                                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    archive.extractFile(secondMember, baos);
                                }
                            });

            assertThat(thrown).isNotInstanceOf(ArrayIndexOutOfBoundsException.class);
            assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
        }
    }
}
