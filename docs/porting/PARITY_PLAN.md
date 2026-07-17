# junrar → unrar 7.2.7 extraction parity plan

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
human-parseable (small logical commits); the 20-row no-go list survives and the 6
UNPINNED rows get pinning tests before any re-port touches their files.

---

## 1. Strategy decision — component milestones, version-ordered inside

**DECISION: milestone/component-based division (Phase 0 + M1–M4), with
version-ordered stepping used *inside* the milestones where it is genuinely the
easier unit of work.** The delta map's M1–M4 sketch
(`reports/unrar-delta-map.md` §5) is adopted with amendments (below), not
rubber-stamped.

### 1.1 The rejected alternative: version-by-version stepping (3.7.3 → … → 7.2.7)

A full walk is ~90 release steps (`git -C ~/git/unrar log --reverse master`). The
census evidence kills it:

| Evidence | Consequence for version-stepping |
| --- | --- |
| 5.0.0 (`a224d33`) is a single release commit containing the whole file split **and** RAR5 wholesale: unpack.cpp 1187→305 lines, 51 new files incl. `headers5.hpp`, `crypt5.cpp`, `unpack50.cpp` (`unrar-delta-map.md` §1, §5) | The hardest step of the walk is itself a mega-diff. Version-stepping fails to decompose exactly the work it would need to decompose; the RAR5 lift must be split by *component* no matter what. |
| 7.1.1 (`a2e5484`) is a std::vector/std::wstring sweep — 952 lines of unicode.cpp churn, zero format change (`unrar-delta-map.md` §2.8, §4.8) | A version-stepper ports pure textual noise; a component plan compares semantics and skips it. |
| junrar's class topology is not unrar's file topology: one `Unpack` chain vs `#include` split, eager header list vs lazy cursor (manual §4.1, §5.1) | Intermediate "unrar 4.x-shaped junrar" states have no Java counterpart to validate against and never ship. Each of ~90 intermediate states would still owe a 20-row no-go re-verification (manual §7) — ~90 audits vs 5. |
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
   so the 12,475-JSON corpus flips exactly once (§4.4).

---

## 2. Platform decisions

### 2.1 Java version — **stay at release 8** (recommendation; owner sign-off flag)

The bump permission requires "a strong reason (e.g. stdlib improvements that would
make it significantly easier to port)". Enumerating every stdlib API this port
actually needs shows none is missing at 8:

| Port need | API | Available since |
| --- | --- | --- |
| RAR5 KDF (crypt5.cpp:85) | `SecretKeyFactory` `PBKDF2WithHmacSHA256` | 8 |
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

### 2.2 Dependencies — **zero new runtime dependencies; port Blake2sp in pure Java**

Runtime today = `org.slf4j:slf4j-api` only (build.gradle:18; manual §3). Per-need
analysis:

| Need | Candidates | Decision |
| --- | --- | --- |
| **Blake2sp** (FHEXTRA_HASH type 0, 32-byte file hashes) — the one primitive the JDK lacks (manual §4.11, §5.5) | (a) pure-Java port of `blake2s.cpp` (~300 lines, self-contained, no SIMD needed) + `blake2sp.cpp` 8-lane tree composition (~120 lines); (b) Bouncy Castle `bcprov` `Blake2sDigest`; (c) small third-party libs (e.g. jblake2) | **(a) pure-Java port.** Decisive fact: BC provides Blake2**s** but NOT Blake2**sp** — the 8-lane tree composition would still be hand-built on top (manual §5.5), so the dependency saves only the ~200-line core compression function while costing a ~8 MB jar, a notorious version-conflict surface (BC on Android/classpath), and the "zero deps" property. Small libs fail the "reputable" bar and also lack sp. The core is frozen (RFC 7693) and validated mechanically: RFC 7693 BLAKE2s vectors + official BLAKE2sp test vectors from the reference implementation + real-fixture cross-check against unrar (§4.2). |
| PBKDF2-HMAC-SHA256, AES-256-CBC, SHA-256, HMAC-SHA256, CRC32 | JDK | JDK (manual §4.11 policy: KDF/format math ported, primitives never). |
| Everything else (vint, LZ, filters, headers) | — | Plain Java. No candidate dependency shrinks any of it. |

**Verdict: runtime dependency list stays `slf4j-api` only.** No ecosystem cost.
**Flagged for owner sign-off** (Blake2sp port ≈ 400–500 Java lines is the price).

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
  green; test asserts extraction of the fixture yields
  `CrcErrorException`/`CorruptHeaderException` (whichever the guard produces —
  recorded in the test) and NOT `ArrayIndexOutOfBoundsException`.
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
  equals `grep -c '>>' <5 files>` output (recorded in the ledger header).
- **Fixtures:** none (synthetic buffers).
- **No-go rows:** C15 (pins the class as far as unit-pinnable).
- **Est. diff:** ~300 lines.

#### P0.5 — S8 decision: PPM MaxMB policy (investigate → decide → red→green)

