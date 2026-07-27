package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarEncryptedException;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.unpack.Unpack;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2 (issue #293) RAR 1.4 extraction coverage matrix -- the unencrypted seam (stored, method
 * 0x30, and compressed via the existing Unpack15 engine), with Checksum14 verification and the
 * archive-solid routing convention RAR 1.4 needs (no per-file solid flag exists in the wire
 * format). Every synthetic fixture here is hand-built byte-by-byte from the same field layout
 * {@link Rar14HeaderListingTest} pins (unrar {@code Archive::ReadHeader14},
 * {@code d861246:arcread.cpp:1256-1331}); the byte-builder helpers below are intentionally
 * duplicated from that file rather than shared, matching this suite's existing per-file
 * convention.
 *
 * <p><b>{@code RarCRC.checkOldCrc} fix-round history (load-bearing context for the stored-fixture
 * payload lengths below):</b> the first P2 round found {@link RarCRC#checkOldCrc} DIVERGED from
 * unrar's reference algorithm ({@code d861246:crc.cpp:155-164}) for the overwhelming majority of
 * payloads longer than about six bytes -- a Java {@code short} accumulator sign-extends through
 * {@code >>> 15} once the running checksum crosses 0x8000, corrupting the rotate. That round kept
 * every stored-fixture payload here to six ASCII characters or fewer (verified, not assumed, to
 * dodge the bug) and left it as a reported carry-forward. It has since been fixed in
 * {@code RarCRC.checkOldCrc} itself (masked 16-bit arithmetic throughout, matching crc.cpp
 * exactly -- {@link #checksum14AlgorithmPinsAgainstHandDerivedVectors()}'s fourth vector pins the
 * crossing case). The short payloads below are UNCHANGED by the fix (frozen fixtures); {@link
 * #realDistributionArchiveExtractsAllTenEntriesByteExact()} (coverage row 3), which extracts
 * real, far-longer-than-six-byte payloads, is what actually proves the fix: it is now strict (no
 * exception permitted), where it previously had to tolerate a {@code CrcErrorException} on every
 * one of the 10 real entries.
 */
class Rar14ExtractionTest {

    @TempDir Path tempDir;

    private static final byte[] MARKER14 = {0x52, 0x45, 0x7e, 0x5e};

    /**
     * Coverage row 3's oracle: unrar 7.x extracted {@code /private/tmp/rar140dc.rar} ("All OK"),
     * SHA-256 of each of its 10 entries recorded 2026-07-27 at
     * {@code /private/tmp/rar14-oracle/extracted/} (session-verified per the P2 brief). Order
     * matches header order, cross-checked against {@link
     * Rar14HeaderListingTest#realDistributionArchiveListsTenEntriesFirstAnyToRarDoc()}.
     */
    private static final String[] ORACLE_NAMES = {
        "ANY2RAR.DOC",
        "LICENSE.DOC",
        "OPTIONS.DOC",
        "RAR.DOC",
        "RAR_BBS.DOC",
        "README",
        "README.A2R",
        "TECHNOTE.DOC",
        "TRANSLAT.DOC",
        "WHATSNEW.DOC"
    };

    private static final String[] ORACLE_SHA256 = {
        "f85debe8ad28c79568fb481e2bad1ae94a87aec24b583a0c0a40a0c81303bf97",
        "232ebcd69e9861a9ead790cb0994f3403f21c4498bc6edfa06ba3022196572f9",
        "034e5155f163036e5bf039d04a61726bd484cba5a6b90cbaab37b069129f06a9",
        "91b5811c3d2726893b3f9fd7cc74491a11e0fcd5a35f8b7dc5c0f0aac13abfd7",
        "60346b12096a534283e380b9ed78cd8dd0a1d8f3bfc84ec8e6bc584b4a1965e7",
        "72afe524c93ca4682f1dbc14e0fac18c9340db383920554d7e375103bd4fbaa6",
        "443c307d81b58a96339db5d5deff633cde7e57ac93015b7984e252ac37949e80",
        "35acad886f3808d53670c4c9400ec3f3a4c82f97ef08775089e9d5c433234d0a",
        "2861ed45ae962e71b7b792b655fa511b933c4bbe55468a7270c6971dd0e08134",
        "04eea91abf14caaf2b92a5224763c53438b4a1e5d518c71f35edd0d1507fa7ce"
    };

    private File writeTemp(String name, byte[] bytes) throws Exception {
        final Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    private static void u16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    private static void u32(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xff));
        out.write((int) ((v >>> 8) & 0xff));
        out.write((int) ((v >>> 16) & 0xff));
        out.write((int) ((v >>> 24) & 0xff));
    }

    /** The 7-byte SIZEOF_MAINHEAD14 block: marker + HeadSize u16 LE + Flags u8. */
    private static byte[] mainHeader14(int headSize, int flags) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER14, 0, MARKER14.length);
        u16(out, headSize);
        out.write(flags);
        return out.toByteArray();
    }

    /**
     * The SIZEOF_FILEHEAD14 (21-byte) fixed block plus the OEM name bytes -- {@code headSize} is
     * always well-formed ({@code 21 + nameBytes.length}) in this suite, unlike
     * {@link Rar14HeaderListingTest}'s hostile-input variant, since P2's coverage matrix targets
     * the extraction seam, not header parsing.
     */
    private static byte[] fileHeader14(
            int dataSize,
            int unpSize,
            int crc16,
            int dosTimeValue,
            int fileAttr,
            int flags,
            int unpVerByte,
            int method,
            byte[] nameBytes) {
        final int headSize = 21 + nameBytes.length;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, dataSize);
        u32(out, unpSize);
        u16(out, crc16);
        u16(out, headSize);
        u32(out, dosTimeValue);
        out.write(fileAttr);
        out.write(flags);
        out.write(unpVerByte);
        out.write(nameBytes.length);
        out.write(method);
        out.write(nameBytes, 0, nameBytes.length);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /** A stored (method 0) RAR 1.4 file entry: fixed header + name + the payload verbatim. */
    private static byte[] storedEntry(String name, byte[] payload, int crc16) {
        final byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        return concat(
                fileHeader14(payload.length, payload.length, crc16, 0, 0x20, 0, 1, 0, nameBytes),
                payload);
    }

    private static String sha256(byte[] data) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] digest = md.digest(data);
            final StringBuilder sb = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // ---- Row 1: stored entry, correct Checksum14 -> exact bytes, no exception. ----

    @Test
    void storedEntryWithCorrectChecksumExtractsExactBytes() throws Exception {
        final byte[] payload = "Data1".getBytes(StandardCharsets.US_ASCII);
        // Verified (not assumed): RarCRC.checkOldCrc(0, "Data1", 5) == 0x1416, matching unrar's
        // crc.cpp Checksum14 reference for this short payload (class javadoc).
        final byte[] entry = storedEntry("ROW1.TXT", payload, 0x1416);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("row1.rar", bytes))) {
            final FileHeader hd = archive.getFileHeaders().get(0);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            archive.extractFile(hd, out);
            assertThat(out.toByteArray()).isEqualTo(payload);
        }
    }

    // ---- Row 2: stored entry, WRONG Checksum14 -> CrcErrorException, but partial output
    // written (unstoreFile writes before the post-hoc CRC compare runs). ----

    @Test
    void storedEntryWithWrongChecksumThrowsCrcErrorButStillWritesOutput() throws Exception {
        final byte[] payload = "Data1".getBytes(StandardCharsets.US_ASCII);
        // One bit flipped from the correct 0x1416 (verified above): 0x1417.
        final byte[] entry = storedEntry("ROW2.TXT", payload, 0x1417);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("row2.rar", bytes))) {
            final FileHeader hd = archive.getFileHeaders().get(0);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Throwable thrown = catchThrowable(() -> archive.extractFile(hd, out));
            assertThat(thrown).isExactlyInstanceOf(CrcErrorException.class);
            assertThat(out.toByteArray()).as("partial output still written").isEqualTo(payload);
        }
    }

    // ---- Row 3: real oracle archive, all 10 entries, SHA-256 exact AND no exception (strict
    // -- fix round: this used to tolerate CrcErrorException on every entry, see class javadoc).
    // ----

    @Test
    void realDistributionArchiveExtractsAllTenEntriesByteExact() throws Exception {
        final Path real = Paths.get("/private/tmp/rar140dc.rar");
        assumeTrue(
                Files.exists(real), "local-only oracle fixture /private/tmp/rar140dc.rar absent");

        try (Archive archive = new Archive(real.toFile())) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).extracting(FileHeader::getFileName).containsExactly(ORACLE_NAMES);

            for (int i = 0; i < files.size(); i++) {
                final FileHeader hd = files.get(i);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                // Strict: extraction of a real, correctly-checksummed archive must not throw.
                // (Before the checkOldCrc fix this threw CrcErrorException on all 10 entries
                // despite byte-exact output -- a tolerate-and-typecheck pattern that let this
                // test report PASS while masking a real, every-real-file defect. Not anymore.)
                archive.extractFile(hd, out);
                assertThat(sha256(out.toByteArray()))
                        .as(
                                "entry %d (%s) decompressed bytes vs unrar 7.x oracle",
                                i, hd.getFileName())
                        .isEqualTo(ORACLE_SHA256[i]);
            }
        }
    }

    // ---- Row 4: encrypted 1.4 entry -> UnsupportedRarEncryptedException, not NPE/CrcError.
    // ----

    @Test
    void encryptedEntryThrowsUnsupportedRarEncryptedException() throws Exception {
        final byte[] payload = "abcd".getBytes(StandardCharsets.US_ASCII);
        final byte[] nameBytes = "SECRET.TXT".getBytes(StandardCharsets.US_ASCII);
        final byte[] entry =
                concat(
                        fileHeader14(
                                payload.length,
                                payload.length,
                                0,
                                0,
                                0x20,
                                BaseBlock.LHD_PASSWORD,
                                1,
                                0,
                                nameBytes),
                        payload);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("row4.rar", bytes))) {
            final FileHeader hd = archive.getFileHeaders().get(0);
            assertThat(hd.isEncrypted()).isTrue();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Throwable thrown = catchThrowable(() -> archive.extractFile(hd, out));
            assertThat(thrown).isExactlyInstanceOf(UnsupportedRarEncryptedException.class);
        }
    }

    // ---- Row 5: two-entry stored fixture, extract the SECOND entry directly (random access)
    // -- proves per-entry data-offset seek, not sequential-read luck. ----

    @Test
    void secondEntryExtractsDirectlyByteExactProvingPerEntryDataOffset() throws Exception {
        final byte[] payload1 = "Entry1".getBytes(StandardCharsets.US_ASCII);
        final byte[] payload2 = "Entry2".getBytes(StandardCharsets.US_ASCII);
        // Verified: checkOldCrc("Entry1")==0x2c16, checkOldCrc("Entry2")==0x2c18.
        final byte[] entry1 = storedEntry("FIRST.TXT", payload1, 0x2c16);
        final byte[] entry2 = storedEntry("SECOND.TXT", payload2, 0x2c18);
        final byte[] bytes = concat(mainHeader14(7, 0), entry1, entry2);
        try (Archive archive = new Archive(writeTemp("row5.rar", bytes))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(2);

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Extract ONLY the second entry -- never touch the first -- so a correct result
            // can only come from this entry's OWN data-offset field, not from a sequential
            // cursor left over from reading entry 1.
            archive.extractFile(files.get(1), out);
            assertThat(out.toByteArray()).isEqualTo(payload2);
        }
    }

    // ---- Row 6: Checksum14 algorithm pin -- 3 brief-mandated hand-derived vectors from
    // crc.cpp:155-164, plus a 4th (fix round) that crosses 0x8000 and exposes the
    // sign-extension bug the fix removes. ----

    /**
     * Hand derivation (crc.cpp:155-164: {@code crc=(crc+byte)&0xffff}, then
     * {@code crc=rotl16(crc,1)}, init 0):
     *
     * <ul>
     *   <li>Empty input: the loop body never runs -> result is the init value, 0.
     *   <li>Single byte 0x01: crc=(0+1)&0xffff=1; rotl16(1,1) = (1&lt;&lt;1)|(1&gt;&gt;&gt;15) =
     *       2|0 = 2.
     *   <li>Two-byte ASCII "AB" (0x41, 0x42): byte 0x41 -> crc=(0+0x41)&0xffff=0x41;
     *       rotl16(0x41,1)=(0x41&lt;&lt;1)|(0x41&gt;&gt;&gt;15)=0x82|0=0x82. byte 0x42 ->
     *       crc=(0x82+0x42)&0xffff=0xC4; rotl16(0xC4,1)=(0xC4&lt;&lt;1)|0=0x188. Final: 0x0188
     *       (392 decimal).
     * </ul>
     *
     * <p><b>Fourth vector (fix round): eight bytes of 0xFF, chosen to cross 0x8000 and expose
     * the pre-fix sign-extension bug.</b> Each step is crc=(crc+0xff)&amp;0xffff then
     * rotl16(crc,1); rotl16(x,1) = ((x&lt;&lt;1)|(x&gt;&gt;&gt;15))&amp;0xffff, where
     * {@code x>>>15} reads bit 15 of the 16-bit value (0 or 1) since 16-bit x never has bits
     * above 15 set. Carrying the value forward step by step:
     *
     * <pre>
     * step 1: (0+0xff)&amp;0xffff=0x00FF; bit15=0 -&gt; rotl=0x01FE
     * step 2: (0x01FE+0xff)=0x02FD;      bit15=0 -&gt; rotl=0x05FA
     * step 3: (0x05FA+0xff)=0x06F9;      bit15=0 -&gt; rotl=0x0DF2
     * step 4: (0x0DF2+0xff)=0x0EF1;      bit15=0 -&gt; rotl=0x1DE2
     * step 5: (0x1DE2+0xff)=0x1EE1;      bit15=0 -&gt; rotl=0x3DC2
     * step 6: (0x3DC2+0xff)=0x3EC1;      bit15=0 -&gt; rotl=0x7D82
     * step 7: (0x7D82+0xff)=0x7E81;      bit15=0 (0x7E81&lt;0x8000) -&gt; rotl=0xFD02
     * step 8: (0xFD02+0xff)&amp;0xffff=0xFE01; bit15=1 (0xFE01&gt;=0x8000) -&gt;
     *         rotl=((0xFE01&lt;&lt;1)&amp;0xffff)|1 = 0xFC02|1 = 0xFC03
     * </pre>
     *
     * Final (correct, reference) result after 8 bytes of 0xFF: {@code 0xFC03}. Before the fix,
     * step 8's rotate corrupted this to {@code 0xFFFF}: the value carried INTO step 8 (0xFD02)
     * is >=0x8000, so as a Java {@code short} it is negative (-766); promoting it to {@code int}
     * for {@code >>> 15} sign-extends the top 16 bits to all-1s instead of zeros, so the shift
     * pulls 17 garbage 1-bits down instead of the single real bit 15, and the final
     * {@code (short)} cast collapses the OR of two now-all-1s halves to {@code 0xFFFF}.
     */
    @Test
    void checksum14AlgorithmPinsAgainstHandDerivedVectors() {
        assertThat(RarCRC.checkOldCrc((short) 0, new byte[0], 0)).isEqualTo((short) 0);
        assertThat(RarCRC.checkOldCrc((short) 0, new byte[] {0x01}, 1)).isEqualTo((short) 2);
        assertThat(RarCRC.checkOldCrc((short) 0, new byte[] {0x41, 0x42}, 2) & 0xffff)
                .isEqualTo(0x0188);

        final byte[] eightFF = new byte[8];
        java.util.Arrays.fill(eightFF, (byte) 0xff);
        assertThat(RarCRC.checkOldCrc((short) 0, eightFF, eightFF.length) & 0xffff)
                .as("crosses 0x8000 at step 8 -- see method javadoc derivation")
                .isEqualTo(0xfc03);
    }

    // ---- Row 7: solid-flag ARGUMENT-SEAM contract (fix round 3, gate finding). ----

    /** Records one {@code (unpVersion, solid)} pair observed at {@link Archive#invokeDoUnpack}. */
    private static final class DoUnpackCall {
        final int unpVersion;
        final boolean solid;

        DoUnpackCall(final int unpVersion, final boolean solid) {
            this.unpVersion = unpVersion;
            this.solid = solid;
        }
    }

    /**
     * Solid-flag routing pins the EXACT {@code (version, solid)} argument pair {@link Archive}
     * passes to {@code Unpack.doUnpack} for each of two sequentially-extracted stored entries in
     * an {@code MHD_SOLID} archive: {@code (10, false)} then {@code (10, true)}.
     *
     * <p><b>Why this test exists ALONGSIDE the real-fixture one below, not instead of it:</b> a
     * stored-only fixture can never observably differ between {@code solid=false} and {@code
     * solid=true} through decoded OUTPUT bytes ({@code unstoreFile} never reads the flag) --
     * which is exactly how the gate caught the previous version of this test: both a hardcoded
     * {@code effectiveSolid=false} and a hardcoded {@code effectiveSolid=true} survived the
     * entire suite, because nothing asserted the argument itself, only an indirect ordinal
     * ({@link Archive#getOldFormatExtractedCount()}). {@link
     * #realSolidArchiveExtractsAllThreeEntriesByteExactAndCrcExact()} below is now a real,
     * decoder-observable, genuinely solid+compressed fixture (a real DOS RAR 1.402 archive was
     * found after all -- an earlier claim that none could exist here is retracted), but it
     * cannot discriminate an "always route to UnpVer 15" mutation from a correct one: every
     * entry in that archive is already compressed (method != stored), so the routing ternary
     * (stored keeps 10/13, compressed hardcodes 15) evaluates to 15 either way. THIS test's
     * fixture is stored (UnpVer 10), so it is the one that still catches that mutation (see the
     * mutation-proof record in the P2 handoff).
     *
     * <p>The argument-seam discriminator: pin the exact argument pair, which is unrar's
     * {@code Unp->DoUnpack(15, FileCount>1 && Arc.Solid)} ({@code d861246:extract.cpp:919}) at
     * the ARGUMENT level, not an inferred side effect. {@link Unpack} is {@code final} (cannot
     * be subclassed) so the recording double below overrides {@link Archive#invokeDoUnpack} --
     * the one package-private, non-final method Archive exposes specifically for this -- on an
     * anonymous {@code Archive} subclass, and DELEGATES to the real call (rather than no-op) so
     * the fixture's stored bytes still extract and verify normally, keeping the existing
     * byte-exact/ordinal assertions meaningful alongside the new argument-seam ones.
     */
    @Test
    void solidArchiveRoutesFirstEntryNonSolidAndSecondSolidArgumentSeam() throws Exception {
        final byte[] payload1 = "SOLID1".getBytes(StandardCharsets.US_ASCII);
        final byte[] payload2 = "SOLID2".getBytes(StandardCharsets.US_ASCII);
        // Verified: checkOldCrc("SOLID1")==0x271a, checkOldCrc("SOLID2")==0x271c.
        final byte[] entry1 = storedEntry("S1.TXT", payload1, 0x271a);
        final byte[] entry2 = storedEntry("S2.TXT", payload2, 0x271c);
        // MHD_SOLID set on the synthesized main header.
        final byte[] bytes = concat(mainHeader14(7, BaseBlock.MHD_SOLID), entry1, entry2);
        final File file = writeTemp("row7.rar", bytes);

        final List<DoUnpackCall> calls = new ArrayList<>();
        try (Archive archive =
                new Archive(file) {
                    @Override
                    void invokeDoUnpack(final int unpVersion, final boolean solid)
                            throws IOException, RarException {
                        calls.add(new DoUnpackCall(unpVersion, solid));
                        super.invokeDoUnpack(unpVersion, solid);
                    }
                }) {
            assertThat(archive.getMainHeader().isSolid()).isTrue();
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(archive.getOldFormatExtractedCount()).isZero();

            final ByteArrayOutputStream out1 = new ByteArrayOutputStream();
            archive.extractFile(files.get(0), out1);
            final ByteArrayOutputStream out2 = new ByteArrayOutputStream();
            archive.extractFile(files.get(1), out2);

            // Byte-exact output and the session ordinal, kept from the prior version of this
            // test -- real, but insufficient alone (gate finding); see the argument-seam
            // assertions below for what actually pins the routing.
            assertThat(out1.toByteArray()).isEqualTo(payload1);
            assertThat(out2.toByteArray()).isEqualTo(payload2);
            assertThat(archive.getOldFormatExtractedCount()).isEqualTo(2);

            // The seam-contract assertion: the EXACT argument pairs Archive passed.
            assertThat(calls).hasSize(2);
            assertThat(calls.get(0).unpVersion)
                    .as("first entry version (unpVerByte 1)")
                    .isEqualTo(10);
            assertThat(calls.get(0).solid).as("first entry: solid must be false").isFalse();
            assertThat(calls.get(1).unpVersion)
                    .as("second entry version (unpVerByte 1)")
                    .isEqualTo(10);
            assertThat(calls.get(1).solid).as("second entry: solid must be true").isTrue();
        }
    }

    // ---- Row 7 (real fixture): genuinely solid + compressed RAR 1.4 archive -- decoder side of
    // the same contract, complementing the argument-seam test above. ----

    /**
     * {@code src/test/resources/com/github/junrar/rar14-solid.rar} provenance: {@code
     * SOLID.RAR} from github.com/bitplane/oldrar ({@code tests/fixtures/rar13/SOLID.RAR}), MIT
     * OR Apache-2.0 dual-licensed, a genuine DOS-era RAR-1.402-produced archive (session-probed:
     * main header Flags byte 0x88 -&gt; {@code MHD_SOLID} set; 3 entries; first file header
     * method 3 [compressed], {@code unpVerByte} 0x02 -&gt; {@code UnpVer} 13). unrar 7.x
     * extracted it "All OK" (oracle run 2026-07-27); the three per-entry SHA-256 digests below
     * are that run's output.
     *
     * <p>This is the decoder-observable half of the solid-routing proof: entries 2 and 3
     * ({@code HELLO.TXT}, {@code TINY.TXT}) only decode to the correct bytes AND pass the
     * Checksum14 compare if Unpack15's LZ window and {@code ComprDataIO}'s CRC accumulator both
     * correctly carry over from entry 1 ({@code BIG80K.TXT}) -- i.e. only if {@code solid=true}
     * was genuinely passed to {@code doUnpack} for them (unrar {@code extract.cpp:919}). This
     * fixture is what surfaced the P2 fix-round-3 finding below (not a byte-content bug: SHA-256
     * matched the oracle on the FIRST run against pre-fix code -- it was the CRC compare itself
     * that was wrong).
     *
     * <p><b>Fix-round-3 finding (in scope, fixed in this round):</b> RED against the pre-fix
     * code threw {@code CrcErrorException} on both {@code HELLO.TXT} and {@code TINY.TXT} (never
     * on {@code BIG80K.TXT}), despite decompressed bytes matching the oracle SHA-256 exactly on
     * every entry (verified by reflection probe before writing this test, and reproduced by this
     * test's own strict assertions). Root cause: {@code ComprDataIO.unpWrite}'s old-format CRC
     * branch called {@code RarCRC.checkOldCrc(startCrc, addr, count)}, silently dropping the
     * {@code offset} parameter -- so it always hashed {@code addr[0, count)} instead of {@code
     * addr[offset, offset+count)}. For the first flush of a fresh window ({@code offset==0}, the
     * common case exercised by every RAR3+ archive this library has ever been tested against)
     * that is harmless; a SOLID entry's flush starts wherever the shared window pointer ({@code
     * wrPtr}) already sits after the PREVIOUS entry -- nonzero, in general -- which P2 is the
     * first code path to ever reach with {@code isOldFormat()==true}. Output bytes were still
     * correct ({@code outputStream.write(addr, offset, count)} always used {@code offset}
     * correctly), only the checksum read from the wrong window slice. Fixed by adding an
     * offset-aware {@code RarCRC.checkOldCrc(short, byte[], int, int)} overload (additive, the
     * existing 3-arg signature is unchanged per the round-2 authorization) and using it at this
     * one call site.
     */
    @Test
    void realSolidArchiveExtractsAllThreeEntriesByteExactAndCrcExact() throws Exception {
        final File file =
                new File(getClass().getResource("/com/github/junrar/rar14-solid.rar").toURI());
        try (Archive archive = new Archive(file)) {
            assertThat(archive.getMainHeader().isSolid()).isTrue();
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files)
                    .extracting(FileHeader::getFileName)
                    .containsExactly("BIG80K.TXT", "HELLO.TXT", "TINY.TXT");

            final String[] expectedSha = {
                "00c17390322bed4ba19a0b208a133be120891e6e64426838928e211b14ee3e23",
                "f04a81f40cf9f9523a368dd16166e79dca9701b4f91dbd18e339c3b8106f07e0",
                "d206dc60246d939acc46587b8aa30421d8331c98b7142673efda41f94cfa3655"
            };
            for (int i = 0; i < files.size(); i++) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                // Strict: a real, correctly-checksummed archive must extract without throwing.
                archive.extractFile(files.get(i), out);
                assertThat(sha256(out.toByteArray()))
                        .as(
                                "entry %d (%s) decompressed bytes vs unrar 7.x oracle",
                                i, files.get(i).getFileName())
                        .isEqualTo(expectedSha[i]);
            }
        }
    }
}
