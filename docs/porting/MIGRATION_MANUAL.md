# junrar C++ → Java Migration Manual

The single rulebook to read **before porting any unrar (C++) code into junrar (Java)**.
It distills seven evidence-checked analysis reports (in [`reports/`](reports/)) into
navigable rules. Every rule keeps its `file:line` evidence; when a rule here feels
too terse, the referenced report has the long form.

Evidence base: junrar `master` @ `99866b74` (2026-06-29) read against the
`pmachapman/unrar` release mirror at `~/git/unrar` (one commit per release,
3.1.0 → 7.2.7). All C++ refs of the form `2e71167:file:line` mean
`git -C ~/git/unrar show 2e71167:<file>` at that line.

---

## 1. Purpose and how to use this manual

You are about to port a specific unrar change (a hardening guard, a RAR5 component,
a filter, a header record) into junrar. Read in this order:

| If you are… | Read |
| --- | --- |
| starting ANY port | §2 (ground truth) + §3 (house rules) — always |
| translating C++ constructs to Java | §4 (pattern catalog) — the heart |
| touching a specific subsystem | §5 (layer guide for that layer) |
| editing a file that has known bugs or deliberate skew | §6 (traps) + §7 (no-go list) — **before the first edit** |
| writing the tests (always) | §8 |
| planning scope / picking a target unrar version | §9 |

Raw reports (long-tail detail, full evidence chains):

| Report | Covers |
| --- | --- |
| [`reports/baseline-pin.md`](reports/baseline-pin.md) | version-pin evidence, marker tables, post-baseline cherry-picks |
| [`reports/layer-headers-parsing.md`](reports/layer-headers-parsing.md) | header/parsing patterns, skew list S1–S12, block-type recipe |
| [`reports/layer-unpack-core.md`](reports/layer-unpack-core.md) | LZ engines, BitInput, ComprDataIO, decode tables, engine lifecycle |
| [`reports/layer-ppm-vm-crypt.md`](reports/layer-ppm-vm-crypt.md) | PPMd pointer emulation, RarVM, crypto/CRC decisions, IO routing |
| [`reports/layer-idioms-style.md`](reports/layer-idioms-style.md) | style, build, tests, CI, API conventions, regression corpus |
| [`reports/divergences-no-go.md`](reports/divergences-no-go.md) | the 20-row regression no-go table, fixture map |
| [`reports/unrar-delta-map.md`](reports/unrar-delta-map.md) | 3.7.3→7.2.7 census, RAR5/RAR7 inventory, milestones, pinned-evidence table |

Issue citations: bare `#NN` numbers refer to two different trackers — upstream
`junrar/junrar` for historical bugfix provenance, and the port's working tracker for
the M/P chunks. **Appendix A** disambiguates them and carries a self-contained index
of every port-tracker issue, so the citations survive if that tracker goes away.

Report reconciliations (contradictions found during synthesis, resolved with fresh evidence):

- **`unsigned/` package usage.** `layer-ppm-vm-crypt.md` §8 claims the package is
  "used by newer header-parsing code"; `layer-headers-parsing.md` §2.2 claims zero
  importers. Re-probed 2026-07-17: `grep -rn "junrar.unsigned" src/main` outside the
  package itself → **no hits**. The headers report is correct: the package is dead
  scaffolding (see §6, trap T8).
- **RAR5 filter-type count.** `layer-unpack-core.md` §8 says "5 fixed types",
  `layer-ppm-vm-crypt.md` §3.6 says "6 hardcoded filter types". The evidence-pinned
  census (`unrar-delta-map.md` §2.7, from `d861246:unpack50.cpp:427-485`) is
  authoritative: RAR5 **applies exactly 4 filter types** — DELTA(0), E8(1), E8E9(2),
  ARM(3). The "6" conflates the post-5.5.1 RAR3-compat *fingerprint table* (6 entries,
  §5.4 below); the "5" is a loose count. Use 4 for RAR5, 6 for the RAR3 fingerprint set.

---

## 2. Ground truth

### 2.1 The baseline is unrar 3.7.3 — not 3.9

**junrar's bulk translation baseline is unrar 3.7.x, pinned to the 3.7.3–3.7.6 source
window, most plausibly 3.7.3.** The 3.9.x folklore is refuted: junrar is missing every
3.7.7+ marker and every 3.9.x marker in the bulk code. Confidence: high for
"3.7.3 ≤ baseline ≤ 3.7.6" (the four releases are identical in every translated file),
medium for exactly 3.7.3 (Java file headers carry `Creation date: 31.05.2007`, the
3.7.3-current era). Full marker tables (10 lower-bound, 7 upper-bound):
[`reports/baseline-pin.md`](reports/baseline-pin.md).

There is **no per-subsystem split**: every subsystem (unpack29/20/15, PPMd, RarVM,
headers) sits in the same envelope. The only newer-unrar material is isolated, dated
cherry-picks (extended time 2022, corrupt-ext-time bounds 2022, audio init fixes 2022 —
list in `baseline-pin.md` "Post-baseline deltas"). None moves the baseline.

Practical consequence: **when you need the C++ that junrar's code corresponds to, read
3.7.3 (`2e71167`), not the version you are porting from.** Diff `2e71167 → <target>` to
see what the port must change.

### 2.2 Reading any unrar version

The mirror `~/git/unrar` has one commit per release, linear history, 3.1.0 → 7.2.7.

```sh
git -C ~/git/unrar log --format='%h %s' --reverse master   # release → commit map
git -C ~/git/unrar show <commit>:<file>                    # any file at any release
git -C ~/git/unrar diff <c1> <c2> -- <files...>            # what changed between releases
```

Key pins: `2e71167` = 3.7.3 (baseline), `5db30e6` = 4.2.4 (last release with the old
file layout — the best 3.7.3-shaped diff context), `8f437ab` = 6.2.12 (last pre-RAR7),
`d52ee2f` = 7.0.1 (RAR7 format), `d861246` = 7.2.7 (tip). The full pinned-evidence
table (which release introduced which guard/feature) is the appendix of
[`reports/unrar-delta-map.md`](reports/unrar-delta-map.md).

### 2.3 License rule — extraction only, forever

junrar's LICENSE is the UnRAR license: *code may not be used to develop a RAR
(WinRAR) compatible archiver*. Therefore:

- unrar source is usable for **extraction** work only.
- **No compression code is ever ported.** Not "later", not "for tests", not partially.
  The C++ compression half (Pack, `PPM_CONTEXT::encode*`, encode tables) is
  out of scope permanently — junrar already dropped it wholesale
  (`reports/layer-ppm-vm-crypt.md` §9).
- Test fixtures needing new RAR files are created with the real `rar` CLI
  (see `generate-testdata.sh` precedent, §8.1), never with ported compression code.

---

## 3. House rules (non-negotiable)

The ArchUnit and Javadoc gates run in `./gradlew build` (= `check`); a violation fails
CI, not just review. The style rows are house style without an automated gate right now:
Checkstyle was removed on `master` and Spotless is its planned replacement, currently
present only as a commented-out block (build.gradle:19,229) — until it lands, style
holds by review. Evidence: [`reports/layer-idioms-style.md`](reports/layer-idioms-style.md).

| Rule | Enforcement |
| --- | --- |
| 4-space indent, no tabs, ≤175 cols, no trailing whitespace, newline at EOF | house style (was checkstyle 10.3.2; Spotless pending) |
| No star imports; no redundant/unused imports | house style (was checkstyle `AvoidStarImport` etc.; Spotless pending) |
| K&R braces; `if (x) return y;` single-liners legal | house style (was checkstyle `LeftCurly`/`NeedBraces`; Spotless pending) |
| No `System.out/err`, no `printStackTrace` | ArchUnit `CodingRulesTest.java:18` |
| No bare `throw new RuntimeException/Exception` | ArchUnit `CodingRulesTest.java:21` |
| No `java.util.logging`, no Joda-Time | ArchUnit `CodingRulesTest.java:24-27` |
| Tests: JUnit 5 Jupiter + **AssertJ only** — depending on `org.junit.jupiter.api.Assertions` is build-failing | ArchUnit `TestCodingRulesTest.java:14` (the old checkstyle `IllegalImport` half retired with checkstyle) |
| Javadoc must at least build (`withJavadocJar()`) | build.gradle:51 |

**Java level.** `src/main` compiles at **release 8 today** (build.gradle:55) — no
`var`, records, `List.of` in production code; tests/regressionTest compile at toolchain
level (JDK 21) and freely use records/`var`. *Standing owner permission:* the floor may
be bumped to 17 or 21 LTS **with strong justification** — that decision belongs to the
port plan, not to an individual patch. This manual only records the current floor and
the permission.

**Dependencies.** Runtime today = `org.slf4j:slf4j-api` **only** (build.gradle:17);
crypto is JDK `javax.crypto`. *Standing owner permission:* reputable additions
(e.g. Bouncy Castle for Blake2s) are allowed **when they demonstrably shrink the
port** — again a plan-level decision. Default remains zero new runtime deps.

**Commits and changelog.** Conventional Commits are mandatory (they drive `svu`
version computation AND the jreleaser changelog): `feat:`, `fix:`, `perf:`, `test:`,
`ci:`, lowercase imperative, no trailing period; API breaks carry a
`BREAKING CHANGE:` footer or the release train mis-versions. **`CHANGELOG.md` is
generated by jreleaser — never hand-edit it.** Deprecations: `@Deprecated` +
`@deprecated As of X.Y.Z` Javadoc, removed at next major (the `bestbefore` processor
arms on `.0.0` versions, build.gradle:18-20).

**Naming.** Decoder internals may keep C++ carryover names verbatim (`unpBlockType`,
`UnpWriteBuf`, `DBitLengthCounts`) — greppability against unrar is deliberate; do NOT
"clean them up" wholesale. Public API and new non-decoder classes use idiomatic Java
camelCase. Checkstyle naming modules are all disabled — this is convention, not a gate.

**Fields.** Private + getters/setters on new header/model classes (the FileHeader
pattern). `ContentDescription`'s public fields are grandfathered, not a template.

**License headers.** New files carry **no header** (first line `package …` — the
exception/, volume/, io/ precedent). Keep the innoSysTec/Edmund Wagner header only on
files genuinely descending from the 2007 code. Never copy C++ file headers or SVN
keywords into Java files.

