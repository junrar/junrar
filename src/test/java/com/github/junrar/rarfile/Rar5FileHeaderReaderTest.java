package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.rarfile.rar5.Rar5BaseBlock;
import com.github.junrar.rarfile.rar5.Rar5BlockType;
import com.github.junrar.rarfile.rar5.Rar5FileExtraType;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.rarfile.rar5.Rar5RedirType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * M3.3 (issue #24) direct-parse unit tests for {@link Rar5FileHeaderReader}: the FILE/SERVICE
 * fixed-field layout plus every FHEXTRA record, and the hostile rows (S4/C10 analogs). Crafted
 * byte arrays throughout, mirroring {@code Rar5BaseBlockTest}'s style -- no channel, no
 * {@code Archive}.
 */
class Rar5FileHeaderReaderTest {

    // ---- crafting helpers ---------------------------------------------------

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

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) {
            out.write((value >>> (8 * i)) & 0xff);
        }
    }

    private static void writeLongLE(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) (value >>> (8 * i)) & 0xff);
        }
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

    private static final long FHFL_DIRECTORY = 0x0001;
    private static final long FHFL_UTIME = 0x0002;
    private static final long FHFL_CRC32 = 0x0004;

    /** Builds the FILE/SERVICE fixed-field byte sequence (manual &sect;4b, exact order). */
    private static byte[] fixedFields(long fileFlags, long unpSize, long fileAttr, Integer mtimeUnix,
                                       Integer crc32, long compInfo, long hostOS, byte[] nameBytes) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLen(out, vint(fileFlags));
        writeLen(out, vint(unpSize));
        writeLen(out, vint(fileAttr));
        if (mtimeUnix != null) {
            writeIntLE(out, mtimeUnix);
        }
        if (crc32 != null) {
            writeIntLE(out, crc32);
        }
        writeLen(out, vint(compInfo));
        writeLen(out, vint(hostOS));
        writeLen(out, vint(nameBytes.length));
        writeLen(out, nameBytes);
        return out.toByteArray();
    }

    /** Same as {@link #fixedFields}, but omits the name bytes (for the oversized-name row). */
    private static byte[] fixedFieldsNoNameBytes(long fileFlags, long unpSize, long fileAttr, long compInfo,
                                                  long hostOS, long declaredNameSize) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLen(out, vint(fileFlags));
        writeLen(out, vint(unpSize));
        writeLen(out, vint(fileAttr));
        writeLen(out, vint(compInfo));
        writeLen(out, vint(hostOS));
        writeLen(out, vint(declaredNameSize));
        return out.toByteArray();
    }

    /** Builds a full, CRC-valid RAR5 FILE/SERVICE block. */
    private static byte[] craft(long type, byte[] fixed, byte[] extraArea) {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeLen(body, vint(type));
        final boolean hasExtra = extraArea != null && extraArea.length > 0;
        final long flags = hasExtra ? Rar5BaseBlock.HFL_EXTRA : 0;
        writeLen(body, vint(flags));
        if (hasExtra) {
            writeLen(body, vint(extraArea.length));
        }
        writeLen(body, fixed);
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

    private static FileHeader parse(byte[] buf) throws CorruptHeaderException {
        final Rar5BaseBlock base = Rar5BaseBlock.parse(buf, false);
        return Rar5FileHeaderReader.read(base, buf);
    }

    private static byte[] name(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ---- minimal FILE ---------------------------------------------------------

    @Test
    void minimalFileStoreCrc32WindowsHost() throws Exception {
        final byte[] fixed = fixedFields(FHFL_CRC32, 100, 0x20, null, 0xDEADBEEF, 0, 0, name("MINIMAL.TXT"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, null));

        assertThat(fh.getFileName()).isEqualTo("MINIMAL.TXT");
        assertThat(fh.getUnpSize()).isEqualTo(100);
        assertThat(fh.getFullUnpackSize()).isEqualTo(100);
        assertThat(fh.getFileCRC()).isEqualTo(0xDEADBEEF);
        assertThat(fh.getUnpMethod()).isEqualTo((byte) 0);
        assertThat(fh.getUnpVersion()).isEqualTo((byte) 50);
        assertThat(fh.isDirectory()).isFalse();
        assertThat(fh.isSolid()).isFalse();
        assertThat(fh.getHostOS()).isEqualTo(HostSystem.win32);
        assertThat(fh.getHeaderType()).isEqualTo(UnrarHeadertype.FileHeader);
        assertThat(fh.isFileHeader()).isTrue();
    }

    @Test
    void directoryFlagSetsIsDirectory() throws Exception {
        final byte[] fixed = fixedFields(FHFL_DIRECTORY, 0, 0x10, null, null, 0, 1, name("adir"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, null));
        assertThat(fh.isDirectory()).isTrue();
        assertThat(fh.getHostOS()).isEqualTo(HostSystem.unix);
    }

    // ---- HTIME ------------------------------------------------------------------

    @Test
    void htimeUnixSeconds() throws Exception {
        final long m = 1_700_000_000L;
        final long c = 1_700_000_100L;
        final long a = 1_700_000_200L;
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((0x01 | 0x02 | 0x04 | 0x08)); // UNIXTIME | MTIME | CTIME | ATIME
        writeIntLE(payload, (int) m);
        writeIntLE(payload, (int) c);
        writeIntLE(payload, (int) a);
        final byte[] extra = extraRecord(Rar5FileExtraType.HTIME.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("t.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getLastModifiedTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(m)));
        assertThat(fh.getCreationTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(c)));
        assertThat(fh.getLastAccessTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(a)));
    }

    @Test
    void htimeWindowsFileTime() throws Exception {
        final long ticks = 0x01CB7AE58359F000L;
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((0x02 | 0x04 | 0x08)); // MTIME | CTIME | ATIME, no UNIXTIME
        writeLongLE(payload, ticks);
        writeLongLE(payload, ticks);
        writeLongLE(payload, ticks);
        final byte[] extra = extraRecord(Rar5FileExtraType.HTIME.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("t.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        final long seconds = ticks / 10_000_000L - 11_644_473_600L;
        final long nanos = (ticks % 10_000_000L) * 100L;
        final FileTime expected = FileTime.from(Instant.ofEpochSecond(seconds, nanos));
        assertThat(fh.getLastModifiedTime()).isEqualTo(expected);
        assertThat(fh.getCreationTime()).isEqualTo(expected);
        assertThat(fh.getLastAccessTime()).isEqualTo(expected);
    }

    @Test
    void htimeUnixWithNanoseconds() throws Exception {
        final long baseSeconds = 1_700_000_000L;
        final int mNs = 123_000_000;
        final int cNs = 456_000_000;
        final int aNs = 789_000_000;
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write((0x01 | 0x10 | 0x02 | 0x04 | 0x08)); // UNIXTIME | UNIX_NS | MTIME | CTIME | ATIME
        writeIntLE(payload, (int) baseSeconds);
        writeIntLE(payload, (int) baseSeconds);
        writeIntLE(payload, (int) baseSeconds);
        writeIntLE(payload, mNs);
        writeIntLE(payload, cNs);
        writeIntLE(payload, aNs);
        final byte[] extra = extraRecord(Rar5FileExtraType.HTIME.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("t.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getLastModifiedTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(baseSeconds, mNs)));
        assertThat(fh.getCreationTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(baseSeconds, cNs)));
        assertThat(fh.getLastAccessTime()).isEqualTo(FileTime.from(Instant.ofEpochSecond(baseSeconds, aNs)));
    }

    // ---- HASH ---------------------------------------------------------------------

    @Test
    void hashBlake2Record() throws Exception {
        final byte[] digest = new byte[32];
        for (int i = 0; i < digest.length; i++) {
            digest[i] = (byte) i;
        }
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0)); // FHEXTRA_HASH_BLAKE2
        writeLen(payload, digest);
        final byte[] extra = extraRecord(Rar5FileExtraType.HASH.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("h.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getHashType()).isEqualTo(Rar5HashType.BLAKE2);
        assertThat(fh.getHashDigest()).isEqualTo(digest);
    }

    // ---- VERSION ------------------------------------------------------------------

    @Test
    void versionRecordAppendsSemicolonNumberToName() throws Exception {
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0)); // flags, unused
        writeLen(payload, vint(7));
        final byte[] extra = extraRecord(Rar5FileExtraType.VERSION.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("VERFILE.TXT"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getFileVersion()).isEqualTo(7);
        assertThat(fh.getFileName()).isEqualTo("VERFILE.TXT;7");
    }

    // ---- REDIR --------------------------------------------------------------------

    @Test
    void redirRecordUnixSymlink() throws Exception {
        final byte[] target = name("a/b");
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(Rar5RedirType.UNIX_SYMLINK.getValue()));
        writeLen(payload, vint(0)); // not a directory target
        writeLen(payload, vint(target.length));
        writeLen(payload, target);
        final byte[] extra = extraRecord(Rar5FileExtraType.REDIR.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("link"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getRedirection()).isNotNull();
        assertThat(fh.getRedirection().getType()).isEqualTo(Rar5RedirType.UNIX_SYMLINK);
        assertThat(fh.getRedirection().isDirectory()).isFalse();
        assertThat(fh.getRedirection().getTarget()).isEqualTo("a/b");
    }

    @Test
    void redirRecordHostileTraversalTargetParsedNotRejected() throws Exception {
        final byte[] target = name("../../etc/passwd");
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(Rar5RedirType.UNIX_SYMLINK.getValue()));
        writeLen(payload, vint(0));
        writeLen(payload, vint(target.length));
        writeLen(payload, target);
        final byte[] extra = extraRecord(Rar5FileExtraType.REDIR.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("evil-link"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getRedirection().getTarget()).isEqualTo("../../etc/passwd").contains("..");
        // The entry's own name is a separate concern and is not rejected by the hostile target.
        assertThat(fh.getFileName()).isEqualTo("evil-link");
    }

    @Test
    void redirRecordHugeNameSizeDoesNotOverflowOrThrow() throws Exception {
        // nameSize is an attacker-controlled vint up to ~2^63. Pre-fix, "pos + nameSize"
        // overflowed to a value that passed the bound check, then "(int) nameSize" truncated
        // and Arrays.copyOfRange threw an unchecked IllegalArgumentException. No actual name
        // bytes follow the declared size -- there aren't 2^63 bytes in this buffer.
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(Rar5RedirType.UNIX_SYMLINK.getValue()));
        writeLen(payload, vint(0));
        writeLen(payload, vint(Long.MAX_VALUE));
        final byte[] extra = extraRecord(Rar5FileExtraType.REDIR.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("huge-redir"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getRedirection()).isNotNull();
        assertThat(fh.getRedirection().getType()).isEqualTo(Rar5RedirType.UNIX_SYMLINK);
        assertThat(fh.getRedirection().getTarget()).isNull();
    }

    @Test
    void malformedExtraRecordFieldTypeLongerThanFieldSizeAbortsWholeLoop() throws Exception {
        // FieldSize declares only 1 byte total for type+payload, but the FieldType vint itself
        // is padded to 2 bytes (0x86 0x00 = value 6, UOWNER) -- FieldType alone already overruns
        // the record. unrar aborts the whole extra-area loop in this case (ProcessExtra50:
        // "FieldSize=int64(NextPos-GetPos()); if (FieldSize<0) break;"). parseUOwner has no
        // start>=end guard of its own (unlike parseHash/parseVersion/parseRedir), so without the
        // loop-level guard it would still unconditionally read the trailing byte below as
        // "Flags" and build a non-null Rar5UnixOwner from bytes that belong to the NEXT record
        // (or, with nothing following, past this malformed record entirely) -- proving the
        // record was dispatched despite being truncated.
        final ByteArrayOutputStream extra = new ByteArrayOutputStream();
        writeLen(extra, vint(1)); // FieldSize = 1
        extra.write(0x86);
        extra.write(0x00);
        extra.write(0x00); // byte a correct implementation must never reach

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("m.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra.toByteArray()));

        assertThat(fh.getUnixOwner()).isNull();
    }

    // ---- UOWNER -------------------------------------------------------------------

    @Test
    void uownerRecord() throws Exception {
        final byte[] user = name("alice");
        final byte[] group = name("staff");
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0x01 | 0x02 | 0x04 | 0x08)); // UNAME|GNAME|NUMUID|NUMGID
        writeLen(payload, vint(user.length));
        writeLen(payload, user);
        writeLen(payload, vint(group.length));
        writeLen(payload, group);
        writeLen(payload, vint(501));
        writeLen(payload, vint(20));
        final byte[] extra = extraRecord(Rar5FileExtraType.UOWNER.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 1, name("u.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.getUnixOwner()).isNotNull();
        assertThat(fh.getUnixOwner().getUserName()).isEqualTo("alice");
        assertThat(fh.getUnixOwner().getGroupName()).isEqualTo("staff");
        assertThat(fh.getUnixOwner().getOwnerId()).isEqualTo(501);
        assertThat(fh.getUnixOwner().getGroupId()).isEqualTo(20);
        assertThat(fh.getUnixOwner().isNumericOwnerId()).isTrue();
        assertThat(fh.getUnixOwner().isNumericGroupId()).isTrue();
    }

    // ---- CRYPT (per-file) -----------------------------------------------------------

    @Test
    void cryptRecordSynthesizesEncryptedAndExposesSaltFacts() throws Exception {
        final byte[] salt = new byte[16];
        final byte[] initV = new byte[16];
        for (int i = 0; i < 16; i++) {
            salt[i] = (byte) i;
            initV[i] = (byte) (0x40 + i);
        }
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0)); // encVersion
        writeLen(payload, vint(0x01)); // FHEXTRA_CRYPT_PSWCHECK
        payload.write(14); // Lg2Count
        writeLen(payload, salt);
        writeLen(payload, initV);
        final byte[] pswCheck = {1, 2, 3, 4, 5, 6, 7, 8};
        writeLen(payload, pswCheck);
        writeLen(payload, sha256Prefix(pswCheck)); // csum, verified in M3.4
        final byte[] extra = extraRecord(Rar5FileExtraType.CRYPT.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("enc.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.isEncrypted()).isTrue();
        assertThat(fh.getSalt16()).isEqualTo(salt);
        assertThat(fh.getInitVector()).isEqualTo(initV);
        assertThat(fh.getLg2Count()).isEqualTo(14);
        assertThat(fh.isUsePswCheck()).isTrue();
        assertThat(fh.getPswCheck()).isEqualTo(pswCheck);
    }

    /**
     * M3.4: a per-file pswcheck whose SHA-256-prefix csum does not match drops UsePswCheck (unrar
     * {@code arcread.cpp:1096-1102}), so a damaged check can't reject a valid password.
     */
    @Test
    void cryptRecordWithBadPswCheckCsumDropsTheFlag() throws Exception {
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLen(payload, vint(0));
        writeLen(payload, vint(0x01));
        payload.write(14);
        writeLen(payload, new byte[16]);
        writeLen(payload, new byte[16]);
        writeLen(payload, new byte[]{9, 9, 9, 9, 9, 9, 9, 9}); // pswCheck
        writeLen(payload, new byte[]{0, 0, 0, 0}); // csum that cannot match
        final byte[] extra = extraRecord(Rar5FileExtraType.CRYPT.getValue(), payload.toByteArray());

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("enc.txt"));
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, extra));

        assertThat(fh.isEncrypted()).isTrue();
        assertThat(fh.isUsePswCheck()).isFalse();
        assertThat(fh.getPswCheck()).isNull();
    }

    private static byte[] sha256Prefix(final byte[] data) throws Exception {
        return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(data), 4);
    }

    // ---- SERVICE / SUBDATA -----------------------------------------------------------

    @Test
    void serviceHeaderWithSubData() throws Exception {
        final byte[] payload = "hello comment".getBytes(StandardCharsets.UTF_8);
        final byte[] extra = extraRecord(Rar5FileExtraType.SUBDATA.getValue(), payload);

        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, name("CMT"));
        final FileHeader fh = parse(craft(Rar5BlockType.SERVICE.getValue(), fixed, extra));

        assertThat(fh.getHeaderType()).isEqualTo(UnrarHeadertype.NewSubHeader);
        assertThat(fh.getFileName()).isEqualTo("CMT");
        assertThat(fh.getSubData()).isEqualTo(payload);
    }

    // ---- hostile rows -----------------------------------------------------------------

    @Test
    void nonPositiveNameSizeRejected() {
        final byte[] fixed = fixedFieldsNoNameBytes(0, 0, 0, 0, 0, 0);
        assertThat(catchThrowable(() -> parse(craft(Rar5BlockType.FILE.getValue(), fixed, null))))
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void oversizedNameRunningPastBufferRejected() {
        final byte[] fixed = fixedFieldsNoNameBytes(0, 0, 0, 0, 0, 50);
        assertThat(catchThrowable(() -> parse(craft(Rar5BlockType.FILE.getValue(), fixed, null))))
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void nonUtf8NameBytesDecodeToReplacementCharsWithoutCrashing() throws Exception {
        final byte[] nameBytes = {(byte) 0xFF, (byte) 0xFE, 'a', 'b', '.', 't', 'x', 't'};
        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, nameBytes);
        final FileHeader fh = parse(craft(Rar5BlockType.FILE.getValue(), fixed, null));
        assertThat(fh.getFileName()).contains("ab.txt").contains("�");
    }

    @Test
    void embeddedNulInNameRejectedByFilenameValidityGate() {
        final byte[] nameBytes = {'a', 0x00, 'b'};
        final byte[] fixed = fixedFields(0, 0, 0, null, null, 0, 0, nameBytes);
        assertThat(catchThrowable(() -> parse(craft(Rar5BlockType.FILE.getValue(), fixed, null))))
            .isExactlyInstanceOf(CorruptHeaderException.class);
    }
}
