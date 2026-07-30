package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.volume.InputStreamVolumeManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The multi-volume half of the checksum-less RAR5 entry (unrar {@code HASH_NONE}, see {@link
 * Rar5NoChecksumTest} for the single-file case). Every {@code HFL_SPLITAFTER} part header normally
 * carries the checksum of its own packed chunk, verified at the volume switch; when the entry
 * stores no checksum at all there is nothing to verify and the switch must pass.
 *
 * <p>unrar arms {@code PackedDataHash} with the header's own hash type, so a checksum-less entry
 * accumulates nothing ({@code 8f437ab:volume.cpp:182}), and the merge-time {@code Cmp} short-
 * circuits to true because {@code HashValue::operator==} returns true whenever either side is
 * {@code HASH_NONE} ({@code 8f437ab:hash.cpp:31-32}). Before the {@code ComprDataIO} guard junrar
 * instead measured the packed chunk against a zero {@code getFileCRC()} and threw {@link
 * com.github.junrar.exception.CrcErrorException} at the first switch — so the {@link
 * Rar5NoChecksumTest} fixture alone could never reach this path.
 *
 * <p>Fixture: {@code volumes/rar5-part/nochecksum.partN.rar}, the {@code stored} set with
 * {@code FHFL_CRC32} stripped from every volume's FILE header (see that directory's README for the
 * recipe). {@link #SHA_STORED2} is the {@code unrar 7.20} oracle for it — {@code unrar x} merges
 * all three volumes, prints {@code "?"} instead of {@code OK} for the checksum column, reports
 * {@code All OK} with exit 0, and writes 120 000 bytes byte-identical to the unpatched
 * {@code stored} set's extraction.
 */
@Timeout(60)
class Rar5NoChecksumVolumeTest {

    /** {@code unrar 7.20} {@code x} oracle for {@code stored2.bin}, checksum-less set and stored. */
    private static final String SHA_STORED2 =
            "16a25156ddff0139ef7eb37a3243b314b754f020e9450227915dee0bbb4a9e10";

    @Test
    void splitEntryWithoutChecksumMergesVolumes() throws Exception {
        try (Archive a = openVolumeSet("nochecksum", 3)) {
            final FileHeader hd = a.getFileHeaders().get(0);
            assertThat(hd.getFileName()).isEqualTo("stored2.bin");
            // Not vacuous: the fixture really is a checksum-less entry that really does span.
            assertThat(hd.hasFileCrc()).isFalse();
            assertThat(hd.getFileCRC()).isZero();
            assertThat(hd.isSplitAfter()).isTrue();

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            a.extractFile(hd, out); // must not throw at either volume switch
            assertThat(sha256(out.toByteArray()))
                    .as("a checksum-less entry must merge volumes, not fail the packed-hash check")
                    .isEqualTo(SHA_STORED2);
        }
    }

    /** Open a committed {@code .partN.rar} set through the stream volume manager, first part first. */
    private Archive openVolumeSet(final String name, final int parts) throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        for (int part = 1; part <= parts; part++) {
            final String resource = "volumes/rar5-part/" + name + ".part" + part + ".rar";
            final InputStream is = getClass().getResourceAsStream(resource);
            assertThat(is).as("fixture %s", resource).isNotNull();
            streams.add(is);
        }
        return new Archive(new InputStreamVolumeManager(streams), ArchiveOptions.builder().build());
    }

    private static String sha256(final byte[] b) throws Exception {
        final byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
        final StringBuilder sb = new StringBuilder(d.length * 2);
        for (final byte x : d) {
            sb.append(Character.forDigit((x >> 4) & 0xf, 16))
                    .append(Character.forDigit(x & 0xf, 16));
        }
        return sb.toString();
    }
}
