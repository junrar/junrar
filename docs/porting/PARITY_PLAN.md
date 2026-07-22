# junrar → unrar 7.2.7 extraction parity plan

Port-tracker citations use `PT-NN` keys, defined exclusively — with a self-contained
summary per key — in [`MIGRATION_MANUAL.md` Appendix A](MIGRATION_MANUAL.md#appendix-a-port-tracker-issue-index).

The execution plan for bringing junrar (Java, baseline = C++ unrar 3.7.3 `2e71167`) to
extraction feature parity with unrar 7.2.7 (`d861246`): RAR 1.5–4 ("RAR3") robustness
plus full RAR5/RAR7 extraction. Companion to
[`MIGRATION_MANUAL.md`](MIGRATION_MANUAL.md) (the "how to port" rulebook); this
document decides **what, in which order, in which chunks**. Every chunk is written to
be executable by a mid-capability LLM implementer: self-contained objective, explicit
source refs, bounded scope, mechanical acceptance criteria.

Evidence base: manual §§ refer to `MIGRATION_MANUAL.md`; report files live in
[`reports/`](reports/); C++ refs are `commit:file[:line]` in the `~/git/unrar` release
mirror (`2e71167` = 3.7.3, `5db30e6` = 4.2.4, `8f437ab` = 6.2.12, `d52ee2f` = 7.0.1,
`d861246` = 7.2.7).

Owner constraints in force (2026-07-17): extraction only, no compression code ever
(manual §2.3); SIMD/machine-code paths dropped; CLI-only features non-goals;
recovery-record *use* optional; Java floor may move to 17/21 only with strong reason;
reputable dependencies allowed when they demonstrably shrink the port; diffs must stay
human-parseable (small logical commits); every row of the manual §7 no-go table
(25 rows: S1–S8, C1–C15, D1–D2) survives and the 6 UNPINNED rows get pinning tests
before any re-port touches their files.

Revision history: revised 2026-07-17 after independent adversarial review
(APPROVE-WITH-CHANGES); per-finding dispositions in Appendix A. Revised again
2026-07-17 after external review (GPT-5.6 Sol, REQUEST CHANGES); per-finding
dispositions in Appendix B — notably the RAR5 KDF contract (B-S1) and the
dictionary-guard redesign (B-S3).

---

## 1. Strategy decision — component milestones, version-ordered inside

**DECISION: milestone/component-based division (Phase 0 + M1–M4), with
version-ordered stepping used *inside* the milestones where it is genuinely the
easier unit of work.** The delta map's M1–M4 sketch
(`reports/unrar-delta-map.md` §5) is adopted with amendments (below), not
rubber-stamped.

### 1.1 The rejected alternative: version-by-version stepping (3.7.3 → … → 7.2.7)

A full walk is **161** release steps
(`git -C ~/git/unrar rev-list --count 2e71167..d861246` → 161; re-executed
2026-07-17). The census evidence kills it:

| Evidence | Consequence for version-stepping |
| --- | --- |
| 5.0.0 (`a224d33`) is a single release commit containing the whole file split **and** RAR5 wholesale: 134 files changed, +13541/−9332; unpack.cpp 1187→305 lines; 32 new files (`git diff --name-only --diff-filter=A -M 5db30e6 a224d33` → 32; rename-detection-sensitive, 29–33) incl. `headers5.hpp`, `crypt5.cpp`, `unpack50.cpp` (probes re-executed 2026-07-17; `unrar-delta-map.md` §1, §5) | The hardest step of the walk is itself a mega-diff. Version-stepping fails to decompose exactly the work it would need to decompose; the RAR5 lift must be split by *component* no matter what. |
| 7.1.1 (`a2e5484`) is pure container modernization: 45 files, +862/−406, `array.hpp` deleted (−169), **zero** format change (`git show a2e5484 --stat`, re-executed); the 6.x–7.1 std::wstring/std::vector sweep hit unicode.cpp at 7.0.1 (+266) — semantically irrelevant for Java (`unrar-delta-map.md` §4.8) | A version-stepper ports pure textual noise; a component plan compares semantics and skips it. |
| junrar's class topology is not unrar's file topology: one `Unpack` chain vs `#include` split, eager header list vs lazy cursor (manual §4.1, §5.1) | Intermediate "unrar 4.x-shaped junrar" states have no Java counterpart to validate against. Any *shipped* state owes a full no-go re-verification (manual §7, 25 rows) — and version-stepping's intermediate states either ship (audit each) or never ship (then the step bought nothing). The milestone plan audits exactly 5 shipped states. |
| The RAR3-path deltas that matter are already isolated per-release with pinned evidence (`unrar-delta-map.md` §3 + appendix: 3.9.1, 3.9.8, 5.3.1, 5.3.6, 5.5.4, 5.6.1, 7.0.3, encname) | The only real benefit of stepping — release-granular attribution — is already available inside a component plan: every M1 chunk cites its introducing release blob. |

### 1.2 Where version-stepping IS used (the owner's option, honored on evidence)

- **M1 ports the RAR3 guards in release-introduction order** (3.9.1 → 3.9.8 → 5.3.x →
  5.5.4 → 5.6.1 → 7.0.3). Each guard is small, independent, and easiest to read as
  "the diff that release made" against 4.2.4 (`5db30e6`) context
  (`unrar-delta-map.md` §5.1).
- **M2 targets exactly the 5.5.1 shape** (`1522ee0`) — a single-release cliff ported
  as a unit.
- **M3 pins one reference version, 6.2.12 (`8f437ab`)** — last pre-RAR7, most
  field-tested (`unrar-delta-map.md` §5.3) — instead of chasing 7.2.7 through the
  7.1.1 container churn.
- **M4 is literally the 6.2.12 → 7.2.7 version delta**, which is small and localized
  (`unrar-delta-map.md` §2.8).

So the plan is a hybrid: component milestones as the macro structure (because the
census proves the macro deltas are component-shaped), release-pinned steps as the
micro structure (because the guard trail is release-shaped).

### 1.3 Amendments to the delta map's sketch

1. **A Phase 0 (prerequisite) is added before M1** — pinning the 6 UNPINNED no-go
   rows, triaging live traps T1–T10, and building the header-CRC scaffolding. Owner
   mandate; also plain risk hygiene: M1 touches `ModelPPM`/`Unpack20`/`Unpack15`,
   whose regressions rows S8/C1/C15 are currently unpinned.
2. **M3 gains an explicit dictionary cap** (window ≤ 1 GB in one `byte[]`), moving
   the >1 GB window problem wholly into M4 (§6 R1). The delta map mentions the
   segmented window but does not stage it.
3. **The RAR5 integration gate stays closed until M3's final chunk**: the existing
   `UnsupportedRarV5Exception` throw (`Archive.java:359`) is lifted once, at the end,
   so the RAR5 members of the regression corpus flip exactly once (§4.4 — **345**
   of the 12,475 committed JSONs reference RAR5, counted 2026-07-17:
   `grep -rl 'UnsupportedRarV5Exception' src/regressionTest/resources --include='*.json' | wc -l`
   → 345; the other 12,130 must stay byte-identical).

---

## 2. Platform decisions

### 2.1 Java version — **stay at release 8** (recommendation; owner sign-off flag)

The bump permission requires "a strong reason (e.g. stdlib improvements that would
make it significantly easier to port)". Enumerating every stdlib API this port
actually needs shows none is missing at 8:

| Port need | API | Available since |
| --- | --- | --- |
| RAR5 KDF (crypt5.cpp:85) | hand-rolled PBKDF2 loop over `Mac` `HmacSHA256` (M3.4) — `HmacSHA256` is a **required** `Mac` algorithm on Java 8; NO PBKDF2 `SecretKeyFactory` is required on any Java 8 implementation (required list = DES, DESede — Java 8 javadoc, doc-verified 2026-07-17), so `PBKDF2WithHmacSHA256` is a SunJCE accident, not a platform guarantee | 8 |
| AES-256-CBC (headers + data) | `Cipher` `AES/CBC/NoPadding`, 256-bit key | 8 (unlimited policy default since 8u161) |
| pswcheck csum, SetKey50 | `MessageDigest` `SHA-256` | 1.4 |
| `ConvertHashToMAC` (crypt5.cpp:193) | `Mac` `HmacSHA256` | 8 |
| UTF-8 names (manual §5.1), UTF-8/UTF-16LE passwords (T4) | `StandardCharsets` | 7 |
| FHEXTRA_REDIR symlink/hardlink extraction | `java.nio.file.Files.createSymbolicLink/createLink`, `readSymbolicLink` | 7 |
| HTIME nanosecond timestamps | `java.nio.file.attribute.FileTime` (ns precision) | 7 |
| unsigned helpers for §4.2 idioms | `Integer.toUnsignedLong`, `Long.compareUnsigned`, `Integer.divideUnsigned` | 8 |
| CRC32 (D2) | `java.util.zip.CRC32` | already in use |

What 17/21 would add, examined and rejected as non-load-bearing: records/sealed
classes (cosmetic; tests already compile at toolchain 21 and use them —
manual §3 "Java level"), `InputStream.readNBytes`/`transferTo` (9+, convenience),
`Arrays.mismatch` (9, minor). The one genuinely tempting API — long-indexed
`MemorySegment` for >2 GB windows — is final only in Java **22**, beyond both
permitted LTS targets, so no permitted bump solves R1; a segmented `byte[][]` window
(§6 R1) is required regardless and works on 8.

**Ecosystem cost of bumping:** junrar's consumer base includes Android apps and
long-tail JVM consumers (the Apache Tika chain); the library's value proposition is
"plain java, zero deps, runs anywhere". A floor bump to 17/21 cuts consumers while
enabling zero port-relevant APIs — all cost, no benefit.

**Verdict: keep `options.release = 8` (build.gradle:38).** Document the 8u161 crypto
floor for AES-256 in the README at M3. Revisit only if M4's segmented window turns
out untenable in practice (it will not — it is shift+mask arithmetic).
**Flagged for owner sign-off.**

**Runtime proof, not just bytecode proof (review B-S8):** `options.release = 8`
proves API/bytecode compatibility only; CI runs the suite on JDK 21 exclusively
(`ci.yml` — probed 2026-07-17: `java-version: '21'`, three OS, no other JDK).
Provider behavior (JCE algorithm availability, crypto-policy defaults) is a
runtime property of the JRE actually used. P0.9 adds a JRE 8 extraction smoke to
CI; M3.11 extends it with RAR5 AES/PBKDF2 fixtures.

### 2.2 Dependencies — **zero new runtime dependencies; port Blake2sp in pure Java**

Runtime today = `org.slf4j:slf4j-api` only (build.gradle:18; manual §3). Per-need
analysis:

| Need | Candidates | Decision |
| --- | --- | --- |
| **Blake2sp** (FHEXTRA_HASH type 0, 32-byte file hashes) — the one primitive the JDK lacks (manual §4.11, §5.5) | (a) `bcprov` as a runtime dependency — Bouncy Castle **does** ship `org.bouncycastle.crypto.digests.Blake2spDigest`, the full 8-lane sp tree mode (verified against the bcprov-jdk18on 1.85 javadoc; an earlier revision of this plan and of manual §5.5 wrongly claimed only `Blake2sDigest` existed — corrected per review finding F1); (b) pure-Java port of `blake2s.cpp` + `blake2sp.cpp` (183 + 153 lines at `8f437ab`, self-contained, no SIMD needed) in `src/main`, **plus `bcprov` as a `testImplementation`-only dependency** serving as a differential-testing oracle; (c) pure-Java port alone, validated only by official BLAKE2 KAT vectors | **(b) pure-Java port in main + BC as test-only oracle.** Honest trade with the true fact set: BC-at-runtime (a) would make the port smaller by ~400–500 Java lines and ride a maintained implementation, but costs a ~8 MB jar on every consumer, the notorious BC version-conflict surface (Android apps and any classpath already carrying a different bcprov), and the library's "plain java, zero deps" value proposition — recurring costs on every consumer forever vs a one-time bounded port of a frozen algorithm (RFC 7693). (c) keeps zero deps but validates only against fixed vectors. (b) buys (c)'s runtime profile *plus* (a)'s implementation as a correctness oracle at test scope (test deps already include mockito/archunit/etc. — build.gradle:23-28; nothing ships): unit tests compare the port against `Blake2spDigest` on randomized inputs in addition to RFC 7693 BLAKE2s vectors + official `blake2-kat.json` BLAKE2sp KATs + real-fixture cross-check against unrar (§4.2). |
| PBKDF2-HMAC-SHA256, AES-256-CBC, SHA-256, HMAC-SHA256, CRC32 | JDK | JDK (manual §4.11 policy: KDF/format math ported, primitives never). |
| Everything else (vint, LZ, filters, headers) | — | Plain Java. No candidate dependency shrinks any of it. |

**Verdict: RUNTIME dependency list stays `slf4j-api` only; `bcprov` enters
`testImplementation` scope only (never shipped).** **Flagged for owner sign-off**
(the owner named BC as the example dependency; the recommendation declines it at
runtime on the jar-size/conflict/zero-dep grounds above, with the port ≈ 400–500
Java lines as the price and BC still doing duty as the test oracle).

---

## 3. Work breakdown

Phases are strictly ordered; chunks within a phase are ordered unless marked
parallel-safe. Every chunk obeys: manual §3 house rules, §4 pattern catalog, §7
no-go list, §8 testing discipline; conventional commits; **≤ ~600 changed lines**
(estimates below are changed-lines including tests). "Acceptance" lines are runnable
gates; `./gradlew build` (3-OS CI) is implicit for every chunk. Fixture-generation
procedures: §4.

### Phase 0 — prerequisites: pin, triage, scaffold (before ANY re-port edit)

