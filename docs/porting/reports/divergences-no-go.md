# junrar Regression NO-GO List

**Purpose.** junrar is a Java port of C++ unrar 3.7.3. It has accumulated behavioral
fixes, hardening, and deliberate divergences ON TOP of that C++ baseline. This document
enumerates every such divergence so that a future re-sync onto unrar 7.2.7 (RAR5) does not
silently revert one. **A re-translation that drops any (b) item below is a security or
correctness regression.**

- junrar HEAD analyzed: `99866b74` (branch `master`, 2026-06-29). 504 commits swept.
- C++ baseline: unrar 3.7.3 = `git -C ~/git/unrar show 2e71167:<file>`.
- Method: full `git log` classification + per-commit diff read + upstream cross-check +
  test/fixture mapping. Read-only; nothing modified.

## Class counts (non-build, non-docs commits)

| Class | Count | Meaning |
| ----- | ----- | ------- |
| (b) junrar-original hardening/bugfix (NO unrar equivalent) | 20 | The core NO-GO list |
| (c) Java-API feature (new capability) | 8 | Public API surface to preserve |
| (a) port-faithful / behavior-preserving perf | 5 | Must stay behaviorally equivalent |
| (d) refactor / style / build / deps / ci / docs | ~460 | Not behavior-load-bearing |

> **Snapshot note (added during Phase 0):** statuses below are as of this report's
> writing (2026-07-17, pre-P0). Live pin status is tracked in `MIGRATION_MANUAL.md` §7
> only; rows pinned since (C1, C7, C13, D1, …) are NOT updated here.

**UNPINNED count: 6** (behaviors with NO regression test — must add a test before re-port):
solid-v20 AIOOBE (9b69c6b7), PPM MaxMB clamp (0dc9d457), large-file 2G+ InputStream
(cbbe99c4), protect-header seek-past-data (8e91d695), unsigned-shift `>>>` correctness class
(25491b50), deliberate >2GB `byte[]` limit (a43a5192).

---

## THE NO-GO TABLE — behaviors that MUST survive any re-translation

Each row: what MUST hold, the commit(s), the code site on `master` today, and the guard test
(or **UNPINNED**). Ordered roughly by severity.

### Security (CVE-backed) — highest priority