---

## 4. Translation pattern catalog

Each subsection: the RULE, the canonical example (C++ ref → Java ref), the traps.
C++ refs are at baseline `2e71167` unless another commit is named.

### 4.1 Class shape — inheritance flattening

**RULE.** One Java class per C++ *compilation unit*; the C++ struct inheritance tree
is preserved 1:1 as the Java class hierarchy; a C++ struct reused for two block types
stays ONE Java class with type predicates.

- The single C++ `class Unpack : private BitInput` whose engines are split only by
  `#include "unpack15.cpp"` etc. (`unpack.cpp:3-9`, `unpack.hpp:89-216`) became a
  linear chain: `BitInput` → `Unpack15` (abstract) → `Unpack20` (abstract) →
  `Unpack` (final) — `Unpack.java:42`, `Unpack20.java:42`, `Unpack15.java:34`.
- **State lives in the lowest (oldest-engine) class that touches it**, declared
  `protected`, no getters between engine classes: `window`, `oldDist[4]`, `unpIO`,
  `destUnpSize` on `Unpack15` (`Unpack15.java:36-73`) even though C++ declares them in
  the "29" section — all engines use them.
- Downward calls via abstract hooks: `Unpack15.java:136` declares
  `protected abstract void unpInitData(boolean solid)`; the concrete implementation is
  at the top of the chain (`Unpack.java:604`).
- `private` inheritance → plain `extends`; `friend class Pack` dropped (no compressor);
  `(struct Decode *)&LD` downcasts → real polymorphism (`decodeNumber(Decode dec)`).
- Header structs: `BaseBlock` → `BlockHeader` → `FileHeader`/`SubBlockHeader`/… mirrors
  `headers.hpp:95-299` exactly (`rarfile/BaseBlock.java:33` etc.); FILE_HEAD and
  NEWSUB_HEAD share one `FileHeader` class with `isFileHeader()`
  (`FileHeader.java:639-641`).

**Trap.** A RAR5 engine shares almost no state with the RAR3 chain (different window
model, no `Decode` family) — do NOT extend `Unpack20`; build a **sibling class** taking
`ComprDataIO` (§5.2).

### 4.2 Unsigned & signedness — the #1 historical bug source

Every serious shipped junrar bug class traces here (no-go rows C1, C2, C15; skew S8).
C++ unsigned semantics do not survive naive translation.

**The canon** (merged from `layer-ppm-vm-crypt.md` §8 and `layer-unpack-core.md` §2):

| C++ type / op | Java idiom | Canonical example |
| --- | --- | --- |
| `byte` (u8) read | `int` + `& 0xff` on read; `(byte)` cast on write | `State.getSymbol()` `State.java:42-48`; parse idiom `field \|= buf[pos] & 0xff` |
| `ushort` (u16) | `int` + `& 0xffff` on read (or `short` for pure flag/CRC bit patterns) | `PPMContext.getNumStats()` `PPMContext.java:80-92`; `UnixOwnersHeader.java:18` |
| `uint` (u32), bit patterns / identities / CRCs | `int`, compare with masks, never `<`/`>` | header `headCRC`, attrs |
| `uint` (u32), **sizes/offsets that must not go negative** | `long` via `Raw.readIntLittleEndianAsLong` | `BlockHeader.java:54` packSize; `FileHeader.java:98` unpSize |
| `uint` full-range **arithmetic** (range coders, hashes) | `long` + `& 0xFFFFFFFFL` **after every mutation** | `RangeCoder.java:37-97` (`uintMask`) |
| `Int64` | native `long`; compose from two u32 halves | `FileHeader.java:132-138` |
| unsigned `>>` | Java `>>>` — **always**; sign-extension silently corrupts bitstreams | class rule, no-go C15 (commit `25491b50`; the same sweep style in `Unpack.java`/`Rijndael.java` is `d276f937` — outside the 5 C15 files, see `reports/signedness-audit.md`) |
| signed `>>` the C++ casts to `(int)` | Java `>>` | correct model `rarvm.cpp:414` (junrar's VM_SAR got this wrong — §6 T3) |
| unsigned-subtraction wrap guard | add explicit `>= 0` to the fast-path condition | `Unpack20.java:215`, `Unpack.java:574` — C++ `unsigned DestPtr=UnpPtr-Distance` wraps huge and fails `< MAXWINSIZE-260` (`unpack.cpp:88-89`); signed Java passes it and AIOOBEs (no-go C1) |
| u32 sentinel comparisons | suffix the literal with `L` against a `long` | **trap:** `FileHeader.java:127` `unpSize == 0xffffffff` compares long vs int −1 — can never be true (§6 T1) |
| wraparound counters (`byte GlueCount`) | `int` + `& 0xff` in the setter | `ModelPPM.setEscCount` `ModelPPM.java:289-291` |
| bool↔int flag math `(Result<Value1)\|…` | `(cond ? 1 : 0) \| …` — **fully parenthesized** | correct: `PPMContext.java:369-372`; broken: RarVM flags (§6 T3) |
| `-x & mask` trick | works unchanged in int/long | `RangeCoder.java:91`; `RawDataIo.java:56` |

**RULE.** Choose per-variable: (a) value fits signed range → plain `int`; (b) full
32-bit unsigned arithmetic → masked `long`; (c) pure bit patterns → `int` + `>>>`.
Never mix (b) and (c) in one expression without parentheses — Java `+`/`-` bind tighter
than `&`, and `a & 0xFFFFFFFFL + b` computes `a & (0xFFFFFFFFL + b)` (§6 T3 is the
graveyard of this mistake). On any re-port, **re-audit every `>>` on a value that is
unsigned in C++** (no-go C15 — audit procedure + boundary pins in
`reports/signedness-audit.md` and `*SignednessTest`, chunk P0.4).

### 4.3 Pointers & memory

**RULE (general).** `byte*` → `byte[]` + `int` offset; every pointer parameter becomes
an **(array, offset, length) triple**: `UnpIO->UnpWrite(&Window[WrPtr], n)` →
`unpIO.unpWrite(window, wrPtr, n)` (`unpack15.cpp:129` → `Unpack15.java:614`).

**RULE (heap emulation — PPMd style).** Pointer-heavy C++ where structs live inside
one allocation translates to **one `byte[]` heap + `int` offsets as pointers**:

- **NULL = offset 0**, made unambiguous by reserving heap byte 0: `heapStart = 1`
  (`SubAllocator.java:141-148`, comment "1+ for null pointer"); every `ptr==NULL` test
  becomes `addr == 0` (`ModelPPM.java:245`).
- Each C++ struct becomes a **flyweight cursor** subclass of `Pointer`
  ({`byte[] mem; int pos`}, `Pointer.java:26-56`): `static final int size` = packed
  sizeof, typed accessors at hard-coded field offsets via `Raw` little-endian helpers,
  `incAddress/decAddress` for `p++`/`p--`, `setValues` for struct copy, a **second
  cursor aimed at the same offset** for unions/casts (`PPMContext.java:120-125` — the
  `U`/`OneState` union). Never materialize heap structs as Java objects with fields —
  the heap byte layout IS the data structure.
- **Address-graph rule:** any C++ object whose ADDRESS participates in the pointer
  graph (free-list heads, sentinel nodes, stack locals linked into lists) must get
  space INSIDE the emulated heap — junrar appends the `FreeList[]` nodes and the
  `GlueFreeBlocks` temp block to the heap (`SubAllocator.java:141-159`). Grep the C++
  for `&member` / `&local` used as a node pointer before sizing the array.
- **Value copy vs pointer:** a C++ struct value copy (`STATE fs = *FoundState`) = a
  dumb value class (`StateRef.java:27-78`); a C++ local pointer = a reused cursor field
  (`ModelPPM.java:78-88` temp pools). Mixing the two (mutating a cursor where C++ had a
  snapshot) is the classic bug source of this style.

**Memory ops:**

| C++ | Java | Trap |
| --- | --- | --- |
| `memcpy` / `memmove` | `System.arraycopy` (has memmove semantics) | LZ copy loops with **forward overlap** (self-referencing match) must stay byte-sequential (`Unpack.java:592-594`); fast-path arraycopy only where provably non-overlapping |
| `memset(x,0,sizeof)` | `Arrays.fill(x, 0)` | — |
| `memset` of a **struct array** | per-slot `new` in a loop | `Arrays.fill(arr, new X())` shares ONE reference — caused real CRC errors + NPE (no-go C2, `Unpack20.java` AudV/MD init) |
| `Array<T>` growable | `List`/`Vector` (`setSize` ↔ `Alloc`) | boxing wart (`Vector<Byte>` in `VMPreparedProgram.java:36-37`) — replicate with primitive buffers in NEW code |
| pointer into VM memory (`byte *FilteredData`) | int offset + copy-out | `VMPreparedProgram.java:40` |

### 4.4 Macros

**RULE.** Function-like macro → private method, name preserved
(`GetShortLen1(pos)` → `getShortLen1`, `unpack15.cpp:139-140` →
`Unpack15.java:238-244`). Constant header → one `static final` holder class **named
after the C++ header** (`compress.hpp` NC/MAXWINSIZE/… → `decode/Compress.java:26-45`).
Any macro with a comma operator or assignment-in-condition gets a **semantic rewrite
that must be tested, not eyeballed** — `ARI_DEC_NORMALIZE` needed a `boolean` flag
rewrite (`coder.cpp:20-28` → `RangeCoder.java:79-98`; the literal first translation is
kept commented above it). `int64to32(x)` → `(int)` cast; `is64plus(x)` → `>= 0` on a
native `long`; `sizeof(a)/sizeof(a[0])` → `.length`.

### 4.5 Control flow

**RULE.** Keep upstream's loop shapes, `continue`/`break` placement, and **case order
byte-for-byte — the decode loop's case order IS the format spec** (`unpack29` loop
`Unpack.java:172-350` ↔ `unpack.cpp:196-389`, case-by-case identical).

- **Bool protocol stays boolean; transport errors become exceptions.** C++ signals
  corruption by returning `false` up the chain; Java keeps that
  (`if (!readTables()) break;` `Unpack.java:306`) and adds
  `throws IOException, RarException` on anything reaching `unpIO`.