- **Objective:** replace the silent `MaxMB = 1` clamp (`ModelPPM.java:188-189`) with
  a conscious policy. Investigation step: probe `d861246:model.cpp` for the bound
  unrar honors (the field is one byte, so the format-inherent max is ≤ 256 MB
  suballocator). **Recommended decision (to be confirmed by the probe): honor the
  header value like unrar does — the inherent ≤ 256 MB bound is the DoS ceiling —
  and document it; a configurable cap is added only if the probe shows unrar itself
  caps lower.**
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
  header with `unpSize` sentinel.
- **Fixtures:** `password/rar3-nonascii-password.rar`, RAR3 UTF-8-name archive.
- **No-go rows:** C10 (filename validation must keep passing), C4 fallback behavior.
- **Est. diff:** ~350 lines + fixtures.

#### P0.7 — header-CRC verification scaffolding (T7)

- **Objective:** introduce header-CRC32 verification for RAR3 block headers
  (mismatch → `CorruptHeaderException`), the infrastructure RAR5 (which MUST verify,
  manual §5.1) will reuse. Behavior change on corrupt archives — gate on corpus
  evidence: run the regression corpus before merge; any legit archive newly failing
  is investigated before the chunk lands.
- **Files:** `rarfile/BaseBlock.java`, `Archive.java` read loop, tests, fixtures.
- **C++ refs:** `2e71167:arcread.cpp:258-271` (`BrokenHeaderMsg` semantics);
  `d861246:arcread.cpp` for current shape.
- **Patterns:** manual §6 T7, §4.9 (new failure mode → typed exception? no — reuse
  `CorruptHeaderException`; no `setChannel` filter change needed since it already
  rethrows CorruptHeader).
- **Acceptance:** byte-patched bad-CRC fixture → `CorruptHeaderException` through
  all four public surfaces (manual §8.1); corpus run delta reviewed and attached to
  the PR (expected: only genuinely corrupt members change outcome).
- **Fixtures:** `abnormal/bad-header-crc.rar` (byte-patched).
- **No-go rows:** S1–S4 (same read loop — their tests must stay green), C9.
- **Est. diff:** ~200 lines + fixture.

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
- **Fixtures:** existing PPMd-compressed fixtures (identify via corpus/`audio` set;
  add one `-mc`-forced PPMd fixture if none exists — reuse P0.5's).
- **No-go rows:** none directly; enables S8-adjacent safety.
- **Est. diff:** ~250 lines.

#### M1.2 — PPMd corrupt-input guards (3.9.1, 3.9.8, 5.6.1)

- **Objective:** port, in release order: range-coder `count >= scale → false` in
  decodeSymbol1/2 (3.9.1 `a0d5a16`); `SafePPMDecodeChar` wrapper (3.9.8 `48bbbd3`,
  now `d861246:unpack30.cpp:4`); `ps[MAX_O]` overflow guards in
  `CreateSuccessors`/`decodeSymbol2` (5.6.1 `9c55010`). Decide the error-latch shape
  first: keep junrar's `ppmError` latch OR adopt 3.7.7+ `CleanUp()`+`BLOCK_LZ` —
  one shape, never mixed (manual §5.3). Default: keep `ppmError` (smaller diff,
  already pinned by engine-entry checks `Unpack.java:74,168-170`).
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
  the attempt and mark the guard defensive, manual §8).
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
  test); existing filter fixtures stay green.
- **Fixtures:** `abnormal/filter-flood.rar`, `abnormal/channel-flood.rar`,
  `abnormal/flagsplace-oob.rar` (§4.3 byte-patched).
- **No-go rows:** C11 (VM enum tests stay green), C2 (audio init untouched).
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
  never-written-window hostile fixtures → typed exception; audio/solid suites green.
- **Fixtures:** byte-patched distance-into-void archives per engine (15/20/29).
- **No-go rows:** **C1 (its file is edited — pin landed in P0.1), C2, C15.**
- **Est. diff:** ~350 lines.

#### M1.5 — encname bounds rewrite + method-36 drop

- **Objective:** mirror the fully bounds-checked `encname.cpp` rewrite into
  `FileNameDecoder` (every index access preceded by a bounds check, growable
  output); drop method 36 ("alternative hash" experiment, dropped upstream in 5.0.0
  — `unrar-delta-map.md` §3) with corpus evidence that no corpus member regresses.
- **Files:** `unpack/decode/…FileNameDecoder…` (actual path: `io/…` — locate via
  `grep -rn FileNameDecoder src/main`), `Unpack.java` dispatch, tests.
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
**The `UnsupportedRarV5Exception` gate at `Archive.java:359` stays until M3.11**;
until then new RAR5 code is reached only by direct unit tests. All new RAR5 classes
are brand-new code (manual/test-mandate exception 2: no red run against the void;
tests still ship asserting real behavior).

#### M3.1 — vint reader + signature dispatch skeleton

- **Objective:** shared vint reader (base-128 LE, continuation bit 0x80, overflow
  guard — max 10 bytes); RAR format detection (`52 61 72 21 1A 07` + version byte:
  `00`→RAR15, `01`→RAR50, `02..04`→ graceful `UnsupportedRarVersionException`
  (new)); an internal `RarFormat` tag on `Archive`.
