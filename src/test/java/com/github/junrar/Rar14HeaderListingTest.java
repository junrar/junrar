package com.github.junrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1 (issue #293) RAR 1.4 header/listing coverage matrix, rows 2-8 (row 1, the empty-archive
 * fixture, lives in {@link Rar14UnsupportedTest} -- the replaced complementary half of the
 * RED/GREEN proof). Every fixture here is hand-built byte-by-byte from the field layout the
 * P1 brief pins from unrar {@code Archive::ReadHeader14} ({@code
 * d861246:arcread.cpp:1256-1331}) -- no RAR executable or upstream source involved.
 *
 * <p>Row 5's fifth hostile fact ("a crafted NextBlockPos that does not advance") has no
 * dedicated test here: given the {@code HeadSize} floor checks this suite exercises directly
 * ({@link #fileHeadSizeBelow21ThrowsCorruptHeaderException()}, {@link
 * #mainHeadSizeBelow7ThrowsCorruptHeaderException()}), RAR 1.4's fixed-width unsigned
 * {@code HeadSize}/{@code PackSize} fields cannot produce a non-advancing {@code NextBlockPos}
 * -- unlike RAR5's vint {@code DataSize}, which can encode a negative-as-unsigned value past
 * the same kind of floor (see {@code ArchiveRar5FrameworkTest
 * #negativeDataSizeVintRejectsInsteadOfSeekingBackward}). The defensive check is implemented
 * in {@code Archive.readHeaders14} anyway (dead code by construction, matching the RAR50
 * precedent), but no legitimate 16/32-bit field combination can reach it without first
 * failing one of the two floor checks already covered.
 */
class Rar14HeaderListingTest {

    @TempDir Path tempDir;

    private static final byte[] MARKER14 = {0x52, 0x45, 0x7e, 0x5e};

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

    /** unrar DOS-time encode, the inverse of {@code FileHeader.getDateDos}: month is 1-based. */
    private static int dosTime(int year, int month, int day, int hour, int minute, int second) {
        return ((year - 1980) << 25)
                | (month << 21)
                | (day << 16)
                | (hour << 11)
                | (minute << 5)
                | (second / 2);
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
     * The SIZEOF_FILEHEAD14 (21-byte) fixed block plus the OEM name bytes.
     * {@code headSizeOverride}/{@code nameSizeOverride} default to the well-formed values
     * ({@code 21 + nameBytes.length} / {@code nameBytes.length}) when {@code null}, so hostile
     * tests only need to override the one field under test.
     */
    private static byte[] fileHeader14(
            int dataSize,
            int unpSize,
            int crc16,
            Integer headSizeOverride,
            int dosTimeValue,
            int fileAttr,
            int flags,
            int unpVerByte,
            Integer nameSizeOverride,
            int method,
            byte[] nameBytes) {
        final int headSize = headSizeOverride != null ? headSizeOverride : 21 + nameBytes.length;
        final int nameSizeField = nameSizeOverride != null ? nameSizeOverride : nameBytes.length;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        u32(out, dataSize);
        u32(out, unpSize);
        u16(out, crc16);
        u16(out, headSize);
        u32(out, dosTimeValue);
        out.write(fileAttr);
        out.write(flags);
        out.write(unpVerByte);
        out.write(nameSizeField);
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

    private static void assertDosTime(
            java.nio.file.attribute.FileTime time,
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second) {
        assertThat(time).isNotNull();
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time.toMillis());
        assertThat(cal.get(Calendar.YEAR)).isEqualTo(year);
        assertThat(cal.get(Calendar.MONTH)).isEqualTo(month - 1);
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(hour);
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(minute);
        assertThat(cal.get(Calendar.SECOND)).isEqualTo(second);
    }

    /** Coverage row 2: full field assertions across two stored entries, unpVerByte 1 and 2. */
    @Test
    void twoStoredEntriesListWithFullHeaderFields() throws Exception {
        final byte[] name1 = "STORED.TXT".getBytes(StandardCharsets.US_ASCII);
        final int time1 = dosTime(2024, 1, 2, 3, 4, 6);
        final byte[] entry1 =
                concat(
                        fileHeader14(5, 5, 0x1234, null, time1, 0x20, 0, 1, null, 0, name1),
                        "Hello".getBytes(StandardCharsets.US_ASCII));

        final byte[] name2 = "NEWVER.TXT".getBytes(StandardCharsets.US_ASCII);
        final int time2 = dosTime(2020, 6, 15, 12, 30, 44);
        final byte[] entry2 =
                concat(
                        fileHeader14(3, 3, 0x5678, null, time2, 0x20, 0, 2, null, 0, name2),
                        "Hi!".getBytes(StandardCharsets.US_ASCII));

        final byte[] bytes = concat(mainHeader14(7, 0), entry1, entry2);
        try (Archive archive = new Archive(writeTemp("rar14-two.rar", bytes))) {
            assertThat(archive.getFormat()).isEqualTo(RarFormat.RAR14);
            assertThat(archive.isOldFormat()).isTrue();

            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(2);

            final FileHeader f1 = files.get(0);
            assertThat(f1.getFileName()).isEqualTo("STORED.TXT");
            assertThat(f1.getFullPackSize()).isEqualTo(5L);
            assertThat(f1.getFullUnpackSize()).isEqualTo(5L);
            assertThat(f1.getFileCRC()).isEqualTo(0x1234);
            assertThat(f1.getUnpMethod()).isEqualTo((byte) 0x30);
            assertThat(f1.getUnpVersion()).isEqualTo((byte) 10);
            assertThat(f1.isDirectory()).isFalse();
            assertThat(f1.isEncrypted()).isFalse();
            assertDosTime(f1.getLastModifiedTime(), 2024, 1, 2, 3, 4, 6);

            final FileHeader f2 = files.get(1);
            assertThat(f2.getFileName()).isEqualTo("NEWVER.TXT");
            assertThat(f2.getFullPackSize()).isEqualTo(3L);
            assertThat(f2.getFullUnpackSize()).isEqualTo(3L);
            assertThat(f2.getFileCRC()).isEqualTo(0x5678);
            assertThat(f2.getUnpVersion()).isEqualTo((byte) 13);
            assertDosTime(f2.getLastModifiedTime(), 2020, 6, 15, 12, 30, 44);
        }
    }

    /** Coverage row 3: FileAttr bit 0x10 (not a flags/window-mask bit) marks a directory. */
    @Test
    void directoryEntryAttrBit0x10SetsIsDirectory() throws Exception {
        final byte[] name = "SUBDIR".getBytes(StandardCharsets.US_ASCII);
        final byte[] entry =
                fileHeader14(
                        0, 0, 0, null, dosTime(2023, 5, 1, 0, 0, 0), 0x10, 0, 1, null, 0, name);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("rar14-dir.rar", bytes))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).isDirectory()).isTrue();
        }
    }

    /** Coverage row 4: a high OEM byte (CP437 0x9A = U+00DC 'Ü') decodes via IBM437. */
    @Test
    void highOemByteNameDecodesViaCp437() throws Exception {
        final byte[] name = {'F', 'I', 'L', 'E', (byte) 0x9A, '.', 'T', 'X', 'T'};
        final byte[] entry =
                fileHeader14(
                        0, 0, 0, null, dosTime(2023, 1, 1, 0, 0, 0), 0x20, 0, 1, null, 0, name);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("rar14-oem.rar", bytes))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).getFileName()).isEqualTo("FILEÜ.TXT");
        }
    }

    /** Coverage row 5a: a file HeadSize below SIZEOF_FILEHEAD14 (21) is a broken header. */
    @Test
    void fileHeadSizeBelow21ThrowsCorruptHeaderException() throws Exception {
        final byte[] name = "A.TXT".getBytes(StandardCharsets.US_ASCII);
        final byte[] entry =
                fileHeader14(
                        0,
                        0,
                        0,
                        20,
                        dosTime(2023, 1, 1, 0, 0, 0),
                        0x20,
                        0,
                        1,
                        name.length,
                        0,
                        name);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        assertThat(
                        catchThrowable(
                                () ->
                                        new Archive(writeTemp("rar14-badheadsize.rar", bytes))
                                                .close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    /** Coverage row 5b: a main HeadSize below SIZEOF_MAINHEAD14 (7) is a broken header. */
    @Test
    void mainHeadSizeBelow7ThrowsCorruptHeaderException() throws Exception {
        // Physically 7 bytes on disk (marker + a HeadSize field + a Flags byte); the HeadSize
        // *value* itself is what's invalid.
        final byte[] bytes = mainHeader14(6, 0);
        assertThat(
                        catchThrowable(
                                () -> new Archive(writeTemp("rar14-mainsmall.rar", bytes)).close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    /** Coverage row 5c: a declared HeadSize that does not cover the declared NameSize. */
    @Test
    void nameSizeRunningPastHeadSizeThrowsCorruptHeaderException() throws Exception {
        final byte[] name = "TOOLONGNAME.TXT".getBytes(StandardCharsets.US_ASCII); // 15 bytes
        // HeadSize declares only the bare 21-byte fixed block -- no room for the 15-byte name
        // NameSize itself claims.
        final byte[] entry =
                fileHeader14(
                        0,
                        0,
                        0,
                        21,
                        dosTime(2023, 1, 1, 0, 0, 0),
                        0x20,
                        0,
                        1,
                        name.length,
                        0,
                        name);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        assertThat(
                        catchThrowable(
                                () ->
                                        new Archive(writeTemp("rar14-namebeyond.rar", bytes))
                                                .close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    /** Coverage row 5d: EOF mid the 21-byte fixed file-header block. */
    @Test
    void truncatedFileHeaderThrowsCorruptHeaderException() throws Exception {
        final byte[] main = mainHeader14(7, 0);
        final byte[] partial = {0x05, 0x00, 0x00, 0x00, 0x05}; // 5 of the 21 required bytes
        final byte[] bytes = concat(main, partial);
        assertThat(catchThrowable(() -> new Archive(writeTemp("rar14-trunc.rar", bytes)).close()))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    /** Coverage row 6: LHD_PASSWORD marks the entry AND the archive as password-protected. */
    @Test
    void passwordFlaggedEntryMarksEncryptedAndPasswordProtected() throws Exception {
        final byte[] name = "SECRET.TXT".getBytes(StandardCharsets.US_ASCII);
        final byte[] entry =
                concat(
                        fileHeader14(
                                4,
                                4,
                                0,
                                null,
                                dosTime(2023, 1, 1, 0, 0, 0),
                                0x20,
                                BaseBlock.LHD_PASSWORD,
                                1,
                                null,
                                0,
                                name),
                        "abcd".getBytes(StandardCharsets.US_ASCII));
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("rar14-pwd.rar", bytes))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).isEncrypted()).isTrue();
            assertThat(archive.isPasswordProtected()).isTrue();
        }
    }

    /** Coverage row 8: LHD_SPLIT_AFTER marks the entry split. */
    @Test
    void splitAfterFlagSetsIsSplitAfter() throws Exception {
        final byte[] name = "PART.TXT".getBytes(StandardCharsets.US_ASCII);
        final byte[] entry =
                fileHeader14(
                        0,
                        0,
                        0,
                        null,
                        dosTime(2023, 1, 1, 0, 0, 0),
                        0x20,
                        BaseBlock.LHD_SPLIT_AFTER,
                        1,
                        null,
                        0,
                        name);
        final byte[] bytes = concat(mainHeader14(7, 0), entry);
        try (Archive archive = new Archive(writeTemp("rar14-split.rar", bytes))) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).isSplitAfter()).isTrue();
        }
    }

    /**
     * Coverage row 7 (local-only, not committed -- P4 decides corpus placement per the brief):
     * the real RAR 1.40 distribution archive, cross-checked against a from-scratch Python
     * parse of the same field layout this reader implements (10 entries; first entry's fields
     * match the brief's probed values exactly).
     */
    @Test
    void realDistributionArchiveListsTenEntriesFirstAnyToRarDoc() throws Exception {
        final Path real = Paths.get("/private/tmp/rar140dc.rar");
        assumeTrue(
                Files.exists(real), "local-only oracle fixture /private/tmp/rar140dc.rar absent");

        try (Archive archive = new Archive(real.toFile())) {
            final List<FileHeader> files = archive.getFileHeaders();
            assertThat(files)
                    .extracting(FileHeader::getFileName)
                    .containsExactly(
                            "ANY2RAR.DOC",
                            "LICENSE.DOC",
                            "OPTIONS.DOC",
                            "RAR.DOC",
                            "RAR_BBS.DOC",
                            "README",
                            "README.A2R",
                            "TECHNOTE.DOC",
                            "TRANSLAT.DOC",
                            "WHATSNEW.DOC");

            final FileHeader first = files.get(0);
            assertThat(first.getFullPackSize()).isEqualTo(1261L);
            assertThat(first.getFullUnpackSize()).isEqualTo(2614L);
            assertThat(first.getFileCRC()).isEqualTo(0xe5bf);
            assertThat(first.getUnpVersion()).isEqualTo((byte) 13);
            assertThat(first.getUnpMethod()).isEqualTo((byte) 0x33);
            assertThat(first.isDirectory()).isFalse();
            assertThat(first.isEncrypted()).isFalse();
        }
    }
}