- **Poisoned-state latches stay boolean fields**: `PPMError` → `ppmError` checked at
  engine entry (`Unpack.java:74,168-170,196-199`) — a poisoned PPM model must refuse
  re-entry on the next solid file.
- Implicit C++ truthiness → explicit comparison: `if (StMode)` → `if (StMode != 0)`
  (`Unpack15.java:171`); `NewTable=(BitField & 0x4000)` → `!= 0` (`Unpack.java:646`).
- **No labeled breaks.** C++ `goto` (present in PPMd and newer unpack50) → boolean
  latches: `noLoop`/`loopEntry` replacing `goto NO_LOOP/LOOP_ENTRY`
  (`ModelPPM.java:397-434`). Zero labeled breaks exist in the ported layers; follow
  the latch precedent.
- Deep nested-if ladders are hand-copied verbatim, not table-ified
  (`Unpack20.java:334-389`).

### 4.6 Enums & flags

**RULE (flag words).** `#define` masks → `static final` constants on the owning block
class; flag *testing* is **named predicates returning `boolean`**, never raw mask
expressions at call sites: `FileHeader.isSolid/isEncrypted/hasSalt/…`
(`FileHeader.java:599-662`), `MainHeader.isMultiVolume/…` (`MainHeader.java:88-144`).
Multi-bit window-class masks stay compare-mask (`isDirectory()` =
`(flags & LHD_WINDOWMASK) == LHD_DIRECTORY`, `FileHeader.java:660-662`). Watch signed
`short` literals: `LONG_BLOCK = -0x8000` (`BaseBlock.java:78`) because 0x8000 doesn't
fit — masking still works.

**RULE (wire-value enums).** C++ `enum` → Java enum where each constant wraps its wire
value, plus (a) `findX(value)` returning **null for unknown**, (b) `equals(primitive)`
helper, (c) the raw primitive stays the stored field on the parsed object and the enum
is **materialized lazily** (`UnrarHeadertype.java:27-93`; `BaseBlock.java:89,187-189`).
Unknown value ⇒ null ⇒ **caller policy** (throw for block type `Archive.java:348-351`;
`break`-and-skip for sub-block subtype `Archive.java:489`). Variable-length byte-string
discriminators (`SUBHEAD_TYPE_*`) are NOT enums — byte-array constant class with
`byteEquals` (`NewSubHeaderType`).

**Trap.** The `findX` lookup is junrar-only code with no C++ counterpart to diff
against — `VMStandardFilters.findFilter(7)` silently omitted UPCASE for years (no-go
C11). Every new enum gets an `@EnumSource` round-trip test (the
`VMStandardFiltersTest` pattern).

### 4.7 Byte reading & endianness

**RULE.** Everything on-disk is little-endian, hand-composed from `& 0xff`-masked
bytes via the static helpers in `io/Raw.java` (`readShortLittleEndian` :102,
`readIntLittleEndian` :120, `readIntLittleEndianAsLong` :134, write variants :236+).
**Never use `java.nio.ByteBuffer`** — the house style is `Raw` + explicit masks.
Choose the width by *semantic role*, not by C type alone (§4.2 table).

Header classes parse from a caller-supplied `byte[]` with a manual `int pos` cursor —
the constructor-from-`(parent, byte[] tail)` pattern (`MainHeader.java:40`):
`super(bb)` copy-constructor chain replicates C++'s common-prefix memcpy
(`*(BaseBlock *)&NewMhd=ShortBlock;`), the tail buffer holds only the bytes after the
parent's portion, the header class never touches the channel. Bounds behavior
deliberately differs from C++: `RawRead::Get*` silently zero-fills past the end
(`rawread.cpp:53-63`); junrar sizes buffers exactly and throws — port unrar's clamps
(name-size 4096 `FileHeader.java:140`) *and* wrap attacker-controlled allocations in
`safelyAllocate(len, MAX_HEADER_SIZE)` (`Archive.java:92,557-565`), never replicate
the zero-fill.

### 4.8 Struct sizes — the off-by-N trap

C++ `SIZEOF_*` constants (`headers.hpp:4-19`) are **absolute block sizes**
(`SIZEOF_NEWMHD 13`, `SIZEOF_SUBBLOCKHEAD 14`). Java size constants are mostly **the
increment past the parent's bytes** (`BlockHeader.blockHeaderSize = 4` = 11−7;
`SubBlockHeader.SubBlockHeaderSize = 3` = 14−11) — full mapping table in
`reports/layer-headers-parsing.md` §1.3 — EXCEPT `FileHeader.NEWLHD_SIZE = 32`, which
is absolute because SubData math needs it (`FileHeader.java:47,175`).

**RULE.** When porting a size constant, decide whether the call site consumes an
*absolute* block size (subData math) or an *incremental* read size (buffer allocation:
`toRead = headerSize − BaseBlockSize − blockHeaderSize[ − SubBlockHeaderSize]`,
`Archive.java:445-447,519-522`). Every historical off-by-N here was a port bug;
`ProtectHeader` is the standing wreck (§6 T2).

### 4.9 Error handling

**RULE.** C++ `ErrHandler` codes / `Log(...); return 0;` → **typed checked exceptions
at the detection site**, all extending checked `RarException` (`exception/` package —
one anemic file per failure mode, ctors `(Throwable)`/`()`/message-where-used). New
failure mode in a port ⇒ new small `XxxException extends RarException`; never a
generic `RarException("message")` when the caller could dispatch on type — tests
assert `isExactlyInstanceOf`. Caller misuse is **unchecked** (`IllegalArgumentException`
for bad paths, `IllegalStateException` for traversal defense). Canonical signature:
`throws RarException, IOException`. `RarException` extends `Exception`, NOT
`IOException` — changing that is a major-version event.

**THE `setChannel` CATCH-FILTER TRAP.** unrar's "warn and continue on broken archive"
tolerance survives in exactly one place: `Archive.setChannel` (`Archive.java:205-211`)
rethrows only `UnsupportedRarEncrypted|UnsupportedRarV5|CorruptHeader|BadRarArchive`
and **swallows every other exception type** ("allow extraction of working files in
corrupt archive"). Consequence: **introducing a new exception type silently changes
archive-open behavior** — an uncaught new type gets swallowed and the archive opens
"successfully" with partial headers. Every new `RarException` subclass thrown from
`readHeaders` must consciously update (or consciously not update) that filter list,
and say so in the PR.

### 4.10 Logging

**RULE.** SLF4J only (`private static final Logger logger = LoggerFactory.getLogger(X.class);`),
always parameterized `{}` messages, exception as last argument. Levels: `info` =
extraction progress, `warn` = recoverable/skipped conditions, `error` = before rethrow
at the facade boundary. C++ `mprintf`/`eprintf`/`Log(...)` map to these; stdout and
j.u.l are ArchUnit-banned (§3).

### 4.11 Crypto policy

**RULE.** **Port KDF/format math from `crypt*.cpp`; take primitives from the JDK (or
an owner-approved library) — never port `rijndael.cpp`/`sha1.cpp`/`sha256.cpp`.**
The RAR3 precedent (`crypt/Rijndael.java:38-89`): hand-ported KDF loop
(0x40000 SHA-1 rounds, IV from partial digests, per-uint32 key byte swap =
`crypt.cpp:188-286`), primitives via `Cipher.getInstance("AES/CBC/NoPadding")` +
`MessageDigest.getInstance("sha-1")`.

For RAR5: AES-256/CBC = same `Cipher` with a 256-bit key; KDF = a small hand-rolled
PBKDF2 loop over `Mac.getInstance("HmacSHA256")` — NOT
`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`: Java 8 mandates `HmacSHA256`
as a `Mac` algorithm but requires NO PBKDF2 `SecretKeyFactory` at all (required list =
DES, DESede — Java 8 javadoc, doc-verified 2026-07-17), and RAR5's derived trio
(key at count, HashKey at count+16, PswCheck source at count+**32** — the upstream
loop runs segments `{Count-1, 16, 16}` and snapshots one running accumulator,
`crypt5.cpp:105-125`) falls out of ONE pass, where three provider calls would do ~3×
the HMAC work. Port RAR5's specifics on top (2^Lg2Count iterations, reject lg2 > 24,
`ConvertHashToMAC` — `crypt5.cpp:85,193`; `unrar-delta-map.md` §2.6).
SHA-256 = `MessageDigest.getInstance("SHA-256")`.
Blake2sp is the one primitive the JDK lacks — explicit port-or-dependency decision
(§5.5). Two RAR3 warts NOT to inherit (§6 T4/T5): platform-charset password bytes and
the O(n²) digest recompute.

### 4.12 IO routing

**RULE.** Decoders consume bytes **exclusively** through `ComprDataIO.unpRead` /
`Unpack.getChar()` and emit through `ComprDataIO.unpWrite` — that is where decryption,
volume spanning, packed-size metering, and CRC accumulation are interposed
(`ComprDataIO.java:129-203`). Never hand a decoder the underlying channel. Header
parsing reads through **`RawDataIo`**, the channel decorator that adds transparent
AES decryption after `setCipher()` (`RawDataIo.java:52-77`) — `setCipher` is **the
RAR5 header-decryption hook**. Storage abstraction is
`SeekableReadOnlyByteChannel` (absolute seeks only); new byte sources = new channel
implementations, never changes to Archive. Volume acquisition is always through the
`VolumeManager` SPI — never construct file names inside Archive; note
`setVolume` **re-parses the whole header list** of the new volume
(`Archive.java:869-872`), unlike unrar's in-place continue.

Encrypted-read alignment: reads round up to the AES block with
`realRead = toRead + ((~toRead + 1) & 0xF)` (`RawDataIo.java:57-59` =
`rawread.cpp:14-39`), and `BaseBlock.getHeaderSize(boolean encrypted)`
(`BaseBlock.java:175-185`) adds the same padding to header-size accounting. **Any
header-decryption port must apply the align-to-16 formula on BOTH the read path and
the size accounting — never one of them.**

### 4.13 Port DNA — how junrar ports stay maintainable

Four meta-patterns; keep all of them:

