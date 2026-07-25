# junrar ↔ unrar 3.7.3 translation patterns — UNPACK CORE (LZ engines)

Baseline: unrar **3.7.3** = `git -C ~/git/unrar show 2e71167:<file>` (all C++ refs below are
that commit; line numbers from the extracted copies, verified this session).
Java: `~/git/junrar` branch `master`.
Audience: agents porting newer unrar (RAR5 `unpack50.cpp`, `unpack.cpp` blocks, `unpackinline.cpp`)
into junrar. Every section: C++ construct → Java translation → RULE.

---

## 1. Class shape: one C++ class, several .cpp files → Java inheritance chain

**C++**: a single `class Unpack : private BitInput` (unpack.hpp:89-216) holds ALL THREE engines'
state and methods. The engine split is by *compilation unit only*: `unpack.cpp` **textually
includes** the others (`unpack.cpp:3-9`: `#include "coder.cpp"`, `"suballoc.cpp"`, `"model.cpp"`,
`"unpack15.cpp"`, `"unpack20.cpp"`). `friend class Pack` (unpack.hpp:92) exposes internals to the
compressor. Dispatch: `DoUnpack(Method,Solid)` switch (unpack.cpp:49-67).

**Java**: the .cpp-file split became a **linear inheritance chain**, one class per compilation unit:

```text
BitInput (vm/BitInput.java)
  └─ Unpack15 (abstract)   ← unpack15.cpp methods + ALL shared engine state
       └─ Unpack20 (abstract) ← unpack20.cpp methods + decode tables + shared helpers
            └─ Unpack (final)  ← unpack.cpp (RAR29) + PPM + VM/filters
```

- `Unpack.java:42` `public final class Unpack extends Unpack20`
- `Unpack20.java:42` `public abstract class Unpack20 extends Unpack15`
- `Unpack15.java:34` `public abstract class Unpack15 extends BitInput`

State placement rule actually used: **a field lives in the lowest (oldest-engine) class that
touches it**, declared `protected`:

- `Unpack15.java:36-73`: `window`, `unpPtr/wrPtr`, `oldDist[4]`, `oldDistPtr`,
  `lastDist/lastLength`, `unpIO`, `destUnpSize`, `readTop/readBorder`, `suspended`,
  `unpAllBuf/unpSomeRead` — even though C++ declares them in the "29" section of unpack.hpp
  (139-164), all engines use them.
- `Unpack20.java:44-60`: `LD/DD/LDD/RD/BD` decode tables, `MD[4]`, `UnpOldTable20`, audio state —
  shared by the 20 and 29 paths.
- `Unpack.java:44-78`: PPM model, `RarVM`, `filters/prgStack/oldFilterLengths`, `tablesRead`,
  `unpOldTable`, `unpBlockType`, `writtenFileSize`, `ppmError` — 29-only.

Shared *methods* moved down likewise: `unpReadBuf` (C++ unpack.cpp:632) → `Unpack15.java:211`;
`decodeNumber`/`makeDecodeTables` (C++ unpack.cpp:104/968) → `Unpack20.java:272/238`;
`oldUnpWriteBuf` (unpack15.cpp:123) → `Unpack15.java:609`.

Inverted-dependency trick: `Unpack15.java:136` declares `protected abstract void
unpInitData(boolean solid)`; the concrete implementation is at the TOP of the chain
(`Unpack.java:604`) so the base-class engine loops can trigger the full reset cascade.

Flattened C++-isms: `private` inheritance → plain `extends` (Java has no private inheritance —
`BitInput`'s members simply become protected/public); `friend class Pack` dropped (junrar has no
compressor); the `(struct Decode *)&LD` downcasts (unpack.cpp:274 etc.) → real polymorphism,
`decodeNumber(Decode dec)`.

