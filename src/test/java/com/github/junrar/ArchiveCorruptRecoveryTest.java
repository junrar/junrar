package com.github.junrar;

import static com.github.junrar.rarfile.UnrarHeadertype.ProtectHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.rarfile.FileHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tolerating a broken header is only worth anything if the entries around it still come out. These
 * tests take a real archive ({@code rar4.rar}: FILE1.TXT and FILE2.TXT, stored), splice a header
 * the parser cannot use in between, and require both files to list <em>and extract byte for byte</em>
 * anyway -- the recovery unrar performs and the reason the header loop stopped throwing.
 *
 * <p>They also pin the two things that recovery must not cost: a size the wire can express but a
 * {@code long} cannot must not reach a caller as a negative number, and an archive whose
 * enumeration ended early must say so.
 */
class ArchiveCorruptRecoveryTest {

    private static final String FIXTURE = "/com/github/junrar/rar4.rar";

    /** Offset of the second file header in the fixture, just past FILE1.TXT's packed data. */
    private static final int SPLICE_AT = 68;

    /** Offset of the fixture's end-of-archive header. */
    private static final int END_ARC_AT = 116;

    private static final byte[] MARKER = {0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00};

    /** A block of a type junrar does not know, filling the seven bytes it declares. */
    private static final byte[] UNKNOWN_BLOCK = {0x00, 0x00, 0x6f, 0x00, (byte) 0x80, 0x07, 0x00};

    private static final int LONG_BLOCK = 0x8000;
    private static final int LHD_LARGE = 0x0100;

    @TempDir Path tempDir;