| # | Behavior that MUST survive | Commit(s) | Code site today | Guard test + fixture |
| - | -------------------------- | --------- | --------------- | -------------------- |
| S1 | **CVE-2018-12418 DoS**: corrupt size fields must NOT drive unbounded `byte[]` allocation, and a repeated stream position must abort — not loop forever. `safelyAllocate(size, MAX_HEADER_SIZE=20 MB)` wraps every header buffer alloc; `processedPositions` HashSet throws `BadRarArchiveException` on a duplicate `newpos`. | `ad8d0ba8` (2018, #8, ex-Apache Tika) | `Archive.java:92` `MAX_HEADER_SIZE`; `:311` `processedPositions`; `safelyAllocate(...)` at `:316,374,383,391,399,424,437,448,470,...` | Corrupt corpus in `AbnormalFilesTest` (loop*.rar) + `tika-documents.rar` listing (`ArchiveTest.testTikaDocs`). NOTE: the *duplicate-position → BadRarArchiveException* branch itself is only weakly pinned — the loop*.rar fixtures trip the newer validity checks (S3/S4) first. Preserve the guard regardless. |
| S2 | **CVE-2022-23596 (part 1, 7.4.1)**: an invalid SubBlock subtype must not NPE-and-loop. `subHead.getSubType()==null → break` before the `switch`. | `7b16b3d9` (2022) | `Archive.java` subblock switch (~`:490`, `SubBlockHeaderType subType; if (subType==null) break;`) | `AbnormalFilesTest` → `abnormal/loop.rar` expects `CorruptHeaderException` |
| S3 | **CVE-2022-23596 (part 2, 7.5.3)**: Mark header and End-Archive header must be validated. `MarkHeader.isValid()` and `EndArcHeader.isValid()` (requires headCRC `0x3DC4`, type EndArc, flags `0x4000`, size==BaseBlockSize). | `6c7dc7af` (2022) | `Archive.java:364-365` (mark), `:430-431` (endarc); `EndArcHeader.isValid()` | `AbnormalFilesTest` → `abnormal/loop1.rar`, `loop2.rar`, `loop3.rar` → `CorruptHeaderException` |
| S4 | **More invalid-header checks**: `nameSize<=0 → CorruptHeaderException`; EOF while reading a file header → `CorruptHeaderException("Unexpected end of file")` (not raw `EOFException`); `isFilenameValid()` gate. | `cc33b6c1` (2022) | `FileHeader.java:142-143` (nameSize), `:169-170` (filename); `Archive.java:452` (EOF) | Covered by abnormal corpus (`AbnormalFilesTest`) |
| S5 | **CVE-2026-28208 backslash path traversal (Linux)**: entry names must be separator-normalized (`\`→`/`) BEFORE the canonical-path check, and `makeFile` must split on `/` not `\\`. Else `..\..\etc` bypasses the guard on Unix and writes outside the target. | `947ff1d3` (2026, 7.5.8) | `LocalFolderExtractor.java:58` (`invariantSeparatorsPathString(fh.getFileName())`), `:76` (`split("/")`), `:96` (helper) | `LocalFolderExtractorTest.rarWithFileOutsideTarget_ShouldThrowException2` → `parent-dir.rar` |
| S6 | **CVE-2026-41245 sibling-prefix (Zip-Slip) traversal**: the containment check must compare against `destination.getCanonicalPath() + File.separator`, not a bare prefix — else `/tmp/extract_evil` passes `startsWith("/tmp/extract")`. | `d77e9a83` (2026, 7.5.10) | `LocalFolderExtractor.java:35` and `:61` (`startsWith(... + File.separator)`) | `LocalFolderExtractorTest.rarWithFileOutsideTarget_ShouldThrowException3` → `sibling-prefix-traversal.rar` |
| S7 | **Original path-traversal guard (directoryTraversalBug #31)**: `getCanonicalPath().startsWith(destination canonical)` in `createFile`/`createDirectory`; traversal throws `IllegalStateException`. The base defense S5/S6 harden. | `e60b915f` (2019) | `LocalFolderExtractor.java` createFile/createDirectory | `LocalFolderExtractorTest.rarWithDirectoriesOutsideTarget_ShouldThrowException` |
| S8 | **PPM suballocator memory clamp**: `MaxMB` from the archive is clamped to 1 MB to prevent OOM DoS on crafted PPM archives. | `0dc9d457` "fixOutOfMemory" (Seth Chhim, merged 2020) | `ModelPPM.java:188-189` (`final int MaxMBLimit=1; if (MaxMB>MaxMBLimit) MaxMB=MaxMBLimit;`) | **UNPINNED** — no PPM test. **SUSPICIOUS**: hard-forces `MaxMB=1` for *all* PPM archives, diverging from unrar (which honors up to configured max). May truncate legitimate PPMd decoding. Re-port must consciously decide; do not silently drop or silently keep. |

### Correctness / robustness (junrar-original, no unrar equivalent)

| # | Behavior that MUST survive | Commit(s) | Code site today | Guard test + fixture |
| - | -------------------------- | --------- | --------------- | -------------------- |
| C1 | **Solid RAR v20 AIOOBE**: `CopyString20` must guard `destPtr >= 0`. In solid mode a back-ref can point into a previous file, making `destPtr = unpPtr - distance` negative; C++ uses `unsigned int` (wraps into the masked slow path), Java's signed `int` passes `< MAXWINSIZE-300` and does `System.arraycopy` with a negative index → AIOOBE. | `9b69c6b7` (2026, 7.5.9) | `Unpack20.java:215` (`if (destPtr >= 0 && destPtr < ... && unpPtr < ...)`) | **UNPINNED** — commit touched ONLY `Unpack20.java`, added NO fixture. Existing solid fixture `rar4-solid.rar` is v29, not v20. **Add a solid-v20 negative-back-ref fixture before re-port.** unrar 3.7.3 `unpack20.cpp` confirmed uses `unsigned int DestPtr` (no `>=0` check possible). |
| C2 | **Audio-decode: per-slot object init (memset port bug)**: `AudV` and `MD` arrays must be filled with *distinct* new objects per slot, not one shared reference. `Arrays.fill(AudV, new AudioVariables())` (shared ref) caused CRC errors; `MD` was left null → NPE. | `15f4afa2` (AudV, CRC), `952436b2` (MD, NPE) (2022) | `Unpack20.java` init block (per-slot `for` loops replacing `Arrays.fill(..., new ...)`) | `ArchiveTest.testAudioDecompression` → `audio/BoatModernEnglish-audio-text-unpack20.rar`, `-unpack30.rar`, `-regular-unpack15/20/30*.rar` vs `BoatModernEnglish.wav` |
| C3 | **Extended-time parsing (RAR4 EXTTIME)**: `mTime/cTime/aTime/arcTime` parsed as `FileTime` with 100 ns granularity from the ext-time block; DOS mtime is the base. Adds `getLastModifiedTime/getCreationTime/getLastAccessTime/getArchivalTime`. | `d5cc784c` (2022, 7.5.0) | `FileHeader.java:200-224` + `parseExtTime` `:249-273` | `ArchiveTest.testArchiveExtTimes_{LosAngeles,SaoPaulo,Amsterdam,Kolkata}` → `rar4-ext_time.rar` (nano-precise asserts) |
| C4 | **OOB read on corrupt ext-time (#86)**: before reading the 2-byte ext-time flags, require `position+1 < fileHeader.length`; else set flags=0 and warn (UnRAR never reads past the header). Plus `getFileName()` returns `fileName` when `fileNameW` is null/empty even if the unicode bit is set. | `1d52d5d3` (2022, 7.5.1) | `FileHeader.java:204-210` (bounds guard), `:670` (`getFileName()` unicode-empty fallback) | `bugfixes/GitHub86MissingDataTest` → `bugfixes/gh-86-missing-data.rar` |
| C5 | **File-time millisecond zeroing**: `getDateDos` must `cal.set(MILLISECOND, 0)` — else system-clock millis leak into DOS timestamps. | `b1f96385` (2020) | `FileHeader.java:323` | Pinned indirectly by C3 ext-time asserts |
| C6 | **MAC_HEAD / subblock seek-past-data**: after parsing any 0x77 SubHeader, unconditionally seek to `positionInFile + headerSize + dataSize`; else partially-handled subtypes (MAC_HEAD 0x0102, EA_HEAD) leave the channel mid-block and every later header parses at the wrong offset → spurious `CorruptHeaderException`. Also adds a `processedPositions` loop guard on that path. | `ad7ad33b` (2026, 7.5.9) | `Archive.java:405-410` | `bugfixes/MacSubblockTest` → `bugfixes/mac-subblock.rar` (2-file archive, both must extract) |
| C7 | **Protect-header seek-past-data**: `newpos = ph.positionInFile + ph.headerSize + ph.getDataSize()` (the `+ dataSize` is the fix). Earliest instance of the seek-past-data pattern later generalized in C6. | `8e91d695` (2011, Jesse Hallio) | `Archive.java` ProtectHeader case | **UNPINNED** — no corrupt protect-header fixture |
| C8 | **NPE→typed exception on null main header**: `isEncrypted()` (and callers) throw `RarException(mainHeaderNull)` instead of a raw `NullPointerException`. | `8433b1b6` (2018, #14) | `Archive.java` `isEncrypted()` + `RarExceptionType.mainHeaderNull` | `AbnormalFilesTest` → `abnormal/mainHeaderNull.rar` → `BadRarArchiveException` |
| C9 | **NPE on corrupt headers (#36/#45)**: hardened header-read loop; corrupt header yields `CorruptHeaderException`, never NPE. | `b7215896` (2020) | `Archive.java` header loop | `AbnormalFilesTest` → `abnormal/corrupt-header.rar` → `CorruptHeaderException` |
| C10 | **Filename validation refinement (#108)**: validate the *effective* `getFileName()` (unicode-aware) once via `isFilenameValid` (canonical-path round-trip), not `fileName` and `fileNameW` separately (the W copy was rejecting valid unicode). | `a5186a8b` (2023, 7.5.5) refining `cc33b6c1` | `FileHeader.java:169-170`, `isFilenameValid` `:230-237` | `ArchiveTest.gh108_unicodeFileNamesAreDecodedProperly` → `gh108.rar` |
| C11 | **VMSF_UPCASE lookup (#110)**: `VMStandardFilters.findFilter(7)` must return `VMSF_UPCASE`, not null. junrar's enum→lookup helper omitted the case (C++ unrar uses raw ints — no lookup to be wrong; the bug is junrar-specific). Same commit made `UnrarHeadertype.headerByte` final and removed duplicate lookup checks. | `bc9889bc` (2023, 7.5.5) | `VMStandardFilters.java` `findFilter` | `unpack/vm/VMStandardFiltersTest` (`@EnumSource` covers all 8 incl. UPCASE; `findFilter(8)` null). Companion enum tests: `HostSystemTest`, `SubBlockHeaderTypeTest`, `UnrarHeadertypeTest`, `BlockTypesTest`, `VMCommandsTest`, `VMFlagsTest`, `VMOpTypeTest`. unrar 3.7.3 `rarvm.hpp:36-37` confirms VMSF_UPCASE exists upstream. |
| C12 | **Empty-file → InputStream (#88/#90)**: `getInputStream` returns an `EmptyInputStream` for `getFullUnpackSize()<=0`; pipe buffer size is `max(min(size,PIPE),1)` (never 0, which `PipedInputStream` rejects). | `c95a211a` (2022, 7.5.2) | `Archive.java` `getInputStream` + `EmptyInputStream` | `bugfixes/GitHub88EmptyFile` → `bugfixes/gh-88-empty.rar` (files `foo`,``,`bar`) |
| C13 | **Large files 2G+ via InputStream (#104)**: `RandomAccessInputStream.length` must be `long` (int overflowed at 2 GB). | `cbbe99c4` (2022, 7.5.4) | `RandomAccessInputStream.java` (`long length`, `(int)(length>>>BLOCK_SHIFT)`) | **UNPINNED** — no >2 GB fixture (impractical corpus size) |
| C14 | **Missing EndArcHeader on stream parse (#216)**: `InputStreamVolume.getLength()` returns `inputStream.available()` (falling back to `Long.MAX_VALUE` on IOException), so a stream lacking an End-Archive header terminates instead of throwing `CorruptHeaderException`. | `964801cd` (2025, 7.5.6) | `InputStreamVolume.java` `getLength()` | `ArchiveTest` (modified +126 lines for #216; stream-parse assertions) |
| C15 | **Unsigned-shift (signedness) correctness — CLASS RULE**: everywhere C++ uses an unsigned right shift, Java MUST use `>>>`, not `>>`. Sign-extension silently corrupts decompression/bit-reading. Applied across `RandomAccessStream`, `FileNameDecoder`, `Unpack15`, `BitInput`, `RarVM`. | `25491b50` (2020) + `d276f937` (2022) | those 5 files | **UNPINNED** as a class (indirectly exercised by decompression tests). Any re-port MUST re-audit every `>>` on a value that is unsigned in C++. |

### Deliberate divergences (not bugs — conscious departures from unrar)

| # | Divergence to preserve | Commit(s) | Note |
| - | ---------------------- | --------- | ---- |
| D1 | **>2 GB single entry is deliberately NOT extracted to a `byte[]`** — a prior attempt (`6ecfb718`) was reverted because a Java `byte[]` caps at `Integer.MAX_VALUE` and threw OOM. junrar consciously keeps this limitation. | `a43a5192` (2019) reverting `6ecfb718` | **UNPINNED** (documented divergence). Don't "fix" by naively re-porting a `byte[]` path. |
| D2 | **CRC32 via `java.util.zip.CRC32`** (JDK intrinsics) replaces junrar's hand-rolled `RarCRC.checkCrc` table. Standard poly `0xEDB88320` — behavior-preserving. `RarCRC.checkOldCrc` (RAR 1.5 legacy CRC) is RETAINED; do not delete it. | `5270d235` (2026, 7.6.0) | Sites now: `ComprDataIO.java:103-104` (`new CRC32()`), `RarVM.java:903`, legacy `ComprDataIO.java:195` `RarCRC.checkOldCrc`. Must stay bit-identical to the table version. |
| D3 | **FHEXTRA_REDIR link-path reject-vs-strip, three cases (issue #40, follow-up to #31/M3.10)**: (1) a `UNIX_SYMLINK` target containing a backslash (`..\zz\bad`), (2) writing a member through a previously-extracted directory-symlink (`LinksToDirs`), and (3) a `HARDLINK`/`FILE_COPY` target containing `../` traversal — junrar rejects all three with `UnsafeLinkException`; unrar 7.23 sanitizes-and-continues (creates the symlink literally, deletes+recreates the dir, strips the traversal via `ConvertPath`). **Investigated 2026-07-21 and CLOSED as working-as-intended — KEEP the reject on all three, do not adopt unrar's strip-and-continue.** Ecosystem cross-check (executed against local jars): Apache Commons Compress `1.28.0`/`1.26.1` `ZipArchiveEntry.setName` (`archivers/zip/ZipArchiveEntry.java:1404-1409`) replaces `\`→`/` for default (`PLATFORM_FAT`) entries at header-parse time, called from both `ZipArchiveInputStream.readNextEntry` and `ZipFile`'s central-directory parse — i.e. mature libraries treat backslash as a separator to sanitize, not a literal filename byte, matching junrar's S5 posture (case 1). zip4j (`master`, fetched from `github.com/srikanth-lingala/zip4j`) `AbstractExtractFileTask.getFileNameWithSystemFileSeparators` (`tasks/AbstractExtractFileTask.java:191-193`) treats **both** `/` and `\` as directory separators when building the output path, and its own zip-slip guard `assertCanonicalPathsAreSame` (`:74-92`) **throws** `ZipException` on an escaping canonical path rather than stripping and continuing — the same canonical-containment-then-reject junrar already applies (S6/S7). No Java zip/7z library has a native dir-symlink-write-through or hardlink-redirection analogue to cross-check cases 2/3 directly, but the reject-over-strip precedent from case 1 and zip4j's own zip-slip reject extend by the same reasoning: unrar's `LinksToDirs` deletes whatever filesystem object occupies that path (attacker-planted or pre-existing, without checking its target) before recreating a directory, which is a strictly riskier operation than junrar's fail-closed refusal; and even though unrar's `ConvertPath` strip for case 3 is provably safe (`out/secret.t` stayed inside the destination in the executed local probe, see issue #40), adopting "sanitize instead of reject" on any traversal-adjacent path is inconsistent with junrar's own CVE-2026-28208/CVE-2026-41245 fail-closed philosophy for no compatibility benefit that outweighs that consistency. **No code changed** — this is a documentation-only decision; per issue #40 acceptance, moving any of the three to parity would itself need to update this row and requires test-first RED→GREEN plus independent adversarial verification, neither of which is warranted absent a concrete correctness/compat complaint. | M3.10 (`91c8b0c3`), issue #31 / #40 | `LocalFolderExtractor.java` `validateSymlinkTarget` (case 1), `refuseWriteThroughSymlink` (case 2), `resolveTargetWithinDestination` (case 3) | `ArchiveRar5LinkTest` hostile rows (`h-bsl`, `dqqq/evil.txt`, `h-hard.txt`); `src/test/resources/com/github/junrar/links/README.md` |

---

## Java-API features to preserve (class c)

Public capabilities junrar added that unrar (a CLI) has no notion of — dropping them breaks
downstream callers:

| Feature | Commit | Test |
| ------- | ------ | ---- |
| Extract from `InputStream` | `67601314` (2018) | ArchiveTest stream paths |
| Password-protected archives (RAR4/RAR5) + `isPasswordProtected()` | `4402afc0`, `b218bb97` (2020) | `password/*.rar` fixtures |
| Header-encrypted archives | `c2a24f3c` (2020) | `password/rar*-encrypted-junrar.rar` |
| Multi-part extraction from `InputStream` | `9ee32eaf` (2020) | `VolumeExtractorTest`, `volumes/*` |
| `FileHeader.getFileName()` (unicode-aware accessor) | `b6da583f` (2020) | used throughout |
| Thread-pool `getInputStream` (`junrar.extractor.use-executor`) | `a3383b0d` (2022) | ArchiveTest |
| **Random access in solid RAR4** (out-of-order extract: skip-forward by decompress-without-write, reset+replay for backward) | `e0874d21` (2026, 7.6.0) | `ArchiveTest` solid in-order / out-of-order / reverse / inputstream → `solid/rar4-solid.rar` |
| VFS (commons-vfs2) provider | added 2011 (`90d12884`), **REMOVED** `3bbe8eda` (2020) | (moved to a separate `junrar-vfs2` repo — do NOT reintroduce) |

---

## Fixture corpus — what each adversarial/regression `.rar` guards

`src/test/resources/com/github/junrar/`:

| Fixture | Guards | Test |
| ------- | ------ | ---- |
| `abnormal/corrupt-header.rar` | C9 NPE→CorruptHeaderException | AbnormalFilesTest |
| `abnormal/mainHeaderNull.rar` | C8 null main header → BadRarArchiveException | AbnormalFilesTest |
| `abnormal/loop.rar` | S2 CVE-2022-23596 pt1 (invalid subtype NPE/loop) | AbnormalFilesTest |
| `abnormal/loop1.rar`, `loop2.rar`, `loop3.rar` | S3 CVE-2022-23596 pt2 (mark/endarc validity) | AbnormalFilesTest |
| `bugfixes/gh-86-missing-data.rar` | C4 OOB read on corrupt ext-time | GitHub86MissingDataTest |
| `bugfixes/gh-88-empty.rar` | C12 empty file → InputStream | GitHub88EmptyFile |
| `bugfixes/mac-subblock.rar` | C6 MAC_HEAD seek-past-data | MacSubblockTest |
| `parent-dir.rar` | S5 CVE-2026-28208 backslash traversal | LocalFolderExtractorTest (…Exception2) |
| `sibling-prefix-traversal.rar` | S6 CVE-2026-41245 sibling-prefix Zip-Slip | LocalFolderExtractorTest (…Exception3) |
| `gh108.rar` | C10 unicode filename validation (#108) | ArchiveTest.gh108_… |
| `rar4-ext_time.rar` | C3/C5 extended-time nano parsing | ArchiveTest.testArchiveExtTimes_* (4 TZ) |
| `audio/BoatModernEnglish-*-unpack{15,20,30}*.rar` + `.wav` | C2 audio-decode per-slot init | ArchiveTest.testAudioDecompression |
| `solid/rar4-solid.rar` | C-feature random-access solid RAR4 | ArchiveTest solid* |
| `solid/rar5-solid.rar` | RAR5 solid listing | ArchiveTest / JunrarTest |
| `tika-documents.rar` | S1 robustness / normal listing (Apache Tika corpus) | ArchiveTest.testTikaDocs |
| `password/*.rar` | RAR4/RAR5 password + encrypted-header | JunrarTest / ArchiveTest |
| `volumes/new-numbers/*`, `volumes/new-part/*` | multi-volume | VolumeExtractorTest |
| `rar4.rar`, `rar5.rar`, `test.rar`, `unicode.rar` | baseline listing/extraction | JunrarTest / ArchiveTest |

**No fixture exists for:** C1 solid-v20 negative-back-ref AIOOBE, C7 corrupt protect header,
C13 >2 GB entry, S8/D1 PPM/2GB limits, C15 signedness. These are the UNPINNED gaps.

---

## Guidance for the re-port

1. The 8 **Security** rows (S1–S8) are the ones an attacker exercises — none may regress.
   S5/S6/S7 (`LocalFolderExtractor`) are especially fragile: the RAR5 port must re-apply
   separator-normalization + `+ File.separator` containment to whatever new extraction path
   it introduces, and add hostile-input rows for backslash, sibling-prefix, and `..` cases.
2. **Add tests for the 6 UNPINNED behaviors BEFORE touching their code** — especially C1
   (solid-v20 negative back-ref: craft a solid v20 archive whose back-ref exceeds the current
   file offset) and S8 (decide PPM `MaxMB` policy deliberately; the current hard clamp to 1 MB
   is a real divergence from unrar and may already break legitimate PPMd archives).
3. C1, C15, and the audio memset bug (C2) are all instances of the same root hazard: **C++
   unsigned/`memset` semantics do not survive a naive Java re-translation.** Audit every
   unsigned shift, every `memset(array,0,sizeof)` → distinct-object init, and every
   `unsigned int` distance/pointer for signed-Java overflow.
4. Preserve the retained legacy `RarCRC.checkOldCrc` (D2) and the deliberate >2 GB `byte[]`
   limitation (D1) — both look like "dead code / missing feature" to a re-porter and are not.

---

## Upstream sync 2026-07-21 — `99866b74` → `afc1aeb8` (v7.6.1), plan §6 R6 phase-boundary rebase

Phase M4 closed the plan's last phase, so `rar5-port` was rebased onto `upstream/master`
(74 commits replayed). R6 requires new upstream commits to be classified here. Only two of the
20 are functional; the spotless churn cancels out (`0b15bece` applied, `4a67eb6e` reverted), and
the net source delta is 7 files.

### `e6e333b1` — "prevent directory creation outside target directory" (**security**) — ADOPTED

The entry name `../extract_evil/../extract/payload.txt` passes canonical containment (it resolves
back inside the destination), but the old `makeFile` `mkdir`'d every path component on the way,
creating the sibling `extract_evil` *outside* it. The fix normalizes first, then
`Files.createDirectories(parent)`. Taken verbatim; it is the only thing on this branch that
prevents the escape.

**It is NOT covered by this branch's header-CRC gate, and must never be assumed to be.**
`LocalFolderExtractor.extract` calls `createFile` → `makeFile` — which creates the directories —
*before* `Archive.extractFile`, which is where the P0.7/#12 broken-header refusal throws. By then
the directories already exist. The two guards are sequential, not redundant.

Pinned by two rows in `LocalFolderExtractorTest`, both verified against an executed negative
control (revert `makeFile` to the per-component `mkdir` loop → both fail):
`mkdirEscapePocCreatesNoDirectoryOutsideTarget` (upstream's PoC archive) and
`wellFormedHeaderCannotMkdirOutsideDestination` (the same escaping name through a well-formed
header, so no fixture property can mask the guard). Note upstream's PoC archive additionally has
corrupt headers — `unrar 7.23` reports "the file header is corrupt", `Total errors: 5` — which
upstream ignores and this branch refuses; that refusal is asserted separately and is not the
security property.

### `687a09c4` — "better handling of RarVM VM_JMP" — SUPERSEDED by M2.2, not adopted

Upstream's `setIP(int)` returned `void` and early-returned when `ip >= codeSize` or `maxOpCount`
was exhausted, leaving `IP` unchanged so the interpreter loop spun forever — a hang, reachable
from a crafted archive. The fix returns `boolean` and `break`s at all 11 call sites.

M2.2 (`16d03472`) deleted that interpreter outright: `setIP`, `ExecuteCode`, `getOperand` and
`decodeArg` do not exist on this branch (0 occurrences), so the runaway loop is unreachable by
construction rather than by a guard. An unrecognized VM filter is a no-op, matching unrar ≥ 5.5.1,
which dropped the generic interpreter the same way.

Upstream's accompanying test asserts `CrcErrorException`, which pins upstream's architecture (its
interpreter breaks out mid-filter and produces bytes that fail the checksum) rather than the
format. **`unrar 7.23` tests the same PoC `All OK`, rc=0**, so the oracle-correct outcome here is
a clean extraction, and `AbnormalFilesTest#VM_JMP_maxOpCount_bypass` asserts that plus the
5-second `@Timeout` — termination being the invariant the upstream fix actually protects.

### Checkstyle

Upstream deleted `checkstyle.xml` and the plugin, and left spotless fully commented out, so
`./gradlew check` runs no style gate on either side of the sync. Carried as-is deliberately
(owner decision 2026-07-21: upstream will re-introduce a formatter on its own schedule).