- **Files:** `io/Raw.java` or new `io/VInt.java`; `rarfile/MarkHeader.java`;
  `Archive.java` (detection only — V5 gate untouched); new exception; tests.
- **C++ refs:** `d861246:archive.cpp:100-126` (`IsSignature`),
  `d861246:rawread.cpp` (`GetV`/`RawGetV` overflow handling).
- **Patterns:** manual §4.7 (Raw house style — no ByteBuffer), §4.9 (new exception →
  `setChannel` filter decision recorded: `UnsupportedRarVersionException` must be
  rethrown, add to the filter).
- **Acceptance:** vint unit tests: 1..10-byte values, boundary 2^63, overflow →
  exception, hostile continuation-bit floods; signature tests for versions
  00/01/02/03/04 via crafted byte arrays.
- **Fixtures:** none (synthetic buffers) + a tiny future-version fixture
  (byte-patched marker).
- **No-go rows:** S3 (mark validity tests stay green).
- **Est. diff:** ~350 lines.

#### M3.2 — RAR5 block-header framework (CRC-verified, loop-guarded)

- **Objective:** the RAR5 read loop: `CRC32(4) | vint HeadSize | vint Type | vint
  Flags [| vint ExtraSize][| vint DataSize]`; header CRC32 **verified** (P0.7 infra;
  manual §5.1 — the gap must not be inherited); HFL_SKIPIFUNKNOWN honored; unknown
  non-skippable → `CorruptHeaderException`; `safelyAllocate` on every tail;
  `processedPositions` loop guard + explicit seek-past-data (S1/C6 parity);
  eager-parse into the same `headers` list.
- **Files:** new `rarfile/rar5/` package (`Rar5BaseBlock` etc.), `Archive.java`
  RAR5 branch, tests.
- **C++ refs:** `8f437ab:arcread.cpp:555-…` (`ReadHeader50`),
  `d861246:headers5.hpp` (flag values).
- **Patterns:** manual §5.1 (new-block recipe + RAR5 hook points), §4.6 (wire-value
  enums with null-returning `findX` + `@EnumSource` test — the C11 lesson), §4.8.
- **Acceptance:** header-level unit tests parsing crafted blocks; hostile rows:
  bad CRC, HeadSize overflow/underflow, ExtraSize > HeadSize, vint flood, truncated
  mid-header, repeated position → typed exceptions (manual §8.1 four-surface
  pattern once M3.11 opens the gate; direct-parse tests until then).
- **Fixtures:** synthetic byte arrays; byte-patched real RAR5 fixtures (`rar5.rar`
  exists in-tree).
- **No-go rows:** S1, S3, S4 analogs re-applied to the new path (each gets a RAR5
  hostile fixture row).
- **Est. diff:** ~500 lines.

#### M3.3 — main/end/crypt headers + file/service headers with all extras

- **Objective:** parse HEAD_MAIN (+MHFL flags, MHEXTRA_LOCATOR skipped consciously,
  MHEXTRA_METADATA), HEAD_ENDARC (+EHFL_NEXTVOLUME), HEAD_CRYPT (fields only:
  version==0 check, Lg2Count ≤ 24 reject, Salt16, pswcheck presence); HEAD_FILE /
  HEAD_SERVICE with every FHEXTRA record: CRYPT, HASH (Blake2sp digest bytes),
  HTIME (Unix/Windows, ns flag), VERSION, REDIR (type + flags + target), UOWNER,
  SUBDATA; UTF-8 names via explicit `StandardCharsets.UTF_8`; map onto the existing
  `FileHeader` surface (unified model — `unrar-delta-map.md` §4.2) so
  `getFileName()/getFullUnpackSize()/isDirectory()/…` behave for RAR5 entries.
- **Files:** `rarfile/rar5/*` (MainHeader5, FileHeader5 or loader into FileHeader —
  decide for the unified-model shape, record in PR), tests.
- **C++ refs:** `8f437ab:arcread.cpp` (`ReadHeader50` file branch,
  `ProcessExtra50` at `d861246:arcread.cpp:982`), `d861246:headers5.hpp`
  (FHFL/FHEXTRA values), `unrar-delta-map.md` §2.3–2.5.
- **Patterns:** manual §5.1 (RAR5 hooks: salt16 as new fields — never widen RAR3
  SALT_SIZE 8; service headers mirror NEWSUB machinery, not legacy SUB_HEAD), §4.6.
- **Acceptance:** per-record unit tests incl. hostile rows (oversized name, zero
  name, non-UTF-8 bytes, REDIR target with traversal separators — parsed but
  flagged, extraction-side guard comes in M3.10, record cross-ref); listing tests on
  real fixtures (dirs, times incl. ns, uowner, version, comment service header).
- **Fixtures:** §4.2 variant matrix rows (times/uowner/version/comment/links).
- **No-go rows:** S4 analog (nameSize<=0), C10 (RAR5 names feed the same
  `isFilenameValid` gate).
