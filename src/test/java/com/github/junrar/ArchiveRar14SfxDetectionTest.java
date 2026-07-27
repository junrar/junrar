package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1 fix round (issue #293, gate finding 1): {@code Archive.detectFormatAndSeek}'s SFX scan
 * must port unrar's RSFX corroboration for a RAR 1.4 marker found past the very first scanned
 * byte ({@code d861246:archive.cpp:160-166}). The bare 4-byte 1.4 marker ({@code 52 45 7e 5e})
 * is weak and false-positives inside arbitrary SFX stub binaries; unrar only accepts such a
 * match when the fixed absolute file offset 28..31 reads {@code 52 53 46 58} ("RSFX"),
 * otherwise the scan keeps looking rather than accepting or failing outright.
 *
 * <p>unrar's exact guard: {@code Format==RARFMT14 && I>0 && CurPos<28 && ReadSize>31}, where
 * {@code CurPos} is always {@code 1} in this port (so {@code CurPos<28} is unconditionally
 * true) and {@code I} is the scan offset from {@code CurPos} -- i.e. a match at absolute file
 * offset 1 itself (the byte right after the ruled-out offset 0) is exempt, every later match
 * needs RSFX corroboration at the fixed absolute offset 28.
 */
class ArchiveRar14SfxDetectionTest {

    @TempDir Path tempDir;

    private File writeTemp(String name, byte[] bytes) throws Exception {
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private byte[] fixtureBytes(String name) throws Exception {
        return Files.readAllBytes(Paths.get(getClass().getResource(name).toURI()));
    }

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /**
     * A decoy 1.4 marker inside an SFX stub, with no RSFX corroboration at absolute offset 28,
     * followed by a genuine RAR15 signature further in. Must open as the real RAR15 archive,
     * not misdetect RAR14 off the decoy -- this fails today (no RSFX gate: the scan just
     * accepts the first RAR14-shaped match it finds).
     */
    @Test
    void sfxStubWithUncorroboratedDecoy14MarkerOpensRealFormat() throws Exception {
        final byte[] real = fixtureBytes("gh108.rar"); // genuine RAR15 archive, marker at 0
        // Offsets 0-9: non-marker stub padding (first byte != 0x52, so the offset-0 fast path
        // is ruled out, forcing the SFX scan).
        final byte[] leadPadding = new byte[10];
        // Offsets 10-13: decoy RAR 1.4 marker. Absolute offset 10 -> scan index I = 10-1 = 9 >
        // 0, so the RSFX guard is live for this match.
        final byte[] decoyMarker = {0x52, 0x45, 0x7e, 0x5e};
        // Offsets 14-39: padding, deliberately NOT "RSFX" at the fixed absolute offset 28-31
        // (all-zero here), so the decoy is correctly rejected and scanning continues.
        final byte[] tailPadding = new byte[26];
        final byte[] bytes = concat(leadPadding, decoyMarker, tailPadding, real);

        try (Archive archive = new Archive(writeTemp("sfx-decoy14.rar", bytes))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR15);
        }
    }

    /**
     * A genuine 1.4 marker inside an SFX stub, correctly corroborated by "RSFX" at the fixed
     * absolute offset 28. Must open as RAR14 and list its (here, empty) headers. Verifies the
     * new RSFX gate does not reject a legitimate SFX-wrapped RAR 1.4 archive -- a regression
     * guard for the fix, not itself expected to fail before the fix (the pre-fix code has no
     * gate to reject this case either).
     */
    @Test
    void sfxStubWithCorroboratedRsfx14MarkerOpensAsRar14() throws Exception {
        // Offsets 0-27: stub padding (first byte != 0x52).
        final byte[] leadPadding = new byte[28];
        // Offsets 28-31: RSFX corroboration at the fixed absolute offset the brief pins.
        final byte[] rsfx = {0x52, 0x53, 0x46, 0x58};
        // Offsets 32-38: the real RAR 1.4 marker + minimal main header (HeadSize=7, Flags=0),
        // byte-identical in shape to the rar14-empty.rar fixture -- an empty archive. Absolute
        // offset 32 -> scan index I = 32-1 = 31 > 0, so the RSFX guard applies and is satisfied.
        final byte[] rar14MinimalMain = {0x52, 0x45, 0x7e, 0x5e, 0x07, 0x00, 0x00};
        final byte[] bytes = concat(leadPadding, rsfx, rar14MinimalMain);

        try (Archive archive = new Archive(writeTemp("sfx-rsfx14.rar", bytes))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR14);
            assertThat(archive.isOldFormat()).isTrue();
            assertThat(archive.getFileHeaders()).isEmpty();
        }
    }

    /**
     * Gate finding 2: a plain (non-SFX, marker at offset 0) RAR 1.4 archive whose main-header
     * Flags byte carries a stray bit 0x80 (MHD_PASSWORD in the RAR15+ vocabulary) must not
     * mark the archive encrypted -- unrar's {@code ReadHeader14} interprets only
     * MHD_VOLUME|MHD_COMMENT|MHD_LOCK|MHD_SOLID|MHD_PACK_COMMENT from this byte
     * ({@code d861246:arcread.cpp:1274-1278}); RAR 1.4 has no main-header encryption concept at
     * all (only per-file LHD_PASSWORD). Fails today: the synthesized MainHeader only masks the
     * MHD_PACK_COMMENT/MHD_NEWNUMBERING bit collision, so bit 0x80 passes straight through to
     * MainHeader.isEncrypted().
     */
    @Test
    void strayPasswordBitInMainFlagsDoesNotMarkArchiveEncrypted() throws Exception {
        final byte[] bytes = {0x52, 0x45, 0x7e, 0x5e, 0x07, 0x00, (byte) 0x80};

        try (Archive archive = new Archive(writeTemp("rar14-stray-pwd-bit.rar", bytes))) {
            assertThat(archive.isOldFormat()).isTrue();
            assertThat(archive.isEncrypted()).isFalse();
            assertThat(archive.isPasswordProtected()).isFalse();
        }
    }
}