**RULE**: when porting `unpack50.cpp`, follow the file-split-as-class-split precedent — but note a
RAR5 engine shares almost no state with this chain (different window model, no `Decode` struct
family in 5.x, `UnpackBlockHeader` etc.), so a sibling class taking `ComprDataIO` is cleaner than
extending `Unpack20`. Whatever the choice: state at the lowest layer that uses it, `protected`
fields, abstract hooks for downward calls, no getters between the engine classes.

## 2. Pointer arithmetic → arrays + explicit offsets (and the SIGNEDNESS TRAP)

- `byte *Window` → `protected byte[] window` (Unpack15.java:50) + `int` indices. Every pointer
  parameter becomes an **(array, offset, length) triple**:
  `UnpIO->UnpWrite(&Window[WrPtr], n)` (unpack15.cpp:129) →
  `unpIO.unpWrite(window, wrPtr, n)` (Unpack15.java:614);
  `MakeDecodeTables(&Table[NC], ...)` (unpack.cpp:920) →
  `makeDecodeTables(table, Compress.NC, DD, Compress.DC)` (Unpack.java:744) — the Java method
  grew an explicit `int offset` param (Unpack20.java:238).
- **Unsigned-wrap guard**: C++ `CopyString` relies on unsigned arithmetic —
  `unsigned int DestPtr=UnpPtr-Distance; if (DestPtr<MAXWINSIZE-260 ...)` (unpack.cpp:88-89):
  a "negative" DestPtr wraps huge and fails the test, falling to the masked slow path. Java ints
  are signed, so junrar **adds `destPtr >= 0 &&`** to the fast-path condition
  (Unpack.java:574, Unpack20.java:215). Forgetting this guard is the classic porting bug.
- `memcpy` → `System.arraycopy` (Unpack.java:750); `memmove` (self-overlapping, unpack.cpp:640) →
  also `System.arraycopy` (Unpack15.java:222 — arraycopy has memmove semantics).
  BUT the LZ copy loop (`Window[UnpPtr++]=Window[DestPtr++]`) must stay byte-sequential when
  regions overlap forward (self-referencing match): junrar keeps the loop as the fallback
  (Unpack.java:592-594) and adds fast paths only where provably safe (see §10 skew).
- `memset(x,0,sizeof)` → `Arrays.fill(x, 0)` (Unpack.java:607/613); `memset` of a **struct
  array** → re-instantiate objects: `memset(AudV,0,sizeof(AudV))` (unpack20.cpp:274) →
  `AudV[i] = new AudioVariables()` loop (Unpack20.java:486-488).
- Struct arrays → **object classes, not parallel arrays**: `struct AudioVariables` (unpack.hpp:77)
  → JavaBean `decode/AudioVariables.java` with getters/setters — so `V->D3=V->D2` becomes
  `v.setD3(v.getD2())` (Unpack20.java:516). `struct UnpackFilter` (unpack.hpp:54) → bean
  `UnpackFilter.java` (same field names, `Prg` eagerly `new`ed at UnpackFilter.java:42).