    /** Control: the untouched fixture, so the expectations below are the parser's and not the fixture's. */
    @Test
    void theFixtureListsAndExtractsBothEntries() throws Exception {
        assertThat(listAndExtract(fixture()))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    @Test
    void entriesAfterAnUnparseableHeaderStillListAndExtract() throws Exception {
        byte[] rar = splice(fixture(), unparseableBlock());

        assertThat(listAndExtract(rar))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    /**
     * A pack size of 2^63 is expressible on the wire and not in a {@code long}. unrar refuses such
     * an archive outright ({@code SafeAdd} returns 0 for a negative size, forcing its no-forward-
     * progress stop), so no caller of unrar ever sees the value -- and none of ours should see it
     * as the negative number the same bits read as here.
     */
    @Test
    void aPackSizeTooLargeForALongNeverReachesTheCallerAsANegativeNumber() throws Exception {
        byte[] rar = splice(fixture(), oversizedPackSizeBlock());

        try (Archive archive = open(rar)) {
            assertThat(archive.getFileHeaders())
                    .allSatisfy(h -> assertThat(h.getFullPackSize()).isNotNegative());
        }
        assertThat(listAndExtract(rar))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    /**
     * Enumeration that ends early because a block does not move forward keeps what it read, but
     * the caller is then holding a short list with nothing to distinguish it from a complete one.
     */
    @Test
    void aBlockThatCannotAdvanceKeepsEverythingReadBeforeIt() throws Exception {
        byte[] rar = splice(fixture(), zeroSizedBlock());

        assertThat(listAndExtract(rar)).containsExactly(entry("FILE1.TXT", "file1\r\n"));
    }

    /**
     * The stop itself has to be visible, not just the headers that caused one. Here the blocks all
     * parse cleanly and nothing is marked broken -- the end-of-archive header is the only thing
     * wrong -- so the only way a caller can tell this list is short is if the stop is recorded.
     */
    @Test
    void anArchiveWhoseEnumerationEndedEarlyReportsThat() throws Exception {
        byte[] rar = fixture();
        rar[END_ARC_AT] ^= (byte) 0xFF; // the end-of-archive header's own CRC field

        try (Archive archive = open(rar)) {
            assertThat(archive.getFileHeaders()).hasSize(2);
            assertThat(archive.getHeaders()).noneMatch(b -> b.isBrokenHeader());
            assertThat(archive.hasBrokenHeaders()).isTrue();
        }
    }

    /**
     * Skipping a header that cannot be parsed is one event, not two: it does not become a
     * different kind of failure because it happened to every header rather than to one. An
     * archive with nothing left therefore opens and lists nothing, and the empty list is that
     * event's consequence rather than a case of its own -- reported the same way a single
     * skipped header is, through {@link Archive#hasBrokenHeaders()} and the entry each skipped
     * header leaves in {@link Archive#getHeaderFailures()}.
     */
    @Test
    void anArchiveWithNothingToRecoverOpensAndSaysSo() throws Exception {
        byte[] rar = resource("/com/github/junrar/abnormal/corrupt-header.rar");

        Throwable thrown = catchThrowable(() -> open(rar).close());
        assertThat(thrown).isNull();

        try (Archive archive = open(rar)) {
            assertThat(archive.getFileHeaders()).isEmpty();
            assertThat(archive.hasBrokenHeaders()).isTrue();
        }
    }

    /**
     * The skipping above is for file headers. The main header is not one of those: it carries the
     * archive-level record -- volume, solid, locked, encrypted -- and every RAR format carries
     * one, so an archive that yields none has nothing to be an archive with. There is not even
     * enough to say whether the headers behind it are encrypted, which is the question every
     * later read depends on. That is a bad archive, not a skipped header.
     */
    @Test
    void aDamagedArchiveThatNeverYieldsAMainHeaderSaysSo() throws Exception {
        byte[] rar = new byte[MARKER.length + UNKNOWN_BLOCK.length];
        System.arraycopy(MARKER, 0, rar, 0, MARKER.length);
        System.arraycopy(UNKNOWN_BLOCK, 0, rar, MARKER.length, UNKNOWN_BLOCK.length);

        Throwable thrown = catchThrowable(() -> open(rar).close());

        assertThat(thrown).isExactlyInstanceOf(CorruptHeaderException.class);
        assertThat(thrown).hasMessageContaining("main");
    }

    /**
     * A header can be broken for two unrelated reasons -- its CRC did not match, or it announced
     * a field its own declared size did not hold -- and extraction refuses both. Only the first
     * is a checksum, so the refusal must not report one for an entry whose CRC was fine. Here the
     * archive ends inside the last name, which clamps it; the CRC is never even reached.
     */
    @Test
    void refusingAStructurallyBrokenEntryDoesNotBlameItsChecksum() throws Exception {
        // FILE2.TXT's nine name bytes are the tail of its header, at offsets 100..108.
        byte[] truncated = java.util.Arrays.copyOf(fixture(), 105);

        try (Archive archive = open(truncated)) {
            FileHeader broken =
                    archive.getFileHeaders().stream()
                            .filter(FileHeader::isBrokenHeader)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("no broken entry in fixture"));

            Throwable thrown =
                    catchThrowable(() -> archive.extractFile(broken, new ByteArrayOutputStream()));

            assertThat(thrown).isInstanceOf(CorruptHeaderException.class);
            assertThat(thrown).hasMessageNotContaining("CRC");
        }
    }

    /**
     * Whatever becomes of a recovery-record header -- listed, listed and flagged, or too short to
     * parse and skipped -- it must stay skippable: the entries on either side still list and
     * still extract.
     */
    @Test
    void aCorruptProtectHeaderIsSkippedWithoutCostingTheEntriesAroundIt() throws Exception {
        byte[] rar = splice(fixture(), shortProtectBlock());

        assertThat(listAndExtract(rar))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    /**
     * Every RAR format carries a main archive header, and this reader answers "are the headers
     * encrypted?" from it -- so a file entry appearing before one is malformed however well
     * formed the entry itself is, and everything read past it would be read on a guess. The
     * archive must say so rather than list the entry.
     */
    @Test
    void aFileHeaderBeforeAnyMainHeaderIsNotAnArchive() throws Exception {
        byte[] good = plainFileHeader("early.txt");
        byte[] rar = new byte[MARKER.length + good.length];
        System.arraycopy(MARKER, 0, rar, 0, MARKER.length);
        System.arraycopy(good, 0, rar, MARKER.length, good.length);

        Throwable thrown = catchThrowable(() -> open(rar).close());

        assertThat(thrown).isExactlyInstanceOf(CorruptHeaderException.class);
        assertThat(thrown).hasMessageContaining("main");
    }

    /**
     * A block declaring a size of zero, with a zero pack size behind it, computes a next position
     * equal to the one it is already at. The guard refuses that outright, so the block is recorded
     * once and the read ends. Relax it to {@code newpos < position} and the same block is read a
     * second time before the already-seen check catches the repeat -- one extra recorded failure
     * for the same bytes, which is what this counts.
     */
    @Test
    void aBlockThatWouldNotMoveForwardIsRefusedRatherThanReRead() throws Exception {
        // Placed straight after the main header, which never registers a next position of its
        // own -- so this block sits at a position the already-seen check has not recorded, and
        // the forward-progress half of the guard is the only thing standing in the way. Spliced
        // into a normal archive instead, the previous block's advance would have registered this
        // position already and the already-seen half would mask it.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(plainMainHeader());
        out.write(nonAdvancingBlock());

        try (Archive archive = open(out.toByteArray())) {
            assertThat(archive.getHeaderFailures()).hasSize(2);
            assertThat(archive.getHeaderFailures()).filteredOn(f -> !f.isTerminal()).hasSize(1);
        }
    }

    /**
     * A header can be found wrong without being skipped: a bad CRC, or a name or subheader field
     * clamped to the bytes that were there, all leave the entry listed and flagged. Those are not
     * in the failure list, which records headers that could not be used at all -- so the archive
     * still has to answer yes when asked whether anything is broken, or the one call the breaking
     * change tells callers to use reports a corrupt archive as clean.
     */
    @Test
    void anEntryFlaggedButStillListedStillMakesTheArchiveReportBrokenHeaders() throws Exception {
        // FILE2.TXT's nine name bytes are the tail of its header, at offsets 100..108.
        byte[] truncated = java.util.Arrays.copyOf(fixture(), 105);

        try (Archive archive = open(truncated)) {
            assertThat(archive.getFileHeaders()).anyMatch(FileHeader::isBrokenHeader);
            assertThat(archive.hasBrokenHeaders()).isTrue();
        }
    }

    /**
     * The record of the stop is the one entry a caller cannot do without -- it is what separates
     * "lost one entry" from "lost every entry after this point". Capping the list must not be
     * able to drop it, however many skipped headers came first.
     */
    @Test
    void theStopIsRecordedEvenBehindMoreSkippedHeadersThanTheCapHolds() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(plainMainHeader());
        for (int i = 0; i < 150; i++) {
            out.write(UNKNOWN_BLOCK);
        }
        out.write(nonAdvancingBlock());

        try (Archive archive = open(out.toByteArray())) {
            assertThat(archive.getHeaderFailures()).filteredOn(BrokenHeader::isTerminal).hasSize(1);
            // ...and the 150 skips ahead of it are still bounded by the cap, which is the whole
            // reason a hostile archive cannot size this list.
            assertThat(archive.getHeaderFailures()).hasSize(101);
        }
    }

    /**
     * Every block type that advances by its own declared size has to ask whether headers are
     * encrypted, and asking the archive that before a main header has been read used to throw a
     * checked exception of a type nothing rethrows -- so it was discarded and the archive opened
     * reporting nothing. Each type here is placed before any main header, and pins the advance
     * of the arm that handles it: comment and protect. The file-header arms are pinned by
     * {@link #aFileHeaderBeforeAnyMainHeaderIsNotAnArchive} and
     * {@link #aSkippedFileHeaderBeforeTheMainHeaderAlsoFailsAsAnArchive}.
     *
     * <p>Sub-blocks (0x77) are deliberately absent. A generic body leaves the sub-type reading as
     * zero, which is not a type this reader knows, so the arm breaks out before its advance is
     * ever reached -- such a case would pass while pinning nothing.
     */
    @ParameterizedTest
    @ValueSource(bytes = {0x75, 0x78})
    void anyBlockTypeBeforeTheMainHeaderFailsAsAnArchive(byte type) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(blockOfType(type));

        Throwable thrown = catchThrowable(() -> open(out.toByteArray()).close());

        assertThat(thrown)
                .as("block type 0x%02x before any main header", type)
                .isExactlyInstanceOf(CorruptHeaderException.class);
        assertThat(thrown).hasMessageContaining("main");
    }

    /**
     * The same question on one more path: a FILE header that fails to parse advances through the
     * skip path rather than the normal one, which asks separately.
     *
     * <p>The sub-block advance is the one call site left without a fixture. Reaching it needs a
     * sub-block whose sub-type is recognised <em>and</em> whose body is complete enough to parse,
     * since a short one dies earlier on its own read; that is more fixture than the risk warrants
     * for a two-line shared predicate whose other four call sites are pinned here.
     */
    @Test
    void aSkippedFileHeaderBeforeTheMainHeaderAlsoFailsAsAnArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MARKER);
        out.write(unparseableBlock());