1. **Comment-anchored line porting.** Translated methods keep the original C++ line as
   an inline comment (`// memcpy(ptr,OldPtr,U2B(OldNU));` `SubAllocator.java:275`;
   the whole `UnpWriteBuf` filter sweep, `Unpack.java:355-527`). This is the
   correspondence map that makes later upstream diffs mergeable — preserve it in new
   ports. Treat existing comments as provenance breadcrumbs, not spec: some predate
   later fixes.
2. **`// Bug fixed` markers.** Where the port had to deviate from a literal
   translation to be correct, the divergence is flagged (`SubAllocator.java:150`,
   `ModelPPM.java:145`, PPM escape `ch & 0xff` `Unpack.java:226-231`). Adopt the same
   marker for every deliberate divergence.
3. **Structure-preserving first, refactor after validation.** Control flow, names, even
   goto-shapes are emulated first; refactor only after byte-level/oracle validation,
   never during the port.
4. **Byte-diff validation harness for pointer-heavy code.** The PPMd port was validated
   by dumping the C++ heap and the Java heap after identical inputs and byte-comparing
   (`AnalyzeHeapDump.java`; the commented `dumpHeap()` producer
   `SubAllocator.java:387-402`). For any new pointer-emulation port, **build the
   byte-diff harness first** — it turns "does my pointer emulation drift?" into a
   mechanical check. Little-endian heap layout (via `Raw`) is what makes dumps
   comparable with an x86 C++ build.

---

## 5. Layer guides

### 5.1 Headers / parsing

**Where things live.** C++: `headers.hpp` (structs), `arcread.cpp`
(`Archive::ReadHeader`, lazy one-block cursor + `SeekToNext`), `rawread.cpp`
(buffered field reads), `encname.cpp` (unicode names). Java: `rarfile/` (one class per
struct), `Archive.readHeaders(long)` (`Archive.java:302-555`) — an **eager
whole-archive parse at volume-open time** into `List<BaseBlock> headers`; downstream is
list traversal (`nextFileHeader`, `Iterable<FileHeader>`). Per-block position lives ON
the header (`positionInFile`); "seek to next" is recomputed as
`positionInFile + getHeaderSize(encrypted) + packOrDataSize`.

**Layer rules.**

- New block type recipe (the full checklist is `reports/layer-headers-parsing.md`
  "Quick-reference"): wire byte → `UnrarHeadertype` constant + `findType` arm; class in
  `rarfile/` extending the same parent unrar uses, ctor `(Parent, byte[] tail)` +
  manual `pos` cursor; `safelyAllocate` the tail; add to `headers`; **explicitly seek
  past packed data** and register the position in `processedPositions`. Never leave
  the channel mid-block (the regression class recorded at `Archive.java:534-536`;
  no-go C6/C7).
- C++ unions → **two Java fields kept equal**, assigned together at parse time
  (`BlockHeader.java:37-38,54-55`), never one field with two accessors.
- EndArcHeader **terminates** parsing (`Archive.java:434`) — unrar keeps reading;
  Java-side loop guard `processedPositions` → `BadRarArchiveException` (no-go S1).

**RAR5 hook points.**

- RAR5 headers are vint-encoded (base-128 LE, continuation bit 0x80) with layout
  `CRC32 | vint HeadSize | vint Type | vint Flags [| vint ExtraSize][| vint DataSize]`
  and UTF-8 names (`unrar-delta-map.md` §2.2). The `(parent, byte[] tail)` constructor
  shape still applies; the vint reader is new shared code.
- RAR5 names are plain UTF-8 — do NOT reuse `FileNameDecoder`; decode with an explicit
  `StandardCharsets.UTF_8` (and do not copy the platform-default-charset habit, §6 T6).
- RAR5 "service headers" (`CMT`, `QO`, `RR`, …) are file-like blocks — mirror junrar's
  NEWSUB_HEAD machinery (SubData capture, `FileHeader.java:174-191`), NOT the legacy
  SUB_HEAD family (`Archive.java:482-546` — frozen legacy, do not extend).
- RAR5 16-byte salt + Lg2Count are **new fields on RAR5 header classes** — do not
  widen the RAR3 `SALT_SIZE 8` path (`FileHeader.java:45,193-198`).
- **junrar verifies NO header CRCs** (parses `headCRC`, checks only the fixed-value
  Mark/EndArc `isValid`s) — a known gap vs unrar. Unrar's verification is
  format-specific (verified at `2e71167`/`d861246:arcread.cpp`, 2026-07-17): RAR3
  headers carry a **16-bit** CRC (`~CRC32 & 0xffff`, `GetCRC15`) with legacy
  exemptions (SIGN, AV, old Unix-owner sub-blocks; comment-bearing headers CRC only
  the processed bytes) and a mismatch is *recorded* (`BrokenHeader`) while processing
  continues — fatal only for encrypted headers; RAR5 headers carry a full CRC32
  (`GetCRC50`) and an unencrypted mismatch is likewise "report, but attempt to
  process". **The RAR5 port must verify header CRCs, not inherit the gap** (§6 T7) —
  with the format-correct width and tolerance, not a blanket fatal CRC32 check.

### 5.2 Unpack core (LZ engines)

**Where things live.** C++ 3.7.3: `unpack.cpp` (RAR3/29 + dispatcher) textually
including `unpack15.cpp`/`unpack20.cpp`; C++ ≥5.0.0: thin `unpack.cpp` dispatcher +
`unpack15/20/30/50.cpp` + `unpackinline.cpp`. Java: the `BitInput → Unpack15 →
Unpack20 → Unpack` chain (§4.1), `decode/` table classes, `ComprDataIO`.

**Layer rules.**

- Decode-loop case order is the format spec — verbatim (§4.5). Decode tables:
  sized-struct clones → constructor-resized subclasses of `decode/Decode.java`;
  function-local static tables → hoisted `static final` fields (`Unpack20.java:62-81`).
- Engine lifecycle (must survive any new engine): **one `Unpack` per `Archive`,
  lazily created, reused for every entry** (`Archive.java:771-775`); `init(null)`
  (window alloc + full reset) only for **non-solid** headers; `setDestSize` per entry;
  reset cascade `unpInitData(solid)` wipes tables/window/filters only when `!solid`
  (`Unpack.java:604-627` ↔ `unpack.cpp:928-951`). Must keep working with
  `extractFile`'s rewind-and-replay random access for solid archives
  (`Archive.java:584-596`).
- `unpReadBuf` refill: keep the compaction threshold (`inAddr > MAX_SIZE/2`), the
  16-byte-aligned read size `& ~0xf`, and `readBorder = readTop - 30` — the 30-byte
  over-read margin getbits relies on (`Unpack15.java:211-236` ↔ `unpack.cpp:632-651`).

**RAR5 hook points — the sibling-engine rule.**

- **Build `Unpack5` as a sibling class taking `ComprDataIO`, not an extension of
  `Unpack20`.** The RAR3 chain's window is a compile-time constant
  (`MAXWINSIZE = 0x400000`, `Compress.java:28`) and everything indexes it with masked
  `int`s; RAR5 windows are dynamic (128 KB…4 GB; RAR7 up to 64 GB accepted) — the
  `MAXWINMASK` idiom and the `int` indexing headroom both break, so **chain extension
  is wrong by construction**. Per-archive window sizing is required, and every
  hardcoded guard distance (`-260`/`-270`/`-300`) must be re-derived from the target
  unrar source, not copied.