- `Array<byte>` (unrar's growable array with `Add/Alloc/Reset/Size`) → `Vector<Byte>` (boxed!)
  because `setSize()` matches `Alloc()` semantics: VMPreparedProgram.java:36-37, used heavily in
  `UnpWriteBuf`'s GlobalData shuffling (Unpack.java:394-429). Element-wise `for` loops replace the
  `memcpy`s (Unpack.java:401-407).
- Pointer-into-VM-memory `byte *FilteredData` (rarvm.hpp:72) → **int offset + copy-out**:
  `FilteredDataOffset` (VMPreparedProgram.java:40) and
  `FilteredData[i] = rarVM.getMem()[FilteredDataOffset + i]` (Unpack.java:435-439).
- Pointer-walking loop `for(J=0;J<32;J++,CharSet++) *CharSet=...` (unpack15.cpp:483-485) →
  explicit index `pos` (Unpack15.java:561-567).

**RULE**: byte* → byte[]+int; every unsigned comparison/index derived from a subtraction needs an
explicit `>= 0` (or widen to `long`); struct→bean is the house style (accept the getter noise);
sequential LZ copy loops must NOT be blindly arraycopy'd — check overlap direction first.

## 3. Macros → methods; constants → holder classes

- Function-like macros `GetShortLen1/2(pos)` (unpack15.cpp:139-140 — themselves a thread-safety
  fix over mutating a static array, see comment unpack15.cpp:173-174) → private methods
  `getShortLen1/getShortLen2` (Unpack15.java:238-244). Identical ternary body.
- `getbits()/addbits()` are inline methods in C++ already (getbits.hpp:20-33); translated 1:1 to
  `BitInput.java:44-63` with `& 0xff` per byte and `>>>`. `faddbits/fgetbits` (out-of-line
  duplicates in getbits.cpp, existing purely for code-size control) were kept as trivial
  delegates (BitInput.java:75-85) purely for **name parity with upstream** — call-site diffs
  against C++ stay clean.
- `ARI_DEC_NORMALIZE(code,low,range,read)` (coder.cpp:20-28) — a macro whose loop condition
  embeds a comma-operator assignment (`(range=-low&(BOT-1)),1`) → method `ariDecNormalize()`
  with a rewritten condition using a `boolean c2` flag (RangeCoder.java:79-98; the first literal
  translation is kept commented above it). This is the one place macro translation required real
  control-flow surgery.
- Constant macros: compress.hpp's `#define NC 299 / MAXWINSIZE / MAXWINMASK / LOW_DIST_REP_COUNT`
  → `public static final int` in a holder class **named after the C++ header**:
  `decode/Compress.java:26-45`. rarvm.hpp's `VM_GLOBALMEMSIZE` etc. → constants on `RarVM`.
- `int64to32(x)` → `(int)` cast (Unpack.java:989); `is64plus(x)` (int64.hpp:18/76 — needed
  because `Int64` may be an emulated struct on old platforms) → plain `destUnpSize >= 0` on a
  native `long` (Unpack20.java:102).
- `sizeof(a)/sizeof(a[0])` loop bounds → `.length` (Unpack.java:147, Unpack20.java:549).

**RULE**: function-like macro → private method (name-preserving); constant header → one
`static final` holder class per header; any macro with comma-operator/assignment-in-condition
gets a semantic rewrite — test that rewrite, don't eyeball it. Keep upstream names even when
un-Java-ish; greppability against C++ is the asset.

## 4. Control flow: no gotos here, but bool-protocol + exceptions

The 3.7.3 unpack core has **no gotos** in these files — the decode loops are already
`while(true)` + per-code-class `continue` / `break`-to-flush. junrar copied that shape verbatim
(`unpack29` loop Unpack.java:172-350 ↔ unpack.cpp:196-389, case-by-case identical order).
Patterns that DID need translation:

- **Bool protocol kept, I/O errors become exceptions.** C++ signals corruption/EOF by returning
  `false` up the chain (ReadTables, ReadVMCode, UnpReadBuf) and real I/O trouble via the global
  `ErrHandler`. Java keeps the boolean protocol byte-for-byte (`if (!readTables()) break;`
  Unpack.java:306) and adds `throws IOException, RarException` on every method that can reach
  `unpIO` (e.g. Unpack15.java:211 `unpReadBuf`). No global error handler; `Archive.doExtractFile`
  catches and wraps.
- **Error latch field**: `PPMError` (unpack.hpp:163) → `ppmError` boolean field checked at engine
  entry (Unpack.java:74, 168-170, 196-199) — identical to C++, used because a poisoned PPM model
  must refuse re-entry on the next solid file.
- int-as-bool → explicit comparisons: `if (StMode)` → `if (StMode != 0)` (Unpack15.java:171);
  `if (!(Distance & 0xff))` → `if ((Distance & 0xff) == 0)` (Unpack15.java:392);
  `NewTable=(BitField & 0x4000)` → `(BitField & 0x4000) != 0` (Unpack.java:646).
- Deep nested-if ladders (the 15-way `DecodeNumber` tree, unpack.cpp:108-150) hand-copied
  verbatim, no table-ification (Unpack20.java:334-389).
- Mid-loop `return` vs `break` semantics preserved exactly (e.g. suspended → `return` with
  `fileExtracted=false`, Unpack.java:189-192).

**RULE**: keep upstream's loop/continue/case ordering byte-for-byte — the decode loop's case
order IS the format spec; translate `false`-returns as booleans and reserve exceptions for
transport errors; every implicit C++ truthiness gets an explicit `!= 0`. (Newer unrar DOES use
`goto`-like constructs and early-exit flags in unpack50 — expect labeled breaks or state
booleans there; junrar precedent is the boolean-latch style, not labeled breaks: zero labeled
breaks exist in this layer.)

## 5. Bit input: getbits.hpp → vm/BitInput.java

- Same everything: `MAX_SIZE = 0x8000` (BitInput.java:31 ↔ getbits.hpp:7), `inAddr/inBit`
  cursor pair, 3-byte read window producing a 16-bit lookahead
  (BitInput.java:60-62 ↔ getbits.hpp:26-33), `Overflow(IncPtr)` guard
  (BitInput.java:92 ↔ getbits.hpp:36), `InitBitInput` name kept with C++ capitalization.
- Sign hygiene: each buffer byte masked `& 0xff`; shifts are `>>>`.
- It lives in the **vm** package because `RarVM : private BitInput` too (rarvm.hpp:76) — one Java
  class serves both; `Unpack15 extends BitInput` supplies the engine side.
- The refill routine `UnpReadBuf` is NOT in BitInput (matches C++, where it's Unpack's — it needs
  `UnpIO`): Unpack15.java:211-236 ↔ unpack.cpp:632-651, keeping the compaction threshold
  `inAddr > MAX_SIZE/2`, the `memmove` (→ arraycopy), the 16-byte-aligned read size
  `(MAX_SIZE - dataSize) & ~0xf`, and `readBorder = readTop - 30` (the 30-byte safety margin that
  lets getbits over-read by 2 bytes without checks).
- `AddVMCode` creating a **local, stack-scoped BitInput** to parse filter payloads
  (unpack.cpp:480-482) → `BitInput Inp = new BitInput(); Inp.InitBitInput();` + element-copy loop
  (Unpack.java:812-817). RAR5's `RawRead`-style block header parsing can use the same
  local-instance trick.

**RULE**: reuse `vm/BitInput.java` for any new bit-oriented parsing; preserve the 30-byte
over-read margin and the `&~0xf` read alignment; RAR5's `BitInput` gained
`InGetbits32`/larger MAX_SIZE in later unrar — check the target version's getbits.hpp before
assuming this one.

## 6. Decode tables: struct family → tiny class hierarchy; static data → static final arrays

- `struct Decode` + five size-specialized clones (LitDecode/DistDecode/LowDistDecode/RepDecode/
  BitDecode, unpack.hpp:6-52; MultDecode unpack.hpp:69) → base class `decode/Decode.java`
  (maxNum, `decodeLen[16]`, `decodePos[16]`, `decodeNum[2]`) with subclasses that ONLY re-size
  `decodeNum` in their constructor: `LitDecode.java:30-32` (`decodeNum = new int[Compress.NC]`),
  one file per struct, names identical. C++ made the size difference a struct-layout fact and
  cast everything to `Decode*`; Java makes it a constructor fact and uses polymorphism.
- `MakeDecodeTables` (unpack.cpp:968-992) → `makeDecodeTables(byte[] lenTab, int offset, Decode
  dec, int size)` (Unpack20.java:238-270): identical algorithm, `long M,N` retained, offset param
  replaces `&LenTab[k]` pointers.
- `DecodeNumber` (unpack.cpp:104-157) → `decodeNumber` (Unpack20.java:272-397). `unsigned int
  BitField` → `long bitField = getbits() & 0xfffe` to dodge signed compare; result index math
  casts back to int with `>>>`.
- **Static table data**: C++ keeps `LDecode/LBits/SDDecode/SDBits` as *function-local statics*
  duplicated in BOTH `Unpack29` (unpack.cpp:162-168) and `Unpack20` (unpack20.cpp:31-36); junrar
  hoisted ONE copy to class-level `public static final` arrays on `Unpack20`
  (Unpack20.java:62-81) shared by both engines. The RAR2 vs RAR3 DDecode tables genuinely differ:
  RAR2's is the static literal (Unpack20.java:69-73); RAR3's is **runtime-generated** from
  `DBitLengthCounts` — C++ lazily fills a function-local static once (`if (DDecode[1]==0)`,
  unpack.cpp:171-180); Java re-allocates a fresh local `DDecode/DBits` on every `unpack29` call
  and kept the now-always-true lazy guard (Unpack.java:140-154) — a vestigial translation
  artifact (harmless: table rebuilt per file).
- Unpack15's seven Dec*/Pos* table pairs + STARTxx defines (unpack15.cpp:1-37) → `private static
  final int[]` + `private static final int STARTxx` fields (Unpack15.java:75-124); the
  ShortLen/ShortXor function-local statics → package-private `static int[]` (Unpack15.java:126-134).
- Range-coder constants `TOP/BOT` (coder.hpp:5) → fields in `ppm/RangeCoder.java`; **all uint
  arithmetic there rides `long` + `& uintMask (0xFFFFFFFFL)` after every mutation**
  (RangeCoder.java:40-72) — the standard junrar uint-emulation idiom.

**RULE**: sized-struct clones → constructor-resized subclass; function-local static tables →
`static final` fields (hoist ONE shared copy; delete lazy-init guards or accept per-call
rebuild); anything `uint` that can exceed 2^31 (range coders, CRCs, 32-bit hashes) lives in a
masked `long`, everything else in `int` with `>>>` + point masks.

## 7. ComprDataIO: rdwrfn.cpp → ComprDataIO.java

Same name, same field inventory, different plumbing.

- **Kept 1:1** (names + semantics): `unpPackedSize`, `curUnpRead/curUnpWrite/...` counters,
  `packedCRC/unpFileCRC/packFileCRC` init to `0xffffffff` (ComprDataIO.java:100 ↔ rdwrfn.cpp:28),
  `testMode`, `skipUnpCRC`, `subHead`, `nextVolumeMissing`, the read-loop shape of `UnpRead`
  (clamp `count` to `unpPackedSize`, accumulate `totalRead`, volume-advance when packed bytes
  exhaust: ComprDataIO.java:129-183 ↔ rdwrfn.cpp:39-107).
- **File plumbing swapped**: `File *SrcFile/DestFile` → constructor-injected `Archive` +
  per-extract `OutputStream` (`init(OutputStream)`, ComprDataIO.java:87) + a `RawDataIo` channel
  created in `init(FileHeader)` (ComprDataIO.java:107-127), which also seeks to
  `hd.getPositionInFile()+headerSize` — the packed-data feed point.
- **Volume merge**: `MergeArchive(*SrcArc,this,true,CurrentCommand)` (rdwrfn.cpp:72) →
  `archive.getVolumeManager().nextVolume(...)` + `UnrarCallback.isNextVolumeReady` + recursive
  `this.init(hd)` on the next volume's header (ComprDataIO.java:151-172). Java adds an inline
  split-volume packed-CRC check (`CrcErrorException`, ComprDataIO.java:158-162).
- **CRC**: unrar's table-based `CRC()` running-complement → `java.util.zip.CRC32` with the
  complement re-applied at each observation point: `unpFileCRC = (int) (~unpCrc32.getValue())`
  (ComprDataIO.java:196-199); old-format 16-bit CRC → hand-ported `RarCRC.checkOldCrc`
  (ComprDataIO.java:195). Archive.doExtractFile compares `~unpFileCRC` vs header CRC.
- **Encryption moved DOWN a layer**: C++ decrypts inside `UnpRead` after the raw read
  (rdwrfn.cpp:88-103, `CryptData Decrypt` member, method-versioned branches 13/15/20/AES). Java
  installs a `javax.crypto.Cipher` (Rijndael/AES for RAR3) on the **RawDataIo channel**
  (ComprDataIO.java:119-126; decrypt at RawDataIo.java:61 `cipher.update`). ComprDataIO keeps
  vestigial `encryption/decryption` int fields (never driving logic).
  → **RAR5 hook points**: `RawDataIo.setCipher` (swap in AES-256-CBC + PBKDF2-HMAC-SHA256),
  `ComprDataIO.init(FileHeader)` (per-file crypto init from header salt/IV), the `unpWrite` CRC
  branch (RAR5 optional Blake2sp instead of CRC32).
- **Dropped**: `UnpackFromMemory/UnpackToMemory`, `GetUnpackedData`, progress display
  (`ShowUnpRead/ShowPercent`), `SetAV15Encryption/SetCmt13Encryption`, `Wait()`, `Command`.
- **Return-code change**: C++ `UnpRead` returns `-1` on unreadable source; Java throws
  `EOFException` on negative channel read (ComprDataIO.java:134-136) but still returns `-1` for
  a missing next volume (ComprDataIO.java:156) — mixed convention, mind it when adding RAR5
  paths.

**RULE**: keep ComprDataIO as the packed-byte pump + CRC + volume-advance ONLY; put crypto in the
io channel; when adding a hash/cipher, mirror the "complement at observation point" trick rather
than re-implementing unrar's running-CRC tables.

## 8. Filter/VM integration points (unpack29)

- `enum BLOCK_TYPES {BLOCK_LZ,BLOCK_PPM}` (unpack.hpp:4) → `ppm/BlockTypes` Java enum carrying an
  int (BlockTypes.java:27-38); the PPM-vs-LZ switch is field `unpBlockType`, set ONLY in
  `readTables` from the block-header top bit (Unpack.java:664-668 ↔ unpack.cpp:830-835). PPM
  entry point: `ppm.decodeInit(this, ppmEscChar)` — the model pulls raw bytes back through
  `Unpack.getChar()` (Unpack.java:1032-1037 ↔ unpack.hpp:210-215), which bypasses the bit layer
  (byte-aligned range coder), refilling via `unpReadBuf` when near MAX_SIZE.
- Filter ingestion, two producers, one consumer: LZ code **257** → `readVMCode`
  (Unpack.java:311-316, bit-stream framed); PPM escape `NextCh==3` → `readVMCodePPM`
  (Unpack.java:211-216, PPM-symbol framed); both feed `addVMCode(firstByte, code, length)`
  (Unpack.java:811-984 ↔ unpack.cpp:478-629) which parses the filter header from a **local
  BitInput**, resolves the parent filter (`filters` list ↔ `Filters`), pushes an `UnpackFilter`
  onto `prgStack`, computes `BlockStart` relative to `unpPtr` masked into the window, seeds
  `InitR[3..5]` and the 64-byte fixed global memory. Same corruption guards, same magic numbers
  (`FiltPos>1024`, `VMCodeSize>=0x10000`, `+258` when bit 0x40).
- Filter **execution** is inside `UnpWriteBuf` (Unpack.java:355-527 ↔ unpack.cpp:654-778):
  written-region sweep over `prgStack`, wrap-aware `SetMemory` of the window slice into VM memory
  (two-part copy when the block wraps, Unpack.java:378-388), parent↔stack GlobalData shuttling,
  chained same-block filters loop, filtered output written via `unpIO.unpWrite` INSTEAD of the
  window bytes, then `UnpWriteArea` for unfiltered remainder. C++ original lines are kept as
  `//` comments throughout this method — use them as the correspondence map.
- `NextWindow` deferral flag (filter block extends past current write pass) translated intact
  (Unpack.java:363-365, 512-517 ↔ unpack.cpp:663-667, 764-771).

**RULE for RAR5**: RAR5 dropped the RarVM — filters become the 5 fixed types decoded from the
block header (unpack50.cpp `ReadFilter/AddFilter`), but the **integration topology is the same**:
a filter list keyed by window position, applied inside the write-buffer flush before bytes reach
ComprDataIO. Port `UnpWriteBuf`'s sweep shape, not the VM.

## 9. State/reset lifecycle and instance reuse

- Reset cascade preserved exactly: `unpInitData(solid)` (Unpack.java:604-627 ↔
  unpack.cpp:928-951) — full state wipe (tables, oldDist, window pointers, filters, PPMEscChar=2)
  **only when `!solid`**; bit-input reset + `ppmError=false` + counters ALWAYS; chains to
  `unpInitData20(solid)` (Unpack20.java:481 ↔ unpack20.cpp:268) and, in the 1.5 engine only,
  `oldUnpInitData(solid)` (Unpack15.java:531 ↔ unpack15.cpp:446). `tablesRead` is what lets a
  solid RAR3 stream skip re-reading Huffman tables (`(!solid || !tablesRead) && !readTables()`,
  Unpack.java:163).
- Instance reuse: **one `Unpack` per `Archive`, lazily created, reused for every entry**
  (Archive.java:771-775): `new Unpack(dataIO)` once; `unpack.init(null)` — which allocates the
  4 MB window and full-resets — is called only for **non-solid** headers; solid entries inherit
  window + tables + oldDist from the previous file. `setDestSize(fullUnpackSize)` per entry.
- `Init(byte *Window=NULL)` / `ExternalWindow` (unpack.cpp:30-46) → `init(byte[] window)` kept
  the null/else branch but nothing passes a window; the `ExternalWindow` flag was dropped.
- junrar-only affordance: random access into solid streams — `Archive.extractFile`
  (Archive.java:584-596) re-runs `unpack.init(null)` and replays (skip-extracts) intermediate
  entries when the target index precedes the last processed one. The C++ CLI never needs this.
  `skipFile` uses the same doUnpack path with a discarding stream.
- `cleanUp()` (Unpack.java:1047-1054) releases the PPM suballocator on error/close — Java's
  replacement for the C++ destructor (unpack.cpp:22-27).
- `suspended` machinery translated but effectively dead — junrar never calls
  `setSuspended(true)`.

**RULE**: a new RAR5 engine must slot into this per-Archive reuse: constructor(dataIO), init()
alloc-window+reset, setDestSize per entry, solid entries skip re-init — and must keep working
with `extractFile`'s rewind-and-replay for solid random access.

## 10. Skew list — junrar is NOT byte-identical unrar 3.7.3

Do not assume 1:1 when cross-referencing:

1. **Naming case is chaotic**: camelCase (`unpack29`, `readTables`, `copyString`) coexists with
   preserved C++ PascalCase (`UnpWriteBuf`, `UnpWriteArea`, `ReadTables20`, `CopyString20`,
   `DecodeAudio`, `InitBitInput`, `Overflow`) and capitalized locals (`Number`, `Length`,
   `BitField`). Grep case-insensitively.
2. **`unstoreFile` folded into `Unpack.doUnpack`** via `unpIO.getSubHeader().getUnpMethod() ==
   0x30` (Unpack.java:103-136); upstream handles stored files in extract.cpp, outside Unpack.
   (Quirk: after unstore it still falls into the switch — method 0x30 matches no case.)
3. **Copy-loop fast paths are junrar additions**, not upstream: `distance==1` → `Arrays.fill`,
   non-overlap → `System.arraycopy` in `copyString` (Unpack.java:575-589), `oldCopyString`
   (Unpack15.java:577-589), `CopyString20` (Unpack20.java:216-219). Output-identical, code-shape
   different. Also `oldCopyString` parameter order flipped vs C++ (`distance, length` in Java,
   Unpack15.java:574; `Distance,Length` call sites match C++ order — verify per call).
4. **PrgStack slot compaction dropped**: C++ compacts executed (NULL) slots on every `AddVMCode`
   (unpack.cpp:524-539); Java just appends (`prgStack.add(StackFilter)`, Unpack.java:860) and
   leaves nulls behind forever (skipped at Unpack.java:360) — unbounded list growth on
   filter-heavy archives.
5. **`// Bug fixed` markers**: PPM copy-string escape casts `(byte)Ch` → `ch & 0xff`
   (Unpack.java:226-231) — sign-extension fixes relative to a naive translation. Trust the `&
   0xff` version.
6. **Dead code retained**: the first (commented) translation of `decodeNumber`
   (Unpack20.java:275-333), a commented "Duplicate method" `ReadEndOfBlock` (Unpack.java:1002),
   and C++ originals as inline comments everywhere. Treat comments as provenance breadcrumbs,
   not spec — some predate later fixes.
7. **Int64 emulation gone**: `Int64 DestUnpSize` + `is64plus`/`int64to32` (int64.hpp) → native
   `long` + `>= 0` + `(int)` casts. Any new-unrar `int64()` construct maps to plain long math.
8. **AddVMCode writes GlobalData[0x24]/[0x28]=0** (Unpack.java:949-950) where C++ doesn't
   (they're set later in ExecuteCode both sides) — harmless defensive divergence.
9. **RAR 2 audio (mult) decoding present** in both (unpack20.cpp:280 ↔ Unpack20.java:512) —
   nothing was dropped from the engines themselves; the drops are all in ComprDataIO (progress,
   memory modes, comment/AV crypto) and the packer (`friend Pack`).
10. **No RAR5 anywhere**: README states "RAR 4 and lower (there is no RAR 5 support)";
    `UnsupportedRarV5Exception` is thrown at header-parse time. Nothing in the unpack layer
    branches on version 5.0.
11. **Window size is a compile-time constant** `MAXWINSIZE=0x400000` (Compress.java:28); RAR5's
    dynamic dictionary (up to 1 GB in 5.x, 64 GB in 7.x) breaks the `MAXWINMASK` idiom and the
    `int` indexing headroom — a RAR5 port needs per-archive window sizing and must re-derive
    every hardcoded `-260`/`-270`/`-300` guard distance from the new unrar source.
12. **ComprDataIO return/throw mix** (§7): EOF throws, missing-volume returns -1.

## Cross-cutting translation rules (the distilled checklist)

1. One Java class per C++ compilation unit; chain them by inheritance; state lives at the
   lowest engine that touches it, `protected`, no intra-chain getters.
2. Keep upstream identifiers, loop shapes, case order, and magic numbers verbatim — greppability
   against the C++ is the maintenance strategy; junrar even keeps C++ lines as comments beside
   nontrivial translations.
3. `byte` reads always `& 0xff`; unsigned shifts always `>>>`; uint values that can top 2^31 →
   masked `long`; every unsigned-subtraction guard gains an explicit `>= 0`.
4. Pointer params → (array, offset, len); `memcpy/memmove` → arraycopy EXCEPT forward-overlap LZ
   copies; `memset(structArray)` → re-`new`; growable `Array<T>` → List/Vector (setSize ↔ Alloc).
5. Function-like macros → private methods; constant headers → one static-final holder class;
   comma-operator macros need verified control-flow rewrites.
6. bool-return corruption protocol stays boolean; transport errors become
   IOException/RarException; poisoned-state latches stay boolean fields (`ppmError`).
7. Structs → beans (even hot-path ones like AudioVariables — junrar accepted the cost);
   sized-struct clones → constructor-resized subclasses; `(BaseStruct*)` casts → polymorphism.
8. Per-Archive singleton engine with init(null)-unless-solid lifecycle; reset cascades mirror
   UnpInitData exactly, including the always-vs-!solid split.
9. Crypto/CRC live outside the engines: cipher on the io channel, CRC in ComprDataIO with
   complement-at-observation; engines see only unpRead/unpWrite.
10. Expect and document deliberate skew (fast paths, dropped compaction, folded unstore) — audit
    against the C++ before assuming junrar reflects it.
