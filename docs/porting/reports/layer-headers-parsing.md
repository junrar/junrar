# junrar ← unrar 3.7.3: Archive/Header-Parsing Layer — Pattern Catalog

Baseline: unrar **3.7.3** = commit `2e71167` in `~/git/unrar` (all C++ refs are
`git -C ~/git/unrar show 2e71167:<file>` line numbers). Java refs are
`~/git/junrar` branch `master` (HEAD `99866b74`), paths relative to
`src/main/java/com/github/junrar/`.

Audience: agents porting RAR5-era unrar code into junrar. Each pattern gives the
C++ construct, the Java translation, and the RULE to follow (or the trap to avoid).

---

## 1. Struct → class mapping

### 1.1 Inheritance shape is preserved 1:1

C++ (`headers.hpp`):

```text
BaseBlock (129)                        // HeadCRC, HeadType, Flags, HeadSize
├── BlockHeader (146)                  // union { DataSize; PackSize; }
│   ├── FileHeader (165)               // also used for NEWSUB_HEAD blocks
│   ├── SubBlockHeader (226)           // SubType, Level
│   │   ├── UnixOwnersHeader (268)
│   │   ├── EAHeader (278), StreamHeader (287), MacFInfoHeader (299)
│   └── ProtectHeader (242)
├── MainHeader (155), EndArcHeader (219), CommentHeader (233),
│   AVHeader (251), SignHeader (260)
MarkHeader (123) — standalone 7-byte struct in C++
OldMainHeader (~95) / OldFileHeader (108) — old (RAR 1.x) format
```

Java (`rarfile/`): same tree, one class per struct, same names except noted in §9:
`BaseBlock.java:33` → `BlockHeader.java:32 extends BaseBlock` →
`FileHeader.java:41 extends BlockHeader`, `SubBlockHeader.java:7 (extends BlockHeader)`,
`UnixOwnersHeader.java:8 / EAHeader.java / MacInfoHeader.java extend SubBlockHeader`,
`MainHeader.java:32 / EndArcHeader.java:28 / CommentHeader.java / AVHeader.java /
SignHeader.java extend BaseBlock`, `ProtectHeader.java extends BlockHeader`.

Two deliberate reshapes:

- **`MarkHeader` became `extends BaseBlock`** (`MarkHeader.java:32`) even though the
  C++ MarkHeader is a raw 7-byte signature struct. junrar parses the signature *as if*
  it were a BaseBlock and then validates the parsed fields against the signature
  constants (`isValid()` `MarkHeader.java:41-52`: headCRC==0x6152 == "Ra",
  type==0x72 == 'r', flags==0x1a21, size==7). `isSignature()` (`:54-73`)
  *re-serializes* the BaseBlock fields back into 7 bytes with `Raw.writeShortLittleEndian`
  and pattern-matches them against `Rar!\x1a\x07\x00` (V4) / `\x01` (V5) / `RE~^` (OLD)
  — the Java inversion of `Archive::IsSignature` (`archive.cpp:91-107`).
- **`StreamHeader` was never ported** (NTFS-stream sub-block); its subtype is known
  (`SubBlockHeaderType.STREAM_HEAD`) but `Archive.readHeaders` skips it
  (`Archive.java:516-517`).

RULE: keep the C++ struct inheritance as the Java class hierarchy; a C++ struct that
is *reused* for two block types (FileHeader for FILE_HEAD + NEWSUB_HEAD) stays ONE Java
class with type predicates (`FileHeader.isFileHeader()` `FileHeader.java:639-641`),
not two classes.

### 1.2 The constructor-from-(parent, byte[]) pattern — replaces C++ in-place `Raw.Get`

C++ reads every field of every header into **one reused member instance** on `Archive`
(`archive.hpp:86-99`: `ShortBlock, NewMhd, NewLhd, SubHead, CommHead, …`), via
sequential `Raw.Get(field)` calls inside `Archive::ReadHeader` (`arcread.cpp:32`).
The type dispatch is `*(BaseBlock *)&NewMhd=ShortBlock;` — a memcpy of the common
prefix, then reading the remainder.

Java idiom — every header class has exactly this constructor shape:

```java
public MainHeader(BaseBlock bb, byte[] mainHeader) {   // MainHeader.java:40
    super(bb);              // copy-constructor chain == *(BaseBlock*)hd=ShortBlock
    int pos = 0;
    highPosAv = Raw.readShortLittleEndian(mainHeader, pos); pos += 2;
    posAv = Raw.readIntLittleEndian(mainHeader, pos);       pos += 4;
    if (hasEncryptVersion()) { encryptVersion |= mainHeader[pos] & 0xff; }
}
```