        Throwable thrown = catchThrowable(() -> open(out.toByteArray()).close());

        assertThat(thrown).isExactlyInstanceOf(CorruptHeaderException.class);
        assertThat(thrown).hasMessageContaining("main");
    }

    /**
     * A recovery-record header is a header like any other: read, not consumed for extraction,
     * and listed -- the same treatment SIGN, AV, MAC, EA and the Unix owners sub-block get. A
     * broken one is then marked in place, through the ordinary channel.
     *
     * <p>One that is too short to hold its fixed layout is a different case, and gets the same
     * answer every other unparseable header does: it is skipped, not listed. Listing it would
     * mean publishing an eight-byte mark and a version the wire never carried, which is the one
     * thing this change refuses to do.
     */
    @Test
    void aRecoveryRecordHeaderIsListedWhenItParsesAndSkippedWhenItCannot() throws Exception {
        try (Archive archive = open(splice(fixture(), protectBlock()))) {
            assertThat(archive.getHeaders())
                    .filteredOn(b -> b.getHeaderType() == ProtectHeader)
                    .singleElement()
                    .satisfies(b -> assertThat(b.isBrokenHeader()).isFalse());
            assertThat(archive.hasBrokenHeaders()).isFalse();
        }

        try (Archive archive = open(splice(fixture(), shortProtectBlock()))) {
            assertThat(archive.getHeaders()).noneMatch(b -> b.getHeaderType() == ProtectHeader);
            assertThat(archive.getHeaderFailures())
                    .filteredOn(f -> f.getHeaderType() == ProtectHeader)
                    .hasSize(1);
            assertThat(archive.hasBrokenHeaders()).isTrue();
        }
    }

    /**
     * The skip must clear the recovery record's payload as well as its header. Row C7 of the
     * no-go list (8e91d695, 2011) is exactly this seek -- "the {@code + dataSize} is the fix" --
     * and the pin for it only ever covered the path where the header parses. A block whose
     * header cannot be parsed still declares its payload length in the block header ahead of it,
     * so the skip has the same number available and must use it; landing inside the payload
     * makes the parser read recovery data as blocks, and a run of zeroes there declares a
     * zero-sized block, which ends enumeration and costs every entry after it.
     */
    @Test
    void skippingAnUnparseableRecoveryRecordClearsItsPayloadToo() throws Exception {
        byte[] rar = splice(fixture(), shortProtectBlockWithPayload());

        assertThat(listAndExtract(rar))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    /**
     * A body cut short by the end of the file must reach the same rejection a body cut short by
     * its own declared size does. A stream-backed read pads a short read with zeroes rather than
     * failing, so without a bound the header parses from bytes the file never held -- and when
     * the missing bytes were zeroes anyway, even its CRC still matches, leaving the archive
     * claiming to be intact. The file-backed read fails differently on the same bytes, so the
     * two must be brought together as well.
     */
    @Test
    void aRecoveryRecordCutShortByTheEndOfTheFileIsNotParsedFromPadding() throws Exception {
        byte[] complete = splice(fixture(), protectBlock());
        // land five bytes inside the recovery-record header's body
        byte[] truncated = java.util.Arrays.copyOf(complete, 68 + 26 - 5);

        try (Archive archive = new Archive(new java.io.ByteArrayInputStream(truncated))) {
            assertThat(archive.getHeaders()).noneMatch(b -> b.getHeaderType() == ProtectHeader);
            assertThat(archive.hasBrokenHeaders()).isTrue();
        }
    }

    /**
     * The same seek-past-data rule as the recovery record, on the file-header arm. A file header
     * that cannot be parsed still declares its packed data in the block header ahead of it, so
     * the skip must clear that too; landing inside packed data has the parser read it as blocks,
     * and a run of zeroes there declares a zero-sized block, which ends enumeration and costs
     * every entry after it. Every other fixture that reaches this arm carries no packed data, so
     * the term is indistinguishable from zero and nothing would notice it going missing.
     */
    @Test
    void skippingAnUnparseableFileHeaderClearsItsPackedDataToo() throws Exception {
        byte[] rar = splice(fixture(), unparseableBlockWithPackedData());

        assertThat(listAndExtract(rar))
                .containsExactly(entry("FILE1.TXT", "file1\r\n"), entry("FILE2.TXT", "file2\r\n"));
    }

    private static Map.Entry<String, String> entry(String name, String content) {
        return new java.util.AbstractMap.SimpleEntry<>(name, content);
    }

    /** Lists every entry and extracts it, so a name that lists but cannot be read still fails. */
    private Map<String, String> listAndExtract(byte[] rar) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (Archive archive = open(rar)) {
            for (FileHeader header : archive.getFileHeaders()) {
                ByteArrayOutputStream sink = new ByteArrayOutputStream();
                archive.extractFile(header, sink);
                out.put(
                        header.getFileName(),
                        new String(sink.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private Archive open(byte[] rar) throws Exception {
        Path file = Files.createTempFile(tempDir, "recovery", ".rar");
        Files.write(file, rar);
        return new Archive(file.toFile());
    }

    private byte[] fixture() throws Exception {
        return resource(FIXTURE);
    }

    private byte[] resource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("resource %s", path).isNotNull();
            return org.apache.commons.io.IOUtils.toByteArray(in);
        }
    }

    private static byte[] splice(byte[] rar, byte[] block) {
        byte[] out = new byte[rar.length + block.length];
        System.arraycopy(rar, 0, out, 0, SPLICE_AT);
        System.arraycopy(block, 0, out, SPLICE_AT, block.length);
        System.arraycopy(rar, SPLICE_AT, out, SPLICE_AT + block.length, rar.length - SPLICE_AT);
        return out;
    }

    /** A minimal, self-consistent block of the given RAR3 header type. */
    private static byte[] blockOfType(byte type) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // the block header's pack size
        writeFixedFields(body, 1);
        body.write('a');
        byte[] b = body.toByteArray();
        int headerSize = 7 + b.length;
        byte[] header = new byte[headerSize];
        header[2] = type;
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        header[5] = (byte) (headerSize & 0xff);
        header[6] = (byte) ((headerSize >>> 8) & 0xff);
        System.arraycopy(b, 0, header, 7, b.length);
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    /** A main archive header with no flags set. */
    private static byte[] plainMainHeader() throws IOException {
        byte[] header = new byte[13];
        header[2] = 0x73; // MainHeader
        header[5] = 13;
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    /**
     * A FILE block declaring a header size of zero, followed by four zero bytes so the block
     * header's pack size reads as zero too: next position equals current position exactly.
     */
    private static byte[] nonAdvancingBlock() {
        byte[] header = new byte[11];
        header[2] = 0x74; // FILE
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        // header[5], header[6] stay zero: the declared header size
        // header[7..10] stay zero: the block header's pack size
        return header;
    }

    /**
     * A block declaring a size of zero: there is no size to advance by, so enumeration can only
     * stop here. unrar's equivalent is {@code HeadSize < SIZEOF_SHORTBLOCKHEAD}, its one RAR3 hard
     * stop. Everything the loop read before this point is still good and must be kept.
     */
    private static byte[] zeroSizedBlock() {
        byte[] header = new byte[7];
        header[2] = 0x74; // FILE
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        // header[5], header[6] stay zero: the declared header size
        return header;
    }

    /** A well-formed, dataless FILE header for {@code name}. */
    private static byte[] plainFileHeader(String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize
        writeFixedFields(body, nameBytes.length);
        body.write(nameBytes);
        return block(LONG_BLOCK, body.toByteArray());
    }

    /**
     * A FILE block that fills every byte its declared size claims, but announces a name of length
     * zero -- so it frames correctly and parses to nothing.
     */
    /**
     * An unparseable FILE header that declares sixteen bytes of packed data and carries them, so
     * the header size alone is not enough to get past it. The payload is zeroes, which is what
     * makes a short seek fatal rather than merely noisy.
     */
    private static byte[] unparseableBlockWithPackedData() throws IOException {
        int packed = 16;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[] {(byte) packed, 0, 0, 0}); // packSize: data follows this header
        writeFixedFields(body, 0); // a name of length zero: nothing to parse
        byte[] header = block(LONG_BLOCK, body.toByteArray());

        byte[] out = new byte[header.length + packed];
        System.arraycopy(header, 0, out, 0, header.length);
        return out;
    }

    private static byte[] unparseableBlock() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize: no data follows this block
        writeFixedFields(body, 0);
        body.write(new byte[9]); // filler occupying the rest of the declared size
        return block(LONG_BLOCK, body.toByteArray());
    }

    /** A FILE block whose high pack size sets the bit that a signed 64-bit size cannot carry. */
    private static byte[] oversizedPackSizeBlock() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // low packSize: no data follows this block
        writeFixedFields(body, 1);
        body.write(new byte[] {0, 0, 0, (byte) 0x80}); // highPackSize = 0x80000000
        body.write(new byte[4]); // highUnpackSize
        body.write('X');
        return block(LONG_BLOCK | LHD_LARGE, body.toByteArray());
    }

    /** A PROTECT block holding its whole fixed layout, with no recovery data behind it. */
    private static byte[] protectBlock() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // dataSize: no recovery data follows
        body.write(1); // version
        body.write(new byte[2]); // recSectors
        body.write(new byte[4]); // totalBlocks
        body.write(new byte[8]); // mark
        byte[] b = body.toByteArray();
        int headerSize = 7 + b.length;
        byte[] header = new byte[headerSize];
        header[2] = 0x78; // PROTECT
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        header[5] = (byte) (headerSize & 0xff);
        header[6] = (byte) ((headerSize >>> 8) & 0xff);
        System.arraycopy(b, 0, header, 7, b.length);
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    /**
     * The same unparseable PROTECT block, but declaring sixteen bytes of recovery data and
     * carrying them -- so the header size alone is not enough to get past it.
     */
    private static byte[] shortProtectBlockWithPayload() throws IOException {
        int payload = 16;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[] {(byte) payload, 0, 0, 0}); // dataSize: recovery data follows
        body.write(new byte[6]); // six bytes where the fixed layout needs fifteen
        byte[] b = body.toByteArray();
        int headerSize = 7 + b.length;
        byte[] header = new byte[headerSize];
        header[2] = 0x78; // PROTECT
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        header[5] = (byte) (headerSize & 0xff);
        header[6] = (byte) ((headerSize >>> 8) & 0xff);
        System.arraycopy(b, 0, header, 7, b.length);
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);

        byte[] out = new byte[headerSize + payload];
        System.arraycopy(header, 0, out, 0, headerSize);
        return out; // payload left as zeroes, which is what makes a bad seek fatal
    }

    /** A PROTECT block whose declared size cannot hold its fixed fifteen-byte layout. */
    private static byte[] shortProtectBlock() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[4]); // packSize: no recovery data follows
        body.write(new byte[6]); // six bytes where the layout needs fifteen
        byte[] b = body.toByteArray();
        int headerSize = 7 + b.length;
        byte[] header = new byte[headerSize];
        header[2] = 0x78; // PROTECT
        header[3] = (byte) (LONG_BLOCK & 0xff);
        header[4] = (byte) ((LONG_BLOCK >>> 8) & 0xff);
        header[5] = (byte) (headerSize & 0xff);
        header[6] = (byte) ((headerSize >>> 8) & 0xff);
        System.arraycopy(b, 0, header, 7, b.length);
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    private static byte[] block(int flags, byte[] body) throws IOException {
        int headerSize = 7 + body.length;
        byte[] header = new byte[headerSize];
        header[2] = 0x74; // FILE
        header[3] = (byte) (flags & 0xff);
        header[4] = (byte) ((flags >>> 8) & 0xff);
        header[5] = (byte) (headerSize & 0xff);
        header[6] = (byte) ((headerSize >>> 8) & 0xff);
        System.arraycopy(body, 0, header, 7, body.length);
        short crc = RarCRC.computeHeaderCrc16(header, 2, header.length - 2);
        header[0] = (byte) (crc & 0xff);
        header[1] = (byte) ((crc >>> 8) & 0xff);
        return header;
    }

    private static void writeFixedFields(ByteArrayOutputStream out, int declaredNameSize) {
        out.write(new byte[4], 0, 4); // unpSize
        out.write(0); // hostOS
        out.write(new byte[4], 0, 4); // fileCRC
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0x4A); // fileTime
        out.write(20); // unpVersion
        out.write(0x30); // unpMethod, store
        out.write(declaredNameSize & 0xff);
        out.write((declaredNameSize >>> 8) & 0xff);
        out.write(new byte[4], 0, 4); // fileAttr
    }
}
