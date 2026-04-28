package com.github.junrar.rar5.header.extra;

import com.github.junrar.rar5.io.VInt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Rar5ExtraParserTest {

    @Test
    void parseEmptyExtraArea() {
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(new byte[10], 5, 0);
        assertThat(records).isEmpty();
    }

    @Test
    void parseSingleCryptRecord() {
        final byte[] extraData = buildCryptRecord(0, 0, 15, new byte[16], new byte[16], null);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5FileCryptRecord.class);

        final Rar5FileCryptRecord crypt = (Rar5FileCryptRecord) records.get(0);
        assertThat(crypt.getVersion()).isZero();
        assertThat(crypt.getFlags()).isZero();
        assertThat(crypt.getKdfCount()).isEqualTo(15);
        assertThat(crypt.getSalt()).hasSize(16);
        assertThat(crypt.getIv()).hasSize(16);
        assertThat(crypt.hasPswCheck()).isFalse();
    }

    @Test
    void parseCryptRecordWithPswCheck() {
        final byte[] pswCheck = new byte[8];
        pswCheck[0] = 0x42;
        final byte[] extraData = buildCryptRecord(0, Rar5FileCryptRecord.FHEXTRA_CRYPT_PSWCHECK,
            15, new byte[16], new byte[16], pswCheck);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        final Rar5FileCryptRecord crypt = (Rar5FileCryptRecord) records.get(0);
        assertThat(crypt.hasPswCheck()).isTrue();
        assertThat(crypt.getPswCheck()).hasSize(8);
        assertThat(crypt.getPswCheck()[0]).isEqualTo((byte) 0x42);
    }

    @Test
    void parseHashRecord() {
        final byte[] digest = new byte[32];
        digest[0] = (byte) 0xAB;
        final byte[] extraData = buildHashRecord(digest);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5FileHashRecord.class);

        final Rar5FileHashRecord hash = (Rar5FileHashRecord) records.get(0);
        assertThat(hash.isBlake2()).isTrue();
        assertThat(hash.getDigest()).hasSize(32);
        assertThat(hash.getDigest()[0]).isEqualTo((byte) 0xAB);
    }

    @Test
    void parseTimeRecordUnix() {
        final long mtime = 1609459200L;
        final byte[] extraData = buildTimeRecordUnix(mtime, 0, 0, null, null, null);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5FileTimeRecord.class);

        final Rar5FileTimeRecord time = (Rar5FileTimeRecord) records.get(0);
        assertThat(time.isUnixTime()).isTrue();
        assertThat(time.getMtime()).isEqualTo(mtime);
        assertThat(time.getCtime()).isNull();
        assertThat(time.getAtime()).isNull();
    }

    @Test
    void parseTimeRecordWithNanoseconds() {
        final long mtime = 1609459200L;
        final long mtimeNs = 123456789L;
        final byte[] extraData = buildTimeRecordUnixNs(mtime, mtimeNs);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        final Rar5FileTimeRecord time = (Rar5FileTimeRecord) records.get(0);
        assertThat(time.isUnixTime()).isTrue();
        assertThat(time.hasNanoseconds()).isTrue();
        assertThat(time.getMtime()).isEqualTo(mtime);
        assertThat(time.getMtimeNs()).isEqualTo(mtimeNs);
    }

    @Test
    void parseVersionRecord() {
        final byte[] extraData = buildVersionRecord(5);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5FileVersionRecord.class);

        final Rar5FileVersionRecord version = (Rar5FileVersionRecord) records.get(0);
        assertThat(version.getVersion()).isEqualTo(5);
        assertThat(version.hasVersion()).isTrue();
    }

    @Test
    void parseRedirRecord() {
        final byte[] extraData = buildRedirRecord(Rar5RedirRecord.REDIR_UNIX_SYMLINK, 0, "../target");
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5RedirRecord.class);

        final Rar5RedirRecord redir = (Rar5RedirRecord) records.get(0);
        assertThat(redir.getRedirType()).isEqualTo(Rar5RedirRecord.REDIR_UNIX_SYMLINK);
        assertThat(redir.getTargetName()).isEqualTo("../target");
        assertThat(redir.isSymlink()).isTrue();
        assertThat(redir.isDirTarget()).isFalse();
    }

    @Test
    void parseRedirRecordDirTarget() {
        final byte[] extraData = buildRedirRecord(Rar5RedirRecord.REDIR_UNIX_SYMLINK,
            Rar5RedirRecord.FHEXTRA_REDIR_DIR, "/some/dir");
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        final Rar5RedirRecord redir = (Rar5RedirRecord) records.get(0);
        assertThat(redir.isDirTarget()).isTrue();
    }

    @Test
    void parseUnixOwnerRecord() {
        final byte[] extraData = buildUnixOwnerRecord(
            Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMUID | Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMGID
                | Rar5UnixOwnerRecord.FHEXTRA_UOWNER_UNAME | Rar5UnixOwnerRecord.FHEXTRA_UOWNER_GNAME,
            "myuser", "mygroup", 1000, 1000);
        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(extraData, 0, extraData.length);

        assertThat(records).hasSize(1);
        assertThat(records.get(0)).isInstanceOf(Rar5UnixOwnerRecord.class);

        final Rar5UnixOwnerRecord owner = (Rar5UnixOwnerRecord) records.get(0);
        assertThat(owner.getUserName()).isEqualTo("myuser");
        assertThat(owner.getGroupName()).isEqualTo("mygroup");
        assertThat(owner.getUid()).isEqualTo(1000);
        assertThat(owner.getGid()).isEqualTo(1000);
        assertThat(owner.hasNumericUid()).isTrue();
        assertThat(owner.hasNumericGid()).isTrue();
    }

    @Test
    void parseMultipleRecords() {
        final byte[] hashExtra = buildHashRecord(new byte[32]);
        final byte[] timeExtra = buildTimeRecordUnix(1609459200L, 0, 0, null, null, null);
        final byte[] combined = new byte[hashExtra.length + timeExtra.length];
        System.arraycopy(hashExtra, 0, combined, 0, hashExtra.length);
        System.arraycopy(timeExtra, 0, combined, hashExtra.length, timeExtra.length);

        final List<Rar5ExtraRecord> records = Rar5ExtraParser.parse(combined, 0, combined.length);

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).isInstanceOf(Rar5FileHashRecord.class);
        assertThat(records.get(1)).isInstanceOf(Rar5FileTimeRecord.class);
    }

    @Test
    void parseNullData() {
        assertThatThrownBy(() -> Rar5ExtraParser.parse(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void parseNegativeExtraStart() {
        assertThatThrownBy(() -> Rar5ExtraParser.parse(new byte[10], -1, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    void parseNegativeExtraSize() {
        assertThatThrownBy(() -> Rar5ExtraParser.parse(new byte[10], 0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }

    @Test
    void parseExtraBeyondData() {
        assertThatThrownBy(() -> Rar5ExtraParser.parse(new byte[10], 5, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("beyond data");
    }

    private static byte[] buildCryptRecord(final long version, final long flags, final int kdfCount,
                                            final byte[] salt, final byte[] iv, final byte[] pswCheck) {
        int dataSize = 0;
        dataSize += VInt.encodedLength(Rar5FileCryptRecord.TYPE);
        dataSize += VInt.encodedLength(version);
        dataSize += VInt.encodedLength(flags);
        dataSize += 1; // kdfCount
        dataSize += Rar5FileCryptRecord.SALT_SIZE;
        dataSize += Rar5FileCryptRecord.IV_SIZE;
        if (pswCheck != null) {
            dataSize += Rar5FileCryptRecord.PSWCHECK_SIZE;
            dataSize += Rar5FileCryptRecord.PSWCHECK_CSUM_SIZE;
        }

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5FileCryptRecord.TYPE, data, pos);
        pos += VInt.write(version, data, pos);
        pos += VInt.write(flags, data, pos);
        data[pos++] = (byte) kdfCount;
        System.arraycopy(salt, 0, data, pos, salt.length);
        pos += salt.length;
        System.arraycopy(iv, 0, data, pos, iv.length);
        pos += iv.length;
        if (pswCheck != null) {
            System.arraycopy(pswCheck, 0, data, pos, pswCheck.length);
            pos += pswCheck.length;
            pos += Rar5FileCryptRecord.PSWCHECK_CSUM_SIZE; // checksum
        }

        return data;
    }

    private static byte[] buildHashRecord(final byte[] digest) {
        int dataSize = VInt.encodedLength(Rar5FileHashRecord.TYPE)
            + VInt.encodedLength(Rar5FileHashRecord.HASH_BLAKE2)
            + digest.length;

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5FileHashRecord.TYPE, data, pos);
        pos += VInt.write(Rar5FileHashRecord.HASH_BLAKE2, data, pos);
        System.arraycopy(digest, 0, data, pos, digest.length);

        return data;
    }

    private static byte[] buildTimeRecordUnix(final long mtime, final long ctime, final long atime,
                                               final Long mtimeNs, final Long ctimeNs, final Long atimeNs) {
        long flags = Rar5FileTimeRecord.FHEXTRA_HTIME_UNIXTIME | Rar5FileTimeRecord.FHEXTRA_HTIME_MTIME;
        if (ctime != 0) flags |= Rar5FileTimeRecord.FHEXTRA_HTIME_CTIME;
        if (atime != 0) flags |= Rar5FileTimeRecord.FHEXTRA_HTIME_ATIME;

        int dataSize = VInt.encodedLength(Rar5FileTimeRecord.TYPE) + VInt.encodedLength(flags) + 4;
        if (ctime != 0) dataSize += 4;
        if (atime != 0) dataSize += 4;

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5FileTimeRecord.TYPE, data, pos);
        pos += VInt.write(flags, data, pos);
        data[pos++] = (byte) (mtime & 0xFF);
        data[pos++] = (byte) ((mtime >> 8) & 0xFF);
        data[pos++] = (byte) ((mtime >> 16) & 0xFF);
        data[pos++] = (byte) ((mtime >> 24) & 0xFF);
        if (ctime != 0) {
            data[pos++] = (byte) (ctime & 0xFF);
            data[pos++] = (byte) ((ctime >> 8) & 0xFF);
            data[pos++] = (byte) ((ctime >> 16) & 0xFF);
            data[pos++] = (byte) ((ctime >> 24) & 0xFF);
        }
        if (atime != 0) {
            data[pos++] = (byte) (atime & 0xFF);
            data[pos++] = (byte) ((atime >> 8) & 0xFF);
            data[pos++] = (byte) ((atime >> 16) & 0xFF);
            data[pos++] = (byte) ((atime >> 24) & 0xFF);
        }

        return data;
    }

    private static byte[] buildTimeRecordUnixNs(final long mtime, final long mtimeNs) {
        final long flags = Rar5FileTimeRecord.FHEXTRA_HTIME_UNIXTIME
            | Rar5FileTimeRecord.FHEXTRA_HTIME_MTIME
            | Rar5FileTimeRecord.FHEXTRA_HTIME_UNIX_NS;

        int dataSize = VInt.encodedLength(Rar5FileTimeRecord.TYPE) + VInt.encodedLength(flags) + 4 + 4;

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5FileTimeRecord.TYPE, data, pos);
        pos += VInt.write(flags, data, pos);
        data[pos++] = (byte) (mtime & 0xFF);
        data[pos++] = (byte) ((mtime >> 8) & 0xFF);
        data[pos++] = (byte) ((mtime >> 16) & 0xFF);
        data[pos++] = (byte) ((mtime >> 24) & 0xFF);
        data[pos++] = (byte) (mtimeNs & 0xFF);
        data[pos++] = (byte) ((mtimeNs >> 8) & 0xFF);
        data[pos++] = (byte) ((mtimeNs >> 16) & 0xFF);
        data[pos++] = (byte) ((mtimeNs >> 24) & 0xFF);

        return data;
    }

    private static byte[] buildVersionRecord(final long version) {
        final int dataSize = VInt.encodedLength(Rar5FileVersionRecord.TYPE) + VInt.encodedLength(0) + VInt.encodedLength(version);
        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5FileVersionRecord.TYPE, data, pos);
        pos += VInt.write(0, data, pos); // flags
        pos += VInt.write(version, data, pos);
        return data;
    }

    private static byte[] buildRedirRecord(final long redirType, final long flags, final String target) {
        final byte[] nameBytes = target.getBytes(StandardCharsets.UTF_8);
        int dataSize = VInt.encodedLength(Rar5RedirRecord.TYPE) + VInt.encodedLength(redirType)
            + VInt.encodedLength(flags) + VInt.encodedLength(nameBytes.length) + nameBytes.length;

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5RedirRecord.TYPE, data, pos);
        pos += VInt.write(redirType, data, pos);
        pos += VInt.write(flags, data, pos);
        pos += VInt.write(nameBytes.length, data, pos);
        System.arraycopy(nameBytes, 0, data, pos, nameBytes.length);
        return data;
    }

    private static byte[] buildUnixOwnerRecord(final long flags, final String userName,
                                                final String groupName, final long uid, final long gid) {
        final byte[] userBytes = userName != null ? userName.getBytes(StandardCharsets.UTF_8) : new byte[0];
        final byte[] groupBytes = groupName != null ? groupName.getBytes(StandardCharsets.UTF_8) : new byte[0];

        int dataSize = VInt.encodedLength(Rar5UnixOwnerRecord.TYPE) + VInt.encodedLength(flags);
        if (userName != null) dataSize += VInt.encodedLength(userBytes.length) + userBytes.length;
        if (groupName != null) dataSize += VInt.encodedLength(groupBytes.length) + groupBytes.length;
        if ((flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMUID) != 0) dataSize += VInt.encodedLength(uid);
        if ((flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMGID) != 0) dataSize += VInt.encodedLength(gid);

        final byte[] data = new byte[VInt.encodedLength(dataSize) + dataSize];
        int pos = 0;
        pos += VInt.write(dataSize, data, pos);
        pos += VInt.write(Rar5UnixOwnerRecord.TYPE, data, pos);
        pos += VInt.write(flags, data, pos);
        if (userName != null) {
            pos += VInt.write(userBytes.length, data, pos);
            System.arraycopy(userBytes, 0, data, pos, userBytes.length);
            pos += userBytes.length;
        }
        if (groupName != null) {
            pos += VInt.write(groupBytes.length, data, pos);
            System.arraycopy(groupBytes, 0, data, pos, groupBytes.length);
            pos += groupBytes.length;
        }
        if ((flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMUID) != 0) {
            pos += VInt.write(uid, data, pos);
        }
        if ((flags & Rar5UnixOwnerRecord.FHEXTRA_UOWNER_NUMGID) != 0) {
            pos += VInt.write(gid, data, pos);
        }
        return data;
    }
}
