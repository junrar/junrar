package com.github.junrar;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.rarfile.AVHeader;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.SignHeader;
import com.github.junrar.rarfile.UnixOwnersHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RAR3 header-CRC verification (P0.7, issue #12; docs/porting/PARITY_PLAN.md SS3, chunk
 * "P0.7 -- header-CRC verification scaffolding (T7)"; docs/porting/MIGRATION_MANUAL.md
 * SS6 T7). Covers the "record and continue" tolerance (a broken FILE header does not
 * abort archive open, it is only flagged -- see AbnormalFilesTest for the extract-time
 * refusal this flag drives) and the three upstream-sanctioned exemptions (SIGN, AV,
 * old-Unix-owner sub-blocks), which must parse cleanly with no broken-header flag at all
 * even though their stored CRC is deliberately wrong -- pin tests guarding against an
 * over-strict implementation, not corrupt-archive tests.
 */
public class HeaderCrcVerificationTest {

    @Test
    public void givenFileHeaderWithBadCrc_whenArchiveOpened_thenHeaderIsBrokenButArchiveOpensAndLists() throws Exception {
        File file = new File(getClass().getResource("abnormal/bad-header-crc.rar").toURI());

        try (Archive archive = new Archive(file)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);

            FileHeader fileHeader = fileHeaders.get(0);
            assertThat(fileHeader.getFileName()).isEqualTo("payload.txt");
            assertThat(fileHeader.isBrokenHeader()).isTrue();
        }
    }

    @Test
    public void givenLhdCommentFileHeaderWithNarrowCrcMatchButWideCrcMismatch_whenArchiveOpened_thenHeaderIsBrokenAndExtractionIsRefused() throws Exception {
        // Issue #38 item 2 (P0.7 Finding A): unrar runs TWO CRC checks on FILE/SERVICE
        // headers -- the CRCProcessedOnly-aware inline check (d861246:arcread.cpp
        // :430-445), which for an old-style LHD_COMMENT header covers only the parsed
        // fields (excludes the trailing embedded comment blob), AND an unconditional
        // generic check after the switch (:514-522) that always covers the FULL header
        // buffer and does NOT exempt FILE/SERVICE. This fixture is bad-header-crc.rar's
        // pristine FILE header (LHD_COMMENT flag set, a 40-byte comment blob appended,
        // HeadSize grown to match) with its stored headCRC set to the NARROW-coverage
        // value -- i.e. exactly what the first check alone accepts -- while the
        // full-buffer CRC (computed over the same bytes plus the appended blob)
        // deliberately differs. A single-check implementation calls this header clean;
        // the dual check must catch it.
        File file = new File(getClass().getResource("abnormal/lhd-comment-dual-crc.rar").toURI());

        try (Archive archive = new Archive(file)) {
            List<FileHeader> fileHeaders = archive.getFileHeaders();
            assertThat(fileHeaders).hasSize(1);

            FileHeader fileHeader = fileHeaders.get(0);
            assertThat(fileHeader.hasComment()).isTrue();
            assertThat(fileHeader.isBrokenHeader())
                .as("the narrow (processed-only) CRC matches; only the generic full-buffer "
                    + "check (not exempt for FILE/SERVICE) catches the corrupted comment tail")
                .isTrue();

            assertThatThrownBy(() -> archive.extractFile(fileHeader, new ByteArrayOutputStream()))
                .isInstanceOf(CorruptHeaderException.class);
        }
    }

    @Test
    public void givenSignHeaderWithBadCrc_whenArchiveOpened_thenParsesAndIsNotBroken() throws Exception {
        File file = new File(getClass().getResource("abnormal/sign-header-crc-mismatch.rar").toURI());

        try (Archive archive = new Archive(file)) {
            SignHeader signHeader = findHeader(archive, SignHeader.class);
            assertThat(signHeader.getHeadCRC()).isEqualTo((short) 0x0000);
            assertThat(signHeader.isBrokenHeader())
                .as("SIGN headers are exempt from CRC verification (upstream: no CLI warning ever fires for them)")
                .isFalse();
        }
    }

    @Test
    public void givenAvHeaderWithBadCrc_whenArchiveOpened_thenParsesAndIsNotBroken() throws Exception {
        File file = new File(getClass().getResource("abnormal/av-header-crc-mismatch.rar").toURI());

        try (Archive archive = new Archive(file)) {
            AVHeader avHeader = findHeader(archive, AVHeader.class);
            assertThat(avHeader.getHeadCRC()).isEqualTo((short) 0x0000);
            assertThat(avHeader.isBrokenHeader())
                .as("AV headers are exempt from CRC verification (upstream: \"Old AV header does not have header CRC properly set\")")
                .isFalse();
        }
    }

    @Test
    public void givenOldUnixOwnerSubBlockWithBadCrc_whenArchiveOpened_thenParsesAndIsNotBroken() throws Exception {
        File file = new File(getClass().getResource("abnormal/old-uo-header-crc-mismatch.rar").toURI());

        try (Archive archive = new Archive(file)) {
            UnixOwnersHeader uoHeader = findHeader(archive, UnixOwnersHeader.class);
            assertThat(uoHeader.getOwner()).isEqualTo("root");
            assertThat(uoHeader.getGroup()).isEqualTo("wheel");
            assertThat(uoHeader.getHeadCRC()).isEqualTo((short) 0x0000);
            assertThat(uoHeader.isBrokenHeader())
                .as("old Unix-owner (UO_HEAD) sub-blocks are exempt: upstream's own header-size accounting "
                    + "excludes their string fields, so the generic CRC approach can't verify them")
                .isFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BaseBlock> T findHeader(Archive archive, Class<T> type) {
        return (T) archive.getHeaders().stream()
            .filter(type::isInstance)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " found in archive headers"));
    }
}
