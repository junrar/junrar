package com.github.junrar.rarfile.rar5;

import com.github.junrar.exception.CorruptHeaderException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
        final long archiveFlags = Rar5MainHeader.MHFL_VOLUME | Rar5MainHeader.MHFL_VOLNUMBER | Rar5MainHeader.MHFL_SOLID;
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
        final byte[] extra = extraRecord(Rar5MainExtraType.METADATA.getValue(), payload.toByteArray());

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
        final byte[] extra = extraRecord(Rar5MainExtraType.METADATA.getValue(), payload.toByteArray());

        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        final FileTime expected = FileTime.from(Instant.ofEpochSecond(
            nanosSinceEpoch / 1_000_000_000L, nanosSinceEpoch % 1_000_000_000L));
        assertThat(h.getMetadataTime()).isEqualTo(expected);
    }

    @Test
    void locatorRecordsPresenceOnlyOffsetsSkipped() throws Exception {
        final byte[] fixed = vint(0);
        final byte[] extra = extraRecord(Rar5MainExtraType.LOCATOR.getValue(), vint(0));
        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, extra));
        assertThat(h.hasLocator()).isTrue();
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
        final Rar5MainHeader h = parse(craft(Rar5BlockType.MAIN.getValue(), fixed, malformed.toByteArray()));

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
        writeLen(fixed, new byte[4]); // csum, unverified in M3.3

        final Rar5MainHeader h = parse(craft(Rar5BlockType.CRYPT.getValue(), fixed.toByteArray(), null));
        assertThat(h.getCryptVersion()).isEqualTo(0);
        assertThat(h.isUsePswCheck()).isTrue();
        assertThat(h.getLg2Count()).isEqualTo(15);
        assertThat(h.getSalt16()).isEqualTo(salt);
        assertThat(h.getPswCheck()).isEqualTo(pswCheck);
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
        assertThat(catchThrowable(() -> parse(craft(Rar5BlockType.CRYPT.getValue(), fixed.toByteArray(), null))))
            .isExactlyInstanceOf(CorruptHeaderException.class);
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
