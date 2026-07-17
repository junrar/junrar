# C15 signedness audit — the 5 unsigned-shift files

Date: 2026-07-17. Chunk P0.4 (`docs/porting/PARITY_PLAN.md` §3). Audited tree:
`~/git/junrar-issue9` at `9b7d1fc9` (branch `issue/9-pin-c15-signedness`). C++ coordinates
resolved against `~/git/unrar` (one commit per release): `2e71167` = 3.7.3 (the pinned
translation baseline, `reports/baseline-pin.md`); `d861246` = current mirror HEAD (7.2.7),
used only where a coordinate is unavoidably newer.

## File resolution (manual §7 row C15 vs the tree)

The manual/no-go report name `RandomAccessStream`; the tree has no such file. Commit
`25491b502603ca1a01bb6457b52648427faff606` ("refactor: replace >> with >>>", 2020), the
first of the two class-rule commits, touched a file literally named
`io/RandomAccessStream.java`. `git log --follow` on the current
`io/RandomAccessInputStream.java` reaches back through that exact history (`bb362df3`,
`7950dc34`, …) — a straight rename, not an ambiguity. Resolution: **RandomAccessStream =
`src/main/java/com/github/junrar/io/RandomAccessInputStream.java`**. The other 4 names
match tree paths verbatim:

| Manual name | Path |
| --- | --- |
| RandomAccessStream | `src/main/java/com/github/junrar/io/RandomAccessInputStream.java` |
| FileNameDecoder | `src/main/java/com/github/junrar/rarfile/FileNameDecoder.java` |
| Unpack15 | `src/main/java/com/github/junrar/unpack/Unpack15.java` |
| BitInput | `src/main/java/com/github/junrar/unpack/vm/BitInput.java` |
| RarVM | `src/main/java/com/github/junrar/unpack/vm/RarVM.java` |

## Counting pipeline (committed with this ledger)

```sh
grep -oE '>{2,3}' <file> | grep -cx '>>'
```

One row per **occurrence** (not per line); an occurrence that is part of `>>>` is excluded
by construction (the greedy `{2,3}` match consumes all 3 characters as one token, so it
never collapses into a spurious `>>` hit). Per-file output, run twice against the paths
above for reproducibility:

| File | Pipeline output (plain `>>` count) |
| --- | --- |
| RandomAccessInputStream.java | 0 |
| FileNameDecoder.java | 0 |
| Unpack15.java | 0 |
| BitInput.java | 0 |
| RarVM.java | 0 |

## Mechanical audit table

