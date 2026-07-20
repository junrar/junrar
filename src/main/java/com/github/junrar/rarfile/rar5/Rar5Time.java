package com.github.junrar.rarfile.rar5;

import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * RAR5 timestamp decoding (unrar {@code RarTime::SetUnix}/{@code SetWin}/{@code SetUnixNS}):
 * the three wire encodings used by MHEXTRA_METADATA and FHEXTRA_HTIME -- Unix seconds, Windows
 * FILETIME (100 ns ticks since 1601-01-01Z), and Unix nanoseconds since the 1970 epoch. Public
 * because it is shared across the {@code rarfile} and {@code rarfile.rar5} packages (M3.3,
 * issue #24).
 */
public final class Rar5Time {

    /** 100 ns ticks per second, the Windows FILETIME unit. */
    private static final long FILETIME_TICKS_PER_SECOND = 10_000_000L;

    /** Seconds between the Windows FILETIME epoch (1601-01-01) and the Unix epoch (1970-01-01). */
    private static final long FILETIME_EPOCH_DIFF_SECONDS = 11_644_473_600L;

    private Rar5Time() {
    }

    public static FileTime fromUnixSeconds(final long seconds) {
        return FileTime.from(Instant.ofEpochSecond(seconds));
    }

    public static FileTime fromUnixNanos(final long nanosSinceEpoch) {
        final long seconds = Math.floorDiv(nanosSinceEpoch, 1_000_000_000L);
        final long nanos = Math.floorMod(nanosSinceEpoch, 1_000_000_000L);
        return FileTime.from(Instant.ofEpochSecond(seconds, nanos));
    }

    public static FileTime fromWindowsFileTime(final long fileTime100ns) {
        final long seconds = Math.floorDiv(fileTime100ns, FILETIME_TICKS_PER_SECOND) - FILETIME_EPOCH_DIFF_SECONDS;
        final long nanos = Math.floorMod(fileTime100ns, FILETIME_TICKS_PER_SECOND) * 100L;
        return FileTime.from(Instant.ofEpochSecond(seconds, nanos));
    }
}