- Java arrays cap at 2^31 bytes — dictionaries > 2 GB need a segmented-window
  abstraction. That is junrar's own design problem (unrar's `unpack50frag.cpp`
  fragmented allocator is a 32-bit-C++ concern, optional to mirror). **Shipped at
  M4.3** (issue #35): `Unpack5Window` is a `byte[][]` of 256 MB power-of-two
  segments addressed by shift + mask, allocated lazily on first touch, with one
  segment spanning the whole window at 1 GB and below so those archives keep the
  flat layout. Engine capability 64 GB; the `maxDictionarySize` budget (4 GiB) is
  what keeps larger dictionaries opt-in.
- **The window is mask-addressed only if the window size is a power of two, and a
  RAR7 one is not.** The dictionary is `0x20000 << dictBits` *plus* a 5-bit
  fraction of itself (`winSize += winSize/32*frac`), so `& (size - 1)` wraps at the
  wrong position and reads window space that was never written. Port
  `WrapUp`/`WrapDown` (`d861246:unpack.hpp:416-435`, whose comment says exactly
  this) instead of the mask, and note `AddFilter` uses `% MaxWinSize` rather than a
  wrap because a malformed block start can exceed the window many times over
  (`d861246:unpack50.cpp:228-233`). Port `WrapDown` by *intent*: its C++ body
  (`WinPos >= MaxWinSize ? WinPos + MaxWinSize : WinPos`) undoes an unsigned
  `size_t` underflow, which signed Java `long` never has — transcribed literally it
  never wraps at all.
- RAR7 (method 70) is NOT a new engine: `Unpack5(ExtraDist=true)` with the DCX=80
  distance table (`unrar-delta-map.md` §2.8). Design `Unpack5` with that flag from
  day one.
- `BitInput` is reusable for RAR5 block headers (the local-instance trick,
  `Unpack.java:812-817`), but check the target version's `getbits.hpp` first — newer
  unrar grew `getbits32`/larger MAX_SIZE.

### 5.3 PPMd

**Where things live.** C++: `model.cpp`/`model.hpp`, `suballoc.cpp`/`suballoc.hpp`,
`coder.cpp` (renamed `PPM_*` → `RARPPM_*` in 5.x; `SafePPMDecodeChar` moved to
`unpack30.cpp:4`). Java: `unpack/ppm/` — `ModelPPM`, `SubAllocator`, `RangeCoder`,
the `Pointer` flyweight family (§4.3).

**Layer rules.** The heap-emulation rules of §4.3 govern everything here; the range
coder is the canonical masked-`long` uint domain (§4.2); PPM pulls raw bytes through
`Unpack.getChar()` (byte-aligned, bypasses the bit layer, `Unpack.java:1032-1037`).
Validation = the heap byte-diff harness (§4.13.4) — rebuild it before touching
allocator internals.

**Port targets (M1, from `unrar-delta-map.md` §3.1).** The corrupt-input guards to
bring over: 3.9.1 range-coder `count >= scale → return false` in decodeSymbol1/2;
3.9.8 `SafePPMDecodeChar()` wrapper; 5.6.1 `ps[MAX_O]` stack-overflow guards in
`CreateSuccessors`/`decodeSymbol2`. Watch the interaction with junrar's existing
`ppmError` latch (3.7.6-era shape — `Unpack.java:74`): 3.7.7+ replaced it with
`PPM.CleanUp()` + `UnpBlockType=BLOCK_LZ`; decide one shape, don't mix.

**Standing decision — RESOLVED by chunk P0.5 (`e23273d8`).** The `MaxMB = 1` clamp is
gone: junrar now honors `MaxMB+1` MB verbatim like unrar (format ceiling 256 MB; no-go
S8 row below). The 256 MB header-driven exposure becomes budget-gated by
`ArchiveOptions.maxDictionarySize` when P0.8 lands.

### 5.4 VM / filters

**Where things live.** C++ 3.7.3: `rarvm.cpp` (1107-line interpreter) + `rarvmtbl.cpp`
+ standard-filter native implementations; C++ ≥5.5.1: `rarvm.cpp` gutted to 351 lines —
`Prepare()` only fingerprints bytecode ({length, CRC32} against a **6-entry** table:
E8 53/0xad576887, E8E9 57/0x3cd7e57e, ITANIUM 120/0x3769893f, DELTA 29/0x0e06077d,
RGB 149/0x1c2c5dc8, AUDIO 216/0xbc85e701), anything else is a **no-op filter**;
UPCASE dropped; raw VM execution gone. Java: `unpack/vm/` — `RarVM` (full 3.7.3
interpreter + the same native standard filters), `BitInput`, `VMCmdFlags`, enums.

**Layer rules.**

- Filter integration topology (survives into RAR5): two producers (LZ code 257 →
  `readVMCode`; PPM escape → `readVMCodePPM`) feed `addVMCode` which pushes an
  `UnpackFilter` onto `prgStack`; **execution happens inside `UnpWriteBuf`'s
  written-region sweep** — wrap-aware two-part window copy into filter memory,
  parent↔stack GlobalData shuttling, chained same-block filters, filtered output
  written INSTEAD of window bytes, `NextWindow` deferral for blocks extending past the
  write pass (`Unpack.java:355-527` ↔ `unpack.cpp:654-778`; C++ originals inline as
  comments — the correspondence map).
- The interpreter has **live mis-translation bugs** (§6 T3) that persist because real
  archives hit the standard-filter fast path. Do not copy its idioms; parenthesize
  every masked term.

**RAR5 hook points.**

- **RAR5 dropped the VM entirely.** Filters are enumerated in the block header;
  only **4 types are applied**: DELTA(0), E8(1), E8E9(2), ARM(3)
  (`d861246:unpack50.cpp:427-485`; DELTA carries a 5-bit channel count). Port
  `UnpWriteBuf`'s **sweep topology**, not the VM. Limits to port with it:
  `MAX_FILTER_BLOCK_SIZE 0x400000`, `MAX_UNPACK_FILTERS 8192`.
- For the RAR3 path, milestone M2 (§9) replaces junrar's interpreter with the 5.5.1
  fingerprint-recognition shape: recognize the 6 canonical programs by hash, run the
  already-existing native transforms, drop UPCASE and raw programs. (unpack30 keeps
  *parsing* VM code bytes from the stream — parsing survives, interpretation doesn't.)

### 5.5 Crypto / CRC / IO

**Where things live.** C++: `crypt.cpp` (split to `crypt1/2/3/5.cpp` at 5.0.0),
`rijndael.cpp`, `sha1.cpp`/`sha256.cpp`, `blake2s*.cpp`, `hash.cpp` (`DataHash`
abstraction, 5.0.0), `crc.cpp`, `rdwrfn.cpp`. Java: `crypt/Rijndael.java` (KDF math
only), `crc/RarCRC.java` (ONLY `checkOldCrc` remains), `java.util.zip.CRC32`
everywhere else, `unpack/ComprDataIO.java`, `io/RawDataIo.java`.

**Layer rules.**

- Crypto: §4.11 policy. Cipher lives on the **io channel** (`RawDataIo.setCipher` for
  headers; `ComprDataIO.init(FileHeader)` builds the per-file decipherer from header
  salt, `ComprDataIO.java:119-126`), never inside decoders.
- CRC register convention: unrar keeps the un-finalized register (init 0xFFFFFFFF,
  xor at compare time); `java.util.zip.CRC32.getValue()` returns the finalized value.
  junrar bridges by storing `unpFileCRC = (int) (~unpCrc32.getValue())` after every
  update and comparing `~getUnpFileCRC() == hd.getFileCRC()` (`ComprDataIO.java:198`,
  `Archive.java:783-788`). **Keep this complement-at-observation-point trick in one
  place**; never re-implement running-CRC tables.
- `RarCRC.checkOldCrc` (RAR 1.5 rotate-add CRC) looks dead — it is NOT
  (`ComprDataIO.java:195`; no-go D2). Do not delete.

**RAR5 hook points.**

- `RawDataIo.setCipher` = header decryption; swap in AES-256-CBC (per-header 16-byte
  IV prepended, `HEAD_CRYPT` semantics — `unrar-delta-map.md` §2.4).
- `ComprDataIO.init(FileHeader)` = per-file crypto init (FHEXTRA_CRYPT: version 0,
  PSWCHECK/HASHMAC flags, Lg2Count, Salt16, IV16).
- The `unpWrite` CRC branch = where RAR5's optional **Blake2sp** (32-byte,
  FHEXTRA_HASH type 0) plugs in — mirror unrar's `DataHash` (CRC32 | Blake2sp) as a
  small Java interface. Blake2sp is the one JDK-missing primitive: port
  `blake2s.cpp` + `blake2sp.cpp` (183 + 153 lines at `8f437ab`, self-contained,
  8-lane "sp" tree composition) or use Bouncy Castle's `Blake2spDigest` (bcprov
  ships the full sp tree mode) — an owner-level dependency decision (§3).
- pswcheck makes wrong-password a first-class detectable error (8-byte check +
  4-byte csum) instead of a CRC failure — surface it as a distinct exception type,
  minding the `setChannel` filter (§4.9).
- Correction (2026-07-17, plan-review finding F1): an earlier revision of the
  Blake2sp bullet above claimed Bouncy Castle provides only `Blake2sDigest` and that
  the sp composition "still hand-built" — false. bcprov ships
  `org.bouncycastle.crypto.digests.Blake2spDigest` (full 8-lane sp tree mode;
  verified against the bcprov-jdk18on 1.85 javadoc). The port-vs-dependency decision
  is re-argued on the true facts in `PARITY_PLAN.md` §2.2.
- ComprDataIO return/throw mix: EOF throws `EOFException`, missing next volume
  returns `-1` (`ComprDataIO.java:134-136,156`) — mind it when adding RAR5 paths.

---

## 6. Known traps & live bugs

Mistranslation bug classes that exist in the tree TODAY. When your port touches one,
fix-or-avoid consciously; never propagate the idiom.