- The **first argument** is the already-parsed, less-specific header (BaseBlock →
  BlockHeader → SubBlockHeader chain); `super(bb)` is the **copy constructor**
  (`BaseBlock.java:100-106`, `BlockHeader.java:44-49`, `SubBlockHeader.java` copy ctor)
  that replicates C++'s common-prefix memcpy.
- The **second argument** is a byte[] containing **only the bytes after the parent's
  portion** (see §1.3 sizes), read from the stream by the caller (`Archive.readHeaders`).
- Field cursor is a local `int pos` advanced manually — the Java equivalent of
  RawRead's internal `ReadPos`.

RULE: for a new header type, (a) subclass at the same level unrar does; (b) add
`XHeader(ParentHeader parent, byte[] tail)` reading with a manual `pos` cursor;
(c) add a copy constructor if any subclass will chain from it; (d) the caller
(Archive.readHeaders) allocates and fills `tail` and picks the class — the header class
itself never touches the channel.

### 1.3 Size constants: C++ counts from block start, Java counts the *increment*

C++ `SIZEOF_*` (`headers.hpp:4-19`) are absolute block sizes: `SIZEOF_NEWMHD 13`,
`SIZEOF_LONGBLOCKHEAD 11`, `SIZEOF_SUBBLOCKHEAD 14`, `SIZEOF_AVHEAD 14`, …
Java constants are **the delta on top of the parent's bytes**:

| C++ (headers.hpp) | Java | Value |
|---|---|---|
| SIZEOF_SHORTBLOCKHEAD 7 | `BaseBlock.BaseBlockSize` (BaseBlock.java:37) | 7 |
| SIZEOF_LONGBLOCKHEAD 11 | `BlockHeader.blockHeaderSize` (BlockHeader.java:33) | 4 (=11−7) |
| SIZEOF_NEWMHD 13 | `MainHeader.mainHeaderSize` / `WithEnc` (MainHeader.java:34-35) | 6 / 7 (=13−7, +EncryptVer) |
| SIZEOF_SUBBLOCKHEAD 14 | `SubBlockHeader.SubBlockHeaderSize` | 3 (=14−11) |
| SIZEOF_MACHEAD 22 | `MacInfoHeader.MacInfoHeaderSize` | 8 (=22−14) |
| SIZEOF_EAHEAD 24 | `EAHeader.EAHeaderSize` | 10 (=24−14) |
| SIZEOF_COMMHEAD 13 | `CommentHeader.commentHeaderSize` | 6 |
| SIZEOF_AVHEAD 14 | `AVHeader.avHeaderSize` | 7 |
| SIZEOF_SIGNHEAD 15 | `SignHeader.signHeaderSize` | 8 |
| SIZEOF_NEWLHD 32 | `FileHeader.NEWLHD_SIZE` (FileHeader.java:47) | 32 (absolute! used in SubData math) |

RULE: when porting a size constant, decide whether the call site consumes an
*absolute* block size (subData math, `hd->HeadSize-hd->NameSize-SIZEOF_NEWLHD`,
FileHeader.java:175) or an *incremental* read size (buffer allocation in
readHeaders) — junrar mixes both, and every historical off-by-N here has been a
port bug. Variable-size reads compute
`toRead = headerSize − BaseBlockSize − blockHeaderSize[ − SubBlockHeaderSize]`
(Archive.java:445-447, 519-522).

### 1.4 C++ unions → two Java fields

`BlockHeader`'s `union { uint DataSize; uint PackSize; }` (`headers.hpp:146-152`)
became **two long fields kept equal** (`BlockHeader.java:37-38,54-55`:
`this.packSize = Raw.readIntLittleEndianAsLong(...); this.dataSize = this.packSize;`).
`FileHeader`'s `union { uint FileAttr; uint SubFlags; }` became `int fileAttr` +
`int subFlags` (`FileHeader.java:88-90`, comment "same as fileAttr (in header)").

RULE: never translate a union to one field with two accessors—junrar's convention is
two named fields assigned together at parse time.

---

## 2. Byte-level reading

### 2.1 RawRead → `io/Raw` static functions + caller-managed buffers

C++ `RawRead` (`rawread.cpp`) buffers a whole header (`Read(int Size)` :14), then
pops fields via overloaded `Get`:

- `Get(byte&)` :53 → Java `buf[pos] & 0xff` inline (often the `field |= buf[pos] & 0xff`
  idiom, e.g. `BaseBlock.java:112`, `MainHeader.java:49`)
- `Get(ushort&)` :65 (`Data[p]+(Data[p+1]<<8)`) → `Raw.readShortLittleEndian(buf,pos)`
  (`io/Raw.java:102-108`) — returns **signed short**; callers mask when a numeric
  value is needed (`UnixOwnersHeader.java:18` `& 0xFFFF`)
