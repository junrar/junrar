package com.github.junrar.rarfile.rar5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CorruptHeaderException;
import java.io.ByteArrayOutputStream;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/**
 * M3.3 (issue #24) direct-parse unit tests for {@link Rar5MainHeader}: MAIN archive flags +
 * MHEXTRA_LOCATOR/METADATA, CRYPT (valid + the two reject rows), and ENDARC. Crafted byte
 * arrays throughout, mirroring {@link Rar5BaseBlockTest}'s style -- no channel, no
 * {@code Archive}.
 */
class Rar5MainHeaderTest {

    // ---- crafting helpers (mirrors Rar5BaseBlockTest) ---------------------

    private static byte[] vint(long value) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long x = value;
        while (true) {
            final int b = (int) (x & 0x7f);
            x >>>= 7;
            if (x != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                break;
            }
        }
        return out.toByteArray();
    }

    private static void writeLen(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] extraRecord(long fieldType, byte[] payload) {
        final byte[] typeV = vint(fieldType);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLen(out, vint(typeV.length + payload.length));
        writeLen(out, typeV);
        writeLen(out, payload);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            writeLen(out, part);
        }
        return out.toByteArray();
    }

    /** Builds a full, CRC-valid RAR5 block: {@code type}, fixed fields, and an optional extra area. */
    private static byte[] craft(long type, byte[] fixedFields, byte[] extraArea) {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeLen(body, vint(type));
        final boolean hasExtra = extraArea != null && extraArea.length > 0;
        final long flags = hasExtra ? Rar5BaseBlock.HFL_EXTRA : 0;
        writeLen(body, vint(flags));
        if (hasExtra) {
            writeLen(body, vint(extraArea.length));
        }
        writeLen(body, fixedFields);
        if (hasExtra) {
            writeLen(body, extraArea);
        }
        final byte[] b = body.toByteArray();
        final byte[] sizeV = vint(b.length);
        final int headerSize = 4 + sizeV.length + b.length;
        final byte[] buf = new byte[headerSize];
        System.arraycopy(sizeV, 0, buf, 4, sizeV.length);
        System.arraycopy(b, 0, buf, 4 + sizeV.length, b.length);
        final CRC32 crc = new CRC32();
        crc.update(buf, 4, headerSize - 4);
        final int v = (int) crc.getValue();
        buf[0] = (byte) v;
        buf[1] = (byte) (v >>> 8);
        buf[2] = (byte) (v >>> 16);
        buf[3] = (byte) (v >>> 24);
        return buf;
    }

    private static Rar5MainHeader parse(byte[] buf) throws CorruptHeaderException {
        final Rar5BaseBlock base = Rar5BaseBlock.parse(buf, false);
        return Rar5MainHeader.from(base, buf);
    }

    /** Independent (not code-under-test) Windows FILETIME -> FileTime conversion, for assertions. */
    private static FileTime windowsFileTime(long ticks100ns) {
        final long seconds = ticks100ns / 10_000_000L - 11_644_473_600L;
        final long nanos = (ticks100ns % 10_000_000L) * 100L;
        return FileTime.from(Instant.ofEpochSecond(seconds, nanos));
    }

    private static void writeLongLE(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value >>> (8 * i)) & 0xff);
        }
    }

    // ---- MAIN ---------------------------------------------------------------

    @Test
    void volumeSolidAndVolumeNumberDriveFirstVolume() throws Exception {
        final long archiveFlags =
                Rar5MainHeader.MHFL_VOLUME
                        | Rar5MainHeader.MHFL_VOLNUMBER
                        | Rar5MainHeader.MHFL_SOLID;
        final byte[] fixed = concat(vint(archiveFlags), vint(5));
        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, null));
        assertThat(h.isVolume()).isTrue();
        assertThat(h.isSolid()).isTrue();
        assertThat(h.isLocked()).isFalse();
        assertThat(h.isProtected()).isFalse();
        assertThat(h.getVolumeNumber()).isEqualTo(5);
        assertThat(h.isFirstVolume()).isFalse();
    }

    @Test
    void volumeWithoutVolumeNumberIsFirstVolume() throws Exception {
        final byte[] fixed = vint(Rar5MainHeader.MHFL_VOLUME);
        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, null));
        assertThat(h.isVolume()).isTrue();
        assertThat(h.getVolumeNumber()).isEqualTo(0);
        assertThat(h.isFirstVolume()).isTrue();
    }

    @Test
    void metadataNameAndWindowsCtime() throws Exception {
        final byte[] fixed = vint(0);
        final long metaFlags = 0x01 | 0x02; // NAME | CTIME (no UNIXTIME -> Windows FILETIME)
        final byte[] nameBytes = "Arc".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(metaFlags));
        writeLen(payload, vint(nameBytes.length));
        writeLen(payload, nameBytes);
        final long ticks = 0x01CB7AE58359F000L;
        writeLongLE(payload, ticks);
        final byte[] extra =
                extraRecord(Rar5MainExtraType.METADATA.getValue(), payload.toByteArray());

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        assertThat(h.getMetadataName()).isEqualTo("Arc");
        assertThat(h.getMetadataTime()).isEqualTo(windowsFileTime(ticks));
    }

    @Test
    void metadataUnixNsCtime() throws Exception {
        final byte[] fixed = vint(0);
        final long metaFlags = 0x02 | 0x04 | 0x08; // CTIME | UNIXTIME | UNIX_NS
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(metaFlags));
        final long nanosSinceEpoch = 1_700_000_000_123_456_789L;
        writeLongLE(payload, nanosSinceEpoch);
        final byte[] extra =
                extraRecord(Rar5MainExtraType.METADATA.getValue(), payload.toByteArray());

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        final FileTime expected =
                FileTime.from(
                        Instant.ofEpochSecond(
                                nanosSinceEpoch / 1_000_000_000L,
                                nanosSinceEpoch % 1_000_000_000L));
        assertThat(h.getMetadataTime()).isEqualTo(expected);
    }

    @Test
    void metadataUnixSecondsCtime() throws Exception {
        // CTIME | UNIXTIME with UNIX_NS *clear* selects the 4-byte seconds field -- the third
        // and last of the three ctime encodings (the other two are pinned by
        // metadataNameAndWindowsCtime and metadataUnixNsCtime). Reading it as anything else
        // yields a wildly different instant, which is what the assertion below catches.
        final byte[] fixed = vint(0);
        final long metaFlags = 0x02 | 0x04; // CTIME | UNIXTIME, no UNIX_NS
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(metaFlags));
        final long secondsSinceEpoch = 1_700_000_000L;
        for (int i = 0; i < 4; i++) {
            payload.write((int) (secondsSinceEpoch >>> (8 * i)) & 0xff);
        }
        final byte[] extra =
                extraRecord(Rar5MainExtraType.METADATA.getValue(), payload.toByteArray());

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        assertThat(h.getMetadataTime())
                .isEqualTo(FileTime.from(Instant.ofEpochSecond(secondsSinceEpoch)));
    }

    @Test
    void metadataRecordWithEmptyPayloadYieldsNoMetadata() throws Exception {
        // A METADATA record whose declared size covers the FieldType vint and nothing else.
        // parseMetadata must return before reading its flags vint rather than running off the
        // end of the record into whatever follows.
        final byte[] fixed = vint(0);
        final byte[] extra = extraRecord(Rar5MainExtraType.METADATA.getValue(), new byte[0]);

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        assertThat(h.getMetadataName()).isNull();
        assertThat(h.getMetadataTime()).isNull();
    }

    @Test
    void mainExtraRecordDeclaringMoreBytesThanTheHeaderHoldsIsNotDispatched() throws Exception {
        // The loop-level size guard is what stops an over-long FieldSize from becoming an
        // out-of-bounds read. Every bound inside parseMetadata is relative to the record end
        // computed from FieldSize, so a record that declares far more bytes than the header
        // holds hands parseMetadata an end past the buffer -- and the NAME branch then reads
        // `nameSize` bytes that are not there. Declaring a 200-byte name inside a record that
        // claims 0x7fff bytes makes that read run off a header only a few dozen bytes long.
        //
        // Guarded, the record is never dispatched and the header parses clean. Unguarded, the
        // name read escapes the buffer as an IndexOutOfBoundsException -- not a
        // CorruptHeaderException, so it would cross the public API untyped.
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0x01)); // NAME
        writeLen(payload, vint(200)); // a name far longer than this header
        payload.write('A');

        final byte[] typeV = vint(Rar5MainExtraType.METADATA.getValue());
        final ByteArrayOutputStream extra = new ByteArrayOutputStream();
        writeLen(extra, vint(0x7fff)); // FieldSize far past the end of this header
        writeLen(extra, typeV);
        writeLen(extra, payload.toByteArray());

        final byte[] fixed = vint(0);
        final Rar5MainHeader h =
                parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra.toByteArray()));

        assertThat(h.getMetadataName()).isNull();
        assertThat(h.getMetadataTime()).isNull();
    }

    @Test
    void locatorRecordsPresenceOnlyOffsetsSkipped() throws Exception {
        final byte[] fixed = vint(0);
        final byte[] extra = extraRecord(Rar5MainExtraType.LOCATOR.getValue(), vint(0));
        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        assertThat(h.hasLocator()).isTrue();
    }

    @Test
    void zeroFieldSizeAtTheExtraAreaTailAbortsTheLoopCleanly() throws Exception {
        // A FieldSize vint of 0 followed by a lone continuation byte. The fieldSize <= 0 break
        // (unrar ProcessExtra50: "if (FieldSize<=0 || ...) break;") is what makes this a clean
        // parse; without it the loop reads the 0x80 tail byte as a FieldType vint, runs off the
        // buffer, and VInt's underrun throw rejects the whole otherwise-valid header. Not an
        // equivalent mutant, which is why this row exists.
        final byte[] fixed = vint(0);
        final byte[] extra = {0x00, (byte) 0x80};

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));

        assertThat(h.hasLocator()).isFalse();
        assertThat(h.getMetadataName()).isNull();
        assertThat(h.getMetadataTime()).isNull();
    }

    @Test
    void malformedMainExtraRecordFieldTypeLongerThanFieldSizeAbortsWholeLoop() throws Exception {
        // FieldSize declares only 1 byte total for type+payload, but the FieldType vint itself
        // is padded to 2 bytes (0x81 0x00 = value 1, LOCATOR) -- FieldType alone already
        // overruns the record. unrar aborts the whole extra-area loop in this case
        // (ProcessExtra50: "FieldSize=int64(NextPos-GetPos()); if (FieldSize<0) break;").
        // LOCATOR's handler has no byte-read guard of its own (it never reads the payload, only
        // records presence), so without the loop-level guard this malformed record would still
        // set hasLocator() true -- proving the record was dispatched despite being truncated.
        final ByteArrayOutputStream malformed = new ByteArrayOutputStream();
        writeLen(malformed, vint(1)); // FieldSize = 1
        malformed.write(0x81);
        malformed.write(0x00);

        final byte[] fixed = vint(0);
        final Rar5MainHeader h =
                parse(craft(Rar5BlockType.MAIN.getValue(), fixed, malformed.toByteArray()));

        assertThat(h.hasLocator()).isFalse();
    }

    // ---- CRYPT ----------------------------------------------------------------

    @Test
    void validCryptBlockParsesAllFields() throws Exception {
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0)); // CryptVersion
        writeLen(fixed, vint(0x0001)); // CHFL_CRYPT_PSWCHECK
        fixed.write(15); // Lg2Count
        final byte[] salt = new byte[16];
        for (int i = 0; i < 16; i++) {
            salt[i] = (byte) i;
        }
        writeLen(fixed, salt);
        final byte[] pswCheck = new byte[8];
        for (int i = 0; i < 8; i++) {
            pswCheck[i] = (byte) (0x10 + i);
        }
        writeLen(fixed, pswCheck);
        writeLen(fixed, sha256Prefix(pswCheck)); // csum, verified in M3.4

        final Rar5MainHeader h =
                parse(craft(Rar5BlockType.CRYPT.getValue(), fixed.toByteArray(), null));
        assertThat(h.getCryptVersion()).isEqualTo(0);
        assertThat(h.isUsePswCheck()).isTrue();
        assertThat(h.getLg2Count()).isEqualTo(15);
        assertThat(h.getSalt16()).isEqualTo(salt);
        assertThat(h.getPswCheck()).isEqualTo(pswCheck);
    }

    /**
     * M3.4: a pswcheck whose SHA-256-prefix csum does not match is unusable -- unrar keeps parsing
     * but drops UsePswCheck ({@code arcread.cpp:752-755}) so a damaged check can't reject a valid
     * password.
     */
    @Test
    void cryptBlockWithBadPswCheckCsumDropsTheFlag() throws Exception {
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0));
        writeLen(fixed, vint(0x0001));
        fixed.write(15);
        writeLen(fixed, new byte[16]);
        writeLen(fixed, new byte[8]);
        writeLen(fixed, new byte[] {1, 2, 3, 4}); // csum that cannot match SHA-256(pswCheck)

        final Rar5MainHeader h =
                parse(craft(Rar5BlockType.CRYPT.getValue(), fixed.toByteArray(), null));
        assertThat(h.isUsePswCheck()).isFalse();
        assertThat(h.getPswCheck()).isNull();
    }

    /**
     * M3.4: a CRYPT header that sets the PSWCHECK flag but is truncated before the pswcheck bytes
     * must drop UsePswCheck (never leave it true with a null value) -- otherwise a subsequent
     * encrypted header would throw a spurious WrongPasswordException for the correct password.
     */
    @Test
    void cryptBlockWithFlagButTruncatedPswCheckDropsTheFlag() throws Exception {
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0));
        writeLen(fixed, vint(0x0001)); // PSWCHECK flag set
        fixed.write(15);
        writeLen(fixed, new byte[16]); // salt, then the header ends -- no pswcheck bytes follow

        final Rar5MainHeader h =
                parse(craft(Rar5BlockType.CRYPT.getValue(), fixed.toByteArray(), null));
        assertThat(h.isUsePswCheck()).isFalse();
        assertThat(h.getPswCheck()).isNull();
    }

    private static byte[] sha256Prefix(final byte[] data) throws Exception {
        return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(data), 4);
    }

    @Test
    void unsupportedCryptVersionRejected() {
        final byte[] fixed = vint(1);
        assertThat(catchThrowable(() -> parse(craft(Rar5BlockType.CRYPT.getValue(), fixed, null))))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void lg2CountAbove24Rejected() {
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0));
        writeLen(fixed, vint(0));
        fixed.write(25);
        assertThat(
                        catchThrowable(
                                () ->
                                        parse(
                                                craft(
                                                        Rar5BlockType.CRYPT.getValue(),
                                                        fixed.toByteArray(),
                                                        null))))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void cryptHeaderEndingBeforeLg2CountRejected() {
        // CryptVersion + EncFlags and nothing else. Without the length guard the very next
        // statement indexes header[pos] past the end and escapes as a raw
        // ArrayIndexOutOfBoundsException instead of the typed corruption error every other
        // malformed-header path produces.
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0)); // CryptVersion
        writeLen(fixed, vint(0)); // EncFlags, no PSWCHECK

        final Throwable thrown =
                catchThrowable(
                        () ->
                                parse(
                                        craft(
                                                Rar5BlockType.CRYPT.getValue(),
                                                fixed.toByteArray(),
                                                null)));
        assertThat(thrown)
                .isExactlyInstanceOf(CorruptHeaderException.class)
                .hasMessageContaining("Truncated RAR5 CRYPT header");
    }

    @Test
    void cryptHeaderEndingMidSaltRejected() {
        // Lg2Count present and valid, but only 8 of the 16 salt bytes. This one cannot be
        // caught by the guard above: the failure mode without the salt-length check is silent
        // rather than loud, because Arrays.copyOfRange zero-pads a short range instead of
        // throwing -- the header would parse "successfully" with a half-fabricated salt, and
        // every key derived from it would be wrong.
        final ByteArrayOutputStream fixed = new ByteArrayOutputStream();
        writeLen(fixed, vint(0)); // CryptVersion
        writeLen(fixed, vint(0)); // EncFlags
        fixed.write(15); // Lg2Count, within range
        fixed.write(new byte[8], 0, 8); // half a salt

        final Throwable thrown =
                catchThrowable(
                        () ->
                                parse(
                                        craft(
                                                Rar5BlockType.CRYPT.getValue(),
                                                fixed.toByteArray(),
                                                null)));
        assertThat(thrown)
                .isExactlyInstanceOf(CorruptHeaderException.class)
                .hasMessageContaining("Truncated RAR5 CRYPT header");
    }

    // ---- ENDARC -----------------------------------------------------------------

    @Test
    void endArcWithoutNextVolume() throws Exception {
        final Rar5MainHeader h = parse(craft(Rar5BlockType.ENDARC.getValue(), vint(0), null));
        assertThat(h.isNextVolume()).isFalse();
    }

    @Test
    void endArcWithNextVolume() throws Exception {
        final Rar5MainHeader h = parse(craft(Rar5BlockType.ENDARC.getValue(), vint(0x0001), null));
        assertThat(h.isNextVolume()).isTrue();
    }
}
