# RAR5 link fixtures (M3.10, issue #31)

FHEXTRA_REDIR fixtures for `ArchiveRar5LinkTest` — Unix symlinks, hardlinks, file copies,
and the three symlink-safety layers (5.2.5 up-level depth, 6.1.7 target validation, 6.2.3
`LinksToDirs`). Produced with `rar 7.23` (`-ma5 -ol`); the benign golden is the `unrar 7.23`
oracle. No `rar`/`unrar` binary or upstream source is committed — only the two small
archives, the generator (`gen_link_fixtures.sh`), the byte-patch helper
(`patch_hostile_targets.py`), and this recipe.

## Archives

| File | Command (see `gen_link_fixtures.sh`) |
| --- | --- |
| `rar5-links.rar` | `rar a -ma5 -m3 -ol -oh -oi1:1024 … file.txt sub link-flat hard.txt copy-a.bin copy-b.bin` |
| `rar5-links-hostile.rar` | two appends: `-ol -oh` link set, then a plain file under `dqqq/` |

`-ol` stores symbolic links, `-oh` stores hard links as links (not the target data), `-oi`
deduplicates identical files so `copy-b.bin` is stored as a FILE_COPY reference to `copy-a.bin`.

## `rar5-links.rar` — benign, extracts on Unix CI

`unrar 7.23` oracle (`unrar x`), which the test asserts:

| Entry | Type | Oracle result |
| --- | --- | --- |
| `file.txt`, `sub/inner.txt` | file | 39-byte payloads |
| `link-flat` | Unix symlink | `-> file.txt` (exact target bytes) |
| `sub/link-up` | Unix symlink | `-> ../file.txt` |
| `hard.txt` | hard link | shares `file.txt`'s inode (`Files.isSameFile` true) |
| `copy-a.bin` / `copy-b.bin` | file / FILE_COPY | identical 4864-byte bytes, **distinct** inodes |

Windows CI skips symlink/hardlink creation (recorded JVM non-goal) and instead asserts the
redirection metadata via `FileHeader.getRedirection()` (`redirectionMetadataSurfacesOnAllPlatforms`).

## `rar5-links-hostile.rar` — hostile rows synthesized at test runtime

All committed targets are **benign, equal-length placeholders**; `rar` refuses to *create*
hostile symlinks, so the test overwrites the placeholder field with the hostile string and
recomputes the enclosing RAR5 block header CRC32 (the M3.7/M3.9 patch recipe;
`patch_hostile_targets.py` is the standalone form, `ArchiveRar5LinkTest#patchTarget` is the
runtime Java form, scoped to one entry's header via `FileHeader.getPositionInFile()`). Nothing
hostile is committed. Every row is rejected with a typed `UnsafeLinkException`:

| Entry | Placeholder → hostile | Layer | unrar 7.23 oracle |
| --- | --- | --- | --- |
| `h-up` | `aaaaaaaaa` → `../../etc` | 5.2.5 up-level depth | *"Skipping the potentially unsafe … link"* |
| `h-abs` | `bbbbbbbbb` → `/etc/shd0` | 6.1.7 absolute target | *"Skipping the potentially unsafe … link"* |
| `h-bsl` | `ccccccccc` → `..\zz\bad` | S5 backslash normalize | **created literally** (see divergence below) |
| `h-hard.txt` | target `h-file.txt` → `../../pt.x` | containment on hardlink target | *"Cannot create hard link … unpack the link target first"* |
| `dqqq/evil.txt` | name `dqqq` → `dlnk` | 6.2.3 `LinksToDirs` | dir-symlink deleted, real dir made, file written inside |

### Deliberate divergences from the unrar-Unix oracle (recorded, not bugs)

- **Backslash target (`h-bsl`).** On Unix a backslash is an ordinary filename character, so
  unrar 7.23 creates `h-bsl -> ..\zz\bad` verbatim (0 up-levels, "safe"). junrar normalizes
  `\`→`/` first (S5, CVE-2026-28208 class) and rejects the resulting `../zz/bad` traversal on
  every platform — a Windows JVM would otherwise treat it as a real separator.
- **`LinksToDirs` (`dqqq/evil.txt` → `dlnk/evil.txt`).** unrar deletes the intervening
  directory-symlink and re-creates a real directory, then writes the file inside the
  destination. junrar instead refuses with `UnsafeLinkException`, matching the M3.10 acceptance
  row ("rejected with a typed exception") and the divergences-no-go guidance.
- **Hardlink/file-copy traversal target (`h-hard.txt` → `../../pt.x`).** unrar's `ConvertPath`
  strips the leading `../` components and resolves the remainder against the destination root
  (`pt.x`), linking to it if already extracted or skipping with a warning otherwise — it never
  escapes the destination. junrar instead refuses the escaping target outright with
  `UnsafeLinkException` (`resolveTargetWithinDestination`), never attempting a destination-relative
  reinterpretation of the path.

**Issue #40 decision (2026-07-21): all three CLOSED as working-as-intended — KEEP the reject,
do not adopt unrar's strip-and-continue.** Ecosystem cross-check (Apache Commons Compress
`ZipArchiveEntry.setName`, zip4j `getFileNameWithSystemFileSeparators` + its own zip-slip guard
`assertCanonicalPathsAreSame`, both executed/read from local jars and upstream source — see
`docs/porting/reports/divergences-no-go.md` row D3 for the full citations) confirms fail-closed
reject over silent sanitize-and-continue is the mature-Java-library norm, not a junrar
implementation accident. No code changed by this decision.

## Regenerate

`RAR=/path/to/rar sh gen_link_fixtures.sh` (rar 7.23). Payloads are fixed strings, so the
archives are byte-reproducible modulo rar's own timestamp fields.