- `Get(uint&)` :77 → `Raw.readIntLittleEndian` (`Raw.java:120-124`, signed int) or
  `Raw.readIntLittleEndianAsLong` (`Raw.java:134-139`, zero-extended into long) when
  the uint is a *size/offset* that must not go negative
- `Get8(Int64&)` :90 (`int32to64(High,Low)`) → manual composition
  (`FileHeader.java:132-138`: `fullPackSize |= highPackSize; fullPackSize <<= 32;
  fullPackSize |= getPackSize();`)
- `Get(byte*,int)` :99 → `System.arraycopy` (`FileHeader.java:147`) or explicit loop
  (`FileHeader.java:180-184`)
- `Get(wchar*,int)` :111 — no Java equivalent needed (names decoded from bytes, §4)

Endianness: everything on-disk is **little-endian**, hand-composed from
`& 0xff`-masked bytes; `Raw` also carries big-endian and write variants used by
`MarkHeader.isSignature` and the unpack layer.

RULE: NEVER use `java.nio.ByteBuffer`; the house style is `Raw.read*LittleEndian`
plus explicit masks. Choose the width by *semantic role*, not by C type alone (§2.2).

### 2.2 Unsigned-type mapping (the real rules, not the `unsigned/` package)

| C++ type | Java storage | Idiom |
|---|---|---|
| `byte` (u8) | `byte`, masked at use | `b & 0xff`; parse idiom `field \|= buf[pos] & 0xff` |
| `ushort` (u16) | `short` (flags/CRC) or `int` (counts) | `readShortLittleEndian`; mask `& 0xFFFF` when arithmetic (UnixOwnersHeader.java:18) |
| `uint` (u32), identity/CRC/attr | `int` (bit-pattern only) | `readIntLittleEndian`; compare with `~`/masks, never `<` |
| `uint` (u32), size/offset | `long` | `readIntLittleEndianAsLong` (BlockHeader packSize :54, FileHeader unpSize :98) |
| `Int64` | `long` | compose from two u32 halves (FileHeader.java:132-138) |