- **Est. diff:** ~600 lines (largest header chunk; split VERSION/UOWNER/SUBDATA into
  a follow-up commit inside the chunk if it crowds 600).
- **Parallel-safe** with M3.4/M3.5 after M3.2.

#### M3.4 — RAR5 crypto: KDF, pswcheck, header + data decryption

- **Objective:** PBKDF2-HMAC-SHA256 KDF via JDK (`2^Lg2Count` iterations); the
  SetKey50 derived trio (AES key at count, HashKey at count+16, PswCheck at
  count+17 folded to 8 bytes); pswcheck verification (+SHA-256-truncated 4-byte
  csum) → new `WrongPasswordException` **added to the `setChannel` rethrow filter**
  (manual §4.9 trap — stated in PR); header decryption: AES-256-CBC via
  `RawDataIo.setCipher` with per-header 16-byte IV, align-to-16 applied on BOTH the
  read path and `getHeaderSize(encrypted)` accounting (manual §4.12); per-file
  decryption via the `ComprDataIO.init(FileHeader)` FHEXTRA_CRYPT branch;
  `ConvertHashToMAC` (HMAC-SHA256 with HashKey) for HASHMAC-protected checksums;
  4-entry KDF cache (5.2.1 `f0474dc` — cheap, needed for multi-file archives);
  password bytes = UTF-8 (T4 lesson).
- **Files:** new `crypt/Rar5Crypt.java` (or similar), `io/RawDataIo.java`,
  `unpack/ComprDataIO.java`, `exception/WrongPasswordException.java`,
  `Archive.java` filter list, tests.
- **C++ refs:** `8f437ab:crypt5.cpp` (`pbkdf2` :85, `SetKey50`,
  `ConvertHashToMAC` :193), `unrar-delta-map.md` §2.4/§2.6; the disabled
  `TestPBKDF2` vector in crypt5.cpp as a port reference vector.
- **Patterns:** manual §4.11, §4.12 (align-16 both paths), §4.9.
- **Acceptance:** KDF unit test against the crypt5.cpp reference vector; wrong
  password on a pswcheck archive → `WrongPasswordException` (not CRC failure);
  right password on `-p` and `-hp` fixtures lists+extracts oracle-identical;
  Lg2Count 25 → typed reject; non-ASCII UTF-8 password fixture green.
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
- **Acceptance:** RFC 7693 BLAKE2s vectors + official BLAKE2sp reference vectors
  green; a Blake2-checksummed fixture verifies end-to-end (extraction cross-checked
  vs unrar oracle in M3.11).
- **Fixtures:** `-htb` archive (§4.2).
- **No-go rows:** D2 (CRC32 sites untouched; `checkOldCrc` retained).
- **Est. diff:** ~550 lines.
- **Parallel-safe** with M3.3/M3.4.

#### M3.6 — Unpack5 skeleton: sibling engine, window, tables

- **Objective:** `Unpack5` as a **sibling** engine class taking `ComprDataIO`
  (manual §5.2 — NOT extending `Unpack20`); dynamic window sizing from the header
  (`0x20000 << bits`, 128 KB–4 GB; min alloc 0x40000; solid streams never shrink or
  grow the window mid-set); **M3 cap: window ≤ 1 GB in a single `byte[]`** — larger
  → new `UnsupportedDictionarySizeException` (typed, documented, lifted in M4);
  bit input with `getbits32` (check `8f437ab:getbits.hpp` before reuse — manual
  §5.2); RAR5 block header read (BlockSize/BitSize/LastBlockInFile/TablePresent) +
  the 5 Huffman tables {LD 306, DD 64, LDD 16, RD 44, BD 20} with table-present
  logic; `ExtraDist` constructor flag designed in from day one (RAR7 = same engine,
  manual §5.2) but fixed `false` until M4.
- **Files:** new `unpack/Unpack5.java` (+ small `unpack/decode/` additions for
  RAR5 alphabets), `exception/…`, tests.
- **C++ refs:** `8f437ab:unpack50.cpp` (block header + table read),
  `8f437ab:unpack.cpp:80-158` (`Init(uint64,bool)` window rules),
  `d861246:compress.hpp` (NC=306, DCB=64, LDC=16, RC=44, BC=20, MAX_LZ_MATCH).