Mandate: manual §7 ("an UNPINNED behavior gets its pinning test BEFORE any re-port
touches its file") + owner constraint. No chunk in M1+ may start before the Phase 0
chunk covering its files has landed.

#### P0.1 — pin C1: solid-v20 negative back-ref

- **Objective:** regression test proving a solid RAR v20 back-reference reaching
  before the window start fails with a typed `RarException`, never AIOOBE.
- **Files:** `src/test/…/Unpack20SolidTest.java` (new); fixture under
  `src/test/resources/com/github/junrar/solid/`.
- **C++ refs:** `2e71167:unpack20.cpp` (`unsigned int DestPtr` — wrap semantics);
  junrar guard `Unpack20.java:215` (commit `9b69c6b7`).
- **Patterns:** manual §4.2 (unsigned-subtraction wrap), §8.1 corrupt-input pattern.
- **Acceptance:** `./gradlew test --tests "com.github.junrar.Unpack20SolidTest"`
  green; test asserts extraction of the fixture yields **`CrcErrorException`**
  and NOT `ArrayIndexOutOfBoundsException`. (Pinned by code probe 2026-07-17:
  the `Unpack20.java:215` guard routes out-of-range `destPtr` to a masked-wrap
  copy — `window[destPtr++ & Compress.MAXWINMASK]`, lines 230-235 — which throws
  nothing and emits garbage; the typed failure is the data-CRC check at
  extraction end. If the executed test observes a different type, that is an
  ESCALATE-worthy contradiction, not a blank to fill.)
- **Fixtures:** solid v20 archive with a negative back-ref — produced per §4.3
  (RAR 2.x binary if obtainable, else byte-patched v20 fixture; the hostile variant
  is a corrupt archive by construction, so byte-patching is legitimate).
- **No-go rows:** C1 (pins it).
- **Est. diff:** ~120 lines + fixture.

#### P0.2 — pin C7, then fix T2 (ProtectHeader)

- **Objective:** first commit pins protect-header seek-past-data (corrupt
  protect-header fixture parses the FOLLOWING headers correctly); second commit
  fixes the T2 double-read/size-constant wreck against the real C++ layout.
- **Files:** `rarfile/ProtectHeader.java`, `Archive.java` (ProtectHeader case),
  new test + fixture.
- **C++ refs:** `2e71167:headers.hpp:242-249` (`SIZEOF_PROTECTHEAD 26`, field
  layout).
- **Patterns:** manual §4.8 (absolute vs incremental size), §6 T2.
- **Acceptance:** new `ProtectHeaderTest` green; commit 1's test stays byte-identical
  through commit 2 (red→green not required — behavior-preserving pin, manual §8 /
  test mandate exception 1 — but the pin must demonstrably exercise the seek path:
  fixture has a file header AFTER the protect block).
- **Fixtures:** byte-patched archive containing a protect block followed by a normal
  file (§4.3).
- **No-go rows:** C7 (pins), C6 pattern adjacency.
- **Est. diff:** ~150 lines + fixture.

#### P0.3 — pin C13 + D1 with virtual >2 GB channels (no giant fixtures)

- **Objective:** unit-pin the two >2 GB behaviors without multi-GB files: a synthetic
  `SeekableReadOnlyByteChannel` reporting length > `Integer.MAX_VALUE` proves
  (C13) `RandomAccessInputStream` length stays `long`-clean past 2 GB, and (D1) the
  extract-to-`byte[]` path refuses/never allocates for `fullUnpackSize >
  Integer.MAX_VALUE` per its current contract (assert the *current* observable —
  probe it first, record it in the test name).
- **Files:** tests only (+ a small test util channel).
- **C++ refs:** none (junrar-original divergences).
- **Patterns:** manual §8.1; `divergences-no-go.md` C13/D1.
- **Acceptance:** `./gradlew test --tests "*LargeEntryContractTest"` green.
- **Fixtures:** none (virtual channel).
- **No-go rows:** C13, D1 (pins both).
- **Est. diff:** ~200 lines.

#### P0.4 — pin C15 class: signedness boundary tests + audit ledger

- **Objective:** boundary-value unit tests for the shift-sensitive primitives
  (`BitInput.getbits` with high-bit-set buffers, `FileNameDecoder` with ≥0x80 bytes,
  `RandomAccessStream` offsets ≥ 2^31) so a future `>>>`→`>>` typo goes red; plus a
  committed audit ledger (`docs/porting/reports/signedness-audit.md`) listing every
  `>>` in the 5 C15 files with its verdict (signed-on-purpose / unsigned-required).
- **Files:** tests; one report file.
- **C++ refs:** per-site C++ counterparts recorded in the ledger.
- **Patterns:** manual §4.2 (the canon table).
- **Acceptance:** `./gradlew test --tests "*SignednessTest"` green; ledger row count
  equals the output of the exact counting pipeline committed WITH the ledger —
  occurrences of `>>` that are not part of a `>>>`, code lines only:
  `grep -oE '>{2,3}' <file> | grep -cx '>>'` per file (comment/string hits triaged
  in the ledger with a `non-code` verdict column, so the row count stays mechanical
  while the classification is auditable). Pipeline + per-file outputs recorded in
  the ledger header.
- **Fixtures:** none (synthetic buffers).
- **No-go rows:** C15 (pins the class as far as unit-pinnable).
- **Est. diff:** ~300 lines.

#### P0.5 — S8 decision: PPM MaxMB policy (investigate → decide → red→green)

- **Objective:** replace the silent `MaxMB = 1` clamp (`ModelPPM.java:188-189`) with
  the unrar policy. **Decision (probe executed 2026-07-17, no longer open):**
  `d861246:model.cpp` `DecodeInit` reads `MaxMB=UnpackRead->GetChar()` (one byte,
  0–255) and calls `SubAlloc.StartSubAllocator(MaxMB+1)` **verbatim, with no
  additional cap** — the format-inherent ceiling is 256 MB. junrar honors
  `MaxMB+1` MB exactly the same. The 256 MB header-driven exposure is then
  bounded by the shared `maxDictionarySize` budget once P0.8 lands (default
  4 GiB ⇒ PPMd never gated by default — parity preserved; DoS-sensitive callers
  lowering the budget below `MaxMB+1` MB get the typed refusal).
- **Files:** `unpack/ppm/ModelPPM.java`, tests, fixtures.
- **C++ refs:** `d861246:model.cpp` (`RestartModelRare`, suballoc init);
  `2e71167:model.cpp` for baseline shape.
- **Patterns:** manual §5.3 "standing decision needed"; §8 red→green (behavior
  change: a legit MaxMB>1 archive currently truncates/fails — that failing
  extraction is the red run).
- **Acceptance:** new PPMd fixture with MaxMB > 1 (created with real rar,
  `-mc` text-compression forcing — §4.2) extracts byte-identical to unrar output;
  red run (clamped code) pasted in the chunk handoff; hostile fixture claiming max
  MaxMB completes within `@Timeout` bounds (allocation bounded by format).
- **Fixtures:** legit large-model PPMd archive + oracle payload; hostile max-MaxMB
  header (byte-patched).
- **No-go rows:** S8 (resolves its "pending investigation").
- **Est. diff:** ~250 lines + fixtures.

#### P0.6 — live-bug fixes T1, T4, T6 (one commit each)

- **Objective:** (T1) suffix the dead u32 sentinel — `unpSize == 0xffffffffL`
  (`FileHeader.java:127`) restoring the INT64MAX promotion of `arcread.cpp:148`;
  (T4) RAR3 KDF password bytes: replace platform-charset `password.getBytes()`
  (`Rijndael.java:47`) with the encoding unrar actually uses (probe
  `2e71167:crypt.cpp:188-286` — wide chars serialized 2 bytes/char, i.e. UTF-16LE);
  (T6) decode ANSI names with an explicit charset and port the whole-name-UTF-8
  branch of `arcread.cpp:189-194` (`FileHeader.java:157-165`).
- **Files:** `rarfile/FileHeader.java`, `crypt/Rijndael.java`, tests, fixtures.
- **C++ refs:** `2e71167:arcread.cpp:148,189-194`, `2e71167:crypt.cpp:188-286`.
- **Patterns:** manual §4.2 (sentinel literals), §6 T1/T4/T6.
- **Acceptance:** per-fix red→green with executed runs: T4 red = non-ASCII-password
  RAR3 fixture fails before, green after (fixture from real rar, §4.2); T6 red =
  UTF-8-named RAR3 fixture名 mis-decodes before; T1 pin = unit test on a crafted
  header with `unpSize` sentinel; scoped corpus run (§4.4 steps 1–3 — T1/T6 change
  header-level observables on corpus members; possibly-zero delta attached to PR).
- **Fixtures:** `password/rar3-nonascii-password.rar`, RAR3 UTF-8-name archive.
- **No-go rows:** C10 (filename validation must keep passing), C4 fallback behavior.
- **Est. diff:** ~350 lines + fixtures.

#### P0.7 — header-CRC verification scaffolding (T7) — unrar-tolerance contract

- **Objective:** introduce RAR3 header-CRC verification with **unrar's own
  format-specific contract** (probed 2026-07-17 at both `2e71167` and `d861246`;
  review B-S4 — an earlier revision wrongly specified blanket-fatal CRC32):
  - **Algorithm/width:** 16-bit — `~CRC32(header bytes from offset 2) & 0xffff`
    (`d861246:rawread.cpp` `GetCRC15`; identical math at 3.7.3,
    `2e71167:arcread.cpp:258` `~Raw.GetCRC(...)&0xffff`). NOT a 32-bit check.
  - **Coverage:** byte 2 to end of parsed header; comment-bearing headers
    (`LHD_COMMENT`) cover only the processed prefix (`CRCProcessedOnly`,
    `d861246:arcread.cpp:430-431`).
  - **Exemptions (skip verification):** SIGN headers, AV headers, old
    Unix-owner sub-blocks (`HEAD3_SIGN`/`HEAD3_AV`/`HEAD3_OLDSERVICE`+`UO_HEAD` —
    upstream: "Old AV header does not have header CRC properly set",
    `d861246:arcread.cpp:514-523`); ENDARC with rev-space recovery
    (trailing-7-zero-bytes rule, `d861246:arcread.cpp:525-535`).
  - **Tolerance:** a FILE/NEWSUB mismatch is **recorded and processing
    continues** (upstream `BrokenHeader=true` + warning,
    `d861246:arcread.cpp:432-445`): junrar marks the header — new public
    `FileHeader.isBrokenHeader()` accessor — and logs a warning; **extracting**
    a broken-header entry throws `CorruptHeaderException` (upstream analog:
    `ReadSubData` refuses on `BrokenHeader`, `d861246:arcread.cpp:1487-1492`).
    Other unencrypted header types: record + continue. **Encrypted headers with
    a bad CRC are fatal** — `CorruptHeaderException` at open (upstream returns 0
    and stops, both eras).
  - **Divergence note (conscious, recorded):** junrar throws at *extract time*
    for a broken file header where the unrar CLI merely warns and lets the data
    CRC decide; junrar has no CLI warning channel, and silent garbage extraction
    would be worse. Everything else matches unrar tolerance — this chunk is
    "toward unrar semantics" plus one named, narrower-scoped strictness.
  This is the infrastructure RAR5 reuses in M3.2 (full-CRC32 variant, same
  record-vs-fatal split). Behavior change on corrupt archives — gate on corpus
  evidence: run the regression corpus before merge; any legit archive newly
  failing is investigated before the chunk lands.
- **Files:** `rarfile/BaseBlock.java`, `rarfile/FileHeader.java`, `Archive.java`
  read loop, tests, fixtures.
- **C++ refs:** `2e71167:arcread.cpp:258-271`; `d861246:arcread.cpp:431-445`
  (file-header check), `:514-545` (generic check + exemptions + encrypted-fatal
  split); `d861246:rawread.cpp` (`GetCRC15`).
- **Patterns:** manual §6 T7, §4.9 (reuse `CorruptHeaderException`; no `setChannel`
  filter change needed since it already rethrows CorruptHeader).
- **Acceptance:** byte-patched bad-CRC **file** header → archive OPENS and lists,
  the header reports `isBrokenHeader()`, extracting that entry →
  `CorruptHeaderException` through all four public surfaces (manual §8.1);
  byte-patched bad-CRC **encrypted-header** fixture → `CorruptHeaderException`
  at open; exemption rows: SIGN/AV/old-UO fixtures with a wrong stored CRC still
  parse (byte-patched, one each); corpus run delta reviewed and attached to the
  PR (expected: only genuinely corrupt members change outcome).
- **Fixtures:** `abnormal/bad-header-crc.rar`, `abnormal/bad-crc-enc-headers.rar`,
  exemption-class fixtures (all byte-patched).
- **No-go rows:** S1–S4 (same read loop — their tests must stay green), C9.
- **Est. diff:** ~300 lines + fixtures.

#### P0.8 — `ArchiveOptions`: the construction-time configuration path (API)

- **Objective:** one construction-time configuration object covering password and
  resource limits — the API contract of §5.2 (review B-S7; designed here, consumed
  by M3.4/M3.6/M4). Deliverables:
  - **`ArchiveOptions`** (immutable, builder): `password(char[])` (defensive copy
    at the builder; `password(String)` convenience delegates),
    `maxDictionarySize(long bytes)` — default **4 GiB** = unrar 7.2.7's own
    default extraction limit (`d861246:options.cpp:13` `WinSizeLimit=0x100000000`;
    raised by callers exactly like unrar's `-mdx`), `unrarCallback(...)`.
    `build()` validates (`maxDictionarySize > 0`).
  - **New canonical constructors:** `Archive(VolumeManager, ArchiveOptions)`,
    `Archive(File, ArchiveOptions)`, `Archive(InputStream, ArchiveOptions)`. No
    new String/char[] overload pairs anywhere — probe 2026-07-17: javac already
    rejects `new Archive(file, null)` as ambiguous between the existing
    `(File,String)`/`(File,UnrarCallback)` ctors ("reference to A is ambiguous");
    adding `char[]` twins would widen that hole, `ArchiveOptions` is a distinct
    type and adds no new null-ambiguity beyond the pre-existing one.
  - **`Junrar` facade:** one `ArchiveOptions` overload per source shape
    (`extract(File, File, ArchiveOptions)`, `extract(InputStream, File,
    ArchiveOptions)`, `extract(VolumeManager, File, ArchiveOptions)`,
    `extract(String, String, ArchiveOptions)`) — the options path reaches
    one-shot extraction (the old setter idea could not).
  - **Password hygiene contract:** `Archive` copies the password `char[]` at
    construction and **wipes its copy in `close()`** (`Arrays.fill('\0')`);
    internal plumbing carries `char[]` (RAR3 UTF-16LE and RAR5 UTF-8 encode
    straight from `char[]`); the `String` entry points remain (ubiquitous) with
    Javadoc noting a `String` password cannot be wiped — prefer `char[]`.
    `ArchiveOptions` retains its defensive copy for its own lifetime
    (documented: scope the options object as narrowly as the password). Honest
    JVM substitute for upstream secpassword (`unrar-delta-map.md` §5.5).
  - **Deprecation path (decided):** existing constructors and `Junrar` methods
    stay **undeprecated** in the RAR5 major (they delegate to the options path;
    churn for every consumer buys nothing); Javadoc cross-references
    `ArchiveOptions`. Revisit deprecation no earlier than the next major.
  - **Budget wiring (first consumer):** `maxDictionarySize` gates the PPMd
    suballocator target (P0.5): `MaxMB+1` MB > budget → new
    **`UnsupportedDictionarySizeException`** (introduced here; `setChannel`
    filter decision NO — extraction-time, §5.3). Default 4 GiB > 256 MB format
    ceiling ⇒ no behavior change by default.
- **Files:** new `ArchiveOptions.java`, `Archive.java` (ctors + close-wipe),
  `Junrar.java`, `exception/UnsupportedDictionarySizeException.java`,
  `unpack/ppm/ModelPPM.java` (budget check), tests.
- **C++ refs:** `d861246:options.cpp:13`, `d861246:extract.cpp:1758`
  (`CheckWinLimit`), `d861246:uiconsole.cpp` (`uiDictLimit` returns false = refuse
  — the non-interactive analog is the typed exception).
- **Patterns:** manual §3 (house API style), §4.9 (new exception → filter decision
  recorded).
- **Acceptance:** options-path tests: password via `char[]` opens an encrypted
  RAR3 fixture (existing `password/` fixtures); wipe-on-close asserted (the
  array handed to `Archive` internals is zeroed after `close()` — test hook);
  builder defensive copy asserted (mutating the caller's array after `build()`
  does not affect extraction); `Junrar.extract(..., options)` end-to-end row;
  PPMd budget row: P0.5's MaxMB>1 fixture with `maxDictionarySize` set below
  `MaxMB+1` MB → `UnsupportedDictionarySizeException`, and with default options
  extracts unchanged (before-state asserted); existing ctor surface untouched
  (compile + suite green).
- **Fixtures:** none new (reuses `password/` + P0.5's PPMd fixture).
- **No-go rows:** Java-API class-c rows (`divergences-no-go.md` — existing entry
  points preserved).
- **Est. diff:** ~450 lines.
- Runs **after P0.5** (consumes its fixture); parallel-safe with P0.6/P0.7.

#### P0.9 — CI: JRE 8 runtime extraction smoke (provider behavior gate)

- **Objective:** prove Java-8 *runtime* behavior, not just `options.release=8`
  bytecode (review B-S8; ci.yml probed 2026-07-17 — JDK 21 only, 3 OS). New
  `jre8-smoke` job in `ci.yml`: build the jar with the normal JDK 21 toolchain,
  then set up **Temurin 8** (`actions/setup-java`, ubuntu only — provider
  behavior is JRE-, not OS-, dependent) and run a **framework-free**
  `public static void main` smoke runner (compiled with `--release 8`; JUnit 5
  needs newer JDKs, so the suite itself cannot run — the runner is deliberately
  minimal) against committed fixtures: plain RAR3 extraction + **an AES
  (password) RAR3 fixture** (exercises `Cipher AES/CBC/NoPadding` through the
  real JRE 8 JCE), asserting extracted SHA-256s. M3.11 extends the fixture list
  with RAR5 plain + encrypted (PBKDF2/`Mac HmacSHA256` path) rows.
- **Files:** `.github/workflows/ci.yml`, `src/jre8smoke/` (or equivalent minimal
  source set — one Main class), no production code.
- **C++ refs:** none.
- **Patterns:** CLAUDE-grade red canary: the job proves its red path once at
  introduction (feed a wrong expected digest, watch the job fail) — evidence in
  the chunk handoff, not committed.
- **Acceptance:** CI run with the job green on the branch; red-canary run
  demonstrated in-session; job runtime < 2 min (it is one jar + two
  extractions).
- **Fixtures:** existing (`test.rar`, `password/` AES fixture).
- **No-go rows:** none (CI-only).
- **Est. diff:** ~150 lines.
- Parallel-safe with everything after P0.6 (needs an AES fixture that already
  exists in-tree).

**Trap triage ledger (owner-visible dispositions for the rest of §6):** T3 —
**defer to M2**, which deletes the interpreter wholesale; fixing individual opcodes
first would be churn against code scheduled for deletion (M2.2 records the deletion
as the fix). T5 — **defer indefinitely** (RAR3 KDF perf wart, output-correct;
PBKDF2 in M3 does not inherit it; optional KDF3Cache is out of scope as pure perf).
T8 — ignore (dead scaffolding, manual reconciliation note). T9 — **fixed in M1.3**
(the 8192 cap is upstream's own answer). T10 — leave (provenance breadcrumbs).

### Phase M1 — RAR3 hardening to 7.2.7 semantics (no format work)

Target: the pinned guard list (`unrar-delta-map.md` §3, manual §9.3-M1). Context
diffs against 4.2.4 (`5db30e6`) where 3.7.3-shaped context is needed.

#### M1.1 — rebuild the PPMd heap byte-diff harness (scaffolding)

- **Objective:** revive the dump-and-compare harness (manual §4.13.4:
  `AnalyzeHeapDump.java`, commented `dumpHeap()` `SubAllocator.java:387-402`) as a
  usable test utility, and record a golden heap dump for the existing PPMd fixtures
  BEFORE any guard lands.
- **Files:** test utilities only (+ un-commenting/porting `dumpHeap` into test
  scope).
- **C++ refs:** none (junrar tooling).
- **Patterns:** manual §4.13.4.
- **Acceptance:** documented one-command run producing byte-identical dumps across
  two consecutive runs; golden dumps committed for ≥1 PPMd fixture.
- **Fixtures:** P0.5's `-mc`-forced PPMd fixture (M1.1 runs after P0.5 — no
  fixture-discovery step; the in-tree suite has no confirmed PPMd member).
- **No-go rows:** none directly; enables S8-adjacent safety.
- **Est. diff:** ~250 lines.

#### M1.2 — PPMd corrupt-input guards (3.9.1, 3.9.8, 5.6.1)

- **Objective:** port, in release order: range-coder `count >= scale → false` in
  decodeSymbol1/2 (3.9.1 `a0d5a16`); `SafePPMDecodeChar` wrapper (3.9.8 `48bbbd3`,
  now `d861246:unpack30.cpp:4`); `ps[MAX_O]` overflow guards in
  `CreateSuccessors`/`decodeSymbol2` (5.6.1 `9c55010`). **Error-latch shape
  (decided, not implementer-chosen — review B-S9): keep junrar's `ppmError`
  latch**; do NOT adopt 3.7.7+ `CleanUp()`+`BLOCK_LZ`, and never mix the two
  shapes (manual §5.3). Grounding (probed 2026-07-17): the latch already exists
  and is engine-entry-checked at `Unpack.java:74` (field), `:168` (entry check),
  `:197` (set site), `:622` (reset) — the guards land as new set-sites on the
  existing latch, the smallest correct diff.
- **Files:** `unpack/ppm/ModelPPM.java`, `RangeCoder.java`, `unpack/Unpack.java`
  (PPM call sites), tests.
- **C++ refs:** `git -C ~/git/unrar diff 2e71167 d861246 -- model.cpp suballoc.cpp`
  (the 526-line delta, `unrar-delta-map.md` §3.1); `d861246:unpack30.cpp:4`.
- **Patterns:** manual §4.2 (masked-long uint domain), §4.5 (bool protocol +
  poisoned latch), §4.13 (comment-anchored lines, `// Bug fixed` markers).
- **Acceptance:** heap byte-diff harness (M1.1) shows zero drift on legit fixtures;
  hostile PPMd fixtures (byte-patched escape/reset states, §4.3) →
  typed exception, no OOB/hang; red→green per guard (red = crafted corrupt input
  crashing/looping the current code — if a guard's red cannot be produced, record
  the attempt and mark the guard defensive, manual §8); scoped corpus run
  (§4.4 steps 1–3 — corrupt corpus members may flip to typed exceptions; delta
  attached).
- **Fixtures:** corrupt-PPMd set (byte-patched from M1.1's fixture).
- **No-go rows:** S8 (P0.5 decision must survive), C15 (audit new `>>`).
- **Est. diff:** ~400 lines.

#### M1.3 — LZ/filter/channel limits (5.3.1, 5.3.6, 5.5.4)

- **Objective:** `MAX3_UNPACK_FILTERS 8192` cap on the RAR3 filter stack (also fixes
  T9 unbounded `prgStack` growth); unpack15 `HuffDecode` `FlagsPlace` index guard;
  `MAX3_UNPACK_CHANNELS 1024` delta-filter channel cap.
- **Files:** `unpack/Unpack.java` (addVMCode/prgStack), `unpack/Unpack15.java`,
  tests.
- **C++ refs:** 5.3.1 `34b8c34`, 5.3.6 `dd09d7d`, 5.5.4 `78c231f` (grep the blobs:
  `git -C ~/git/unrar show <hash>:unpack.hpp | grep MAX3`);
  `d861246:unpack30.cpp` for final shape.
- **Patterns:** manual §4.5, §6 T9.
- **Acceptance:** hostile fixtures — filter-flood archive and >1024-channel delta
  archive (byte-patched) — complete quickly with typed exception or clean truncation
  matching unrar 7.2.7's observable behavior (record unrar's actual behavior in the
  test); existing filter fixtures stay green; scoped corpus run (§4.4 steps 1–3 —
  hostile corpus members may flip outcomes; delta attached).
- **Fixtures:** `abnormal/filter-flood.rar`, `abnormal/channel-flood.rar`,
  `abnormal/flagsplace-oob.rar` (§4.3 byte-patched).
- **No-go rows:** C11 (VM enum tests stay green), C2 (audio init untouched), C15
  (`Unpack15` is a C15-class file — every touched `>>` audited, ledger updated).
- **Est. diff:** ~300 lines.

#### M1.4 — FirstWinDone distance validation (7.0.3)

- **Objective:** port `FirstWinDone` first-window-wrap tracking and the CopyString
  reject rule (`!FirstWinDone && Distance>UnpPtr || Distance>MaxWinSize ||
  Distance==0`) into all legacy paths (15/20/29) — deterministic corrupt-archive
  behavior (`unrar-delta-map.md` §2.8).
- **Files:** `unpack/Unpack15.java`, `Unpack20.java`, `Unpack.java`, tests.
- **C++ refs:** 7.0.3 `5faaa45` diff (`git -C ~/git/unrar diff 3b91101^..5faaa45
  -- unpack*.cpp | cat` — note unpack15 took the largest rework, 107 lines).
- **Patterns:** manual §4.2 (the C1 wrap-guard row — same hazard family), §4.5.
- **Acceptance:** P0.1's C1 test stays green byte-identical; new
  never-written-window hostile fixtures → typed exception; audio/solid suites green;
  scoped corpus run (§4.4 steps 1–3 — corrupt members may flip to deterministic
  typed exceptions; delta attached).
- **Fixtures:** byte-patched distance-into-void archives per engine (15/20/29).
- **No-go rows:** **C1 (its file is edited — pin landed in P0.1), C2, C15.**
- **Est. diff:** ~350 lines.

#### M1.5 — encname bounds rewrite + method-36 drop

- **Objective:** mirror the fully bounds-checked `encname.cpp` rewrite into
  `FileNameDecoder` (every index access preceded by a bounds check, growable
  output); drop method 36 ("alternative hash" experiment, dropped upstream in 5.0.0
  — `unrar-delta-map.md` §3) with corpus evidence that no corpus member regresses.
- **Files:** `rarfile/FileNameDecoder.java` (path probed 2026-07-17:
  `grep -rn "class FileNameDecoder" src/main` → `rarfile/FileNameDecoder.java:21`),
  `Unpack.java` dispatch, tests.
- **C++ refs:** `d861246:encname.cpp` vs `2e71167:encname.cpp`;
  method dispatch `d861246:unpack.cpp` (`DoUnpack`).
- **Patterns:** manual §4.7, §4.2 (C15 applies to FileNameDecoder — P0.4 pins).
- **Acceptance:** hostile encoded-name fixtures (truncated/oversized encname
  streams) → clean typed failure; unicode fixture suite (`gh108.rar`,
  `unicode.rar`) green; corpus run shows method-36 members (if any) flip to a typed
  unsupported error — reviewed.
- **Fixtures:** byte-patched encname fixtures.
- **No-go rows:** C10, C15, C4.
- **Est. diff:** ~300 lines.

### Phase M2 — RarVM replacement (unrar 5.5.1 shape)

#### M2.1 — fingerprint recognition + native transform wiring

- **Objective:** replace VM program interpretation with the 5.5.1 recognition model:
  fingerprint incoming VM bytecode by {length, CRC32} against the 6-entry table
  (E8 53/0xad576887, E8E9 57/0x3cd7e57e, ITANIUM 120/0x3769893f,
  DELTA 29/0x0e06077d, RGB 149/0x1c2c5dc8, AUDIO 216/0xbc85e701 —
  `d861246:rarvm.cpp` `Prepare()`); recognized → the already-existing native
  transforms; unrecognized (incl. UPCASE) → no-op filter emitting unfiltered bytes,
  exactly unrar ≥5.5.1's behavior. Stream *parsing* of VM code bytes survives
  (manual §5.4).
- **Files:** `unpack/vm/RarVM.java` (recognition path), `unpack/Unpack.java`
  (readVMCode/addVMCode), tests.
- **C++ refs:** `1522ee0:rarvm.cpp` (the gutted 351-line shape),
  `d861246:rarvm.cpp`.
- **Patterns:** manual §5.4, §4.6 (enum lookup trap — C11).
- **Acceptance:** red→green: a custom-VM-program fixture that currently produces
  interpreter output flips to no-op output matching `unrar 7.2.7 x` on the same
  archive (oracle run pasted); the 6 canonical filters each covered by a
  fixture extracting byte-identical to unrar (audio fixtures already cover
  AUDIO/DELTA paths); corpus run delta reviewed (expected: only archives carrying
  non-canonical programs change output — matching upstream).
- **Fixtures:** per-filter fixture set (§4.3); one custom-program fixture
  (byte-patched or crafted with old WinRAR default filters).
- **No-go rows:** **C11 — consciously superseded in execution while the enum lookup
  test survives:** `VMStandardFilters.findFilter` and its `@EnumSource` test remain
  (UPCASE stays a named constant); only *execution* semantics change, matching
  upstream. The PR must state this supersession explicitly.
- **Est. diff:** ~450 lines.

#### M2.2 — delete the interpreter (T3 surface removal)

- **Objective:** remove raw VM execution (opcode switch, `VMCmdFlags`, opcode enums
  unreferenced after M2.1), shrinking `RarVM` to recognition + native transforms;
  this deletion IS the T3 disposition (the precedence/flag/SAR bugs leave with the
  code).
- **Files:** `unpack/vm/*` (deletions), tests updated.
- **C++ refs:** deletion mirrors `1522ee0` (rarvmtbl.cpp removed upstream).
- **Patterns:** manual §4.13.3 (refactor only after validation — M2.1's oracle
  fixtures are the validation).
- **Acceptance:** M2.1's fixture suite green byte-identical; jacoco shows the
  deleted paths had no surviving callers (`grep -rn` audit in PR); ArchUnit/build
  green.
- **Fixtures:** none new.
- **No-go rows:** C11 (per M2.1 note), C15 (files deleted, ledger updated).
- **Est. diff:** ~-1500/+100 lines (pure deletion is exempt from the 600-line cap;
  reviewable because it is subtractive).

### Phase M3 — RAR5 core (target unrar 6.2.12 `8f437ab`)

Reading order per `unrar-delta-map.md` §5.3: `headers5.hpp` →
`arcread.cpp:555-1257` → `crypt5.cpp` → `unpack50.cpp` → `ulinks.cpp`/`extinfo.cpp`.
**The `UnsupportedRarV5Exception` gate at `Archive.java:359` stays until M3.11.**
Archive-level acceptance for M3.3–M3.10 runs through **the pre-gate test harness, a
named M3.2 deliverable** (see M3.2): a package-private, test-only `Archive` factory
that suppresses the V5 throw. Implementers never improvise a bypass and never lift
the gate early (the corpus flips exactly once, at M3.11, where the harness is
deleted in the same commit). All new RAR5 classes are brand-new code
(manual/test-mandate exception 2: no red run against the void; tests still ship
asserting real behavior).

#### M3.1 — vint reader + signature dispatch skeleton + SFX signature scan

- **Objective:** shared vint reader (base-128 LE, continuation bit 0x80, overflow
  guard — max 10 bytes for the general reader; the RAR5 *header-size* field is
  additionally capped at 3 vint bytes by M3.2's contract); RAR format detection
  (`52 61 72 21 1A 07` + version byte: `00`→RAR15, `01`→RAR50, `02..04`→ graceful
  `UnsupportedRarVersionException` (new)); a `RarFormat` enum tag on `Archive`
  with a **public accessor** (`Archive.getFormat()`); **regression-record model
  fix (review B-S2, must land before M3.11):** `ArchiveRecord.fromArchive`
  hardcodes `isRarV5=false` and `fromException` derives it solely from
  `UnsupportedRarV5Exception` (probed 2026-07-17), so post-flip successful-RAR5
  and wrong-password-RAR5 records would be falsely non-RAR5 — `fromArchive` now
  reads `archive.getFormat()`, and `fromException` gains the file path and
  derives `isRarV5` from the 8-byte signature (`52 61 72 21 1A 07 01 00`) probe
  of the input file. Zero corpus churn now (pre-flip, no RAR5 archive
  constructs successfully, and the 345 exception records keep `isRarV5: true`
  by the signature probe); **SFX support** (review F4 —
  previously an unstated hole): a bounded signature scan for the marker within the
  first `MAXSFXSIZE` = 0x400000 (4 MB, `d861246:rardefs.hpp`; was 512 KB at 3.7.3)
  bytes before MarkHeader parse, benefiting RAR3 and RAR5 SFX alike; no signature
  within the bound → `BadRarArchiveException`.
- **Files:** new `io/VInt.java` (decided — `io/Raw.java` is stateless
  static-offset helpers, a poor fit for a position-advancing variable-length
  read); `rarfile/MarkHeader.java`; `Archive.java` (detection + scan — V5 gate
  untouched); new exception; `regressionTest` `ArchiveRecord.java`/
  `RegressionTest.java`; tests.
- **C++ refs:** `d861246:archive.cpp:100-126` (`IsSignature`),
  `d861246:rawread.cpp` (`GetV`/`RawGetV` overflow handling),
  `d861246:rardefs.hpp` (`MAXSFXSIZE`).
- **Patterns:** manual §4.7 (Raw house style — no ByteBuffer), §4.9 (new exception →
  `setChannel` filter decision recorded: `UnsupportedRarVersionException` must be
  rethrown, add to the filter).
- **Acceptance:** vint unit tests: 1..10-byte values, boundary 2^63, overflow →
  exception, hostile continuation-bit floods; signature tests for versions
  00/01/02/03/04 via crafted byte arrays; SFX fixtures (stub + archive
  concatenation, RAR3 and RAR5 variants) open and list; hostile row: ≥4 MB of
  signature-free garbage → `BadRarArchiveException` within bounded time.
- **Fixtures:** SFX fixtures built by concatenating an arbitrary stub blob with
  existing fixtures (scripted, §4.3-adjacent — concatenation is a *valid* SFX
  shape); tiny future-version fixture (byte-patched marker).
- **No-go rows:** S3 (mark validity tests stay green), S1 (scan is bounded — no
  unbounded read/alloc).
- **Est. diff:** ~450 lines.

#### M3.2 — RAR5 block-header framework (CRC-verified, loop-guarded)

- **Objective:** the RAR5 read loop: `CRC32(4) | vint HeadSize | vint Type | vint
  Flags [| vint ExtraSize][| vint DataSize]`. **Header-size boundary contract
  (review B-S5, verified 2026-07-17 at `d861246:arcread.cpp:634-668` +
  `archive.hpp:24`):** the first read is exactly 7 bytes (4 CRC + at most **3**
  header-size vint bytes — upstream comment: "Header size must not occupy more
  than 3 variable length integer bytes resulting in 2 MB maximum header size
  (MAX_HEADER_SIZE_RAR5)", = `0x200000`); a size vint spilling past 3 bytes, a
  zero BlockSize, or total `HeaderSize < SIZEOF_SHORTBLOCKHEAD5` →
  `CorruptHeaderException` **before any tail allocation** — the header buffer is
  allocated only after the ≤ 2 MB bound holds (`safelyAllocate` capped at
  `MAX_HEADER_SIZE_RAR5`, not a generic cap). **Header CRC32 verified** with
  P0.7's infra and P0.7's record-vs-fatal split, RAR5 shape
  (`d861246:arcread.cpp:674-696`): full CRC32 over bytes 4..end (`GetCRC50`);
  unencrypted mismatch → header marked broken, parse attempts to continue
  (upstream: "Report, but attempt to process"), extraction of a broken-header
  entry throws; **encrypted** mismatch → fatal at open (upstream sets
  `FailedHeaderDecryption`, returns 0) — M3.4 maps it onto
  `WrongPasswordException` (indistinguishable from a wrong key under CBC).
  `ExtraSize >= HeadSize` → `CorruptHeaderException`
  (`d861246:arcread.cpp:700-707`); HFL_SKIPIFUNKNOWN honored; unknown
  non-skippable → `CorruptHeaderException`; `safelyAllocate` on every tail;
  `processedPositions` loop guard + explicit seek-past-data (S1/C6 parity);
  eager-parse into the same `headers` list. **Deliverable: the pre-gate test
  harness** — a package-private static factory on `Archive` (tests live in
  `com.github.junrar`, so package-private suffices; no system property, no
  production-reachable flag) that constructs an `Archive` with the
  `Archive.java:359` V5 throw suppressed; used by every M3.3–M3.10 archive-level
  acceptance line; deleted in M3.11's gate-lift commit.
- **Files:** new `rarfile/rar5/` package (`Rar5BaseBlock` etc.), `Archive.java`
  RAR5 branch, tests.
- **C++ refs:** `8f437ab:arcread.cpp:555-…` (`ReadHeader50`),
  `d861246:headers5.hpp` (flag values).
- **Patterns:** manual §5.1 (new-block recipe + RAR5 hook points), §4.6 (wire-value
  enums with null-returning `findX` + `@EnumSource` test — the C11 lesson), §4.8.
- **Acceptance:** header-level unit tests parsing crafted blocks; hostile rows:
  bad CRC (unencrypted → broken-marked + extract-time throw; encrypted → fatal
  at open), HeadSize overflow/underflow, **boundary rows (B-S5): size vint of
  exactly 3 bytes at max value (2 MB − ε) accepted-shape, size vint of 4 bytes →
  reject, BlockSize 0 → reject, declared size > `MAX_HEADER_SIZE_RAR5` → reject
  with no allocation (asserted via allocation-free path, e.g. the reject fires
  before `safelyAllocate`)**, ExtraSize ≥ HeadSize, vint flood, truncated
  mid-header, repeated position → typed exceptions (direct-parse tests + the
  pre-gate harness; the manual §8.1 four-surface pattern re-runs at M3.11);
  harness demonstrably suppresses only the V5 throw (a RAR3 archive through the
  harness behaves identically to the public path — asserted).
- **Fixtures:** synthetic byte arrays; byte-patched real RAR5 fixtures (`rar5.rar`
  exists in-tree).
- **No-go rows:** S1, S3, S4 analogs re-applied to the new path (each gets a RAR5
  hostile fixture row).
- **Est. diff:** ~500 lines.

#### M3.3 — main/end/crypt headers + file/service headers with all extras

- **Objective:** parse HEAD_MAIN (+MHFL flags, MHEXTRA_LOCATOR skipped consciously,
  MHEXTRA_METADATA), HEAD_ENDARC (+EHFL_NEXTVOLUME), HEAD_CRYPT (fields only:
  version==0 check, Lg2Count **> 24 → reject** (`CRYPT5_KDF_LG2_COUNT_MAX 24`,
  `d861246:crypt.hpp:20`), Salt16, pswcheck presence); HEAD_FILE /
  HEAD_SERVICE with every FHEXTRA record: CRYPT, HASH (Blake2sp digest bytes),
  HTIME (Unix/Windows, ns flag), VERSION, REDIR (type + flags + target), UOWNER,
  SUBDATA; UTF-8 names via explicit `StandardCharsets.UTF_8`; map onto the existing
  `FileHeader` surface (unified model — `unrar-delta-map.md` §4.2) so
  `getFileName()/getFullUnpackSize()/isDirectory()/…` behave for RAR5 entries.
- **Files:** `rarfile/rar5/*` — **shape decided (review B-S9): loader into the
  unified `FileHeader`, no parallel `FileHeader5` type.** A package-private
  `rarfile/rar5/Rar5FileHeaderReader` parses the RAR5 wire format and populates
  the existing `FileHeader` (plus its new RAR5-fact accessors, §5.1) — mirroring
  upstream's one-`FileHeader`-model/per-format-loader topology
  (`unrar-delta-map.md` §4.2) and keeping every existing consumer accessor
  working unmodified; `Rar5MainHeader` for HEAD_MAIN/HEAD_CRYPT/HEAD_ENDARC
  facts. Tests.
- **C++ refs:** `8f437ab:arcread.cpp` (`ReadHeader50` file branch,
  `ProcessExtra50` at `d861246:arcread.cpp:982`), `d861246:headers5.hpp`
  (FHFL/FHEXTRA values), `unrar-delta-map.md` §2.3–2.5.
- **Patterns:** manual §5.1 (RAR5 hooks: salt16 as new fields — never widen RAR3
  SALT_SIZE 8; service headers mirror NEWSUB machinery, not legacy SUB_HEAD), §4.6.
- **Acceptance:** per-record unit tests incl. hostile rows (oversized name, zero
  name, non-UTF-8 bytes, REDIR target with traversal separators — parsed but
  flagged, extraction-side guard comes in M3.10, record cross-ref); listing tests on
  real fixtures (dirs, times incl. ns, uowner, version, comment service header) —
  archive-level listing via the pre-gate harness (M3.2).
- **Fixtures:** §4.2 variant matrix rows (times/uowner/version/comment/links).
- **No-go rows:** S4 analog (nameSize<=0), C10 (RAR5 names feed the same
  `isFilenameValid` gate).
- **Est. diff:** ~600 lines (largest header chunk; split VERSION/UOWNER/SUBDATA into
  a follow-up commit inside the chunk if it crowds 600).
- **Parallel-safe** with M3.4/M3.5 after M3.2.

#### M3.4 — RAR5 crypto: KDF, pswcheck, header + data decryption

- **Objective:** RAR5 KDF — **contract corrected per review B-S1 (the previous
  revision's c/c+16/c+17 was wrong; so was its Appendix-A F7 predecessor):** a
  **hand-rolled single-pass PBKDF2 loop over `Mac.getInstance("HmacSHA256")`**,
  the exact upstream shape (`d861246:crypt5.cpp:105-125`): U1 = HMAC(pwd,
  salt‖00000001), then segment counts `{Count-1, 16, 16}` over ONE running XOR
  accumulator, snapshotting after each segment — **AES key at `Count` = 2^Lg2Count
  total iterations, HashKey at `Count+16`, PswCheck source at `Count+32`**. NOT
  `SecretKeyFactory "PBKDF2WithHmacSHA256"` (Java 8 requires NO PBKDF2
  SecretKeyFactory of any flavor — §2.1, doc-verified; SunJCE shipping one is a
  provider accident) and NOT three provider calls (≈3× the HMAC work of the
  single pass); reuse one initialized `Mac` instance across iterations (the Java
  analog of upstream's cached inner/outer contexts, `ICtxOpt`/`RCtxOpt`).
  PswCheck folds V2 32→8 bytes (`PswCheck[i % 8] ^= V2[i]`,
  `d861246:crypt5.cpp:177-182`); pswcheck verification (+SHA-256-truncated 4-byte
  csum) → new `WrongPasswordException` **added to the `setChannel` rethrow filter**
  (manual §4.9 trap — stated in PR), thrown equally for a **missing** password on
  header-encrypted archives (unrar's own passwordless `-hp` open reports
  "Incorrect password" — probed 2026-07-17) and for an encrypted-header CRC
  failure after decryption (M3.2); header decryption: AES-256-CBC via
  `RawDataIo.setCipher` with per-header 16-byte IV, align-to-16 applied on BOTH the
  read path and `getHeaderSize(encrypted)` accounting (manual §4.12); per-file
  decryption via the `ComprDataIO.init(FileHeader)` FHEXTRA_CRYPT branch;
  `ConvertHashToMAC` (HMAC-SHA256 with HashKey) for HASHMAC-protected checksums;
  4-entry KDF cache (5.2.1 `f0474dc`; `KDF5Cache[4]`, `d861246:crypt.hpp:88` —
  cheap, needed for multi-file archives); password bytes = UTF-8 from the
  `char[]` (T4 lesson; upstream `WideToUtf`, `d861246:crypt5.cpp:158-160`).
- **Files:** new `crypt/Rar5Crypt.java` (pinned — sibling of `crypt/Rijndael.java`),
  `io/RawDataIo.java`, `unpack/ComprDataIO.java`,
  `exception/WrongPasswordException.java`, `Archive.java` filter list, tests.
- **C++ refs:** `d861246:crypt5.cpp` (`hmac_sha256` :1-80, `pbkdf2` :85-131,
  `SetKey50` :134-191, `ConvertHashToMAC` :193, `TestPBKDF2` :219-235);
  `unrar-delta-map.md` §2.4/§2.6 (corrected 2026-07-17).
- **Patterns:** manual §4.11 (corrected 2026-07-17), §4.12 (align-16 both paths),
  §4.9.
- **Acceptance:** KDF unit tests — **mandated KATs, all six independently
  recomputable with any PBKDF2-HMAC-SHA256 (verified 2026-07-17: Python
  `hashlib.pbkdf2_hmac` at counts c/c+16/c+32 reproduces upstream's three
  `TestPBKDF2` Key vectors byte-exact, proving the snapshot semantics):**

  ```text
  ("password","salt",c=1):
    Key(c)      120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b
    V1(c+16)    ae4ee2d75b78ca4b52c833c7a2c46aa5afcda84910c816129180c7db94d12828
    V2(c+32)    8d0d186304625d4fdf487b4f929c0224d6e26bbd33a698e446fdb17e876f6975
  ("password","salt",c=4096):
    Key(c)      c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a
    V1(c+16)    f5813aeb14cd6ad5a2714b4f82f277a5c9d3b5441199fd731cebf707eaf39f12
    V2(c+32)    9de7fbbc3868a043dd5a14e254be45a9afb7feaca0ce4f4a6e2713ff8e7bea7f
  ("just some long string pretending to be a password",
   "salt, salt, salt, a lot of salt",c=65536):
    Key(c)      080fa31d422db047839bce3a3bce4951e262b9ff762f57e9c47196ce4b6b6ebf
    V1(c+16)    62c33556fc0d8238ff9a11f7d1463dbf8eb384e2f5227a060224444a7647d0f7
    V2(c+32)    d85dd4f96fc85298a93e2af7cf0d6555b835856e11dce700604f7fad2808a119
  ```

  (Key rows = upstream `TestPBKDF2` Res1/Res2/Res3 verbatim; V1/V2 rows replace
  the previously planned "instrumented unrar run" — plain PBKDF2 at the higher
  counts needs no instrumentation.) A one-call-dkLen=96 implementation and a
  c/c+16/c+17 implementation both FAIL these vectors; wrong password on a
  pswcheck archive → `WrongPasswordException` (not CRC failure); passwordless
  open of a `-hp` fixture → `WrongPasswordException`; `-p`/`-hp` fixtures LIST
  correctly and pswcheck verifies — all via the pre-gate harness (M3.2);
  Lg2Count 25 → typed reject; non-ASCII UTF-8 password fixture green. **Deferred rows (carried in the
  handoff, executed once their dependency lands): "extracts oracle-identical"
  needs M3.6/M3.7 (decode) — runs in M3.7's acceptance; the HASHMAC row needs
  M3.5 (Blake2sp) — runs after M3.5, latest at M3.11.**
- **Fixtures:** §4.2 rows: `-p`, `-hp`, non-ASCII password, wrong-password
  (same fixture, wrong input), HASHMAC (encrypted + Blake2).
- **No-go rows:** Java-API rows (password/header-encrypted support must extend, not
  fork, the existing API — `divergences-no-go.md` class c).
- **Est. diff:** ~550 lines.

#### M3.5 — Blake2s/Blake2sp + DataHash abstraction

- **Objective:** pure-Java Blake2s core + 8-lane Blake2sp tree composition (§2.2
  decision); a minimal `DataHash`-style seam (CRC32 | Blake2sp) at the
  `ComprDataIO.unpWrite` CRC branch, preserving the complement-at-observation CRC
  trick untouched (manual §5.5).
- **Files:** new `crypt/blake2/` (2–3 classes), `unpack/ComprDataIO.java`
  (hash seam), tests.
- **C++ refs:** `8f437ab:blake2s.cpp`, `blake2sp.cpp` (skip `blake2s_sse.cpp` —
  owner: SIMD left behind).
- **Patterns:** manual §4.2 (u32 arithmetic → masked long or int+`>>>` — Blake2s is
  pure u32 add/rotr, use `int` + `Integer.rotateRight`), §4.13.1.
- **Acceptance:** RFC 7693 BLAKE2s vectors + official `blake2-kat.json` BLAKE2sp
  KATs green; differential test vs Bouncy Castle `Blake2spDigest`
  (`testImplementation` scope, §2.2) on randomized inputs green; a
  Blake2-checksummed fixture verifies end-to-end (extraction cross-checked vs
  unrar oracle in M3.11).
- **Fixtures:** `-htb` archive (§4.2).
- **No-go rows:** D2 (CRC32 sites untouched; `checkOldCrc` retained).
- **Est. diff:** ~550 lines.
- **Parallel-safe** with M3.3/M3.4.

#### M3.6 — Unpack5 skeleton: sibling engine, window, tables

- **Objective:** `Unpack5` as a **sibling** engine class taking `ComprDataIO`
  (manual §5.2 — NOT extending `Unpack20`); dynamic window sizing from the header
  (`0x20000 << bits`, 128 KB–4 GB; min alloc 0x40000; solid streams never shrink or
  grow the window mid-set). **Dictionary resource model (redesigned per review
  B-S3 — three separated concepts, upstream-anchored):**
  1. **Engine capability ceiling** (what the code can decode): M3 = **1 GB flat
     `byte[]`**; larger → `UnsupportedDictionarySizeException` (typed,
     documented; capability raised to 64 GB only when M4.3's segmented window
     lands — M4.1 raises nothing). **Raised to 64 GB at M4.3 as planned**
     (`Unpack5.CAPABILITY_MAX_WIN_SIZE`); the budget below did not move.
  2. **Caller resource limit:** `ArchiveOptions.maxDictionarySize` (shipped in
     P0.8; default **4 GiB = unrar's own default**, `d861246:options.cpp:13`
     `WinSizeLimit=0x100000000`; the `-mdx`/`CheckWinLimit` analog,
     `d861246:extract.cpp:1758` — non-interactive junrar refuses with the typed
     exception where console unrar refuses via `uiDictLimit`). Checked BEFORE
     any window allocation; the default never moves across M3/M4 (kills the
     prior revision's raise-default-before-capability ordering bug).
  3. **Allocation policy — growth-capped, never header-eager:** the flat window
     starts at `min(winSize, 0x40000)` and grows by doubling (arraycopy) as
     `unpPtr` actually advances, capped at `winSize`. Sound because pre-wrap
     reads beyond the written region are rejected by the day-one FirstWinDone
     semantics (M3.7), and reaching the first wrap means `winSize` real bytes
     were written — a legit archive; amortized copy cost ≤ 2× bytes written. A
     tiny archive claiming a 1 GB dict therefore allocates KBs **under default
     options** — strictly better than upstream, which eagerly allocates up to
     its limit; recorded as a conscious positive divergence. Precedent:
     junrar's own S1 (20 MB header-alloc cap) and S8 treat header-driven eager
     allocation as a defect class — the breaking major must not ship a
     tiny-archive-forces-1-GB-alloc hole (the prior revision's knob-default-1-GB
     "fix" still shipped exactly that hole; review B-S3).
  Also: bit input with 16-bit `getbits` + 32-bit `getbits32` mirroring
  `8f437ab:getbits.hpp` (probed 2026-07-17: junrar has no `getbits32` anywhere —
  only the 16-bit `getbits()`/`fgetbits()` family on the `Unpack` base — so
  `Unpack5` implements its own bit input; sibling rule keeps it off the RAR3
  base class); RAR5 block header read
  (BlockSize/BitSize/LastBlockInFile/TablePresent) + the 5 Huffman tables
  {LD 306, DD 64, LDD 16, RD 44, BD 20} with table-present logic; `ExtraDist`
  constructor flag designed in from day one (RAR7 = same engine, manual §5.2) but
  fixed `false` until M4.
- **Files:** new `unpack/Unpack5.java` (+ small `unpack/decode/` additions for
  RAR5 alphabets; the window abstraction with its growth policy), tests
  (`UnsupportedDictionarySizeException` and the knob already exist — P0.8).
- **C++ refs:** `8f437ab:unpack50.cpp` (block header + table read),
  `8f437ab:unpack.cpp:80-158` (`Init(uint64,bool)` window rules),
  `d861246:compress.hpp` (NC=306, DCB=64, LDC=16, RC=44, BC=20, MAX_LZ_MATCH).
- **Patterns:** manual §4.1 (sibling rule), §4.2 (all guard distances re-derived
  from 6.2.12 source, never copied from RAR3's −260/−270/−300), §5.2.
- **Acceptance:** table-read unit tests on crafted block streams (incl. hostile:
  truncated tables, oversized bit lengths); window-size matrix test: 128 KB, 32 MB,
  1 GB accepted; 2 GB/4 GB → typed exception (M3 capability); solid window rule
  pinned; **hostile rows (B-S3, both mandatory):** (a) a ~100-byte archive whose
  header claims a 1 GB dict, with `maxDictionarySize` lowered to 16 MB → typed
  exception BEFORE any window allocation (the guard fires in the size check, not
  in `new byte[]` — asserted via test hook); (b) window-abstraction unit test:
  init with a 1 GB declared size under DEFAULT options allocates only the
  0x40000 floor, and after writing N bytes capacity stays ≤ 2×N (growth-capped
  policy proven at the unit level here; the archive-level twin — tiny 1 GB-claim
  archive extracts with KB-scale peak window — runs in M3.7's acceptance once
  the decode loop exists); growth-boundary row: writes crossing several doubling
  boundaries read back byte-identical (growth is invisible to content).
- **Fixtures:** synthetic streams + byte-patched dict-bits headers (§4.3 sanctioned
  class) for the accept/reject matrix; real decode fixtures land with M3.7.
- **No-go rows:** D1 (window is engine state, not entry extraction — no byte[]
  entry path added), C15 (every `>>` audited — ledger extended).
- **Est. diff:** ~550 lines.

#### M3.7 — Unpack5 decode loop + engine lifecycle

- **Objective:** the main decode loop verbatim-shaped from
  `8f437ab:unpack50.cpp:139-160` (256=filter, 257=last-length, 258–261=rep-dists,
  literal/match decode; case order IS the format spec — manual §4.5);
  low-distance-bits handling; `CopyString` with FirstWinDone semantics (M1.4
  parity); window write-out via the `UnpWriteBuf`-analog path metering
  `destUnpSize`; solid-file reset rules (`unpInitData(solid)` analog); integration
  with the engine lifecycle: one engine per Archive, `extractFile` rewind-and-replay
  for solid sets keeps working (manual §5.2; `Archive.java:584-596,771-775`).
- **Files:** `unpack/Unpack5.java`, `unpack/Unpack.java` or `ComprDataIO` dispatch
  (method 0x50), tests.
- **C++ refs:** `8f437ab:unpack50.cpp`, `8f437ab:unpackinline.cpp` (CopyString /
  DecodeNumber / InsertOldDist shared inlines).
- **Patterns:** manual §4.5 (loop shapes), §4.3 (memcpy trap: self-referencing
  overlap must stay byte-sequential), §4.13.1 (comment-anchored lines).
- **Acceptance:** store-method and each compression level extract byte-identical to
  unrar oracle payload SHA-256s (fixtures §4.2; archive access via the pre-gate
  harness, M3.2); M3.4's deferred "extracts oracle-identical" rows (`-p`/`-hp`)
  execute here; solid multi-file fixture extracts in-order, out-of-order, and
  reverse (mirroring the existing RAR4 solid tests); hostile rows:
  distance-into-void, oversized match, truncated stream → typed exception;
  **carried from M3.6 (B-S3):** a tiny byte-patched 1 GB-claim archive extracts
  under DEFAULT options with peak window capacity ≤ 2× bytes written (KBs —
  window-capacity test hook), the archive-level proof of the growth-capped
  policy.
- **Fixtures:** core RAR5 matrix (§4.2): m0/m3/m5 × {plain, solid} × dict
  {128 KB, 32 MB} — dict-bearing rows use compressible payloads ≥ dict size
  (§4.2; rar silently reduces the recorded dict to the payload size otherwise).
- **No-go rows:** C1-family (wrap guards in new code — same hazard, new site),
  C15.
- **Est. diff:** ~600 lines — the likeliest overflow chunk; split at these internal
  commit boundaries if it grows: (1) decode loop + literal/match paths,
  (2) rep-dists/last-length + CopyString + FirstWinDone semantics, (3) solid reset
  rules + engine lifecycle + method-0x50 dispatch wiring.

#### M3.8 — RAR5 filters (DELTA/E8/E8E9/ARM) + sweep topology

- **Objective:** the 4 applied filter types with their limits
  (`MAX_FILTER_BLOCK_SIZE 0x400000`, `MAX_UNPACK_FILTERS 8192`); DELTA's 5-bit
  channel count; port the written-region sweep topology (wrap-aware two-part copy,
  filtered output written instead of window bytes, deferral for blocks extending
  past the write pass) — the topology survives from RAR3, the VM does not
  (manual §5.4).
- **Files:** `unpack/Unpack5.java` (+ small filter classes), tests.
- **C++ refs:** `d861246:unpack50.cpp:427-485` (filter application — the
  authoritative 4-type census, manual §1 reconciliation), `8f437ab:unpack50.cpp`
  (`ReadFilterData`, filter stack).
- **Patterns:** manual §5.4 (RAR5 hook points), §4.5.
- **Acceptance:** per-filter fixtures extract oracle-identical via the pre-gate
  harness (x86 exe → E8/E8E9, ARM binary → ARM, WAV/BMP-like data → DELTA —
  creation §4.2; if rar declines to emit a filter for an input, unit-test the
  transform against a synthetic filter block and record the fixture as
  unattainable); hostile: filter flood > 8192, block > 0x400000 → typed
  exception/no-op matching unrar.
- **Fixtures:** filter matrix rows (§4.2) + byte-patched hostile rows.
- **No-go rows:** none new (RAR3 filter path untouched).
- **Est. diff:** ~450 lines.

#### M3.9 — RAR5 multi-volume

- **Objective:** `.partN.rar` sets: MHFL_VOLUME/MHFL_VOLNUMBER, EHFL_NEXTVOLUME,
  HFL_SPLITBEFORE/AFTER spanning through `ComprDataIO`'s volume path (mind the
  return-vs-throw mix: EOF throws, missing next volume returns −1 —
  `ComprDataIO.java:134-136,156`); `VolumeManager` naming for `.partN`; started-
  mid-set detection (not-first-volume opened → typed error mirroring unrar);
  stream-based volumes keep working (C14).
- **Files:** `volume/*` (naming), `unpack/ComprDataIO.java`, `Archive.java`
  (setVolume re-parse noted — manual §4.12), tests.
- **C++ refs:** `8f437ab:volume.cpp`, `arcread.cpp` volume flags;
  `unrar-delta-map.md` §2.9 (AnalyzeArchive = CLI-adjacent; mirror only the
  starting-volume semantics, skip the rest — recorded non-goal).
- **Patterns:** manual §4.12 (VolumeManager SPI — never construct names in
  Archive).
- **Acceptance:** 3-part fixture set extracts from File and from InputStreams (via
  the pre-gate harness until M3.11); missing part2 → typed exception; split-file
  CRC verifies across the span; solid multi-volume row.
- **Fixtures:** `volumes/rar5-part/*.partN.rar` (§4.2).
- **No-go rows:** C14 (stream-volume length fallback), Java-API class-c rows
  (multi-part from InputStream preserved).
- **Est. diff:** ~400 lines.

#### M3.10 — links: REDIR extraction + the three symlink-safety layers

- **Objective:** FHEXTRA_REDIR extraction — Unix symlinks (target from header,
  `ExtractUnixLink50` semantics), hardlinks (`Files.createLink`), file copies;
  Windows symlink/junction records surfaced as metadata (creation non-goal on
  JVM/cross-platform — recorded); port the three safety layers to
  `LocalFolderExtractor`: 5.2.5 `IsRelativeSymlinkSafe` depth check, 6.1.7 target
  validation, 6.2.3 `LinksToDirs` (refuse writing through a previously-extracted
  dir-symlink); re-apply S5/S6/S7 (separator-normalize before canonical check,
  `+ File.separator` containment) to every new path.
- **Files:** `LocalFolderExtractor.java`, `Archive.java`/extractor glue, tests.
- **C++ refs:** `8f437ab:extinfo.cpp:110` (`IsRelativeSymlinkSafe`),
  `22b5243:ulinks.cpp`, `2ecab6b:extract.cpp` (`LinksToDirs`,
  `LastCheckedSymlink` reset), `d861246:extract.cpp:654-672` plumbing.
- **Patterns:** manual §7 S5–S7 rules; `divergences-no-go.md` guidance item 1.
- **Acceptance:** hostile-input rows (mandatory, planner-supplied): symlink target
  `../../etc`, absolute target, backslash target, sibling-prefix target, dir-symlink
  -then-file-through-it, hardlink to outside path → all rejected with typed
  exception; benign relative symlink + hardlink fixtures extract correctly on
  Unix CI (Windows CI: creation skipped, metadata asserted). Archive access via the
  pre-gate harness until M3.11.
- **Fixtures:** `-ol` link fixtures, benign + hostile (§4.2 — hostile ones
  byte-patched: rar refuses to *create* hostile targets).
- **No-go rows:** **S5, S6, S7 — their existing tests must stay green and gain RAR5
  twins.**
- **Est. diff:** ~500 lines.

#### M3.11 — integration: lift the V5 gate, corpus flip, API surface

- **Objective:** remove the `UnsupportedRarV5Exception` throw (`Archive.java:359`)
  **and delete the M3.2 pre-gate harness in the same commit** (the harness-scoped
  test plumbing goes with it; the public-surface tests are the NEW files below),
  route RARFMT50 archives through the new stack end-to-end;
  `@Deprecated` the now-unthrown exception class (retirement path §5.3); audit the
  `setChannel` catch filter one final time against every new exception type
  (checklist in PR); four-surface tests (manual §8.1) for the whole RAR5 fixture
  matrix; extend the P0.9 JRE 8 smoke with RAR5 plain + encrypted rows;
  regression corpus regeneration per §4.4.
- **Files:** `Archive.java`, `Junrar.java` (no signature changes expected),
  exception Javadoc, regression JSONs, `src/jre8smoke/`, tests.
- **C++ refs:** none new.
- **Patterns:** manual §8.2 (the corpus gate), §4.9.
- **Acceptance:** **red→green (behavior change), frozen-test discipline
  (respecified per review B-S6 — the prior "flip the expectations in the same
  commit" procedure edited the tests between red and green, which proves
  nothing):**
  1. AUTHOR the new public-surface success tests first — the full §4.2 RAR5
     matrix through all four public surfaces, asserting successful extraction
     and oracle digests — and EXECUTE them on the gate-closed tree: every one
     RED, failing with `UnsupportedRarV5Exception` (output pasted in the
     handoff; `git hash-object` of each test file recorded at red time).
  2. Lift the gate (+ delete the harness). The SAME tests, **zero edits**
     (hash-verified), run GREEN in the same commit's CI.
  3. The OLD gate-pinning tests (asserting `UnsupportedRarV5Exception` on RAR5
     fixtures) are **retired in the gate-lift commit as a separate concern** —
     deleted, or where a fixture still earns coverage, rewritten as success
     tests *counted as new tests* (they are not evidence; the frozen set from
     step 1 is the proof).
  Corpus regeneration diff passes the scripted audit (§4.4) and the
  maintainer-approved regression run.
- **Fixtures:** the complete §4.2 matrix.
- **No-go rows:** ALL — every row of manual §7 (25 rows: S1–S8, C1–C15, D1–D2)
  gets a final sweep: each row's guard test re-run and ticked in the PR body.
- **Est. diff:** ~300 code lines + large JSON churn (mechanical, script-audited).

### Phase M4 — RAR7 delta (6.2.12 → 7.2.7)

#### M4.1 — RAR7 header parse + dictionary-limit gate

- **Objective:** parse RAR7 compression info: algo version 1 → `VER_PACK7`; 5 dict
  bits + 5 fraction bits (`WinSize = 0x20000L << bits; WinSize += WinSize/32*frac` —
  `d861246:arcread.cpp:868-874`); `FCI_RAR5_COMPAT` (RAR7-written, RAR5-decodable →
  route without ExtraDist); reject > `UNPACK_MAX_DICT` 64 GB. **Parse only —
  neither the engine capability (1 GB until M4.3) nor the `maxDictionarySize`
  default (4 GiB since P0.8, permanent) moves here** (review B-S3: the prior
  revision raised the default to 4 GB in this chunk, before M4.3 shipped the
  mechanism — a RAR7 dict > 1 GB keeps throwing
  `UnsupportedDictionarySizeException` with the size in the message until M4.3
  lands; ≤ 1 GB RAR7 dicts extract from M4.2 on).
- **Files:** `rarfile/rar5/*` (compression-info parse), tests.
- **C++ refs:** `d52ee2f` diff (`git -C ~/git/unrar diff 8f437ab d52ee2f --
  arcread.cpp headers5.hpp rardefs.hpp | cat`), `d861246:rardefs.hpp:40`.
- **Patterns:** manual §5.2 (RAR7 hook), §4.2 (64-bit window math — `long`
  throughout).
- **Acceptance:** parse matrix: dict 128 KB…64 GB encodings incl. fractional
  (e.g. bits=1+frac=16 → 384 KB) decode to the right `long` (synthetic headers +
  §4.3 byte-patched dict-bits fixtures — real rar only records dicts up to the
  payload size, §4.2); 64 GB+1 encoding → reject; header claiming 64 GB with
  `maxDictionarySize` default → typed exception (dict-bomb row).
- **Fixtures:** RAR7 fixtures via rar 7.23 (§4.2); dict-bomb + large-dict-encoding
  rows byte-patched (§4.3 sanctioned class).
- **No-go rows:** none new.
- **Est. diff:** ~300 lines.

#### M4.2 — ExtraDist decode (DCX=80)

- **Objective:** flip `Unpack5(ExtraDist=true)` live: DCX=80 distance table, the
  extended distance decode; `FCI_RAR5_COMPAT` streams keep DCB=64.
- **Files:** `unpack/Unpack5.java`, decode table constants, tests.
- **C++ refs:** `git -C ~/git/unrar diff 8f437ab d861246 -- unpack50.cpp
  compress.hpp | cat` (localized to table sizes + distance decode —
  `unrar-delta-map.md` §5.4); mind 7.1.1 container noise: compare semantics.
- **Patterns:** manual §5.2, §4.5.
- **Acceptance:** RAR7 fixtures (small dict) extract oracle-identical; RAR5-compat
  RAR7 fixture extracts through the DCB=64 path (assert via engine flag in test);
  M3 RAR5 matrix stays green byte-identical.
- **Fixtures:** rar 7.x fixtures: default, `-md` large-ish, RAR5-compat mode.
- **No-go rows:** C15 (new distance math audited).
- **Est. diff:** ~250 lines.
- **Acceptance deviation (executed probes 2026-07-21, rar/unrar 7.23 — see
  `rar7/README.md`):** the "small dict" RAR7 fixture is **unproducible**, so row 1 moves
  to M4.3. `rar` only writes `algo=1` once the *recorded* dictionary passes the 4 GB that
  RAR5's four dict bits can encode (below that regime it accepts only powers of two, and
  neither `-mdx`, `-mc*` nor a format switch defeats the payload-size reduction), so every
  genuine RAR7 stream declares a >4 GB window — past the 1 GB capability, past the 4 GiB
  `maxDictionarySize` default, and past what a flat Java `byte[]` can address. `unrar`
  itself refuses the fixture by default and needs ~6 GB to force it. ExtraDist is therefore
  driven from crafted engine-level streams (the M3.6/M3.8 seam) and mutation-probed. Row 2
  is kept, from a `FCI_RAR5_COMPAT` word patched over a real RAR5 stream — a third
  sanctioned byte-patch class, see §4.3.

#### M4.3 — segmented window > 1 GB (lazy-allocated)

- **Objective:** replace the single `byte[]` window with a long-indexed segmented
  window (power-of-two segments, e.g. 256 MB; shift+mask addressing) used only when
  `winSize > 1 GB`; segments allocated **lazily as the window actually fills** (a
  dict-bomb header allocates nothing beyond the guard check — the segmented
  sibling of M3.6's growth-capped flat policy); **this is the one chunk that
  raises the engine capability** (1 GB → `min(maxDictionarySize, 64 GB)`; the
  knob default stays 4 GiB — B-S3, so > 4 GiB dicts remain caller opt-in
  exactly like unrar's `-mdx`). Note (review F2): lazy allocation does
  NOT make big-dict extraction cheap — a genuinely large-dict archive carries a
  payload ≥ its dict size, so the window really fills; the CI/local split below is
  the consequence.
- **Files:** `unpack/Unpack5.java` + new window abstraction class, tests.
- **C++ refs:** design-informed by `143e317:unpack50frag.cpp` (fragmented window —
  a 32-bit-C++ concern; junrar's segmentation is its own design, manual §5.2), not
  a port of it.
- **Patterns:** manual §5.2 (Java array cap), §6 R1 (this chunk is the R1
  resolution).
- **Acceptance (reworked per review F2 — a tiny-payload big-dict fixture is
  unproducible: rar records the dict reduced to payload size, §4.2 probe):**
  (a) PR CI: window-abstraction unit tests — cross-segment match copy, wrap at
  segment boundary, self-referencing overlap, long-index addressing; parse/guard
  tests on a §4.3 byte-patched 2 GB-dict header (valid small stream, CRC refixed);
  1 GB-and-below archives still use the flat path (assert via test hook); >4 GB
  stays opt-in via `maxDictionarySize`. (b) Scheduled (non-PR) or local-only job:
  a REAL `-md2g` archive generated on the fly (not committed) from ≥ 2 GiB of
  zeros — budget per the executed 1 GiB probe (§4.2: 1.1 GiB zeros → 9 s
  generation, 46 KB archive; 2 GiB scales to ~20 s + ~4 GiB scratch disk) —
  extracted through a digest-sink (no disk write of the payload), digest compared
  to the generator's input digest; run cadence: nightly or pre-release, never a
  PR gate.
- **Fixtures:** committed: byte-patched 2 GB-dict header row; generated on the
  fly (never committed): the real `-md2g` archive for the scheduled job.
- **No-go rows:** D1 (entry-extraction byte[] limits unchanged — window ≠ entry).
- **Est. diff:** ~450 lines.
- **Scope addition executed at M4.3 — the mask idiom, not just the array width.**
  Segmentation is only half the chunk. junrar folded every window position with
  `& (size - 1)`, and a RAR7 dictionary is **not** a power of two: the
  compression-info word carries a 5-bit fraction on top of the dictionary bits
  (`winSize += winSize/32*frac`), which M4.1 already parses, so a declared window
  such as `0x42000` was reachable *below* the 1 GB capability. On such a window the
  mask wraps at the size rounded down to a power of two and addresses window space
  that was never written: a real `-md=264k` archive (`unrar 7.23`: `All OK`) failed
  with `ArrayIndexOutOfBoundsException: Index 263878 out of bounds for length
  262144`. `d861246:unpack.hpp:416-435` says so in its own comment — `WrapUp`/
  `WrapDown` "replaces `&MaxWinMask` for non-power 2 window sizes" — and
  `AddFilter` additionally needs `% MaxWinSize` rather than a wrap, because a
  malformed block start can be many times the window size
  (`d861246:unpack50.cpp:228-233`). Landed as its own commit ahead of the
  segmentation, with the fractional-window extraction RED first. Window positions
  widen to `long` with it (C++ `size_t`) — see the M4.3 addendum in
  `reports/signedness-audit.md`.
- **Acceptance as executed:** (a) shipped in PR CI, with the acceptance-row window
  unit tests driven through an explicit-segment-shift test constructor so segment
  boundaries are reachable without allocating 256 MB per segment. (b) is the
  committed `rar7/rar7-md6g.rar` rather than an on-the-fly `-md2g` archive: it is a
  genuine `algo=1` stream, its oracle was recorded from `unrar 7.23` at generation
  time, and it needs no scratch disk. Tagged `bigdict`, excluded from `test`, run by
  `./gradlew bigDictTest`. **Executed 2026-07-21:** 4,563,402,752 bytes,
  CRC32 `8E9B8BF4`, 18.9 s at `-Xmx6g` through a digest sink (`unrar -md6g`: 7.3 s,
  3.0 GiB RSS). This is the archive-level RAR7 oracle row deferred from M4.1 and
  M4.2.

**Explicit non-goals at every phase** (owner constraints +
`unrar-delta-map.md` §2.9/§5.5): unpack50mt/threadpool, qopen, recvol3/recvol5/rs16
(recovery-record *use*), largepage, motw, secpassword (char[] hygiene instead —
§5.2), blake2s_sse/AES-NI, fragmented-window port, cmdfilter/cmdmix/ui*, compression
of any kind (forever). **RAR3 compressed-comment extraction** (arccmt.cpp routes
3.x comments through the old unpack — `unrar-delta-map.md` §3.5) stays a non-goal:
current junrar behavior (comment blocks parsed/skipped, payload not decompressed)
is preserved; the RAR5 `CMT` service header is *parsed* in M3.3, its payload
decompression equally a non-goal (review N5 disposition). SFX is IN scope (M3.1,
review F4).

---

## 4. Test & fixture strategy

### 4.1 Ground rules

- Manual §8 governs: JUnit 5 + AssertJ, four-surface corrupt-input pattern,
  issue-pinned test naming, **real unrar as the oracle** ("Data taken from UnRAR").
- Red→green per CLAUDE-grade discipline with the manual's two exceptions: brand-new
  RAR5 code needs no red against the void; every change to EXISTING observable
  behavior (P0.5, P0.6, P0.7, M1.5, M2.1, M3.11) carries an executed red run pasted
  in its handoff.
- Every hostile-input row named in a chunk is planner-supplied and mandatory: the
  chunk is not done with fewer rows than listed.

### 4.2 Fixture creation — junrar can never ship a compressor

All fixtures are produced with **official rar binaries** (licensed trial use for
test-data generation, per the `generate-testdata.sh` precedent — manual §8.1) or
byte-patched from those outputs (hostile variants only). The generation procedure is
committed as an extension of `generate-testdata.sh` (exact command lines, rar
version recorded per fixture in a manifest), so fixtures are reproducible even
though the binary is not vendored.

**Binary procurement (owner directive 2026-07-17; state re-probed at review
round).** rar/unrar 7.23 are installed at `~/.local/bin` (owner install
2026-07-17, from the official WinRAR macOS tarballs —
`https://www.win-rar.com/fileadmin/winrar-versions/rarmacos-arm-723.tar.gz` /
`…/rarmacos-x64-723.tar.gz`); the rar CLI must NOT come from Homebrew (being
removed from brew). rar 7.23 is the primary generator: RAR5-era fixtures via
`-ma5` (switch sets kept inside 6.2.12 stream semantics — oracle cross-check
enforces it), RAR7 fixtures via the v7 dictionary switches.

**RAR4-format creation is confirmed ABSENT from rar 7.23** (executed probe:
`rar a -ma4 t4.rar f.txt` → `ERROR: Unknown option: ma4`), so every RAR3/RAR4-era
gap-fill fixture (P0.5 PPMd MaxMB>1, P0.6 non-ASCII password + UTF-8 names, M1.1)
uses an **era binary** — pre-verified available: rar 6.24 macOS x64 tarball at
`https://www.rarlab.com/rar/rarmacos-x64-624.tar.gz` (probed 2026-07-17: HTTP 200,
`application/x-gzip`, 607,872 bytes, gzip magic `1f 8b`; win-rar.com fileadmin
mirrors the same file). One residual ASSUMED, probed at first use in P0.5:
rar 6.24's `-ma4` path still emits PPMd under `-mct+` (RAR4 creation was dropped
at 7.0, so 6.24 is expected to carry the full RAR4 method set); if refuted, walk
further back the same URL scheme (5.x era). P0.1's solid-v20 needs a RAR 2.x-era
emitter or the §4.3 byte-patch fallback (unchanged). The tarball URL + rar version
used is recorded per fixture in the manifest.

**Dictionary-size ground truth (executed probes, 2026-07-17 — review F2):** rar
silently reduces the *recorded* dictionary to the payload size, so a tiny-payload
big-dict fixture is unproducible with the real generator:

```text
$ rar a -ma5 -md2g t2g.rar f.txt        # f.txt = 19 bytes
$ rar lt t2g.rar | grep -i compression
 Compression: RAR 5.0(v50) -m0 -md=128k
$ dd if=/dev/zero of=big.bin bs=1048576 count=33 && rar a -ma5 -md32m t32.rar big.bin
$ rar lt t32.rar | grep -i compression
 Compression: RAR 5.0(v50) -m3 -md=32m   # archive: 1,543 bytes
$ dd if=/dev/zero of=big1g.bin bs=1048576 count=1100 && rar a -ma5 -md1g t1g.rar big1g.bin
$ rar lt t1g.rar | grep -i compression
 Compression: RAR 5.0(v50) -m3 -md=1g    # archive: 46,769 bytes; 9 s to generate
```

Consequences baked into the rows below: dict-bearing fixtures use **compressible
(all-zeros) payloads ≥ the dict size** — the archives stay KB-sized and
committable up to `-md1g`; extraction of the big ones writes the full payload, so
their extraction tests run through a **digest-sink** (count + SHA-256, no disk
write) and anything ≥ 1 GB dict is tagged **non-PR** (scheduled/local); dict
encodings beyond what a payload can realize (2–64 GB rows, dict-bombs) are §4.3
byte-patched headers used for parse/guard tests only.

| Variant class | Producer + switches | Consumed by |
| --- | --- | --- |
| RAR5 core matrix: m0 (store), m3, m5 × plain/solid (`-s`) × dict `-md128k`, `-md32m` | rar 7.23 `-ma5`; payloads ≥ dict size (zeros — 33 MB payload → 1.5 KB archive, probe above) | M3.6/M3.7 |
| dict 1 GB accept row (real stream) | rar 7.23 `-ma5 -md1g`, ≥ 1 GiB zeros payload → ~46 KB committed archive; extraction via digest-sink, tagged non-PR (scheduled/local) | M3.6, M4.3 |
| dict 2 GB/4 GB/64 GB encodings, dict-bomb, M3 reject rows | §4.3 byte-patched dict-bits header over a valid small stream (header CRC refixed) — parse/guard tests only | M3.6, M4.1 |
| encrypted: `-p<pw>` (file), `-hp<pw>` (header), non-ASCII UTF-8 pw, wrong-pw reuse | rar 7.23 `-ma5` | M3.4 |
| Blake2: `-htb`; Blake2+encrypted (HASHMAC) | rar 7.23 `-ma5` | M3.5, M3.4 |
| multi-volume: `-v100k` → `.part1/.part2/.part3`; solid multi-volume | rar 7.23 `-ma5` | M3.9 |
| links: `-ol` relative symlink, hardlink pairs | rar 7.23 `-ma5` on a Unix host (macOS/Linux) | M3.10 |
| times/owner/version/comment: `-ts+`, `-ow`, `-ver`, `-z<file>` | rar 7.23 `-ma5` | M3.3 |
| filters: payloads chosen to trigger them — x86 `.exe` (E8/E8E9), ARM ELF (ARM), WAV/BMP-like tabular data (DELTA); presence verified by extraction-correctness vs oracle + an engine-side filter counter asserted in unit tests; unobtainable filters fall back to synthetic filter blocks in unit tests (recorded) | rar 7.23 `-ma5` | M3.8 |
| RAR7: default, `-md` fractional sizes realizable by payload, RAR5-compat mode | rar 7.23 (v7 format switches; payload ≥ dict rule applies) | M4.1–M4.2 |
| RAR7 real `-md2g` extraction check | generated on the fly (never committed): ≥ 2 GiB zeros payload (~20 s + ~4 GiB scratch, extrapolated from the 1 GiB probe); scheduled/local job only | M4.3 |
| SFX (RAR3 + RAR5) | scripted concatenation of a stub blob + existing fixture | M3.1 |
| RAR3 gap-fills: PPMd `-mc` forced text model (MaxMB>1), non-ASCII `-p`, UTF-8 names | era rar 6.24 binary (rar 7.23 `-ma4` probe EXECUTED 2026-07-17: `ERROR: Unknown option: ma4` — see procurement note; tarball pre-verified there) | P0.5, P0.6, M1.1 |
| solid v20 (C1) | RAR 2.x binary if obtainable; else byte-patch a v20 fixture (hostile-by-construction) | P0.1 |
| hostile set (always byte-patched from legit fixtures, one mutation each): bad header CRC, vint flood/overflow, HeadSize/ExtraSize lies, truncated mid-header/mid-stream, name size 0/huge, dict-bomb header, bad pswcheck, filter flood >8192, filter block >0x400000, distance-into-void, REDIR traversal targets (backslash, `..`, absolute, sibling-prefix), protect-header corrupt | scripted patcher committed next to the fixtures | P0.*, M1.*, M2.*, M3.* |

Oracle capture: for every extraction fixture, `unrar x` output payloads' SHA-256
(and `unrar lt` listing where metadata is asserted) recorded at generation time
into the manifest; tests assert against the recorded digests, not against live-run
unrar. The oracle binary is the `unrar` bundled in the same rar 7.23 tarball
(no rar/unrar is preinstalled on the dev machine — see procurement note above).

### 4.3 Byte-patched fixtures — legitimacy rule

Byte-patching is legitimate for three fixture classes (review F2 widened the rule; M4.2
added class 2):

1. **Hostile/corrupt variants** — a corrupt archive is corrupt however it was made;
   follows the house corrupt-input pattern (manual §8.1).
2. **Version-flag headers over a stream that really is in that format** (sanctioned
   positive class, added M4.2): rewrite the compression-info word of a real archive and refix
   the header CRC32, but *only* where the untouched stream already satisfies what the new
   word claims. Two instances: `algo=1` + `FCI_RAR5_COMPAT` over a RAR5 stream, whose entire
   meaning is "RAR7 header, RAR5-decodable stream"; and `algo=1` over a **stored** stream,
   which carries no version-specific encoding at all. Both are faithful specimens rather than
   claims the stream cannot back, and `unrar 7.23` tests both patched archives `All OK`.
   Legitimate as an extraction oracle **only** because the expected output is the unpatched
   original's own. The compat patch is not length-preserving (a RAR5 compression-info word
   never reaches bit 14, so it is at most 2 vint bytes while bit 20 needs 3): the extra byte
   is absorbed by the header-size vint. The stored promotion is length-preserving.
3. **Inflated-resource headers over a valid stream** (sanctioned positive class):
   patch the dict-bits field of a valid small archive's file header and refix the
   header CRC32. The stream stays decodable — its true back-references never exceed
   the originally-encoded distances, so a larger declared window is semantically
   harmless — which makes these the only producible fixtures for dict encodings no
   real payload can realize (2–64 GB rows, dict-bombs; §4.2 probe). Used for
   parse/guard/window-sizing tests; never as an extraction-correctness oracle.

Every patched fixture commits its patch script (offset + before/after bytes + why)
so review can verify the mutation is the intended one.

### 4.4 The 12,475-JSON regression-corpus gate

The committed reference JSONs currently expect `UnsupportedRarV5Exception` for RAR5
members (manual §8.2). The M3.11 flip procedure — reviewed, mechanical:

1. Baseline run in `check` mode on the pre-flip commit: green (proves the
   environment is sane).
2. Flip to `generate` mode (build.gradle `includeTags`), run, commit the churn as a
   dedicated commit touching ONLY `src/regressionTest/resources/corpus/**`.
3. **Scripted audit of the diff** (script committed under `docs/porting/` or
   `scripts/`): every changed JSON must match one of the THREE allowed shapes
   (review F10 + B-S2 — file-encrypted `-p` archives ARE listable without a
   password, header-encrypted `-hp` are not; probed 2026-07-17 with rar 7.23:
   `unrar l -p- tp.rar` lists the entry, `unrar l -p- thp.rar` → "Incorrect
   password") — **(a) plain RAR5 members:** `exception:
   UnsupportedRarV5Exception` removed, `fileHeaders[]` populated, `isRarV5: true`
   retained; **(b) file-encrypted (`-p`) RAR5 members:** same as (a) plus
   `isPasswordProtected: true` and per-header `isEncrypted: true` (headers are
   NOT encrypted — they populate); **(c) header-encrypted (`-hp`) RAR5 members**
   (passwordless corpus run cannot read headers): the exception swaps
   `UnsupportedRarV5Exception` → `WrongPasswordException` (§5.3 — pinned, no
   longer "or the recorded type"), headers absent, `isRarV5: true` retained (the
   signature probe — M3.1), `isEncrypted` consistent. Any OTHER field
   change (RAR3 members, unexpected new exceptions) is a finding that blocks the
   merge and gets investigated.
4. **Oracle-check ALL flipped members, not a sample** (review B-S2 — at 345
   members this is one scripted loop, and a random 20 adds little confidence):
   scripted comparison of every flipped member's record against `unrar lt`
   output (names, sizes, times; `-hp` members: against the expected
   `WrongPasswordException` shape) — script + summarized output attached to the
   PR; any mismatch blocks.
5. The maintainer-approved `regression.yml` environment run is the final gate
   (manual §8.3).

Intermediate corpus impact (review F9 widened the list): P0.6 (T1/T6 header
observables), P0.7 (header CRC), M1.2/M1.3/M1.4 (corrupt members flip to typed
exceptions), M1.5 (method 36), M2.1 (no-op filters) each run steps 1–3 scoped to
their expected delta (possibly zero) and attach the evidence; they must NOT wait
for M3 — M3.11's step 1 green baseline presumes every earlier flip was already
flushed.

---

## 5. API design sketch (plan-level)

### 5.1 Entry points — unchanged where possible

`Junrar.extract(...)`, `Archive` construction, `FileHeader` accessors, and the
`VolumeManager`/`SeekableReadOnlyByteChannel` SPIs keep their signatures; RAR5
support arrives *inside* them (format dispatch on the signature version byte,
M3.1). New accessors only where RAR5 has new facts: e.g.
`FileHeader.getHashDigest()` (Blake2sp), `getRedirection()` (type + target),
nanosecond time accessors reusing the C3 `FileTime` pattern; `Archive.getFormat()`
(the `RarFormat` enum — M3.1) and `FileHeader.isBrokenHeader()` (P0.7). The
unified-header approach mirrors upstream (one `FileHeader` model, per-format
loaders — `unrar-delta-map.md` §4.2; pinned in M3.3).

### 5.2 Passwords & configuration — `ArchiveOptions` is THE path (review B-S7)

- **One construction-time configuration object, `ArchiveOptions` (P0.8 — full
  contract there):** builder with `password(char[])` (defensive copy),
  `password(String)` convenience, `maxDictionarySize(long)` (default 4 GiB =
  upstream's `WinSizeLimit`), `unrarCallback(...)`; new
  `Archive(<source>, ArchiveOptions)` constructors and
  `Junrar.extract(..., ArchiveOptions)` overloads — so one-shot extraction is
  configurable too (a post-construction setter cannot reach `Junrar.extract`).
- **NO parallel `char[]` overloads beside the `String` ones** — probed
  2026-07-17: `new Archive(file, null)` is ALREADY a javac ambiguity between
  `(File,String)`/`(File,UnrarCallback)`; a `char[]` twin per `String` entry
  point would compound it, an options type does not. Existing `String`
  constructors/methods stay, delegate to the options path, and are **not
  deprecated in the RAR5 major** (Javadoc cross-references `ArchiveOptions` and
  notes a `String` password cannot be wiped; deprecation revisited no earlier
  than the following major).
- **Hygiene contract:** defensive copy at the builder AND at `Archive`
  construction; `Archive.close()` wipes its copy (`Arrays.fill('\0')`);
  internals carry `char[]` end-to-end (secpassword's honest JVM substitute —
  `unrar-delta-map.md` §5.5).
- Encoding is format-owned and explicit: RAR5 → UTF-8 (`crypt5.cpp` contract);
  RAR3 → what `2e71167:crypt.cpp:188-286` actually does (UTF-16LE wide-char
  serialization — verified in P0.6/T4), never platform default.

### 5.3 Exceptions — new types and the `setChannel` filter (the trap)

Every new type extends `RarException` (checked, house rule manual §4.9); each row
below records its `setChannel` catch-filter decision **at introduction time**
(manual §4.9 trap: an unlisted type is silently swallowed at archive open):

| New exception | Thrown when | In `setChannel` rethrow filter? |
| --- | --- | --- |
| `UnsupportedRarVersionException` | signature version bytes 02..04 | YES (archive is unreadable by design) |
| `WrongPasswordException` | pswcheck mismatch; missing password on a header-encrypted archive; encrypted-header CRC failure after decryption (all RAR5 — M3.2/M3.4) | YES (open cannot proceed meaningfully) |
| `UnsupportedDictionarySizeException` | header-declared memory > engine capability (RAR5 window: 1 GB until M4.3, then 64 GB) or > `maxDictionarySize` budget (default 4 GiB; also gates PPMd `MaxMB+1` — P0.8) | NO — headers parse fine; extraction of that entry throws (matches "extract working files" tolerance). Recorded consciously. Introduced at P0.8. |

`UnsupportedRarV5Exception`: after M3.11 it is never thrown; `@Deprecated` +
`@deprecated As of <version>` Javadoc, removed at the **next** major after the one
that ships RAR5 (bestbefore processor arms on `.0.0` — manual §3). It stays in the
`setChannel` filter until removal (harmless, avoids behavioral churn for one
release).

### 5.4 Semver consequence

RAR5 support ships as a **breaking major** (conventional commit
`BREAKING CHANGE:` footer — manual §3). Decision on the release-event split
(review F8 — this section and §7 previously contradicted each other): the major's
breaking rationale is **the RAR5 flip alone** (RAR5 archives open instead of
throwing `UnsupportedRarV5Exception`; the exception's deprecation). The
`ArchiveOptions` surface (P0.8) is **binary-compatible and touches no existing
signature** — new type, new overloads only — but is **source-incompatible** for
a caller passing a bare `null` password literal to the three-argument
`Junrar.extract` overloads, since the new `ArchiveOptions` overload makes that
call site javac-ambiguous. Remedies are disclosed in the affected overloads'
Javadoc: drop the password argument, cast to `(String) null`, or pass a
password-less `ArchiveOptions`. It ships as **minor-with-release-note** — the
same disposition class as the P0.7/M2 items below — rather than a bare
additive minor before the major. P0.7's
header-CRC verification (unrar-tolerance contract, one named narrower
strictness) and M2's filter semantics are declared **acceptable
minor-with-release-note changes** — each moves junrar toward unrar's own semantics
and lands with corpus evidence (§4.4) — and are NOT held behind the major; §7's
version-event column is authoritative for them.

---

## 6. Risk register

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| R1 | RESOLVED at M4.3 (PT-35, MIGRATION_MANUAL.md Appendix A): capability 64 GB, `byte[][]` of lazily allocated 256 MB segments above 1 GB, flat layout kept at and below it, `maxDictionarySize` default unmoved at 4 GiB. Positions are `long` and wrap instead of masking, because RAR7 dictionaries are not powers of two. Original entry: | | |
| R1 | **RAR5/RAR7 windows vs Java's 2^31 array cap** — RAR5 alone encodes 4 GB dicts; RAR7 to 64 GB; D1's >2 GB `byte[]` limitation is a *deliberate* divergence for entry extraction and must not be "fixed" sideways | Extraction failures or an accidental D1 regression | Staged (review B-S3): capability = 1 GB flat `byte[]` at M3 (covers the overwhelming majority of real archives — rar default dict is 32 MB), 64 GB segmented+lazy at M4.3; budget = `ArchiveOptions.maxDictionarySize`, default 4 GiB (= upstream `WinSizeLimit`) from P0.8, never moved; allocation = growth-capped (M3.6) / lazy segments (M4.3), so a dict-bomb header allocates KBs even under DEFAULT options. Entry-extraction byte[] paths untouched (D1 pinned in P0.3). |
| R2 | **Regression-corpus churn** — 345 JSON expectation flips at M3.11 (of 12,475; the rest must stay byte-identical) could smuggle regressions past review | Silent RAR3 regressions | §4.4 procedure: single dedicated commit, scripted three-shape audit, ALL-flipped-members oracle check (B-S2), maintainer-gated corpus run. Intermediate behavior changes (P0.7/M1.5/M2.1) flush their corpus deltas early instead of folding into the big flip. |
| R3 | **PPMd heap drift** — M1.2 edits pointer-emulation code where a one-byte layout slip corrupts everything downstream | Wrong output, latent corruption | M1.1 rebuilds the byte-diff heap harness BEFORE any guard lands (manual §4.13.4); golden dumps committed; guards land one release-pin at a time. |
| R4 | **M2 behavior change on custom-VM archives** (incl. UPCASE) — output flips from interpreted to no-op | User-visible extraction change; C11 test conflict | Parity with unrar ≥5.5.1 IS the spec (upstream made the same flip a decade ago); oracle red→green on a custom-program fixture; C11 supersession documented in-PR (enum lookup test survives, execution changes); corpus delta reviewed. |
| R5 | **Fixture-generation dependency on proprietary rar binaries** (and a RAR 2.x relic for C1) | Blocked chunks, irreproducible fixtures | Committed generation manifest (rar version + command per fixture, §4.2); oracle digests recorded at generation time so CI never needs the binary; byte-patch fallback for hostile/legacy variants (P0.1); fixtures are small and committed. |
| R6 | **Fork drift from junrar master** — rar5-port is long-lived; upstream junrar keeps landing fixes (the no-go list grew two CVE rows in 2026 alone) | Painful final merge; silently missing new guards | Rebase the branch on upstream master at every phase boundary (5 sync points); each rebase re-runs the full no-go guard-test sweep; new upstream commits get classified against `divergences-no-go.md` and appended if load-bearing. |
| R7 | **`setChannel` swallow trap** — any forgotten new exception type silently changes archive-open behavior | Corrupt/encrypted archives "open" with partial headers | §5.3 table maintained as part of every exception-introducing chunk's acceptance; M3.11 does a final audit of the filter list vs `grep -rn "extends RarException"`. |
| R8 | **Solid RAR5 × engine lifecycle** — `extractFile` rewind-and-replay (random access into solid sets) is a junrar-only API the new engine must honor | Broken public API on solid RAR5 archives | M3.7 acceptance pins in-order/out-of-order/reverse extraction on a solid RAR5 fixture, mirroring the existing RAR4 solid tests (`divergences-no-go.md` class c). |

Consciously waived (review axis G): a performance-regression harness for the hot
RAR3 paths M1.2/M1.4 touch. junrar has no benchmark harness; the ported guards are
branch-predictable compares taken verbatim from upstream's own hot loops, and
oracle byte-identity is the acceptance. A reported regression becomes its own JMH
work item — not a silent gap, a named deferral.

---

## 7. Milestone → deliverable map

| After | A junrar user can… | Version event |
| --- | --- | --- |
| Phase 0 | …configure junrar via `ArchiveOptions` (char[] passwords, `maxDictionarySize` default 4 GiB — P0.8); otherwise internal: all 6 UNPINNED no-go rows pinned; T1/T4/T6 fixed (non-ASCII RAR3 passwords + UTF-8 names now correct); RAR3 header CRCs verified with unrar tolerance; JRE 8 runtime smoke in CI | patch/minor |
| M1 | …feed junrar hostile RAR3 archives that now fail deterministically like unrar 7.2.7 (PPMd/LZ/name-decoder guards, filter/channel caps); PPMd archives with MaxMB>1 extract (S8 decision) | minor |
| M2 | …extract RAR3 VM-filter archives with unrar ≥5.5.1 semantics (6 canonical filters native, others no-op); interpreter attack/bug surface (T3) deleted | minor (behavior note) |
| M3 | …**extract RAR5 archives end-to-end**: plain/solid/multi-volume (`.partN`), `-p` and `-hp` encrypted (with wrong-password detection), Blake2sp-verified, UTF-8 names, ns timestamps, uowner/version records, symlinks/hardlinks with full traversal safety — dictionaries ≤ 1 GB | **breaking major** (the RAR5 release) |
| M4 | …extract RAR7 (method 70) archives incl. fractional dictionaries; dicts ≤ 1 GB from M4.2, ≤ 64 GB from M4.3 (engine capability raise; > 4 GiB stays caller opt-in via `maxDictionarySize`), RAR5-compat streams | minor on the new major |

---

## 8. Review & conversion notes (for the independent reviewer)

- Every chunk above is intended to become one GitHub sub-issue under a per-phase
  main issue; the chunk block already carries the sub-issue's required fields
  (objective, refs, acceptance, fixtures, no-go risk).
- Chunk-size discipline: ≤ ~600 changed lines (M2.2's deletion exempted); a chunk
  that grows past it in implementation splits at the commit boundaries already
  named in its objective.
- Open decisions requiring owner sign-off before implementation: Java floor stays 8
  (§2.1); Blake2sp pure-Java port with `bcprov` as a `testImplementation`-only
  differential oracle (§2.2, re-argued on corrected facts per review F1); the S8
  PPMd policy (P0.5 — probe now executed, decision pinned: honor `MaxMB+1` like
  upstream, budget-gated via P0.8); the dictionary resource model (capability
  1 GB→64 GB at M4.3, `ArchiveOptions.maxDictionarySize` default 4 GiB
  permanent, growth-capped/lazy allocation — §6 R1, reviews F5 + B-S3); the
  `ArchiveOptions` API shape incl. the no-deprecation decision (P0.8/§5.2,
  review B-S7); C11 execution-semantics supersession in M2 (§3 M2.1); SFX
  in-scope disposition (M3.1, review F4); the release-event split (§5.4,
  review F8).

---

## Appendix A — review disposition (adversarial review of `7a0f9369`, 2026-07-17)

Verdict was APPROVE-WITH-CHANGES. Every finding dispositioned; probes marked
"re-executed" were run again for this revision (this-session artifacts), not
inherited from the review.

| Finding | Disposition | Where / evidence |
| --- | --- | --- |
| F1 (BLOCKING) — §2.2 rested on false "BC lacks Blake2sp" | APPLIED | §2.2 re-argued on true facts (bcprov ships `Blake2spDigest`); new recommendation = pure port in main + `bcprov` `testImplementation`-only differential oracle; manual §5.5/§9.2 corrected in a separate commit (`docs: correct Blake2sp/Bouncy Castle claim in migration manual`). |
| F2 — tiny-payload big-dict fixtures unproducible | APPLIED | Probes re-executed (19 B payload + `-md2g` → recorded `-md=128k`; 33 MB zeros + `-md32m` → `-md=32m`, 1,543 B archive; 1.1 GiB zeros + `-md1g` → `-md=1g`, 46,769 B archive, 9 s) and pasted into §4.2; dict rows reworked (payload ≥ dict; ≥1 GB rows non-PR digest-sink; 2–64 GB rows byte-patched); §4.3 gained the sanctioned inflated-header class; M3.6/M3.7/M4.1/M4.3 acceptance reworked with CI vs scheduled/local split. |
| F3 — M3 acceptance unexecutable behind the V5 gate | APPLIED | Pre-gate harness = package-private test-only `Archive` factory, a named M3.2 deliverable, deleted in M3.11's gate-lift commit; M3.3–M3.10 acceptance lines annotated; M3.4's decode-dependent rows explicitly deferred to M3.7 / post-M3.5. |
| F4 — SFX silently undispositioned | APPLIED | In scope: bounded ≤ 4 MB (`MAXSFXSIZE`, `d861246:rardefs.hpp`) signature scan added to M3.1 with concatenation fixtures + hostile no-signature row; non-goals section cross-references it. |
| F5 — M3 header-driven 1 GB alloc unguarded until M4.1 | APPLIED — **superseded by B-S3** | Round-1 fix: `maxDictionarySize` knob pulled into M3.6, default 1 GB. External review B-S3 showed that fix still shipped the hole (a default-config tiny archive could force a 1 GB alloc) and kept a default-raise at M4.1 ahead of the M4.3 capability. Current design (Appendix B S3): knob in P0.8 (`ArchiveOptions`, default 4 GiB = upstream, permanent), growth-capped/lazy allocation, capability raised only at M4.3. |
| F6 — three wrong §1.1 census cells | APPLIED | Re-executed: 161 steps (`rev-list --count`); 5.0.0 = 134 files +13541/−9332, 32 new files (`--diff-filter=A -M`; 29–33 rename-detection-sensitive, noted); 7.1.1 = 45 files +862/−406, `array.hpp` −169, zero unicode.cpp churn (unicode moved at 7.0.1, +266). Conclusion unchanged (strengthened). |
| F7 — KDF acceptance couldn't catch dkLen=96 mistake | APPLIED — **but the fix itself was wrong** | M3.4 brief mandated three PBKDF2 invocations at c/c+16/**c+17** — the c+17 offset was wrong (correct: c+32), inherited from the planner's own pre-review M3.4 text and `unrar-delta-map.md` §2.6, and repeated (not caught) by this finding. Corrected per external review B-S1 (Appendix B): upstream runs segments `{Count-1,16,16}`, snapshots at c/c+16/c+32 (`d861246:crypt5.cpp:105-125`, confirmed by independent recomputation of the `TestPBKDF2` vectors). The dkLen=96 prohibition stands. |
| F8 — §5.4 vs §7 release-event contradiction | APPLIED | Decision recorded in §5.4: breaking-major rationale = RAR5 flip alone; P0.7 strictness + M2 filter semantics = minor-with-release-note (both corpus-evidenced moves toward unrar semantics); §7 authoritative. |
| F9 — intermediate corpus audits under-enumerated | APPLIED | Scoped §4.4 steps 1–3 runs added to P0.6, M1.2, M1.3, M1.4 acceptance; §4.4 intermediate list widened accordingly. |
| F10 — corpus audit shape rejects encrypted members' legitimate flip | APPLIED | §4.4 step 3 now allows shape (b): exception swap to the §5.3 encrypted-open outcome, headers absent, encryption flags consistent. |
| F11 — M1.3 missing C15 row | APPLIED | C15 added to M1.3's no-go rows (`Unpack15` is a C15-class file). |
| F12 — P0.4 ledger count mechanically ill-defined | APPLIED | Exact pipeline specified and committed with the ledger: `grep -oE '>{2,3}' <file> \| grep -cx '>>'` per file; non-code hits triaged in a ledger verdict column; outputs recorded in the header. |
| F13 — stale procurement note; `-ma4` ASSUMED now resolvable | APPLIED | §4.2 updated: rar/unrar 7.23 at `~/.local/bin`; `-ma4` probe re-executed (`ERROR: Unknown option: ma4`) and pasted; era rar 6.24 macOS tarball pre-verified (HTTP 200, `application/x-gzip`, 607,872 B, gzip magic `1f 8b`); residual ASSUMED (6.24 `-mct+` PPMd emission) named with its probe point (P0.5). |
| N1 — "20-row" vs 25-row no-go table | APPLIED | Preamble + M3.11 sweep now say "every row of manual §7 (25 rows: S1–S8, C1–C15, D1–D2)". |
| N2 — number drift (PPMd 526; blake2 line counts) | PART-REBUTTED / PART-APPLIED | Blake2 counts APPLIED (183 + 153 at `8f437ab`, re-measured). PPMd recount REBUTTED with an executed probe: `git diff 2e71167 d861246 -- model.cpp suballoc.cpp \| wc -l` → **526** exactly (2026-07-17), matching the delta-map and M1.2's text; the reviewer's 442/514 did not reproduce under the quoted command. Text unchanged. |
| N3 — "~90 audits vs 5" rhetorical | APPLIED | §1.1 row rewritten in the honest form: shipped states owe audits; stepping's intermediates either ship (audit each) or never ship (step bought nothing). |
| N4 — M3.7 overflow risk with no split boundaries | APPLIED | Three internal commit boundaries named in M3.7 (decode loop / CopyString+FirstWinDone / solid+lifecycle+dispatch). |
| N5 — RAR3 comment decompression undispositioned | APPLIED | Non-goals: RAR3 compressed-comment extraction stays out (current parse/skip behavior preserved); RAR5 `CMT` parsed in M3.3, payload decompression equally a non-goal. |

Also applied from the axis verdicts: the axis-G perf note — a benchmark harness for
the M1.2/M1.4 hot paths is consciously waived (named deferral after §6's table).

---

## Appendix B — external review disposition (GPT-5.6 Sol, 2026-07-17)

Verdict was REQUEST CHANGES (9 findings). Every finding independently verified
against primary sources before disposition; all probes below executed this
session (2026-07-17).

| Finding | Disposition | Where / evidence |
| --- | --- | --- |
| S1 (BLOCKING) — KDF derivation wrong (c/c+16/c+17; 3× provider calls; PBKDF2WithHmacSHA256 not guaranteed) | APPLIED | Confirmed: `d861246:crypt5.cpp:105-125` runs `CurCount[]={Count-1,16,16}` over one XOR accumulator → snapshots at c/c+16/**c+32**; independent Python `hashlib.pbkdf2_hmac` reproduces all three `TestPBKDF2` Key vectors byte-exact, proving the semantics. Java 8 javadoc (doc-verified): required `SecretKeyFactory` = DES, DESede only — NO PBKDF2 of any flavor; `Mac HmacSHA256` required. M3.4 respecified: hand-rolled single-pass loop over `Mac("HmacSHA256")`, all six KATs (Key/V1/V2 × 3 cases) computed and pasted into the chunk (replacing the "instrumented unrar" plan). Provenance fixed at the root: `unrar-delta-map.md` §2.6 and manual §4.11 carried c+17 and the SecretKeyFactory recommendation — both corrected (`72c2d49`); Appendix A F7 row annotated (its "fix" repeated the c+17 error, which the planner's own pre-review text introduced). |
| S2 — regression flip cannot produce the specified JSON shapes | APPLIED | Confirmed: `ArchiveRecord.fromArchive` hardcodes `isRarV5=false`; `fromException` keys on `UnsupportedRarV5Exception` only. Counts confirmed: 345 of 12,475 JSONs reference RAR5 (grep executed; the "12,475 flip" wording fixed to "345 flip, 12,130 byte-identical"). `-p` vs `-hp` probed with rar 7.23: `-p` lists passwordless, `-hp` → "Incorrect password". Fixes: record model repaired in M3.1 (public `Archive.getFormat()`; `fromException` gains the path + 8-byte signature probe — zero corpus churn pre-flip); §4.4 audit now has THREE shapes (plain / file-encrypted listable / header-encrypted exception-swap to `WrongPasswordException`); oracle check covers ALL 345 flipped members, not a 20-sample. |
| S3 — dictionary guard unsafe (default still allows tiny-archive 1 GB alloc) and mis-ordered (default raised at M4.1 before M4.3 capability) | APPLIED | Confirmed, plus upstream anchor found: unrar's own default extraction limit is 4 GiB (`d861246:options.cpp:13` `WinSizeLimit=0x100000000`; `-mdx` raises; `uiDictLimit` refuses). Redesign: capability (1 GB flat at M3.6 → 64 GB at M4.3 only) / budget (`ArchiveOptions.maxDictionarySize`, default 4 GiB, permanent — M4.1 raises nothing, now parse-only) / allocation (growth-capped flat window at M3.6, lazy segments at M4.3 — a dict-bomb allocates KBs under DEFAULT options, asserted by a new mandatory hostile row). PPMd 256 MB exposure budget-gated by the same knob (P0.5 + P0.8). |
| S4 — header-CRC contract diverges from unrar semantics | APPLIED | Confirmed at both eras: RAR3 = 16-bit (`GetCRC15`, `~CRC32&0xffff`; `2e71167:arcread.cpp:258`), exemptions (SIGN/AV/old-UO; comment-prefix coverage), file-header mismatch = record+continue, encrypted = fatal (`d861246:arcread.cpp:431-445,514-545`); RAR5 = CRC32 (`GetCRC50`), unencrypted mismatch = "Report, but attempt to process" (`:683-696`). P0.7 respecified with the full format-specific contract + one named conscious divergence (extract-time throw on broken file headers); M3.2 carries the RAR5 record-vs-fatal split; manual §5.1 + §6 T7 corrected (`72c2d49`). |
| S5 — RAR5 exact header-size boundary missing | APPLIED | Confirmed: first read = 7 bytes (4 CRC + ≤3 size-vint bytes), `MAX_HEADER_SIZE_RAR5 0x200000` (`d861246:arcread.cpp:634-641`, `archive.hpp:24`). M3.2 gains the pre-allocation rejection contract (allocate only after the ≤2 MB bound holds) + boundary rows (3-byte max accepted / 4-byte reject / BlockSize 0 / no-allocation assertion); M3.1 notes the general 10-byte vint vs the 3-byte header-size field split. |
| S6 — M3.11 red→green invalid (expectations edited between red and green) | APPLIED | Confirmed against the frozen-test mandate. M3.11 respecified: NEW public-surface success tests authored + executed RED (failing with `UnsupportedRarV5Exception`, hashes recorded) before the gate lift, byte-identical through GREEN; old gate-pinning tests retired separately in the lift commit, counted as evidence of nothing. |
| S7 — API needs one construction-time config path; char[] overloads ambiguous | APPLIED (with one nuance) | Nuance: `new Archive(file, null)` is ALREADY ambiguous today — javac probe: "reference to A is ambiguous" between `(File,String)`/`(File,UnrarCallback)` — so the hole predates char[]; the conclusion stands and the fix removes rather than compounds it. New P0.8 chunk = the contract: `ArchiveOptions` builder (password char[] defensive-copy, `maxDictionarySize` long, callback), new `(source, ArchiveOptions)` ctors, `Junrar.extract(..., options)` overloads (reaches one-shot calls), wipe-on-close on `Archive`, String ctors kept undeprecated with Javadoc pointer; §5.2 rewritten as THE contract. |
| S8 — Java 8 needs a runtime gate | APPLIED | Confirmed: `ci.yml` runs JDK 21 only (probed). New P0.9 chunk: `jre8-smoke` CI job — JDK 21-built jar, Temurin 8 runtime, framework-free main extracting plain + AES RAR3 fixtures with digest asserts (JCE provider behavior on a real JRE 8); M3.11 extends with RAR5 plain + encrypted rows. Red canary demonstrated at introduction. |
| S9 — unresolved implementer choices | APPLIED | Full-document sweep + probes executed, every hit pinned: P0.1 → `CrcErrorException` (masked-wrap else branch probed, `Unpack20.java:230-235`); P0.5 → policy pinned (`d861246:model.cpp` `DecodeInit`: `StartSubAllocator(MaxMB+1)`, no extra cap); M1.1 → fixture = P0.5's (no discovery step); M1.2 → keep `ppmError` latch (sites `Unpack.java:74,168,197,622`); M1.5 → path = `rarfile/FileNameDecoder.java`; M3.1 → new `io/VInt.java`; M3.3 → loader-into-unified-`FileHeader` (no `FileHeader5` type); M3.4 → `crypt/Rar5Crypt.java`; M3.6 → own bit input with `getbits32` (junrar has none — grep probe); §4.2 `-ma4` row updated to the executed probe. |

### Own-initiative changes (same evidence bar, beyond the 9)

- **KDF KATs generated and embedded** (M3.4): V1/V2 vectors at c+16/c+32 for all
  three upstream test cases computed via independent PBKDF2 — no instrumented
  unrar run needed; a c+17 or dkLen=96 implementation fails them by construction.
- **`WrongPasswordException` pinned for passwordless `-hp` open** (§4.4 shape (c),
  §5.3, M3.4): unrar itself reports passwordless header-encrypted open as
  "Incorrect password" (probe); kills the residual "or the recorded
  encrypted-open exception type" vagueness, and M3.2's encrypted-bad-CRC path
  maps to the same type (indistinguishable from a wrong key under CBC).
- **Lg2Count wording fixed** (M3.3): "≤ 24 reject" → "> 24 → reject"
  (`CRYPT5_KDF_LG2_COUNT_MAX 24`, `d861246:crypt.hpp:20`).
- **KDF cache size cited** (M3.4): `KDF5Cache[4]`, `d861246:crypt.hpp:88`.
- **PswCheck fold made precise** (M3.4): `PswCheck[i%8] ^= V2[i]`
  (`SIZE_PSWCHECK 8`).
- **§2.1 stdlib table row corrected** (part of S1 fallout): the KDF row no longer
  cites `SecretKeyFactory PBKDF2WithHmacSHA256` as the Java-8 API.
- **Delta-map inventory re-check**: every §2 component of
  `reports/unrar-delta-map.md` maps to a chunk (2.1→M3.1, 2.2→M3.2, 2.3–2.5→M3.3
  /M3.10, 2.6→M3.4, 2.7→M3.5–M3.8, 2.8→M4, 2.9→M3.9); no uncovered component
  found.
- **P0.7 broken-header surfacing**: new `FileHeader.isBrokenHeader()` accessor
  named in §5.1 (the tolerance contract needs an observable).