| # | Trap | Site | Detail |
| --- | --- | --- | --- |
| T1 | **Inert unsigned-sentinel compare** | `FileHeader.java:127` | `unpSize == 0xffffffff` — `long` vs int −1, can never be true; the port of `arcread.cpp:148`'s INT64MAX promotion is silently dead. Rule: suffix u32 sentinel literals with `L`. |
| T2 | **ProtectHeader double-read** — FIXED on rar5-port by chunk P0.2 (`266f2bbc`); description kept as a never-copy exemplar | `ProtectHeader.java:24-33` | read `version` at pos 0 **without advancing**, then `recSectors` also at pos 0; `mark` was 1 byte vs C++ `byte Mark[8]`; size constant 8 vs the real 15 tail bytes (`SIZEOF_PROTECTHEAD 26`, `headers.hpp:242-249`). Harmless only because the block is skipped by size. Never copy this pattern. |
| T3 | **RarVM precedence bugs** — DELETED on rar5-port by chunk M2.2 (issue #21); description kept as a never-copy exemplar | (was) `RarVM.java:250-251,300-393,509,565-609,636` | (a) `a & 0xFFffFFff + b` parses as `a & (0xFFffFFff + b)` — VM_ADDB/SUBB/INC/DEC/MUL/ADC/SBB all wrong; (b) ternary-vs-`\|` flag computation drops the sign bit + signed compare where C++ is unsigned; (c) VM_SAR uses `>>>` where C++ is arithmetic `>>`; (d) `prepare()` copies code with `inBuf[i] \|= code[i]` — a second prepare() can OR stale bytes. Survived because real archives hit the standard-filter path. **The bugs left with the code**: M2.1 (issue #20) replaced the interpreter with 5.5.1 fingerprint recognition, M2.2 then deleted the dead executor + opcode enums (mirrors unrar `1522ee0` = 5.5.1). **Rule (still applies to any future VM code): parenthesize every masked term; per-opcode unit tests for anything new.** |
| T4 | **Platform-charset passwords** | `Rijndael.java:47` | `password.getBytes()` assumes 1 byte/char + platform charset; non-Latin-1 passwords diverge from unrar. RAR5 mandates UTF-8 — convert explicitly. **Fixed 2026-07-18** (RAR3 KDF path now serializes passwords as UTF-16LE, `2e71167:crypt.cpp:188-286`). Known divergence kept open: for a non-BMP (supplementary-plane) password code point, Java's UTF-16LE (surrogate pair, 2 code units) matches unrar's Windows family (`wchar_t` is 16-bit) and its _APPLE family (`UtfToWide` also emits a surrogate pair) exactly; only generic-Linux/glibc unrar (`mbstowcs`, 4-byte `wchar_t`) truncates the code point to its low 16 bits in `WideToRaw`, diverging. Windows is the reference implementation and Java's choice matches 2 of the 3 platform families — accepted, not fixed further. |
| T5 | **KDF O(n²)** | `Rijndael.java:56-74` | Java re-hashes the whole accumulated buffer where C++ snapshots the running SHA-1 context. Same output, quadratic cost. Unnecessary under PBKDF2 — do not imitate. |
| T6 | **Charset / missing UTF-8 branch** | `FileHeader.java:157-165` | ANSI names decoded with platform default charset; the whole-name-UTF-8 branch of `arcread.cpp:189-194` was never ported (`fileNameW=""`). Fix rather than extend. **Fixed 2026-07-18, two rounds**: (1) the whole-name-UTF-8 branch is now ported (`fileNameW`, `FileHeader.java:170`) — no NUL split within the name field means the entire field is UTF-8-encoded (`UtfToWide`). (2) the ANSI-fallback name (`fileName`, both the split-representation half at `:162` and the genuinely non-LHD_UNICODE path at `:176`) decodes byte-transparently via `StandardCharsets.ISO_8859_1`, not UTF-8 — `arcread.cpp:186-205`'s non-LHD_UNICODE path does no charset conversion at all (raw `strncpyz`); UTF-8 decoding a legacy-codepage byte (CP1251/CP936/Windows-1252) that is not also valid UTF-8 silently and irreversibly replaces it with U+FFFD. Final decision: UTF-8 is reserved for the whole-name LHD_UNICODE branch only. **Superseded 2026-07-21, round 3 (issue #44) — the baseline moved from 3.7.3 to 7.2.7**: 7.2.7 no longer has a separate whole-name-UTF-8 branch at all. `d861246:arcread.cpp:345-360` runs `EncodeFileName::Decode` only when the name field carries a NUL split, then sends *every* name it did not produce — the plain-ANSI field, and the LHD_UNICODE-without-split field alike — through one narrow decode, `ArcCharToWide(FileName.data(),hd->FileName,ACTW_OEM)`. On Unix that is a strict multibyte decode in the process locale (UTF-8 on any modern system): `strfn.cpp:38-79` short-circuits all-low-ASCII, else calls `CharToWide`, which is `mbsrtowcs` on glibc and `UtfToWide` on macOS (`unicode.cpp:95-96`). So the detection rule is a **validity scan**, not a heuristic and not unconditional. junrar now mirrors that with `FileHeader.decodeNarrowName`: decode as UTF-8 when the field is valid UTF-8, else keep the round 2 byte-transparent ISO-8859-1 decode. It is called at three sites — the two that are literally unrar's `hd->FileName.empty()` fallback, plus the ANSI half of a NUL-split name, which is junrar-only (unrar keeps one name and discards that half when `EncodeFileName::Decode` succeeds) but is junrar's own fallback whenever `fileNameW` decodes empty (no-go C4), i.e. exactly the case unrar does convert. That third site is invisible to the corpus, which records `getFileName()` only, so it is pinned directly by `FileHeaderUtf8NameTest.splitNameAnsiHalfGetsTheSameNarrowDecode` through the deprecated `getFileNameString()`. The failure branch is a deliberate divergence (no-go D4): unrar's own fallbacks are lossy *and* platform-split — macOS truncates the name at the first bad byte (executed: `unrar 7.23 lb rar3-ansi-name.rar` prints `caf`), glibc remaps each byte `>= 0x80` into the U+E000 private-use area behind a U+FFFE marker (`unicode.cpp:203-248`). Pinned by `FileHeaderNarrowNameUtf8Test` (valid UTF-8 → decoded; fixture `unicode/rar3-ansi-utf8-name.rar`) and the unchanged `FileHeaderAnsiNameTest` (invalid → byte-transparent). Corpus consequence, measured not guessed: exactly **1** of 12,475 members flips, `commoncrawl3/3I/3ILZMJBO2TG2LCBV6L5AHMXEYLEXH4X2`, `Ð¡ÑÐµÐ¼Ð°3.spl` → `Схема3.spl`, matching `unrar 7.23 lt` on that member (name, sizes, mtime and CRC32 all agree). An independent scan of all 150,820 recorded names confirms it is the only one whose bytes are non-ASCII *and* valid UTF-8, and finds zero U+FFFD names, so unifying the LHD_UNICODE-without-split site changed nothing there. Of the two M3.11 scripts the issue asks to reuse, `scripts/corpus_oracle_check_m311.py` applies and was executed clean (`--corpus-root build/regressionCorpus`: *oracle-checked 1 flipped members: 1 listable OK, 0 mismatches*); `scripts/corpus_audit_m311.py` does not, and was executed to confirm that rather than assumed — it classifies flips into the three M3.11 RAR5 shapes only, so it rejects this RAR3 record as *not the plain V5-exception shape* and flags every non-corpus file. no-go D4 carries the classification for this flip instead. Reference JSONs regenerated (`includeTags 'generate'`, CONTRIBUTING.md); no corpus re-strip was needed — name decoding is a pure post-read transformation, and the flipped record differs in `fileName` alone with every other field intact, so the parser's read ranges are unchanged. |
| T7 | **No header-CRC verification** | `BaseBlock.java:88,110` (parsed, unchecked) | unrar verifies RAR3 header CRCs as **16-bit** (`GetCRC15`, `~CRC32 & 0xffff`) with legacy exemptions (SIGN/AV/old-UO; comment-bearing headers cover processed bytes only) and *records* a file-header mismatch while continuing (`BrokenHeader` + warning; fatal only for encrypted headers) — `d861246:arcread.cpp:431-445,514-545`; RAR5 uses full CRC32 (`GetCRC50`) and likewise attempts to process an unencrypted bad header (`d861246:arcread.cpp:683-696`). junrar checks none (only Mark/EndArc magic constants). **RAR5 must not inherit the gap** — verify with the format-correct width and tolerance. |
| T8 | **Vestigial `unsigned/` package** | `unsigned/*.java` | Three empty classes + one helper class, **zero importers** (re-probed 2026-07-17). Ignore it; never extend it; the mask idioms (§4.2) are the house style. |
| T9 | **PrgStack compaction dropped** | `Unpack.java:360,860` | C++ compacts executed (NULL) filter slots on every `AddVMCode` (`unpack.cpp:524-539`); Java appends forever and skips nulls — unbounded list growth on filter-heavy archives. Fix or bound when touching the filter path (unrar's later `MAX3_UNPACK_FILTERS 8192` cap is the M1 answer). |
| T10 | **Vestigial / dead code to not trust** | various | Old (RAR 1.x) format plumbing is dead (`isOldFormat`/`ReadOldHeader` never ported — headers report S2); `suspended` machinery never engaged; commented first-translations retained (`Unpack20.java:275-333`); the RAR3 DDecode lazy-init guard is vestigial (`Unpack.java:140-154`). Provenance breadcrumbs, not behavior. |

---

## 7. Regression no-go list

**A re-translation that drops any row below is a security or correctness regression.**
These are junrar-original behaviors with NO unrar 3.7.3 equivalent — a diff against the
C++ will show them as "extra" code; they are load-bearing. Source (verbatim, with full
commit archaeology): [`reports/divergences-no-go.md`](reports/divergences-no-go.md).

**The UNPINNED rule: an UNPINNED behavior gets its pinning test BEFORE any re-port
touches its file.** UNPINNED count: 0 — C1 pinned by chunk P0.1, C7 by P0.2, C13 + D1
by P0.3, C15 by P0.4 (as far as unit-pinnable), S8 resolved+pinned by P0.5 (rows below).

### Security (CVE-backed)

| # | Behavior that MUST survive | Commit(s) | Code site today | Guard test + fixture |
| - | -------------------------- | --------- | --------------- | -------------------- |
| S1 | **CVE-2018-12418 DoS**: corrupt size fields must NOT drive unbounded `byte[]` allocation (`safelyAllocate(size, MAX_HEADER_SIZE=20 MB)`); a repeated stream position must abort (`processedPositions` → `BadRarArchiveException`), not loop forever. | `ad8d0ba8` (2018, #8) | `Archive.java:92,311`, `safelyAllocate` call sites | `AbnormalFilesTest` (loop*.rar) + `ArchiveTest.testTikaDocs`. The duplicate-position branch itself is only weakly pinned (S3/S4 checks trip first) — preserve regardless. |
| S2 | **CVE-2022-23596 pt 1**: invalid SubBlock subtype must not NPE-and-loop — `getSubType()==null → break` before the switch. | `7b16b3d9` (2022) | `Archive.java` subblock switch (~:490) | `abnormal/loop.rar` → `CorruptHeaderException` |
| S3 | **CVE-2022-23596 pt 2**: `MarkHeader.isValid()` and `EndArcHeader.isValid()` (headCRC `0x3DC4`, type EndArc, flags `0x4000`, size==BaseBlockSize). | `6c7dc7af` (2022) | `Archive.java:364-365,430-431` | `abnormal/loop1/2/3.rar` → `CorruptHeaderException` |
| S4 | **Invalid-header checks**: `nameSize<=0` → `CorruptHeaderException`; EOF mid-header → `CorruptHeaderException("Unexpected end of file")`, not raw `EOFException`; `isFilenameValid()` gate. | `cc33b6c1` (2022) | `FileHeader.java:142-143,169-170`; `Archive.java:452` | abnormal corpus |
| S5 | **CVE-2026-28208 backslash traversal (Linux)**: separator-normalize (`\`→`/`) BEFORE the canonical-path check; `makeFile` splits on `/` not `\\`. | `947ff1d3` (2026) | `LocalFolderExtractor.java:58,76,96` | `LocalFolderExtractorTest…Exception2` → `parent-dir.rar` |
| S6 | **CVE-2026-41245 sibling-prefix Zip-Slip**: containment compares against `destination.getCanonicalPath() + File.separator`, never a bare prefix. | `d77e9a83` (2026) | `LocalFolderExtractor.java:35,61` | `…Exception3` → `sibling-prefix-traversal.rar` |
| S7 | **Original path-traversal guard (#31)**: canonical-path `startsWith` in createFile/createDirectory; traversal → `IllegalStateException`. | `e60b915f` (2019) | `LocalFolderExtractor.java` | `LocalFolderExtractorTest.rarWithDirectoriesOutsideTarget_…` |
| S8 | **PPM `MaxMB` policy** — historical `MaxMB=1` clamp (`0dc9d457`, 2020) RESOLVED by P0.5 (`e23273d8`): honor `MaxMB+1` verbatim per unrar `model.cpp` `DecodeInit` (format ceiling 256 MB); budget-gating arrives with P0.8 `maxDictionarySize`. | `0dc9d457` (2020), `e23273d8` (P0.5) | `ModelPPM.java` `decodeInit` | `PpmMaxMbTest` → `ppm/legit-maxmb63.rar` (+ oracle), `ppm/preserved-maxmb0.rar`, `ppm/hostile-maxmb255.rar` |

### Correctness / robustness

| # | Behavior that MUST survive | Commit(s) | Code site today | Guard test + fixture |
| - | -------------------------- | --------- | --------------- | -------------------- |
| C1 | **Solid RAR v20 AIOOBE**: `CopyString20` guards `destPtr >= 0` (solid back-ref into a previous file goes negative; C++ unsigned wraps into the masked slow path, signed Java arraycopies a negative index). | `9b69c6b7` (2026) | `Unpack20.java:215` | `Unpack20SolidTest` → `solid/v20-solid-negative-backref.rar` (pinned by P0.1, `ff86ddae`) |
| C2 | **Audio-decode per-slot init**: `AudV`/`MD` arrays filled with *distinct* new objects per slot (shared-ref `Arrays.fill(…, new X())` caused CRC errors; null `MD` → NPE). | `15f4afa2`, `952436b2` (2022) | `Unpack20.java` init block | `ArchiveTest.testAudioDecompression` → `audio/*.rar` vs `.wav` |
| C3 | **RAR4 EXTTIME parsing**: mTime/cTime/aTime/arcTime as `FileTime`, 100 ns units; DOS mtime base. | `d5cc784c` (2022) | `FileHeader.java:200-224,249-273` | `ArchiveTest.testArchiveExtTimes_*` (4 TZ) → `rar4-ext_time.rar` |
| C4 | **OOB read on corrupt ext-time (#86)**: require `position+1 < fileHeader.length` before the 2-byte flags, else flags=0 + warn; `getFileName()` falls back to `fileName` when `fileNameW` empty despite unicode bit. | `1d52d5d3` (2022) | `FileHeader.java:204-210,670` | `GitHub86MissingDataTest` → `gh-86-missing-data.rar` |
| C5 | **DOS-time millisecond zeroing**: `cal.set(MILLISECOND, 0)`. | `b1f96385` (2020) | `FileHeader.java:323` | indirectly by C3 |
| C6 | **Subblock seek-past-data**: after any 0x77 SubHeader, unconditionally seek `positionInFile + headerSize + dataSize` (else later headers parse at wrong offsets); loop guard on that path. | `ad7ad33b` (2026) | `Archive.java:405-410` | `MacSubblockTest` → `mac-subblock.rar` |
| C7 | **Protect-header seek-past-data**: `+ getDataSize()` in the ProtectHeader seek. | `8e91d695` (2011) | `Archive.java` ProtectHeader case | `bugfixes/ProtectHeaderTest` → `bugfixes/protect-header-seek.rar` (pinned by P0.2, `2fee9275`) |
| C8 | **Null main header → typed exception**, never NPE. | `8433b1b6` (2018) | `Archive.java` `isEncrypted()` | `abnormal/mainHeaderNull.rar` → `BadRarArchiveException` |
| C9 | **Corrupt header → `CorruptHeaderException`**, never NPE (#36/#45). | `b7215896` (2020) | `Archive.java` header loop | `abnormal/corrupt-header.rar` |
| C10 | **Unicode filename validation (#108)**: validate the *effective* `getFileName()` once via `isFilenameValid`, not fileName/fileNameW separately. | `a5186a8b` (2023) | `FileHeader.java:169-170,230-237` | `ArchiveTest.gh108_…` → `gh108.rar` |
| C11 | **VMSF_UPCASE lookup (#110)**: `findFilter(7)` returns UPCASE, not null (junrar-only lookup-omission class). | `bc9889bc` (2023) | `VMStandardFilters.java` | `VMStandardFiltersTest` (@EnumSource, all 8) + companion enum tests |
| C12 | **Empty file → InputStream (#88/#90)**: `EmptyInputStream` for size ≤ 0; pipe buffer `max(min(size,PIPE),1)`. | `c95a211a` (2022) | `Archive.java` `getInputStream` | `GitHub88EmptyFile` → `gh-88-empty.rar` |
| C13 | **>2 GB via InputStream (#104)**: `RandomAccessInputStream.length` is `long`. | `cbbe99c4` (2022) | `RandomAccessInputStream.java` | `LargeEntryContractTest` (virtual channel, no giant fixture; pinned by P0.3, `33473861`) |
| C14 | **Missing EndArcHeader on stream parse (#216)**: `InputStreamVolume.getLength()` = `available()` (fallback `Long.MAX_VALUE`), so a truncated stream terminates instead of throwing. | `964801cd` (2025) | `InputStreamVolume.java` | `ArchiveTest` stream-parse assertions |
| C15 | **Unsigned-shift class rule**: every C++ unsigned right shift is Java `>>>`. Applied across `RandomAccessStream`, `FileNameDecoder`, `Unpack15`, `BitInput`, `RarVM`. | `25491b50` (2020; `d276f937` 2022 touched `Unpack.java`/`Rijndael.java` only — not these 5, per `reports/signedness-audit.md`) | those 5 files | `*SignednessTest` (13+1 boundary tests) + `reports/signedness-audit.md` (P0.4, `92011126`+fixes: zero bare `>>` in all 5 files; decode-loop sites deferred to suite coverage per ledger) — pinned as far as unit-pinnable |

### Deliberate divergences (look like bugs/dead code — must stay)

| # | Divergence to preserve | Commit(s) | Note |
| - | ---------------------- | --------- | ---- |
| D1 | **>2 GB single entry deliberately NOT extracted to `byte[]`** — a prior attempt (`6ecfb718`) was reverted (Java arrays cap at `Integer.MAX_VALUE`). | `a43a5192` (2019) | `LargeEntryContractTest` pins the streaming observable (P0.3, `33473861`). Don't "fix" by re-porting a `byte[]` path. |
| D2 | **CRC32 via `java.util.zip.CRC32`** (JDK intrinsics) replaced the hand-rolled table — behavior-preserving; **`RarCRC.checkOldCrc` (RAR 1.5) is RETAINED** — do not delete. | `5270d235` (2026) | Sites: `ComprDataIO.java:103-104,195`, `RarVM.java:903`. Must stay bit-identical to the table version. |

Also preserve the **Java-API features** unrar has no notion of (extract-from-InputStream,
password/header-encrypted archives, multi-part from streams, `getFileName()`,
thread-pool `getInputStream`, solid-RAR4 random access) — table in
`reports/divergences-no-go.md` "Java-API features". The commons-vfs2 provider was
deliberately REMOVED (`3bbe8eda`) — do not reintroduce.

---

## 8. Testing discipline

### 8.1 Conventions (evidence: `reports/layer-idioms-style.md` §6)

- **Framework**: JUnit 5 + AssertJ (JUnit `Assertions` import is build-failing, §3);
  Mockito 5, junit-pioneer (`@DefaultTimeZone`), commons-io available.
- **Fixtures**: `.rar` files under `src/test/resources/com/github/junrar/`, loaded via
  `getClass().getResource(...)`; subfolders by concern (`abnormal/`, `password/`,
  `solid/`, `audio/`, `volumes/{old-numbers,new-numbers,new-part}`, `bugfixes/`).
  New audio-style sweeps via `generate-testdata.sh` (real `rar` CLI). The fixture→guard
  map lives in `reports/divergences-no-go.md` "Fixture corpus".
- **Corrupt-input pattern** (the CVE/fuzz house pattern, `AbnormalFilesTest`):
  `@ParameterizedTest @MethodSource` mapping `fixture path → expected exception Class`,
  asserted `catchThrowable` + `isInstanceOf(RarException.class)` +
  `isExactlyInstanceOf(expected)` — each fixture exercised through **all four public
  surfaces** (Junrar.extract from File and InputStream; manual Archive loop from File
  and InputStream). Every new hostile-input fixture follows this shape.
- **Issue-pinned bugfix tests**: package `com.github.junrar.bugfixes`, class
  `GitHub{NN}{Slug}Test`, issue URL in a comment, **expected values copied from real
  UnRAR output** ("Data taken from UnRAR") — real unrar is the oracle convention.
- Naming: BDD-ish `givenX_whenY_thenZ` for new tests; `@Nested` groups scenarios.
- Resource hygiene pinned by `ResourceReleasedTest` (deletability proves handles
  closed).

### 8.2 Regression corpus — the acceptance gate

- Separate Gradle suite `regressionTest`; corpus = external ~7 GB zip, >13,000 RAR
  files (not in repo); **12,475 reference JSONs ARE committed** under
  `src/regressionTest/resources/corpus/**`.
- Mechanism: for every corpus file, build `Archive`, serialize an `ArchiveRecord`
  (`isRarV5, isEncrypted, isPasswordProtected, isOldFormat, fileHeaders[], exception`)
  and compare to the committed JSON. Exceptions are captured as *expected outcomes*.
  `@Timeout(30)` per file, `@DefaultTimeZone("UTC")`.
- Two tag modes: `check` (default) vs `generate` (flip `includeTags` in build.gradle,
  run, commit the JSON churn).
- Local run: `JUNRAR_REGRESSION_TEST_CORPUS_ROOT` + `…_CORPUS_DIR` env vars,
  `./gradlew regressionTest`.
- **RAR5 implication: the reference JSONs currently encode
  `UnsupportedRarV5Exception` as the expected result for RAR5 members. Landing RAR5
  support flips thousands of expectations** — a mandatory, human-reviewed
  `generate`-mode regeneration is part of the change, and the corpus run (maintainer-
  approved GitHub environment) is the primary acceptance gate.

### 8.3 Commands and CI gates

```sh
./gradlew build            # compile @ release 8 + checkstyle + ArchUnit + unit suite + javadoc
./gradlew check            # same gate set
./gradlew regressionTest   # corpus (env vars above)
```

CI: `ci.yml` runs `./gradlew build` on **ubuntu + macos + windows**, JDK 21 temurin;
jacoco→codecov informational (no threshold gate). `regression.yml` runs on every
PR/push but **gated by manual maintainer approval** (GitHub environment `regression`).
A big PR must pass: 3-OS build + approved corpus run.

---

## 9. The road to 7.2.7

Condensed from [`reports/unrar-delta-map.md`](reports/unrar-delta-map.md) — read it in
full before planning any milestone.

### 9.1 Census

`git diff --stat 2e71167 d861246`: **173 files changed, +26071/−15856; 51 new files,
17 deleted.** Cliff edges that make naive whole-range diffs useless: **5.0.0**
(file split + RAR5 — `unpack.cpp` 1187→305 lines, unpack15/20/30/50/inline split out),
**5.5.1** (RarVM gutted 1128→351 lines, `rarvmtbl.cpp` deleted, UPCASE dropped),
**7.0.1** (RAR7 format: `VER_PACK7`, ExtraDist, fractional dict bits), **7.1.1**
(std::vector/std::wstring churn — semantically irrelevant for Java, textually noisy;
compare semantics, not lines). Use 4.2.4 (`5db30e6`) for 3.7.3-shaped diff context.

### 9.2 RAR5/RAR7 component inventory (extractor-required)

| Component | C++ source | Java landing zone |
| --- | --- | --- |
| vint + RAR5 block layout, flags, extra records | `headers5.hpp`, `arcread.cpp:555-1257` (`ReadHeader50`, `ProcessExtra50`) | new `rarfile/` RAR5 header classes + shared vint reader (§5.1) |
| Signature/version detect (`00`=RAR15, `01`=RAR50, `02..04`=future-graceful) | `archive.cpp` `IsSignature` | `MarkHeader` (V5 byte already detected today) |
| Header encryption (HEAD_CRYPT: AES-256-CBC, per-header IV, Lg2Count≤24, Salt16, pswcheck) | `crypt5.cpp` | `RawDataIo.setCipher` (§5.5) |
| File crypto (FHEXTRA_CRYPT) + PBKDF2-HMAC-SHA256 KDF + pswcheck + `ConvertHashToMAC` | `crypt5.cpp:85,193` | `ComprDataIO.init` + JDK `SecretKeyFactory` (§4.11) |
| SHA-256 | `sha256.cpp` | `MessageDigest` — no port |
| Blake2sp file hashes | `blake2s.cpp`, `blake2sp.cpp` | port (~340 lines) or Bouncy Castle `Blake2spDigest` — owner decision (§5.5) |
| Hash abstraction (CRC32 \| Blake2sp) | `hash.cpp` | small Java interface in the `unpWrite` branch |
| RAR5 LZ decoder + 4 filters (DELTA/E8/E8E9/ARM) | `unpack50.cpp`, `unpackinline.cpp` | sibling `Unpack5` engine (§5.2) |
| Dynamic window (128 KB–4 GB; RAR7 to 64 GB, `UNPACK_MAX_DICT`) | `unpack.cpp` `Init(uint64,bool)` | per-archive window + lazily segmented abstraction >1 GB (§5.2, M4.3) |
| Non-power-of-two window wrap (`WrapUp`/`WrapDown`, `%MaxWinSize` in `AddFilter`) | `unpack.hpp:416-435`, `unpack50.cpp:228-233` | `Unpack5.wrapUp`/`wrapDown` on `long` positions (§5.2, M4.3) |
| UTF-8 names, HTIME extras (ns since 5.5.1), UOWNER, VERSION | `arcread.cpp` | RAR5 FileHeader loader |
| REDIR link records + symlink-safety layers (5.2.5 / 6.1.7 / 6.2.3) | `extinfo.cpp`, `ulinks.cpp`, `extract.cpp` | `LocalFolderExtractor` parity (§7 S5–S7 rules) |
| `.partN.rar` volumes, `MHFL_VOLNUMBER`, `EHFL_NEXTVOLUME` | `volume.cpp`, `arcread.cpp` | `VolumeManager` implementations (§4.12) |
| RAR7 delta: `Unpack5(ExtraDist)`, DCX=80, 5+5 dict bits, `FCI_RAR5_COMPAT`, dict-limit gate | `d52ee2f` onward | flag + tables on `Unpack5` (§5.2) |

**Skippable / optional at every stage** (owner's standing notes): `unpack50mt` +
threadpool (single-threaded `Unpack5` is complete and canonical), SIMD/machine-code
paths (`blake2s_sse`, AES-NI — JCE covers it), `qopen` (pure open/list perf),
recovery records (`recvol3/5`, `rs16` — repair command; **recovery-record use is
optional**), `largepage`, `motw` (Windows-only), `secpassword` (JVM GC makes byte-exact
scrubbing moot — use `char[]` hygiene), fragmented window (JVM design differs anyway).
**CLI-only features are non-goals** (`cmdfilter/cmdmix`, ui*, console).

### 9.3 Milestones

The delta map recommends four sync points. *Owner's standing note: a version-by-version
walk is acceptable instead, if the evidence favors it for the piece being ported —
the milestones are the default shape, not dogma.*

1. **M1 — RAR3 hardening to 7.2.7 semantics (no format work).** Port the pinned
   guards directly from 7.2.7 blobs: PPMd 3.9.1 `count>=scale` + 3.9.8
   `SafePPMDecodeChar` + 5.6.1 `ps[MAX_O]` guards; 5.3.1 `MAX3_UNPACK_FILTERS 8192`
   (also answers trap T9); 5.3.6 unpack15 `FlagsPlace` guard; 5.5.4
   `MAX3_UNPACK_CHANNELS 1024`; 7.0.3 `FirstWinDone` distance validation (all legacy
   paths); `encname.cpp` bounds-checked rewrite mirrored into `FileNameDecoder`.
   Note: method 36 (3.6.1 "alternative hash" experiment) was dropped upstream in
   5.0.0 — safe to drop in junrar too.
2. **M2 — RarVM replacement (unrar 5.5.1 shape).** Fingerprint-recognize the 6
   canonical filter programs + hardcoded transforms; drop UPCASE and raw program
   interpretation. Small, self-contained, deletes the T3 bug surface.
3. **M3 — RAR5 core (target unrar 6.2.12 `8f437ab`).** Everything in §9.2 minus RAR7.
   6.2.12 is the last pre-RAR7 release and the most stable, field-tested reference.
   Reading order: `headers5.hpp` → `arcread.cpp:555-1257` → `crypt5.cpp` →
   `unpack50.cpp` → `ulinks.cpp`/`extinfo.cpp`. Includes the regression-corpus
   regeneration (§8.2).
4. **M4 — RAR7 delta (6.2.12→7.2.7, small).** ExtraDist/DCX=80, 5+5 fractional dict
   bits, 64 GB cap + a configurable dict limit (the `CheckWinLimit` analog — expose a
   guard instead of blindly allocating), `FCI_RAR5_COMPAT`. `FirstWinDone` already
   landed in M1. Localized to table sizes + distance decode.

Every milestone obeys §7 (no-go rows + UNPINNED-first testing) and §8 (fixtures for
every new guard; corpus regeneration when observable output changes).

---

## Appendix A — issue-number index

Two different issue trackers are cited across `docs/porting/`, both by bare `#NN`:

1. **Upstream `junrar/junrar` issues** — the provenance of historical bugfixes
   (the §6 trap rows and the no-go S/C rows: #8, #14, #31 in S7, #36/#45 in C9,
   #86, #88, #90, #104, #108, #110, #216). These live in this repository's own
   tracker and are stable; they keep their bare form.
2. **Port-tracker issues** — the working tracker the RAR5/RAR7 port was developed
   under (`andrebrait/junrar`). That fork may not exist forever, so every
   port-tracker issue cited anywhere in `docs/porting/` is summarized
   self-containedly below; the citations remain readable even if the tracker
   disappears.

**Number collision warning:** `#31` means two different things. In no-go row **S7**
it is upstream junrar#31 (the 2019 `directoryTraversalBug` path-traversal fix,
`e60b915f`). Everywhere it appears next to M3.10 or issue #40 (no-go row D3) it is
port-tracker #31, indexed below. Similarly, `#36`/`#45` in no-go row **C9** are
upstream 2020 corrupt-header NPE reports, unrelated to the port tracker.

| Port issue | Chunk | What it did |
| --- | --- | --- |
| #12 | P0.7 | RAR3 header-CRC verification scaffolding: 16-bit `GetCRC15` coverage with unrar's legacy exemptions, and the record-vs-fatal tolerance contract (broken headers are marked and refused at extract time, not at open). |
| #18 | M1.4 | `FirstWinDone` distance validation (unrar 7.0.3 parity): a match distance reaching into the never-written part of the window zero-fills instead of self-copying stale bytes. |
| #20 | M2.1 | Replaced the RarVM bytecode interpreter with unrar-5.5.1-style standard-filter fingerprint recognition ({length, CRC32} against the six canonical filters). |
| #21 | M2.2 | Deleted the dead VM interpreter, opcode enums and parse-only VM types (§6 T3 surface removal); mirrors unrar `1522ee0`. |
| #27 | M3.6 | `Unpack5` sibling-engine skeleton: per-archive window, bit input, block-header read, the five RAR5 Huffman tables, and the three-part dictionary resource model. |
| #31 | M3.10 | `FHEXTRA_REDIR` link extraction with the three symlink-safety layers; unsafe targets throw `UnsafeLinkException`. (Port-tracker #31 — distinct from upstream #31 in S7.) |
| #34 | M4.2 | RAR7 extended-distance decode (`DCX`=80 alphabet, `DBits>36` arm), distance state widened to `long` end to end, version-70 routing via `FileHeader.isRar5Family()`. |
| #35 | M4.3 | Segmented window above 1 GB (`byte[][]` of lazily allocated 256 MB segments), engine capability raised to 64 GB, positions wrap instead of masking (RAR7 dictionaries are not powers of two). |
| #40 | M3.10 follow-up | Investigation that closed the three link-path divergences as working-as-intended: keep junrar's fail-closed reject over unrar's sanitize-and-continue (no-go row D3). |
| #44 | post-M4 | RAR3 narrow-name decode moved to the 7.2.7 rule: UTF-8 validity scan with a lossless ISO-8859-1 fallback (`FileHeader.decodeNarrowName`, §6 T6 round 3, no-go row D4); exactly one corpus member flipped. |