- **Patterns:** manual §4.1 (sibling rule), §4.2 (all guard distances re-derived
  from 6.2.12 source, never copied from RAR3's −260/−270/−300), §5.2.
- **Acceptance:** table-read unit tests on crafted block streams (incl. hostile:
  truncated tables, oversized bit lengths); window-size matrix test: 128 KB, 32 MB,
  1 GB accepted; 2 GB/4 GB → typed exception; solid window rule pinned.
- **Fixtures:** synthetic streams; real fixtures land with M3.7.
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
  unrar oracle payload SHA-256s (fixtures §4.2); solid multi-file fixture extracts
  in-order, out-of-order, and reverse (mirroring the existing RAR4 solid tests);
  hostile rows: distance-into-void, oversized match, truncated stream → typed
  exception.
- **Fixtures:** core RAR5 matrix (§4.2): m0/m3/m5 × {plain, solid} × dict
  {128 KB, 32 MB}.
- **No-go rows:** C1-family (wrap guards in new code — same hazard, new site),
  C15.
- **Est. diff:** ~600 lines.

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
- **Acceptance:** per-filter fixtures extract oracle-identical (x86 exe → E8/E8E9,
  ARM binary → ARM, WAV/BMP-like data → DELTA — creation §4.2; if rar declines to
  emit a filter for an input, unit-test the transform against a synthetic filter
  block and record the fixture as unattainable); hostile: filter flood > 8192,
  block > 0x400000 → typed exception/no-op matching unrar.
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
- **Acceptance:** 3-part fixture set extracts from File and from InputStreams;
  missing part2 → typed exception; split-file CRC verifies across the span; solid
  multi-volume row.
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
  Unix CI (Windows CI: creation skipped, metadata asserted).
- **Fixtures:** `-ol` link fixtures, benign + hostile (§4.2 — hostile ones
  byte-patched: rar refuses to *create* hostile targets).
- **No-go rows:** **S5, S6, S7 — their existing tests must stay green and gain RAR5
  twins.**
- **Est. diff:** ~500 lines.

#### M3.11 — integration: lift the V5 gate, corpus flip, API surface

- **Objective:** remove the `UnsupportedRarV5Exception` throw (`Archive.java:359`),
  route RARFMT50 archives through the new stack end-to-end; `@Deprecated` the
  now-unthrown exception class (retirement path §5.3); audit the `setChannel`
  catch filter one final time against every new exception type (checklist in PR);
  four-surface tests (manual §8.1) for the whole RAR5 fixture matrix; regression
  corpus regeneration per §4.4.
- **Files:** `Archive.java`, `Junrar.java` (no signature changes expected),
  exception Javadoc, regression JSONs, tests.
- **C++ refs:** none new.
- **Patterns:** manual §8.2 (the corpus gate), §4.9.
- **Acceptance:** **red→green (behavior change):** the red run = existing tests
  asserting `UnsupportedRarV5Exception` on RAR5 fixtures, executed before the gate
  lift, then flipped in the same commit as documented expectation changes; full
  fixture matrix green through all four public surfaces; corpus regeneration diff
  passes the scripted audit (§4.4) and the maintainer-approved regression run.
- **Fixtures:** the complete §4.2 matrix.
- **No-go rows:** ALL — this is the chunk where the whole table gets a final sweep
  (each row's guard test re-run and ticked in the PR body).
- **Est. diff:** ~300 code lines + large JSON churn (mechanical, script-audited).

### Phase M4 — RAR7 delta (6.2.12 → 7.2.7)

#### M4.1 — RAR7 header parse + dictionary-limit gate

- **Objective:** parse RAR7 compression info: algo version 1 → `VER_PACK7`; 5 dict
  bits + 5 fraction bits (`WinSize = 0x20000L << bits; WinSize += WinSize/32*frac` —
  `d861246:arcread.cpp:868-874`); `FCI_RAR5_COMPAT` (RAR7-written, RAR5-decodable →
  route without ExtraDist); reject > `UNPACK_MAX_DICT` 64 GB; configurable
  `maxDictionarySize` guard (the `CheckWinLimit` analog, `d861246:extract.cpp:1758`)
  — default 4 GB, exceeded → `UnsupportedDictionarySizeException` with the size in
  the message (a guard, never a blind allocation).
- **Files:** `rarfile/rar5/*` (compression-info parse), `Archive.java` config knob,
  tests.
- **C++ refs:** `d52ee2f` diff (`git -C ~/git/unrar diff 8f437ab d52ee2f --
  arcread.cpp headers5.hpp rardefs.hpp | cat`), `d861246:rardefs.hpp:40`.
- **Patterns:** manual §5.2 (RAR7 hook), §4.2 (64-bit window math — `long`
  throughout).
- **Acceptance:** parse matrix: dict 128 KB…64 GB encodings incl. fractional
  (e.g. bits=1+frac=16 → 384 KB) decode to the right `long`; 64 GB+1 encoding →
  reject; header claiming 64 GB with `maxDictionarySize` default → typed exception
  (dict-bomb row).
- **Fixtures:** RAR7 fixtures via rar 7.x (§4.2); dict-bomb byte-patched.
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

#### M4.3 — segmented window > 1 GB (lazy-allocated)

- **Objective:** replace the single `byte[]` window with a long-indexed segmented
  window (power-of-two segments, e.g. 256 MB; shift+mask addressing) used only when
  `winSize > 1 GB`; segments allocated **lazily as the window actually fills**, so a
  dict-bomb header cannot force allocation and a 2 GB-dict fixture with tiny payload
  runs in CI; raise the supported cap to `min(maxDictionarySize, 64 GB)`.
- **Files:** `unpack/Unpack5.java` + new window abstraction class, tests.
- **C++ refs:** design-informed by `143e317:unpack50frag.cpp` (fragmented window —
  a 32-bit-C++ concern; junrar's segmentation is its own design, manual §5.2), not
  a port of it.
- **Patterns:** manual §5.2 (Java array cap), §6 R1 (this chunk is the R1
  resolution).
- **Acceptance:** window unit tests: cross-segment match copy, wrap at segment
  boundary, self-referencing overlap; a 2 GB-dict fixture (tiny payload, §4.2)
  extracts in CI within timeout; 1 GB-and-below archives still use the flat path
  (assert via test hook); >4 GB stays opt-in via `maxDictionarySize`.
- **Fixtures:** rar 7.x `-md2g` tiny-payload fixture.
- **No-go rows:** D1 (entry-extraction byte[] limits unchanged — window ≠ entry).
- **Est. diff:** ~450 lines.

**Explicit non-goals at every phase** (owner constraints +
`unrar-delta-map.md` §2.9/§5.5): unpack50mt/threadpool, qopen, recvol3/recvol5/rs16
(recovery-record *use*), largepage, motw, secpassword (char[] hygiene instead —
§5.2), blake2s_sse/AES-NI, fragmented-window port, cmdfilter/cmdmix/ui*, compression
of any kind (forever).

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

**Binary procurement (owner directive 2026-07-17).** The dev machine currently has
NO rar/unrar binary installed (probed: `command -v rar unrar` → rc=1), and the rar
CLI must NOT be installed via Homebrew (being removed from brew). Use the official
WinRAR macOS tarballs instead — rar 7.23:
`https://www.win-rar.com/fileadmin/winrar-versions/rarmacos-arm-723.tar.gz`
(macOS ARM64) or
`https://www.win-rar.com/fileadmin/winrar-versions/rarmacos-x64-723.tar.gz`
(macOS AMD64); extract the tarball and run the bundled `rar` binary. rar 7.23 is
the primary generator: RAR5-era fixtures are created with `-ma5` (and dict/switch
sets that stay inside 6.2.12 semantics), RAR7 fixtures with the v7 dictionary
switches, RAR4-format gap-fills with `-ma4`. Where a genuinely older-emitter
fixture is required (P0.5 PPMd, P0.1 solid v20), fetch the era binary from the same
`win-rar.com/fileadmin/winrar-versions/` scheme when available, else fall back to
the byte-patch rule (§4.3). The tarball URL + rar version used is recorded per
fixture in the manifest.

| Variant class | Producer + switches | Consumed by |
| --- | --- | --- |
| RAR5 core matrix: m0 (store), m3, m5 × plain/solid (`-s`) × dict `-md128k`, `-md32m` | rar 7.23 `-ma5` (tarball above; switch sets kept inside 6.2.12 stream semantics — oracle cross-check enforces it) | M3.6/M3.7 |
| dict 1 GB accept + 2 GB/4 GB reject rows | rar 7.23 `-ma5 -md1g/-md2g/-md4g` (tiny payload) | M3.6, M4.3 |
| encrypted: `-p<pw>` (file), `-hp<pw>` (header), non-ASCII UTF-8 pw, wrong-pw reuse | rar 7.23 `-ma5` | M3.4 |
| Blake2: `-htb`; Blake2+encrypted (HASHMAC) | rar 7.23 `-ma5` | M3.5, M3.4 |
| multi-volume: `-v100k` → `.part1/.part2/.part3`; solid multi-volume | rar 7.23 `-ma5` | M3.9 |
| links: `-ol` relative symlink, hardlink pairs | rar 7.23 `-ma5` on a Unix host (macOS/Linux) | M3.10 |
| times/owner/version/comment: `-ts+`, `-ow`, `-ver`, `-z<file>` | rar 7.23 `-ma5` | M3.3 |
| filters: payloads chosen to trigger them — x86 `.exe` (E8/E8E9), ARM ELF (ARM), WAV/BMP-like tabular data (DELTA); presence verified by extraction-correctness vs oracle + an engine-side filter counter asserted in unit tests; unobtainable filters fall back to synthetic filter blocks in unit tests (recorded) | rar 7.23 `-ma5` | M3.8 |
| RAR7: default, `-md` fractional sizes, `-md2g` tiny payload, RAR5-compat mode | rar 7.23 (v7 format switches) | M4.1–M4.3 |
| RAR3 gap-fills: PPMd `-mc` forced text model (MaxMB>1), non-ASCII `-p`, UTF-8 names | probe `rar 7.23 a -ma4` first (ASSUMED unavailable — WinRAR 7 reportedly dropped RAR4-format creation); on refusal, fetch an era 5.x/6.x binary from the same `win-rar.com/fileadmin/winrar-versions/` scheme | P0.5, P0.6, M1.1 |
| solid v20 (C1) | RAR 2.x binary if obtainable; else byte-patch a v20 fixture (hostile-by-construction) | P0.1 |
| hostile set (always byte-patched from legit fixtures, one mutation each): bad header CRC, vint flood/overflow, HeadSize/ExtraSize lies, truncated mid-header/mid-stream, name size 0/huge, dict-bomb header, bad pswcheck, filter flood >8192, filter block >0x400000, distance-into-void, REDIR traversal targets (backslash, `..`, absolute, sibling-prefix), protect-header corrupt | scripted patcher committed next to the fixtures | P0.*, M1.*, M2.*, M3.* |

Oracle capture: for every extraction fixture, `unrar x` output payloads' SHA-256
(and `unrar lt` listing where metadata is asserted) recorded at generation time
into the manifest; tests assert against the recorded digests, not against live-run
unrar. The oracle binary is the `unrar` bundled in the same rar 7.23 tarball
(no rar/unrar is preinstalled on the dev machine — see procurement note above).

### 4.3 Byte-patched fixtures — legitimacy rule

Byte-patching is legitimate ONLY for hostile/corrupt variants (a corrupt archive is
corrupt however it was made) and follows the house corrupt-input pattern
(manual §8.1). Every patched fixture commits its patch script (offset + before/after
bytes + why) so review can verify the mutation is the intended one.

### 4.4 The 12,475-JSON regression-corpus gate

The committed reference JSONs currently expect `UnsupportedRarV5Exception` for RAR5
members (manual §8.2). The M3.11 flip procedure — reviewed, mechanical:

1. Baseline run in `check` mode on the pre-flip commit: green (proves the
   environment is sane).
2. Flip to `generate` mode (build.gradle `includeTags`), run, commit the churn as a
   dedicated commit touching ONLY `src/regressionTest/resources/corpus/**`.
3. **Scripted audit of the diff** (script committed under `docs/porting/` or
   `scripts/`): every changed JSON must change only in the expected shape —
   `exception: UnsupportedRarV5Exception` removed, `fileHeaders[]` populated,
   `isRarV5: true` retained; any OTHER field change (RAR3 members, unexpected new
   exceptions) is a finding that blocks the merge and gets investigated.
4. Spot-check N≥20 randomly-sampled flipped members against live `unrar lt` output
   (names, sizes, times) — sample list + outputs attached to the PR.
5. The maintainer-approved `regression.yml` environment run is the final gate
   (manual §8.3).

Intermediate corpus impact: P0.7 (header CRC), M1.5 (method 36), M2.1 (no-op
filters) each run steps 1–3 scoped to their expected delta (possibly zero) and
attach the evidence; they must NOT wait for M3.

---

## 5. API design sketch (plan-level)

### 5.1 Entry points — unchanged where possible

`Junrar.extract(...)`, `Archive` construction, `FileHeader` accessors, and the
`VolumeManager`/`SeekableReadOnlyByteChannel` SPIs keep their signatures; RAR5
support arrives *inside* them (format dispatch on the signature version byte,
M3.1). New accessors only where RAR5 has new facts: e.g.
`FileHeader.getHashDigest()` (Blake2sp), `getRedirection()` (type + target),
nanosecond time accessors reusing the C3 `FileTime` pattern. The unified-header
approach mirrors upstream (one `FileHeader` model, per-format loaders —
`unrar-delta-map.md` §4.2).

### 5.2 Passwords — char[] first-class, String kept

- Add `char[]` overloads beside every `String password` entry point
  (`Archive`, `Junrar`); `String` variants delegate and are kept (not deprecated —
  ubiquitous, harmless); document that char[] allows caller-side wipe
  (secpassword's honest JVM substitute — `unrar-delta-map.md` §5.5).
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
| `WrongPasswordException` | pswcheck mismatch (RAR5) | YES (open cannot proceed meaningfully) |
| `UnsupportedDictionarySizeException` | dict > cap (M3 1 GB; M4 configurable) | NO — headers parse fine; extraction of that entry throws (matches "extract working files" tolerance). Recorded consciously. |

`UnsupportedRarV5Exception`: after M3.11 it is never thrown; `@Deprecated` +
`@deprecated As of <version>` Javadoc, removed at the **next** major after the one
that ships RAR5 (bestbefore processor arms on `.0.0` — manual §3). It stays in the
`setChannel` filter until removal (harmless, avoids behavioral churn for one
release).

### 5.4 Semver consequence

RAR5 support ships as a **breaking major** (conventional commit
`BREAKING CHANGE:` footer — manual §3): observable behavior flips (RAR5 archives
open instead of throwing; M2 filter semantics; P0.7 header CRC strictness), even
though signatures are compatible. New config surface: `maxDictionarySize` setter on
`Archive` (M4.1) with a safe default (4 GB).

---

## 6. Risk register

| # | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| R1 | **RAR5/RAR7 windows vs Java's 2^31 array cap** — RAR5 alone encodes 4 GB dicts; RAR7 to 64 GB; D1's >2 GB `byte[]` limitation is a *deliberate* divergence for entry extraction and must not be "fixed" sideways | Extraction failures or an accidental D1 regression | Staged: M3 hard-caps window at 1 GB flat `byte[]` + typed exception (covers the overwhelming majority of real archives — rar default dict is 32 MB); M4.3 adds a lazily-allocated segmented long-indexed window to the 64 GB format cap behind a `maxDictionarySize` guard (default 4 GB). Entry-extraction byte[] paths untouched (D1 pinned in P0.3). Dict-bomb headers defused by lazy segment allocation + the guard. |
| R2 | **Regression-corpus churn** — thousands of JSON expectation flips at M3.11 could smuggle regressions past review | Silent RAR3 regressions | §4.4 procedure: single dedicated commit, scripted shape-audit (only the three expected field changes allowed), ≥20-sample live-oracle spot check, maintainer-gated corpus run. Intermediate behavior changes (P0.7/M1.5/M2.1) flush their corpus deltas early instead of folding into the big flip. |
| R3 | **PPMd heap drift** — M1.2 edits pointer-emulation code where a one-byte layout slip corrupts everything downstream | Wrong output, latent corruption | M1.1 rebuilds the byte-diff heap harness BEFORE any guard lands (manual §4.13.4); golden dumps committed; guards land one release-pin at a time. |
| R4 | **M2 behavior change on custom-VM archives** (incl. UPCASE) — output flips from interpreted to no-op | User-visible extraction change; C11 test conflict | Parity with unrar ≥5.5.1 IS the spec (upstream made the same flip a decade ago); oracle red→green on a custom-program fixture; C11 supersession documented in-PR (enum lookup test survives, execution changes); corpus delta reviewed. |
| R5 | **Fixture-generation dependency on proprietary rar binaries** (and a RAR 2.x relic for C1) | Blocked chunks, irreproducible fixtures | Committed generation manifest (rar version + command per fixture, §4.2); oracle digests recorded at generation time so CI never needs the binary; byte-patch fallback for hostile/legacy variants (P0.1); fixtures are small and committed. |
| R6 | **Fork drift from junrar master** — rar5-port is long-lived; upstream junrar keeps landing fixes (the no-go list grew two CVE rows in 2026 alone) | Painful final merge; silently missing new guards | Rebase the branch on upstream master at every phase boundary (5 sync points); each rebase re-runs the full no-go guard-test sweep; new upstream commits get classified against `divergences-no-go.md` and appended if load-bearing. |
| R7 | **`setChannel` swallow trap** — any forgotten new exception type silently changes archive-open behavior | Corrupt/encrypted archives "open" with partial headers | §5.3 table maintained as part of every exception-introducing chunk's acceptance; M3.11 does a final audit of the filter list vs `grep -rn "extends RarException"`. |
| R8 | **Solid RAR5 × engine lifecycle** — `extractFile` rewind-and-replay (random access into solid sets) is a junrar-only API the new engine must honor | Broken public API on solid RAR5 archives | M3.7 acceptance pins in-order/out-of-order/reverse extraction on a solid RAR5 fixture, mirroring the existing RAR4 solid tests (`divergences-no-go.md` class c). |

---

## 7. Milestone → deliverable map

| After | A junrar user can… | Version event |
| --- | --- | --- |
| Phase 0 | …nothing new (internal): all 6 UNPINNED no-go rows pinned; T1/T4/T6 fixed (non-ASCII RAR3 passwords + UTF-8 names now correct); header CRCs verified on RAR3 | patch/minor |
| M1 | …feed junrar hostile RAR3 archives that now fail deterministically like unrar 7.2.7 (PPMd/LZ/name-decoder guards, filter/channel caps); PPMd archives with MaxMB>1 extract (S8 decision) | minor |
| M2 | …extract RAR3 VM-filter archives with unrar ≥5.5.1 semantics (6 canonical filters native, others no-op); interpreter attack/bug surface (T3) deleted | minor (behavior note) |
| M3 | …**extract RAR5 archives end-to-end**: plain/solid/multi-volume (`.partN`), `-p` and `-hp` encrypted (with wrong-password detection), Blake2sp-verified, UTF-8 names, ns timestamps, uowner/version records, symlinks/hardlinks with full traversal safety — dictionaries ≤ 1 GB | **breaking major** (the RAR5 release) |
| M4 | …extract RAR7 (method 70) archives incl. fractional dictionaries, ≤ 64 GB dicts gated by a configurable `maxDictionarySize` (default 4 GB), RAR5-compat streams | minor on the new major |

---

## 8. Review & conversion notes (for the independent reviewer)

- Every chunk above is intended to become one GitHub sub-issue under a per-phase
  main issue; the chunk block already carries the sub-issue's required fields
  (objective, refs, acceptance, fixtures, no-go risk).
- Chunk-size discipline: ≤ ~600 changed lines (M2.2's deletion exempted); a chunk
  that grows past it in implementation splits at the commit boundaries already
  named in its objective.
- Open decisions requiring owner sign-off before implementation: Java floor stays 8
  (§2.1); zero-dependency Blake2sp port (§2.2); S8 recommendation pending the P0.5
  probe; the M3 1 GB dictionary cap and M4 4 GB default guard (§6 R1); C11
  execution-semantics supersession in M2 (§3 M2.1).
