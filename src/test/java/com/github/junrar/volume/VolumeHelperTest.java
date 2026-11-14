package com.github.junrar.volume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * P4 (issue #293) old-numbering ({@code .rar}/{@code .r00}/{@code .r01}/...) next-volume-name
 * unit rows, ported against unrar's {@code NextVolumeName} ({@code d861246:pathfn.cpp
 * :441-495}). No {@link VolumeHelper} unit test existed before this class (grepped first, per
 * the P4 brief) -- the previous coverage was indirect, through full-archive extraction tests.
 *
 * <p>The {@code x.999 -> x.a00} row is the RED case: on the pre-fix code, incrementing the
 * 3-char extension array with no lower bound on the carry index throws {@code
 * ArrayIndexOutOfBoundsException} once the carry reaches the character right after the dot,
 * where unrar's own algorithm carries into the letter range instead ("From .999 to .a00 if
 * started from .001 or for too short names", same source lines). The other four rows already
 * pass before the fix -- the bug only triggers when the carry reaches position 0.
 */
class VolumeHelperTest {

    @ParameterizedTest
    @CsvSource({
        "x.rar, x.r00",
        "x.r00, x.r01",
        "x.r09, x.r10",
        "x.r99, x.s00",
        "x.999, x.a00",
    })
    void oldNumberingProducesExpectedNextName(final String current, final String expected) {
        assertThat(VolumeHelper.nextVolumeName(current, true)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "x.part1.rar, x.part2.rar",
        "x.part9.rar, x.part10.rar",
        "/data/x.part1.rar, /data/x.part2.rar",
        "/data/7/x.part1.rar, /data/7/x.part2.rar",
    })
    void newNumberingProducesExpectedNextName(final String current, final String expected) {
        assertThat(VolumeHelper.nextVolumeName(current, false)).isEqualTo(expected);
    }

    /**
     * The volume number lives in the file name only (unrar {@code GetVolNumPart} and {@code
     * GetExt} start at {@code PointToName}). A name without one has no next volume, rather than
     * one in a sibling directory such as {@code /srv/uploads/42/archive.rar}.
     */
    @ParameterizedTest
    @CsvSource({
        "/srv/uploads/41/archive.rar, false",
        "/data/v9/backup.rar, false",
        "/data/d./xy, true",
    })
    void nextNameNeverEditsADirectory(final String current, final boolean oldNumbering) {
        assertThat(VolumeHelper.nextVolumeName(current, oldNumbering)).isNull();
    }
}