**Zero rows in every file** — the pipeline output above is 0 for all 5 files, so the ledger
row count (0) equals it trivially. **Finding: the C15 class rule already holds with no
exceptions as of this audit.** The two historical commits this row cites —
`25491b50` (2020, "refactor: replace >> with >>>") and `d276f937` (2022, "perf: reduce
array creation, unsigned shifts") — converted every unsigned right shift in these 5 files
to `>>>`, and no plain `>>` has been reintroduced since (`git log -p` on these 5 paths
shows no shift-operator edit after `d276f937`). There is therefore nothing to classify
`signed-on-purpose` / `unsigned-required` / `non-code`: the class rule's entire purpose —
keep every C++-unsigned shift as Java `>>>` — is met with zero exceptions, and the risk
addressed by this chunk is a **future regression** (a `>>>`→`>>` typo), not a present
defect. That risk is what the boundary tests in `src/test/java/**/*SignednessTest.java`
pin against.

## Why the boundary tests still have value (and their real discriminating power)

Every `>>>` site in these 5 files was checked for whether an isolated `>>>`→`>>` swap would
actually change any output, given the surrounding code. Two structural patterns recur and
make most sites **provably immune** to that one-character swap in isolation — worth
recording so a future auditor doesn't re-derive it from scratch:

1. **Pre-masked operand.** If the value being shifted is already bounded non-negative by a
   preceding `& 0xff`/`& 0xfff0`/similar mask (true for `BitInput.getbits()`,
   `FileNameDecoder`'s `flags >>> 6` once `flags` is read via the masked `getChar`, and
   `Unpack15.decodeNum`'s `(Num - …) >>> (16 - StartPos)` since `Num &= 0xfff0` first), bit
   31 of the operand is always clear, so `>>` and `>>>` compute identically for every
   reachable input.
2. **Post-masked/narrowed result.** If the shifted value is immediately combined with
   `& 0xff` (byte extraction, e.g. `RarVM.setLowEndianValue(Vector, int, int)`) or narrowed
   to `char` after a `<<8`-then-add composition (`FileNameDecoder`'s shifted terms), the bits
   that would differ between `>>` and `>>>` live entirely in the discarded region, so the
   mask/narrow cancels the sign-extension difference (a masked byte `b` and its sign-extended
   reading `b-256` differ by an exact multiple of the mask's modulus).

The genuinely **discriminating** sites — where a swap or an adjacent regression IS
observable — are the ones the tests target:

- `RandomAccessInputStream`'s `(int)(pointer >>> BLOCK_SHIFT)`: not the `>>>` operator
  itself (`pointer` is never negative — both `seek(long)` and `seek(int)` clamp/zero-extend
  to non-negative), but the **cast/shift ORDER**: moving the `(int)` cast before the shift
  (a plausible refactor, not a character typo) truncates `pointer` before deriving the block
  index. `LargeEntryContractTest` (P0.3) already pins this at `2^31`, where the two formulas
  coincide by coincidence (single bit set, nothing above it to truncate away).
  `RandomAccessStreamSignednessTest` extends it to `2^32 + BLOCK_SIZE` — a value with bits
  set above bit 31 — where shift-then-truncate (block 8388609) and truncate-then-shift
  (block 1) genuinely diverge; the test asserts this divergence exists AND that the stream
  reads the shift-then-truncate block.
- `FileNameDecoder`'s `flags >>> 6` (case/branch **selection**, not the shifted char value):
  `flags` is masked via `getChar`, but if that mask were ever dropped, a flags byte `>= 0x80`
  (cases 2 and 3 only — cases 0/1 top out at `0x7F`) would read as negative and
  `flags >>> 6` would select a bogus case index outside `{0,1,2,3}`, silently skipping that
  portion of the name. `FileNameDecoderSignednessTest` uses flags bytes `0x80` (case 2) and
  `0xC0` (case 3, both branches) specifically to keep this path live. Case 0/1's unshifted
  data-byte terms (`getChar(...)` appended directly, or added to `high<<8` without their own
  mask) are also genuinely mask-sensitive — sign-extension there lands inside the `char`'s
  low 16 bits, not above them — so those are pinned too (see per-case derivations in the
  test file).
- `RarVM`'s opcode-level shifts (`VM_SHR`/`VM_SAR`, `RarVM.java:499-514`) store the **raw**
  shift result into VM memory with no trailing byte mask — genuinely sign-sensitive for a
  negative `value1` — but are reachable only through full `execute(VMPreparedProgram)`
  opcode dispatch (register/memory operand resolution via `getOperand`/`prepare`), which is
  out of the "static/pure helper" scope this chunk unit-pins (`PARITY_PLAN.md` §3 P0.4
  coverage matrix item 4). Deferred; see "Deferred sites" below.
- `RarVM.filterItanium_GetBits` (private, `RarVM.java:1190-1198`) composes 4 raw mem bytes
  with the top one at `<< 24` and NO intermediate mask: if that top byte is `>= 0x80` the
  composed `bitField` is negative before `bitField >>>= inBit;` runs, so a `>>>`→`>>` swap
  there IS observable for `inBit != 0` — unlike every other site in this file. Also
  reachable only through the `VMSF_ITANIUM` filter path (private, no direct call site
  outside `execute()`'s standard-filter switch); deferred alongside `VM_SHR`/`VM_SAR`
  rather than inventing a private-method-invocation-via-reflection harness beyond this
  chunk's named field-seeding precedent.

## Live-bug-adjacent finding (pre-existing, out of scope — not a new ESCALATE)

`RarVM.java:509` (`VM_SAR` case): `int result = ((int) value1) >>> value2;` uses `>>>`
where unrar's C++ uses a **signed** (arithmetic) shift for this opcode
(`rarvm.cpp:414`, cited by `MIGRATION_MANUAL.md` §4.2's canon table row "signed `>>` the C++
casts to `(int)`"). This is the opposite direction from the C15 class rule (a `>>>` that
should be `>>`, not a `>>` that should be `>>>`) and is **already documented and tracked**
as manual §6 item T3 ("RarVM precedence bugs" / VM_SAR arithmetic-shift mistranslation) —
not a new finding of this audit, and outside P0.4's scope (which pins the class rule's
`>>`→`>>>` direction, not T3's `>>>`→`>>` direction). Not fixed here per the brief's
constraint (tests + this report only, no `src/main` changes); left for whichever chunk
retires T3 (`MIGRATION_MANUAL.md` line ~854 names a future "deletes the T3 bug surface"
chunk).

## Coverage — SignednessTest files

| File | Method(s) pinned | Test |
| --- | --- | --- |
| `BitInput.java` | `getbits()`, `fgetbits()` — the two buffer-widening bit-reading methods | `unpack/vm/BitInputSignednessTest.java` |
| `FileNameDecoder.java` | `decode(byte[], int)`, all 4 switch cases (0/1/2/3-plain/3-correction) | `rarfile/FileNameDecoderSignednessTest.java` |
| `RandomAccessInputStream.java` | `read(byte[], int, int)` block-index arithmetic at offsets with bits above 31 | `io/RandomAccessStreamSignednessTest.java` |
| `Unpack15.java` | `decodeNum(int, int, int[], int[])` | `unpack/Unpack15SignednessTest.java` |
| `RarVM.java` | `setLowEndianValue(Vector<Byte>, int, int)` | `unpack/vm/RarVMSignednessTest.java` |

### BitInput — full public-method enumeration

`InitBitInput()`, `addbits(int)`, `getbits()`, `faddbits(int)`, `fgetbits()`,
`Overflow(int)`, `getInBuf()`. Only `getbits()`/`fgetbits()` derive a value from a buffer
byte through widening + shift ("bit-reading" per the chunk brief); `addbits`/`faddbits`
advance the bit position from the caller-supplied bit **count** (never a buffer byte — every
call site in the codebase passes a small non-negative literal, max `faddbits(16)`),
`Overflow` is a bounds predicate, `getInBuf` a plain accessor, `InitBitInput` a reset. None
of the latter five widen a buffer byte, so none carry the "high-bit-set buffer" risk this
chunk targets — justified exclusion, not an oversight.

### Deferred sites (reachable only through full decode/execute paths)

Per `PARITY_PLAN.md` §3 P0.4 coverage matrix item 4 ("For shifts only reachable through full
decode paths, the ledger verdict + existing decompression-suite coverage is the justified
deferral"):

- **`Unpack15`**: `shortLZ`, `longLZ`, `huffDecode`, `getFlagsBuf`, `corrHuff`,
  `oldCopyString` (lines 263, 266, 273, 289, 322, 351, 375, 388, 402(×2), 409, 477, 489,
  493, 496, 519) all need live `BitInput` buffer state (`unpReadBuf`) and mutable codec
  fields (`AvrLn1/2/3`, `Nhfb`, `Nlzb`, `ChSet*`) populated by a real decode — not
  independently constructible without inventing a decode-state harness beyond this chunk's
  reflection-seeding precedent. Exercised today by
  `ArchiveTest.testAudioDecompression` → `audio/BoatModernEnglish-regular-unpack15-dos.rar`
  and `-win.rar` (RAR 1.5 format).
- **`RarVM`**: `VM_SHR`/`VM_SAR` (opcode execution, lines 499-514) and the private
  `filterItanium_GetBits`/`filterItanium_SetBits` helpers (lines 1173-1199, used only from
  the `VMSF_ITANIUM` standard-filter case) need a `VMPreparedProgram`/`VMPreparedCommand`
  built through `prepare()`'s bytecode decoder or `execute()`'s register/memory operand
  resolution — constructing one by hand invents a bytecode-execution mechanism beyond the
  named reflection-seeding precedent (P0.3's private-field seeding), so it is deferred
  rather than forced. `Unpack.java:390+` wires `RarVM.execute` into the RAR29 filter path;
  general filter-path fixtures exist (e.g. `solid/rar4-solid.rar`, the `tika-documents.rar`
  corpus) but VM_SHR/VM_SAR-specific exercise by a *current* fixture was not independently
  confirmed — recorded honestly rather than overclaimed. `setLowEndianValue(byte[], int,
  int)` (the sibling overload) delegates entirely to `Raw.writeIntLittleEndian`, which has
  no `>>>` of its own in `RarVM.java` and is outside this file's audit.
- All other `>>>` occurrences in `RandomAccessInputStream`/`FileNameDecoder`/`BitInput` are
  covered by the tests in the table above (they are exactly the 4/1/3 total occurrences
  each file has, per `git grep -n '>>>'` against these paths at `9b7d1fc9`).

## Appendix — full `>>>` inventory (context only; NOT counted toward the mechanical row total)

Every `>>>` occurrence, by file and line (`grep -n '>>>' <file>`), for future audits:

- `RandomAccessInputStream.java`: 55, 77, 109, 110 — all
  `(int)(pointer|l|length >>> BLOCK_SHIFT)` block-index arithmetic. Covered.
- `FileNameDecoder.java`: 40 — `flags >>> 6` case selector. Covered.
- `Unpack15.java`: 263, 266, 273, 289, 322, 351, 375, 388, 402 (×2), 409, 477, 489, 493,
  496, 519 — 16 decode-loop occurrences, deferred (see "Deferred sites"). 606 —
  `decodeNum`, covered.
- `BitInput.java`: 46 (`addbits`, not a bit-reading method — see enumeration), 58
  (comment-only, `non-code` if counted), 62 (`getbits`, covered).
- `RarVM.java`, by role:
  - `non-code` (comments): 104-106, 110-112 (`setValue` reference translation), 123-125
    (`setLowEndianValue(byte[], int, int)` reference translation — its active line 121
    delegates to `Raw.writeIntLittleEndian`, no `>>>` of its own), 661 (×2), 663 (×2), 705
    (`prepare()` TODO-comment ASCII arrows `>>>>>>`/`<<<<<<<<<<`, not operators — the
    6-character run of `>` on 661/663 is 2 pipeline occurrences each).
  - Covered: 129-131 (`setLowEndianValue(Vector<Byte>, int, int)`).
  - Deferred, opcode execution (`VM_SHR`/`VM_SAR`): 499, 502, 509, 512.
  - Deferred, `prepare()`/`decodeArg()`/`ReadData()` bytecode decode — all operate on a
    `fgetbits()`-derived value (always non-negative) and/or an immediate `& 0xff`/`& 7`/
    `& 0xf` mask, so this whole path shares the pre/post-masked-invariant pattern above;
    deferred as "needs a hand-built valid VM bytecode stream", not forced: 678, 687, 691,
    695, 767, 774, 783, 789, 868, 871, 874.
  - Deferred, standard-filter execution (reachable only via `execute()`): 970
    (`VMSF_ITANIUM`), 1087 (`VMSF_AUDIO`).
  - Deferred, `filterItanium_GetBits`/`SetBits` (private; see "discriminating sites" —
    `GetBits` is NOT pre/post-masked-invariant): 1176, 1184, 1185, 1197, 1198.