The **`unsigned/` package is vestigial**: `UnsignedShort/Integer/Long.java` are
*empty classes* and `UnsignedByte.java` (5 tiny static helpers) has **zero importers**
(`grep -rn "junrar.unsigned" src/main` → only the package's own files). All real
unsigned handling is inline masks.

RULE: do not extend `unsigned/`; follow the mask idioms. And beware the literal trap
(§9-S8): a u32 sentinel comparison must be written `== 0xFFFFFFFFL` against a long —
`FileHeader.java:127` (`unpSize == 0xffffffff`, int literal −1 vs zero-extended long)
is an existing silent mistranslation of `arcread.cpp:148`.

### 2.3 Buffered header read + encrypted-header alignment

`RawRead::Read` under crypt (`rawread.cpp:14-39`) rounds reads up to the AES block:
`AlignedReadSize = SizeToRead + ((~SizeToRead+1) & 0xf)`. Java preserves the exact
expression in `RawDataIo.readFully` (`io/RawDataIo.java:57-59`:
`realRead = toRead + ((~toRead + 1) & 0xF)`), keeping the surplus decrypted bytes in a
`LinkedList<Byte> dataPool` — the analogue of RawRead's `PaddedSize()`. The header-side
consequence is `BaseBlock.getHeaderSize(boolean encrypted)`
(`BaseBlock.java:175-185`), which adds the same padding to the *header size* so
`position + headerSize + packSize` remains correct for encrypted archives.
The per-header 8-byte salt read before each encrypted header
(`arcread.cpp:41-72`) maps to `Archive.readHeaders` (`Archive.java:319-328`),
with unrar's password prompt replaced by the constructor-supplied `password` and
`InitDeciphererFailedException`.

RULE: any RAR5 header-decryption port must (a) route reads through a
cipher-wrapping channel (`RawDataIo.setCipher`), (b) apply the align-to-16
formula on both the read path and the header-size accounting, never one of them.

### 2.4 Bounds behavior differs — C++ zero-fills, Java throws

`RawRead::Get*` silently returns 0 past the end (`rawread.cpp:53-63` etc.);
junrar instead sizes buffers exactly and lets `readFully`/array indexing fail, with
guard clamps where unrar had them semantically (name size clamp 4096
`FileHeader.java:140`, mirroring `arcread.cpp:152` `Min(hd->NameSize,sizeof(FileName)-1)`;
negative-size check `FileHeader.java:142-144`) plus one Java-only allocation guard:
`safelyAllocate(len, MAX_HEADER_SIZE=20MB)` (`Archive.java:92,557-565`) → 
`BadRarArchiveException` on hostile sizes.

RULE: port unrar's clamps *and* wrap every attacker-controlled allocation in
`safelyAllocate`; do not replicate C++'s silent zero-fill.

---

## 3. Flags and enums

### 3.1 Flag masks: `#define` → `public static final short` on BaseBlock

All of `MHD_*`, `LHD_*`, `EARC_*`, `SKIP_IF_UNKNOWN`, `LONG_BLOCK`
(`headers.hpp:28-70`) live as `short` constants on **BaseBlock**
(`BaseBlock.java:41-83`) — one flat namespace shared by all subclasses (the file even
says `//TODO move somewhere else`). Note `LONG_BLOCK = -0x8000`
(`BaseBlock.java:78`): 0x8000 doesn't fit a signed short, so the sign-flipped literal
is used; masking still works.

Flag *testing* is idiomized as named predicates on the owning class returning
`boolean`, never raw mask expressions at call sites:
`FileHeader.isSplitAfter/isSolid/isEncrypted/isUnicode/hasSalt/hasExtTime/isLargeBlock/isDirectory`
(`FileHeader.java:599-662`), `MainHeader.isMultiVolume/isFirstVolume/isSolid/isLocked/isProtected/isAV/isNewNumbering`
(`MainHeader.java:88-144`), `BaseBlock.hasArchiveDataCRC/hasVolumeNumber/hasEncryptVersion/isSubBlock`
(`BaseBlock.java:120-144`; `isSubBlock` is the direct port of
`headers.hpp:136-143`). C++ cached these as Archive booleans
(`archive.cpp:176-182` `Solid=(NewMhd.Flags & MHD_SOLID)!=0;`) — junrar does not
cache; it re-asks the header (`Archive.java:582` `newMhd.isSolid() || hd.isSolid()`).

RULE (flags): new flag word ⇒ constants on the block class + one predicate per
meaningful bit; call sites use predicates. Window-size class masks stay compare-mask
(`isDirectory()` = `(flags & LHD_WINDOWMASK) == LHD_DIRECTORY`, FileHeader.java:660-662
= `arcread.cpp:495-498`).

### 3.2 Discriminator enums: C++ `enum` → Java enum with byte payload + `findType`

`HEADER_TYPE` (`headers.hpp:72-76`) → `UnrarHeadertype`
(`rarfile/UnrarHeadertype.java:27-39`) — each constant wraps its wire byte, plus:

- `findType(byte) → enum-or-null` (`:46-78`) — the *parse-side* mapping; null means
  "unknown block" and the caller decides (Archive.java:348-351 throws
  `CorruptHeaderException`).
- `equals(byte)` overload (`:91-93`) — used for cheap comparisons against the raw
  stored byte (`BaseBlock.java:136,139`).
- The raw `byte headerType` stays the stored field on BaseBlock (`BaseBlock.java:89`);
  the enum is materialized on demand (`getHeaderType()` `:187-189`).

Same pattern for `HOST_SYSTEM` (`headers.hpp:81-84`) → `HostSystem` enum
(lowercase constants `msdos..beos`, `findHostSystem`), and for the sub-block id enum
(`headers.hpp:78-79` `EA_HEAD=0x100..STREAM_HEAD`) → `SubBlockHeaderType(short)` with
`findSubblockHeaderType`. The C-string sub-header types `SUBHEAD_TYPE_*`
(`headers.hpp:87-94`) → `NewSubHeaderType` — a byte-array constant class with
`byteEquals` (used for the `RR` recovery-record check, `FileHeader.java:187`), NOT a
Java enum, because the payload is a variable-length byte string compared against a
raw name buffer.

RULE: wire-value enums get (a) payload field, (b) `findX(value)` returning null for
unknown, (c) `equals(primitive)` helper; keep the primitive in the parsed object,
convert lazily. Unknown enum value ⇒ null ⇒ caller policy (throw for block type,
`break` and skip for sub-block subtype, Archive.java:489).

---

## 4. Unicode filename decoding

Source is **encname.cpp** (`EncodeFileName::Decode`, encname.cpp:14-58 — *not*
unicode.cpp, which holds the OS charset shims `WideToChar/UtfToWide` junrar never
ported) → `rarfile/FileNameDecoder.java`.

- Field split: the FILE_HEAD name buffer holds `ASCII-name \0 encoded-unicode`.
  C++ `arcread.cpp:186-205`; Java `FileHeader.java:150-167` scans for the NUL,
  takes `fileName` from the prefix, calls `FileNameDecoder.decode(fileNameBytes, len+1)`
  for `fileNameW`.
- The decoder is a **line-for-line port** of the 2-bit opcode VM: opcodes 0..3 from
  a rolling `flags` byte (`Flags>>6` / `flags >>> 6`), high-byte page from the first
  encoded byte, RLE-with-correction in case 3 (`FileNameDecoder.java:40-69` ==
  `encname.cpp:26-52`). Translation choices: the C++ *class with member state*
  (`Flags/FlagBits` in encname.hpp) became a **stateless static method with locals**;
  output `wchar*` became `StringBuilder` + `(char)` casts (UTF-16 code units);
  `MaxDecSize` bound dropped in favor of `name.length` loop bounds.
- **Not ported**: the whole-name-UTF-8 branch (`arcread.cpp:189-194`: when there is
  no NUL, the name is UTF-8 → `UtfToWide`). Java sets `fileNameW=""` in that case
  (`FileHeader.java:158-163`) and decodes the ANSI name with **platform default
  charset** (`new String(fileNameBytes)` `FileHeader.java:165` — no explicit charset).
  Also not ported: `ConvertNameCase`, `ExtToInt` OEM conversion, and
  `ConvertUnknownHeader`'s `/`↔`\` path normalization (`arcread.cpp:566-585`).
- C++ clears `LHD_UNICODE` when decode yields empty (`arcread.cpp:203-204`); Java
  keeps the flag but `getFileName()` (`FileHeader.java:670-672`) prefers `fileNameW`
  only when non-empty — same observable effect, different mechanism.
- Java adds a path-validity gate `isFilenameValid` → `CorruptHeaderException`
  (`FileHeader.java:169-171,230-237`), absent in unrar.

RULE for RAR5: RAR5 names are plain UTF-8 — do NOT reuse FileNameDecoder; add an
explicit `StandardCharsets.UTF_8` decode (and fix the default-charset habit while
there). Keep the "decoder is a pure static function over the raw name bytes" shape.

---

## 5. Archive scanning / iteration

### 5.1 Control-flow reshape: lazy cursor → eager list

C++: `Archive::ReadHeader` (`arcread.cpp:32`) parses ONE block into reused members;
iteration = `ReadHeader(); … SeekToNext()` loops scattered across callers
(`SearchBlock/SearchSubBlock` arcread.cpp:3-29, extract.cpp). Position bookkeeping via
`CurBlockPos/NextBlockPos` members; `NextBlockPos = CurBlockPos + HeadSize
(+DataSize/FullPackSize)`; `SeekToNext()` (`archive.cpp:245`).

Java: **`Archive.readHeaders(long fileLength)` (`Archive.java:302-555`) parses the
entire header chain once at volume-open time** (called from `setChannel`
:198-222) into `List<BaseBlock> headers` (:112). Everything downstream is list
traversal: `getFileHeaders()` :250-258, cursor `nextFileHeader()` :260-269,
`Iterable<FileHeader>` :874-895, `extractFile` locating by `indexOf` :575-579.
`SearchBlock/SearchSubBlock` have **no Java equivalent** — filtering the list replaced
them. Per-block position lives ON the header (`positionInFile`, set at
`Archive.java:345`), and "seek to next" is recomputed as
`newpos = positionInFile + getHeaderSize(encrypted) + packOrDataSize` then
`channel.setPosition(newpos)` (:457-458 file, :473-474 protect, :537-538 sub).

The type dispatch is a switch on `UnrarHeadertype` (:352) where FILE/NEWSUB/PROTECT/SUB
fall into `default:` and get the 4-byte `BlockHeader` read first (:436-440) — mirroring
the C++ structure where those types carry DataSize. The old-style embedded main-header
comment special case (`arcread.cpp:105-108`: MAIN_HEAD+MHD_COMMENT reads only
SIZEOF_NEWMHD) is *implicitly* handled by junrar always reading exactly
mainHeaderSize(±enc) and re-seeking by headerSize.

Termination: C++ returns 0 on EOF/short read; Java `break`s on
`position >= fileLength` (:333) or short read (:340), and **returns** at
EndArcHeader (:434) — unrar instead keeps reading (multi-part EndArc flags). Java adds
a `Set<Long> processedPositions` loop guard (:311,407-410,460-463,476-479,540-543)
→ `BadRarArchiveException` on a position revisit — a defense unrar doesn't need
(its NextBlockPos<=CurBlockPos check `arcread.cpp:390-398` is the analogue and is
*also* absent as such in Java).

RULE: new block types are integrated by (a) adding an `UnrarHeadertype` case in
`readHeaders`, (b) reading parent-then-tail buffers per §1.2, (c) adding the parsed
object to `headers`, (d) explicitly seeking past any packed data via
`positionInFile + headerSize(enc) + dataSize` and registering the position in
`processedPositions`. Never leave the channel mid-block (comment at
Archive.java:534-536 records that regression class).

### 5.2 Error handling: ErrHandler codes → typed exception hierarchy

C++ pattern: `Log(...); ErrHandler.SetErrorCode(WARNING|CRC_ERROR); return 0;`
(non-fatal, caller-visible via `BrokenFileHeader`) or `ErrHandler.Exit(FATAL_ERROR)`
(errhnd.hpp:19-20 enum SUCCESS..USER_BREAK).

Java: `exception/` hierarchy rooted at checked `RarException` —
`BadRarArchiveException` (not-a-rar / hostile size / loop), `NotRarArchiveException`
(unknown block in stream), `CorruptHeaderException` (bad mark/unknown type/truncated),
`CrcErrorException` (extraction CRC), `UnsupportedRarV5Exception`,
`UnsupportedRarEncryptedException`, `InitDeciphererFailedException`,
`MainHeaderNullException`, `HeaderNotInArchiveException`. Mapping examples:
V5 signature → throw `UnsupportedRarV5Exception` (Archive.java:357-359); unknown
header type → `CorruptHeaderException` (:348-351) or `NotRarArchiveException`
(:547-549); extraction CRC mismatch (`doExtractFile` :783-788) → `CrcErrorException`
= unrar's CRC_ERROR path.

unrar's "warn and continue on broken archive" tolerance survives in ONE place:
`setChannel` (:205-211) rethrows only
`UnsupportedRarEncrypted|UnsupportedRarV5|CorruptHeader|BadRarArchive` and
**swallows everything else** ("ignore exceptions to allow extraction of working files
in corrupt archive"). Logging is slf4j `logger.warn(...)` where unrar called
`Log(...)`; per-header `print()` methods (info-level dumps, e.g. BaseBlock.java:195-205)
are a Java-side debugging addition.

RULE: new failure modes get their own `RarException` subclass (flat, cause-carrying
constructors); decide fatal-vs-tolerated by whether readHeaders throws it past
`setChannel`'s filter list — and update that filter list consciously.

---

## 6. Volume handling

C++: `MergeArchive` (`volume.cpp:6`) — close current file, compute next name via
`NextVolumeName(char*, bool OldNumbering)` (**pathfn.cpp:423**, digit-increment of
`.partN.rar` or `.rar→.r00→.r01`), reopen, re-read headers, splice mid-stream from
`ComprDataIO::UnpRead`.

Java splits this into an SPI plus a helper — the **Volume/VolumeManager interfaces are
a junrar invention** (author Rogiel, `volume/Volume.java`, `volume/VolumeManager.java`),
designed so multi-part archives can come from arbitrary sources:

- `VolumeManager.nextVolume(Archive, lastVolume)` — first volume when `lastVolume==null`,
  else successor, `null` = no more. Implementations: `FileVolumeManager`
  (name-increment via `VolumeHelper.nextVolumeName`, `FileVolumeManager.java:19-29`)
  and `InputStreamVolumeManager` (ordinal map of caller-supplied streams).
- `Volume` = `getChannel() + getLength() + getArchive()`; `FileVolume` wraps a file,
  `InputStreamVolume` wraps a stream (length ≈ `available()`, else `Long.MAX_VALUE` —
  a known approximation).
- `VolumeHelper.nextVolumeName(String, boolean oldNumbering)`
  (`volume/VolumeHelper.java:40-96`) is the direct port of pathfn.cpp's
  `NextVolumeName`, both numbering schemes; the `oldNumbering` decision replicates
  `volume.cpp:28`: `!mainHeader.isNewNumbering() || archive.isOldFormat()`
  (`FileVolumeManager.java:23-24`).
- The mid-extraction splice = `ComprDataIO.unpRead` (`unpack/ComprDataIO.java:151-172`):
  on `unpPackedSize==0 && isSplitAfter()` → `nextVolume` → packed-CRC check (port of
  `volume.cpp:15-19`, throws `CrcErrorException`) → `UnrarCallback.isNextVolumeReady`
  (port of the DLL change-volume callback) → `archive.setVolume(next)` (re-runs
  readHeaders on the new channel! `Archive.java:869-872` → `setChannel`) →
  `archive.nextFileHeader()` → `dataIO.init(hd)`.
- NOT ported: unrar's fallback retry with the *other* numbering scheme
  (`volume.cpp:57-68` OldSchemeTested) and recovery-volume reconstruction (recvol).

RULE: volume acquisition is always through VolumeManager — never construct file names
inside Archive; RAR5's `QOpen`/volume changes should extend VolumeManager
implementations, and remember `setVolume` re-parses the whole header list of the new
volume (cursor reset semantics differ from unrar's in-place continue).

---

## 7. io/ abstractions vs unrar File/BufferedFile

unrar: `Archive : public File` (`archive.hpp:8`) — the archive **is** the file;
raw OS handles, `Read/Seek/Tell/FileLength`, `SaveFilePos` RAII.

junrar: composition over a minimal read-only seekable interface:

- `SeekableReadOnlyByteChannel` (`io/SeekableReadOnlyByteChannel.java`):
  `getPosition/setPosition/read()/read(buf,off,len)/readFully(buf,count)/close` —
  the entire File surface the parsing layer needs. `Tell()`→`getPosition`,
  `Seek(x,SEEK_SET)`→`setPosition` (no other whence ever needed).
- `SeekableReadOnlyFile` — `RandomAccessFile("r")` adapter.
- `SeekableReadOnlyInputStream` + `RandomAccessInputStream` — stream support via a
  grow-only 512-byte-block memory cache (public-domain ImageJ code) giving
  backwards seek over non-seekable input; the whole read portion of the stream stays
  in memory.
- `RawDataIo` — decorator over the channel adding header decryption (§2.3); it is the
  read path `readHeaders` actually uses (`Archive.java:315`), one fresh instance per
  block iteration.
- No equivalent of unrar's write side, `BufferedFile`, or `SaveFilePos` (positions are
  recomputed from `positionInFile` instead of saved/restored).

RULE: parsing code takes `SeekableReadOnlyByteChannel` (or `RawDataIo` when decryption
matters), never `File`/`InputStream` directly; new byte sources = new channel
implementations, not changes to Archive.

---

## 8. Salt/crypt fields, comments, legacy sub-blocks — the RAR5 extension surface

- **Salt**: `SALT_SIZE 8` (`headers.hpp:161`) → `FileHeader.SALT_SIZE`
  (`FileHeader.java:45`), `byte[] salt` read when `hasSalt()`
  (`FileHeader.java:193-198` == `arcread.cpp:214-215`); consumed by
  `Rijndael.buildDecipherer(password, salt)` both for file data
  (`ComprDataIO.init` :119-126) and headers (§2.3). RAR5 moves to 16-byte salt +
  KDF-count — extend as new fields on the RAR5 header classes, not by widening this one.
- **EncryptVer**: `MainHeader.encryptVersion` conditional on `MHD_ENCRYPTVER`
  (`MainHeader.java:48-50` == `arcread.cpp:119-120`); junrar ignores its value
  (unrar gates `>=36` for AES tweaks and rejects `>UNP_VER`, archive.cpp:184-196 —
  not ported).
- **Comment paths (two generations both visible)**: old-style COMM_HEAD block →
  `CommentHeader` parsed then **skipped** (data never decompressed;
  Archive.java:397-412; unrar decompresses via arccmt.cpp — not ported); new-style
  comments are NEWSUB_HEAD FileHeaders with name `CMT`
  (`NewSubHeaderType.SUBHEAD_TYPE_CMT`) which junrar stores as ordinary FileHeaders
  (isFileHeader()==false) without special handling. `MainHeader.hasArchCmt()`
  exposes the flag only.
- **NEWSUB extras**: SubData blob capture + RR recovery-sector extraction
  (`FileHeader.java:174-191` == `arcread.cpp:157-183`); `subFlags` aliases fileAttr.
  This NEWSUB_HEAD machinery (typed service records carried as file-like blocks) is
  the closest RAR4 analogue to RAR5's "service headers" — RAR5 porters should mirror
  this shape rather than the legacy SUB_HEAD one.
- **Legacy SUB_HEAD family** (RAR<3 sub-blocks): SubBlockHeader + subtype dispatch
  (`Archive.java:482-546` == `arcread.cpp:303-355`) — MAC_HEAD and EA_HEAD parsed;
  BEEA/NTACL/STREAM recognized but skipped; UO_HEAD parsed with name-size clamp
  semantics diverging slightly (Java bounds-checks against buffer instead of NM-1
  clamp). Frozen legacy — do not extend.
- **EndArc**: conditional tail fields ArcDataCRC/VolNumber by flag
  (`EndArcHeader.java:37-48` == `arcread.cpp:122-128`); `EARC_REVSPACE` recovery
  probing (`arcread.cpp:369-380`) not ported. Java-only strict `isValid()` (headCRC
  0x3DC4, flags exactly 0x4000) applied when single-volume (Archive.java:430-432).

---

## 9. Skew list — where junrar ≠ baseline (do NOT assume 1:1)

- **S1 — No header-CRC verification.** unrar checks every header's CRC
  (`Raw.GetCRC` rawread.cpp:123, checks at arcread.cpp:258-271 & :362) and flags
  `BrokenFileHeader`; junrar computes **no header CRC at all** (only extraction-data
  CRC, plus the fixed-value Mark/EndArc `isValid`s). A RAR5 port must reintroduce
  per-header CRC32 checking rather than copying this gap.
- **S2 — Old (RAR 1.x) format is dead code.** `RARVersion.OLD` is detected
  (`MarkHeader.isSignature`) but `MarkHeader.isValid()` then rejects it, and
  `ReadOldHeader` (`arcread.cpp:410-470`) was never ported, even though
  `Archive.isOldFormat()` and `RarCRC.checkOldCrc` (ComprDataIO.java:194-195)
  exist downstream. Don't trust the old-format plumbing.
- **S3 — No SFX support.** `IsArchive`'s MAXSFXSIZE signature scan
  (`archive.cpp:130-153`) has no Java counterpart; the mark header must be at
  offset 0.
- **S4 — Eager whole-archive header parse** replaces lazy cursor + Search* (§5.1);
  `Archive` holds a `List<BaseBlock>` instead of one reused struct per type
  (`archive.hpp:86-99`). Also `EndArcHeader` **terminates** parsing (Archive.java:434).
- **S5 — Renames.** `MacFInfoHeader`→`MacInfoHeader`; `EncodeFileName`(class, encname.cpp)
  →`FileNameDecoder`(static, decode-only); `NewMhd/NewLhd/SubHead` member naming
  survives only as `Archive.newMhd`; `HEADER_TYPE`→`UnrarHeadertype`;
  `HOST_SYSTEM`→`HostSystem` (lowercase constants); `RawRead`→`Raw`+`RawDataIo` split.
  `StreamHeader` dropped; `OldMainHeader/OldFileHeader` dropped.
- **S6 — Java-side additions**: `positionInFile` on BaseBlock; `processedPositions`
  loop guard; `safelyAllocate`/MAX_HEADER_SIZE; filename canonical-path validation;
  `RARVersion` enum + V5 detection byte (d[6]==0x01, MarkHeader.java:64-69 — beyond
  the 3.7.3 baseline, which required D[6]==0x00); `getHeaderSize(encrypted)` padding
  API; Volume/VolumeManager SPI; slf4j `print()` dumps; `Iterable<FileHeader>`;
  ext-time modernized to `java.nio.file.attribute.FileTime` with 100ns units
  (`FileHeader.java:249-279`) — Date accessors kept as deprecated bridges.
- **S7 — ProtectHeader is mis-ported** (`ProtectHeader.java:24-33`): reads `version`
  at pos 0 **without advancing**, then `recSectors` also at pos 0; `mark` is a single
  byte vs C++ `byte Mark[8]`; `protectHeaderSize=8` vs the 15 tail bytes of
  `SIZEOF_PROTECTHEAD 26` (headers.hpp:242-249, arcread.cpp:293-301). Harmless today
  only because the block is skipped by size. Do not copy its pattern.
- **S8 — Unsigned-literal comparison bug class**: `FileHeader.java:127`
  `unpSize == 0xffffffff` can never be true (long vs int −1) — the port of
  `arcread.cpp:148`'s INT64MAX promotion is silently inert. When porting comparisons
  against unsigned sentinels, suffix the literal with `L`.
- **S9 — Charset behavior**: ANSI names decoded with platform default charset
  (`new String(bytes)`, FileHeader.java:157,165), UTF-8 whole-name branch missing
  (§4); no ConvertNameCase/ExtToInt/path-separator normalization.
- **S10 — unsigned/ package is empty scaffolding** (§2.2) — three empty classes,
  zero users. Ignore it.
- **S11 — Tolerated-vs-fatal split lives in `setChannel`'s catch filter**
  (Archive.java:205-211), not in an ErrHandler code; adding a new exception type
  changes archive-open behavior implicitly (uncaught types get swallowed).
- **S12 — Comment decompression, recovery volumes, NotFirstVolume detection**
  (`archive.cpp:203-236`), `EncryptVer>UNP_VER` rejection, and `EARC_REVSPACE`
  recovery are unported; `MainHeader.isSignHeaderPresent` equivalent (`Signed=PosAV!=0`)
  absent.

---

## Quick-reference: minimal recipe for adding a block type (post-RAR4 style)

1. Wire byte → new constant in `UnrarHeadertype` (+ `findType` arm).
2. Class in `rarfile/` extending the same parent unrar uses; ctor
   `(Parent p, byte[] tail)` + manual `pos` cursor + `Raw.read*LittleEndian`;
   incremental size constant (§1.3); flag predicates (§3.1).
3. `Archive.readHeaders` case: allocate via `safelyAllocate`, `rawData.readFully`,
   construct, `headers.add`, seek past data via
   `positionInFile + getHeaderSize(isEncrypted()) + dataSize`, register in
   `processedPositions`.
4. Failure modes → existing or new `RarException` subtype; check `setChannel` filter.
5. Tests exercising both flag polarities and hostile sizes (junrar test corpus under
   `src/test/`).
