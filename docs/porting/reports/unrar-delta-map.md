# unrar 3.7.3 → 7.2.7 extraction-capability delta map

Baseline: 3.7.3 = `2e71167`; tip: 7.2.7 = `d861246` (~/git/unrar, one commit per release, linear).
Evidence convention: `<hash> = release` from `git -C ~/git/unrar log --reverse master`; per-file first-appearance via `git log --reverse master -- <file>`; guard introductions bisected by grepping each historical blob (`git show <hash>:<file> | grep`). All commands run this session.

Scope: extraction of RAR 1.5–4 ("RAR3") and RAR5/RAR7 archives. Compression is out of scope. Recovery-record USE marked optional.

---

## 1. File-level census

`git diff --stat 2e71167 d861246`: 173 files changed, +26071/−15856. `ls-tree` comparison: **51 new files, 17 deleted**.

### New files, classified (introducing release in parentheses; from `git log --reverse master -- <file>`)

**RAR5/RAR7 format-specific (extractor-required):**

| File | Release | Role |
| --- | --- | --- |
| headers5.hpp | 5.0.0 (`a224d33`) | RAR5 block flags, FCI compression bits, extra-record IDs |
| headers.cpp | 5.0.0 | shared header struct code (split from headers.hpp era) |
| crypt5.cpp | 5.0.0 | PBKDF2-HMAC-SHA256 KDF, SetKey50, ConvertHashToMAC |
| sha256.cpp/.hpp | 5.0.0 | SHA-256 (KDF + pswcheck csum) |
| blake2s.cpp/.hpp, blake2sp.cpp, blake2s_sse.cpp | 5.0.0 | Blake2s + 8-lane Blake2sp file checksums (SSE variant optional) |
| hash.cpp/.hpp | 5.0.0 | `DataHash` abstraction: HASH_NONE/RAR14/CRC32/BLAKE2 |
| unpack50.cpp | 5.0.0 | RAR5 (and RAR7) LZ decoder + delta/E8/E8E9/ARM filters |
| unpack50frag.cpp | 5.0.4 (`143e317`) | fragmented window allocator (large dict on low memory) |
| unpack50mt.cpp | 5.0.0 | multithreaded RAR5 unpack (RAR_SMP) — **optional** |
| qopen.cpp/.hpp | 5.0.0 | Quick-open record (locator→QO service header) — **optional, perf only** |
| win32lnk.cpp | 5.0.0 | Windows symlink/junction creation for FHEXTRA_REDIR |
| hardlinks.cpp | 5.0.0 | FSREDIR_HARDLINK extraction |

**RAR3 code split out of 3.x monoliths (same logic relocated, then hardened):**

| File | Release | Origin |
| --- | --- | --- |
| unpack30.cpp | 5.0.0 | Unpack29 + VM-filter glue out of unpack.cpp |
| unpackinline.cpp | 5.0.0 | CopyString/DecodeNumber/InsertOldDist shared inlines |
| crypt1.cpp / crypt2.cpp / crypt3.cpp | 5.0.0 | RAR1.3/1.5, RAR2.0, RAR3.0 crypto split from crypt.cpp |
| recvol3.cpp | 5.0.0 | RAR3 .rev handling split from recvol.cpp — **optional** |

**Shared infra rework:**

| File | Release | Role |
| --- | --- | --- |
| secpassword.cpp/.hpp | 4.2.2 (`c3ff881`) | in-memory password scrubbing/obfuscation |
| threadmisc.cpp, threadpool.cpp/.hpp | 5.0.0 | thread pool (MT unpack + MT hash) — **optional** |
| rawint.hpp | 5.5.1 (`1522ee0`) | raw LE/BE integer load/store helpers |
| largepage.cpp/.hpp | 7.1.1 (`a2e5484`) | Windows large-page alloc — **optional perf** |

**RAR5 recovery (optional — repair command, not extraction):** recvol5.cpp, rs16.cpp/.hpp (Reed-Solomon over GF(2^16)), all 5.0.0.

**CLI-only / build / platform-out-of-scope:** ui.cpp/.hpp, uicommon.cpp, uiconsole.cpp, uisilent.cpp (5.1.1 UI abstraction); cmdfilter.cpp, cmdmix.cpp (5.7.2 cmddata split); motw.cpp/.hpp (7.1.1, Windows Mark-of-the-Web ADS propagation — Win-only, junrar N/A); dll_nocrypt.def, dll.rc, UnRAR.vcxproj, UnRARDll.vcxproj, makefile, acknow.txt, rarpch.cpp.

### Deleted files

| File(s) | Removed in | Why |
| --- | --- | --- |
| int64.cpp/.hpp | 3.9.1 (`a0d5a16`) | custom Int64 class → native 64-bit ints |
| rarvmtbl.cpp | 5.5.1 (`1522ee0`) | VM opcode table gone with raw-VM removal |
| array.hpp | 7.1.1 (`a2e5484`) | custom Array\<T\> → std::vector (7.x modernization) |
| savepos.cpp/.hpp, rarfn.hpp, ulinks.hpp | various | small-helper consolidation |
| beosea.cpp, os2ea.cpp, unios2.cpp | 5.x | dropped BeOS/OS2 extended-attribute platforms |
| makefile.bcc/.cygmin/.dmc/.msc/.unix, msc.dep | — | build system consolidation |

---

## 2. RAR5 format support inventory (what a RAR5/RAR7 extractor needs)

All observations from 7.2.7 blobs (`git show d861246:<file>`) unless noted.

### 2.1 Signature / format detection (archive.cpp `IsSignature`, lines 100–126)

- RAR5 marker: `52 61 72 21 1A 07` + version byte: `00`=RAR15 (RAR 1.5–4), `01`=RAR50, `02..04`=RARFMT_FUTURE (graceful "unknown version" error). `SIZEOF_MARKHEAD5=8` (7-byte match + trailing 0x00).
- RAR 1.4 marker `52 45 7e 5e` still recognized (ReadHeader14 path).
- SFX stub scan limit `MAXSFXSIZE`: 0x80000 (512 KB) at 3.7.3 → **0x400000 (4 MB)** since 7.1.1 (`rardefs.hpp`).

### 2.2 Header encoding (arcread.cpp `ReadHeader50` line 555, `ProcessExtra50` line 982; rawread.hpp)

- **vint**: base-128 little-endian, continuation bit 0x80 — `RawRead::GetV()` / `RawGetV(Data,ReadPos,DataSize,Overflow)`.
- Block layout: `CRC32(4) | vint HeadSize | vint HeaderType | vint HeaderFlags [| vint ExtraSize][| vint DataSize]`.
- Header types (headers.hpp): `HEAD_MARK=0, HEAD_MAIN=1, HEAD_FILE=2, HEAD_SERVICE=3, HEAD_CRYPT=4, HEAD_ENDARC=5, HEAD_UNKNOWN=0xff`.
- Common flags (headers5.hpp): `HFL_EXTRA 0x01, HFL_DATA 0x02, HFL_SKIPIFUNKNOWN 0x04, HFL_SPLITBEFORE 0x08, HFL_SPLITAFTER 0x10, HFL_CHILD 0x20, HFL_INHERITED 0x40`.
- Names are UTF-8 in the header.

### 2.3 Main archive header

- Flags: `MHFL_VOLUME, MHFL_VOLNUMBER (vint volume number, all vols except first), MHFL_SOLID, MHFL_PROTECT (RR present), MHFL_LOCK`.
- Extra records: `MHEXTRA_LOCATOR 0x01` (flags `QLIST 0x01`, `RR 0x02` — offsets of quick-open record and recovery record) — extractor may ignore (QO = perf, RR = repair); `MHEXTRA_METADATA 0x02` (archive name + creation time, flags incl. Unix ns format) — added **6.2.1** (`635f619`).

### 2.4 Archive encryption header (`HEAD_CRYPT` — encrypted headers)

- Fields: vint crypt version (must be `CRYPT_VERSION 0`), vint flags (`CHFL_CRYPT_PSWCHECK 0x01`), byte Lg2Count (KDF iterations log2, reject > `CRYPT5_KDF_LG2_COUNT_MAX 24`), Salt[16], optional PswCheck[8]+csum[4].
- When present, every subsequent header is AES-256-CBC encrypted with per-header 16-byte IV prepended (`Decrypt` path at arcread.cpp:144ff analog for RAR5 in ReadHeader50).

### 2.5 File / service header

- Fields: vint file flags (`FHFL_DIRECTORY 0x01, FHFL_UTIME 0x02 (Unix mtime present), FHFL_CRC32 0x04, FHFL_UNPUNKNOWN 0x08`), vint unpacked size, vint attributes, [uint32 mtime], [uint32 CRC32], vint compression info, vint host OS (0=Windows 1=Unix), vint name size + UTF-8 name.
- **Compression info bits (headers5.hpp)**: algo version bits 0–5 (stored 0 = RAR5 "5.0", stored 1 = RAR7 "7.0" → `VER_PACK5=50`/`VER_PACK7=70` internally, headers.hpp:16–18); `FCI_SOLID 0x40`; method bits 7–9 (0=store..5=best); dictionary:
  - RAR5 (≤6.2.12): 4 dict bits, `WinSize = 0x20000 << bits` — 128 KB…4 GB.
  - RAR7 (7.0.1, `d52ee2f`): **5 dict bits + 5 fraction bits** (`FCI_DICT_FRACT*`, 1/32 steps): `WinSize=0x20000ULL<<((CompInfo>>10)&0x1f)` then `WinSize+=WinSize/32*((CompInfo>>15)&0x1f)` (arcread.cpp:868–874) — up to 1 TB encodable; extraction rejects > `UNPACK_MAX_DICT` = **64 GB** (rardefs.hpp:40, arcread.cpp:880); `FCI_RAR5_COMPAT 0x100000` marks RAR7-written-but-RAR5-decodable streams.
- **Extra records** (`ProcessExtra50`): `FHEXTRA_CRYPT 0x01` (per-file crypto: version 0, flags PSWCHECK/HASHMAC, Lg2Count, Salt16, IV16, pswcheck+csum), `FHEXTRA_HASH 0x02` (type 0 = Blake2sp, 32-byte digest), `FHEXTRA_HTIME 0x03` (flags: unixtime format, mtime/ctime/atime present, `FHEXTRA_HTIME_UNIX_NS` nanosecond precision — added **5.5.1**), `FHEXTRA_VERSION 0x04` (file version `;N`), `FHEXTRA_REDIR 0x05` (redirection type + `FHEXTRA_REDIR_DIR` flag + target string), `FHEXTRA_UOWNER 0x06` (uname/gname/uid/gid), `FHEXTRA_SUBDATA 0x07` (service payload).
- Redirection types (headers.hpp:109–111): `FSREDIR_UNIXSYMLINK, FSREDIR_WINSYMLINK, FSREDIR_JUNCTION, FSREDIR_HARDLINK, FSREDIR_FILECOPY`. RAR5 stores the link target **in the header** (`ExtractUnixLink50(Cmd,Name,hd)`); RAR3 stored it as file **data** (`ExtractUnixLink30(Cmd,DataIO,Arc,...)`) — ulinks.cpp:73,116.
- Service header names (headers.hpp:115–122): `CMT`, `QO`, `ACL`, `STM`, `UOW`, `AV`, `RR`, `EA2`.

### 2.6 Crypto (crypt.hpp, crypt5.cpp)

- AES-256-CBC (rijndael.cpp; gained AES-NI hardware path in 7.2.7 blob — 10 `_mm_aes*` hits — plus NEON; a Java port uses JCE, N/A).
- KDF: PBKDF2-HMAC-SHA256 (`pbkdf2` crypt5.cpp:85), iteration count `2^Lg2Count`, default lg2 = `CRYPT5_KDF_LG2_COUNT 15` (32768), accept max lg2 24. `SetKey50` derives three values in ONE accumulation pass — the loop runs the segment counts `{Count-1, 16, 16}` and snapshots the running XOR accumulator after each segment (`crypt5.cpp:105-125`): AES key (count iterations), HashKey (count+16), PswCheck material (count+**32**; folded to 8 bytes) — enables **wrong-password detection without decrypting data** (`SIZE_PSWCHECK 8` + SHA-256-truncated 4-byte csum). *(Corrected 2026-07-17: an earlier revision said count+17; the second 16-iteration segment puts V2 at count+32 — verified against the `{Count-1,16,16}` loop and by independent PBKDF2 recomputation of the crypt5.cpp `TestPBKDF2` Key vectors.)*
- `ConvertHashToMAC` (crypt5.cpp:193): when `FHEXTRA_CRYPT_HASHMAC`, stored checksum is HMAC-masked so the CRC/hash doesn't leak plaintext info; verification converts computed hash with HashKey.
- KDF result cache (`KDF5CacheItem`, 4 entries) added **5.2.1** (`f0474dc`) — same-password multi-file extraction perf; RAR3 got `KDF3Cache` in same release.
- Self-test vector `TestPBKDF2` in crypt5.cpp (disabled by default) — useful reference vector for the port.

### 2.7 unpack 5.0 (unpack50.cpp + unpack.hpp + unpackinline.cpp)

- Block-structured stream: `UnpackBlockHeader` (BlockSize, BlockBitSize, LastBlockInFile, TablePresent) + Huffman `UnpackBlockTables` {LD literals, DD distances, LDD low-distance bits, RD rep-distances, BD table-lengths}.
- Alphabets (compress.hpp): `NC=306, DCB=64 (RAR5, ≤4 GB), DCX=80 (RAR7, ≤1 TB), LDC=16, RC=44, BC=20`; `MAX_LZ_MATCH 0x1001`.
- Main loop slots (unpack50.cpp:139–160): 256=filter, 257=last-length rep, 258–261=old-dist rep, else literal/match.
- **Filters**: type = 3 bits after two vint-ish filter data fields (`ReadFilterData`); only `FILTER_DELTA(0), FILTER_E8(1), FILTER_E8E9(2), FILTER_ARM(3)` are applied (unpack50.cpp:427–485). DELTA carries 5-bit channel count. RarVM is **gone from RAR5 entirely** — filters are enumerated, not programs. Limits: `MAX_FILTER_BLOCK_SIZE 0x400000`, `MAX_UNPACK_FILTERS 8192` (unpack.hpp).
- **Window**: 64-bit `WinSize` (`Unpack::Init(uint64,bool)`, unpack.cpp:80–158); caps: 1 TB theoretical, `UNPACK_MAX_DICT` 64 GB, >2 GB refused on 32-bit builds; min alloc 0x40000; solid streams must not grow the window; on `bad_alloc` falls back to **fragmented window** (unpack50frag.cpp, 5.0.4) for 32-bit RAR5 — a Java port can treat this as allocation strategy detail, optional.
- **Multithreaded unpack** (unpack50mt.cpp, `RAR_SMP`, ≤8 threads): pure performance; single-threaded `Unpack5()` is complete — **optional for junrar**.
- Checksums: CRC32 or Blake2sp verified after unpack (hash.cpp); MT CRC/Blake2 optional.

### 2.8 RAR7-era additions (7.0.1 `d52ee2f` … 7.2.7)

- `VER_PACK7` (method 70): **same Unpack5 routine** with `ExtraDist=true` → extended distance coding (DCX=80 table, distances to 1 TB). Introduced 7.0.1 (pinned: `VER_PACK7`, `ExtraDist`, `FCI_DICT_FRACT0` all first appear in `d52ee2f`).
- Dictionary sizes >4 GB up to 64 GB accepted on extraction; `CmdExtract::CheckWinLimit` (extract.cpp:1758, since 7.0.1) prompts/errors (`ERAR_LARGE_DICT` in DLL) when dict exceeds user limit — port should expose a max-dictionary guard instead of blind 64 GB allocation.
- `FirstWinDone` (7.0.3 `5faaa45`): tracks first window wrap; `CopyString` rejects references into never-written window area (`!FirstWinDone && Distance>UnpPtr || Distance>MaxWinSize || Distance==0`) — applied to RAR1.5/2/3/5 paths alike; deterministic corrupt-archive behavior.
- No new hash type in RAR7 (still CRC32/Blake2sp; `FHEXTRA_HASH_BLAKE2` only).
- 7.1.1: std::vector/std::wstring sweep (array.hpp deleted), largepage, motw — no format change.

### 2.9 Volumes, links, QO, misc

- RAR5 volumes: `.partN.rar` naming only (old `.rNN` style is RAR3-only); first-volume detection via `MHFL_VOLUME`+`MHFL_VOLNUMBER`; `EHFL_NEXTVOLUME` on end-of-archive header; header `HFL_SPLITBEFORE/AFTER` replace RAR3 `LHD_SPLIT_*`.
- `CmdExtract::AnalyzeArchive` (6.2.1, extract.cpp:1560): scans volume set to pick correct starting volume/rejoin behavior — CLI-adjacent convenience, semantics worth mirroring for multi-volume extraction entry point.
- Symlink extraction safety (see §3.6) applies to both formats.
- Quick open (qopen.cpp): archive-end copy of headers located via MHEXTRA_LOCATOR; `Archive::QOpen`, `ProhibitQOpen` — pure open/list performance, **optional**.
- Recovery record: RAR5 RR service header + recvol5/rs16 — used by repair command only, **optional**.

---

## 3. RAR3-path changes 3.7.3 → 7.2.7 (bug/robustness fixes a port must mirror)

Method dispatch at 7.2.7 (unpack.cpp `DoUnpack`): 15 (RAR1.5), 20+26 (RAR2, 26=">2GB" variant — both already at 3.7.3), 29 (RAR3), 50/70. **Method 36 ("alternative hash", introduced 3.6.1) was dropped in 5.0.0** — 3.6.x-beta archives with `-mt` experimental streams no longer decode (junrar baseline has it via case fallthrough; safe to drop too).

### 3.1 PPMd (model.cpp, suballoc.cpp; classes renamed PPM_* → RARPPM_*)

- **3.9.1** (`a0d5a16`): range-coder sanity guard `count>=(int)Model->Coder.SubRange.scale → return false` in decodeSymbol1/2 (corrupt-data OOB read fix).
- **3.9.8** (`48bbbd3`): `SafePPMDecodeChar()` wrapper introduced (now unpack30.cpp:4) — validates PPMd escape/reset handling for corrupt input at every non-hot call site.
- **5.6.1** (`9c55010`): `ps[MAX_O]` stack-overflow guards in `CreateSuccessors`/`decodeSymbol2` (`if (pps>=ps+ASIZE(ps)) return NULL/false`).
- Allocation failures now throw `std::bad_alloc` instead of silent NULL propagation (RestartModelRare).
- Full delta: `git diff 2e71167 d861246 -- model.cpp suballoc.cpp` (526 lines; scratchpad `d-ppmd.diff`).

### 3.2 RarVM — the cliff (5.5.1 `1522ee0`)

- 3.7.3: full VM interpreter, 1107 lines + rarvmtbl.cpp opcode table; standard filters `VMSF_E8, E8E9, ITANIUM, RGB, AUDIO, DELTA, UPCASE` executed natively when recognized, arbitrary programs interpreted otherwise.
- **5.5.1**: rarvm.cpp 1128 → **351 lines**; rarvmtbl.cpp deleted. `Prepare()` now only fingerprints the byte-code (XOR checksum + {length, CRC32} against a 6-entry table: E8=53/0xad576887, E8E9=57/0x3cd7e57e, ITANIUM=120/0x3769893f, DELTA=29/0x0e06077d, RGB=149/0x1c2c5dc8, AUDIO=216/0xbc85e701). Anything else → `VMSF_NONE` = filter is a **no-op** (block emitted unfiltered). **VMSF_UPCASE dropped** (was in 3.7.3 rarvm.hpp:37). Raw VM execution, VM opcodes, and custom filter programs are gone for good.
- Consequence for junrar: the RAR3 filter surface at parity = recognize the 6 canonical programs by hash and hardcode their transforms; no VM needed. (unpack30.cpp keeps reading VM code bytes from the stream — parsing survives, interpretation doesn't.)

### 3.3 Legacy LZ decoders (unpack15/20/30)

- **4.0.1** (`c8da0d3`): decode-speed rewrite — `DecodeTable` with `QuickBits/QuickLen/QuickNum` direct-lookup tables (unpack.hpp) replacing the 3.x DecodeLen/DecodePos-only walk; structural, behavior-neutral.
- **5.0.0**: split into unpack15/20/30/50 + unpackinline; shared `CopyString`, `OldDist` handling unified (`OldDistPtr & 3` wrap "needed if RAR 1.5 file is called after RAR 2.0", unpack20.cpp).
- **5.3.1** (`34b8c34`): `MAX3_UNPACK_FILTERS 8192` cap (corrupt archives creating unbounded filter lists).
- **5.3.6** (`dd09d7d`): unpack15 `HuffDecode` index guard `if (FlagsPlace>=sizeof(ChSetC)/sizeof(ChSetC[0])) return` (OOB write via corrupt Huffman state).
- **5.5.4** (`78c231f`): `MAX3_UNPACK_CHANNELS 1024` — RAR3 delta-filter channel cap (corrupt-archive CPU DoS).
- **7.0.3** (`5faaa45`): `FirstWinDone` distance validation in all legacy paths (§2.8); unpack15 got the largest rework (107 changed lines in 7.0.x).
- Buffer-boundary rechecks tightened throughout (`Inp.InAddr>ReadTop-30 && !UnpReadBuf()` patterns; scratchpad `d-unpack15.diff`, `d-unpack20.diff`).

### 3.4 RAR3 crypto (crypt.cpp → crypt3.cpp)

- Algorithm unchanged (SHA-1-based KDF, 0x40000 rounds, AES-128-CBC, 8-byte salt `SIZE_SALT30`).
- **5.2.1**: `KDF3Cache` (4 entries) — avoids re-deriving on every file/volume; pure perf, worth porting for multi-file encrypted archives.
- Password limits: `MAXPASSWORD` 128 @3.7.3 → 512 general + `MAXPASSWORD_RAR 128` @7.2.7 (rardefs.hpp) — RAR-format effective limit still 127 chars.
- Encrypted-header path (`-hp`) hardening in ReadHeader15: decryption only engaged past SFX offset, CRC check with `BrokenHeaderMsg()` on mismatch (arcread.cpp:144–190).

### 3.5 RAR3 header parsing / unicode names / timestamps

- `ReadHeader15` (arcread.cpp:144): name size clamped `Min(NameSize,MAXPATHSIZE=0x10000)`; unknown pack-version → crypt-method mapping table (13/15/26/default→RAR13/15/20/30); `INT32TO64(HighPackSize,DataSize)` for LHD_LARGE (large-file support **already at 3.7.3** — LHD_LARGE/HighPackSize present in `2e71167:headers.hpp:57,179`; method 26 in DoUnpack).
- **encname.cpp** (RAR3 encoded-unicode names): fully bounds-checked rewrite — every `EncName[EncPos++]` access preceded by `if (EncPos>=EncSize) break`, output to growable `std::wstring` (diff at scratchpad; was a classic OOB-read surface). junrar's port of `EncodeFileName.Decode` should mirror each check.
- Timestamps: RAR3 dostime+EXTTIME parse structurally unchanged; RAR5 uses Unix time / Windows FILETIME + HTIME extras; nanosecond Unix precision from 5.5.1 (§2.5).
- Comment extraction (arccmt.cpp) still routes 3.x comments through the old unpack — unchanged semantics.

### 3.6 Symlink/path safety during extraction (both formats — security parity, strongly recommended)

- **5.2.5** (`ed38a83`): `IsRelativeSymlinkSafe` (extinfo.cpp:110) — rejects symlink targets whose up-level depth escapes the extraction root (`CalcAllowedDepth`, `LinkInPath`).
- **6.1.7** (`22b5243`): `SafeCharToWide` + stricter Unix symlink target handling in ulinks.cpp (`UnixSymlink` validation).
- **6.2.3** (`2ecab6b`): `LinksToDirs` (extract.cpp) — refuses to write through a previously extracted symlink that points at a directory (`LastCheckedSymlink` cache, reset after each link creation, extract.cpp:879).
- `ConvertSymlinkPaths` plumbing @7.2.7 extract.cpp:654–672,1414,1495 ties it together. junrar already has some traversal protection; parity target = these three layers.

---

## 4. Architecture reshapes relevant to a port

1. **Unpack class**: 3.7.3 = unpack.cpp (all formats) + unpack15.cpp + unpack20.cpp + rarvm. 5.0.0 = unpack.cpp becomes thin dispatcher `#include`-ing unpack15/20/30/50/inline(/50mt/50frag); one `Unpack` class with per-format method sets and a single shared window. RAR7 did NOT add unpack70 — method 70 rides `Unpack5(ExtraDist=true)`.
2. **Archive reading**: one `Archive` class, format tag `RARFORMAT {RARFMT14, RARFMT15, RARFMT50, RARFMT_FUTURE}`; arcread.cpp holds `ReadHeader14/15/50` + shared `ProcessExtra50`. No arcread5 file — the split is per-function, not per-file. FileHeader is unified across formats (headers.hpp `FileHeader` with `HSYS_*`, `FileHash`, `RedirType`, `WinSize` fields) — junrar will likewise want one normalized header model with RAR3/RAR5 loaders.
3. **Hashing**: raw `uint FileCRC` → `HashValue`/`DataHash` abstraction (CRC32 | Blake2sp) at 5.0.0 — mirrors cleanly to a Java interface.
4. **Crypto**: one `CryptData` with `CRYPT_METHOD {RAR13,RAR15,RAR20,RAR30,RAR50}` enum and per-method files crypt1/2/3/5 at 5.0.0.
5. **ErrHandler**: grew per-error setters + `RARX_*` exit codes; extraction-relevant additions are `ERAR_LARGE_DICT` (7.0.1, DLL) and broken-header vs bad-password distinction on RAR5 (pswcheck makes wrong-password a first-class error instead of CRC failure).
6. **Extraction pipeline**: extract.cpp `CmdExtract` (DoExtract → ExtractArchive → ExtractCurrentFile) is CLI-adjacent but contains the portable ordering logic: volume analysis (6.2.1 AnalyzeArchive), dict-limit gate (CheckWinLimit), name preparation, link handling, time/attr restore. Mark as reference, port selectively.
7. **Threading** (5.0.0 threadpool; unpack50mt, MT-CRC32, MT-Blake2): all optional — single-threaded paths are complete and canonical. For junrar: skip initially; Blake2sp itself is inherently 8-lane parallel-*capable* but runs fine sequentially.
8. **Strings/containers**: 6.x–7.1 migrated char*/Array\<T\> → std::wstring/std::vector (unicode.cpp 952-line churn; array.hpp deleted 7.1.1). Semantically irrelevant for Java; makes *textual* diffs across 7.0/7.1 noisy — compare semantics, not lines.

---

## 5. Milestone recommendation for junrar

Cliff edges in the C++ history: **5.0.0** (file split + RAR5 — diffs across it are near-useless), **5.5.1** (RarVM gutted), **7.0.1** (RAR7 format), **7.1.1** (std containers churn, no format change).

Straight 3.7.3→7.2.7 jump is NOT advisable for the RAR3 path (too many interleaved guards), but a full version-by-version walk is waste. Recommended sync points:

1. **M1 — RAR3 hardening to 7.2.7 semantics (no format work).** Port the pinned guards directly from 7.2.7 blobs of unpack15/20/30, model.cpp, encname.cpp (they're small and enumerated in §3): 3.9.1 + 3.9.8 + 5.6.1 PPMd guards, 5.3.1/5.3.6/5.5.4 limits, 7.0.3 FirstWinDone distance checks, encname bounds. Compare against unrar **4.2.4** (`5db30e6`) when 3.7.3-shaped context is needed — last release with the old file layout.
2. **M2 — RarVM replacement (unrar 5.5.1 shape).** Replace junrar's VM with fingerprint-recognition of the 6 standard filters + hardcoded transforms; drop UPCASE and raw programs. Small, self-contained, big maintenance win.
3. **M3 — RAR5 core (target unrar 6.2.12 `8f437ab`).** Everything in §2 minus RAR7: headers5 vint parse + extras, AES-256/PBKDF2 + pswcheck, Blake2sp, Unpack5 single-threaded (DCB=64), delta/E8/E8E9/ARM filters, `.partN.rar` volumes, REDIR link records with §3.6 safety, UTF-8 names, HTIME. 6.2.12 is the last pre-RAR7 release and a clean, heavily-field-tested reference.
4. **M4 — RAR7 delta (6.2.12→7.2.7, small).** ExtraDist/DCX=80 tables, 5+5 dict bits with fractions, 64 GB cap + configurable dict limit (CheckWinLimit analog), FCI_RAR5_COMPAT, FirstWinDone already done in M1. The decoder change is localized to table sizes + distance decode.
5. **Skip/optional at every stage:** unpack50mt + threadpool, qopen, recvol3/recvol5/rs16 (recovery use — optional), largepage, motw, blake2s_sse, secpassword (Java GC makes byte-exact scrubbing moot; use char[] hygiene), fragmented window (JVM arrays make it moot below 2 GB; note Java arrays cap at 2^31 bytes — >2 GB dictionaries need a segmented window abstraction ANYWAY, which is junrar's own design problem, not a ported one).

Rationale: M1/M2 de-risk the code junrar already has with finite, evidence-pinned patches; M3 is the single big lift and lands on the most stable reference point; M4 is a bounded format increment. Reading order for M3: headers5.hpp → arcread.cpp:555–1257 → crypt5.cpp → unpack50.cpp → ulinks.cpp/extinfo.cpp.

---

## Appendix: pinned evidence table

| Fact | Pin |
| --- | --- |
| unpack.cpp 1187→305 lines; unpack15/20/30/50/inline/mt files appear | 5.0.0 `a224d33` |
| rarvm.cpp 1128→351, rarvmtbl.cpp deleted, UPCASE gone | 5.5.1 `1522ee0` |
| int64.cpp/hpp deleted (native 64-bit) | 3.9.1 `a0d5a16` |
| PPMd `count>=scale` guard | 3.9.1 `a0d5a16` |
| SafePPMDecodeChar | 3.9.8 `48bbbd3` |
| DecodeTable quick-decode rewrite | 4.0.1 `c8da0d3` |
| secpassword.cpp | 4.2.2 `c3ff881` |
| Method 36 dropped | 5.0.0 (present 3.6.1–4.2.4) |
| unpack50frag.cpp | 5.0.4 `143e317` |
| KDF3/KDF5 caches | 5.2.1 `f0474dc` |
| IsRelativeSymlinkSafe | 5.2.5 `ed38a83` |
| MAX3_UNPACK_FILTERS | 5.3.1 `34b8c34` |
| unpack15 FlagsPlace guard | 5.3.6 `dd09d7d` |
| FHEXTRA_HTIME_UNIX_NS; rawint.hpp | 5.5.1 `1522ee0` |
| MAX3_UNPACK_CHANNELS | 5.5.4 `78c231f` |
| PPMd ps[MAX_O] guards | 5.6.1 `9c55010` |
| cmdfilter/cmdmix split | 5.7.2 `3a48041` |
| ulinks SafeCharToWide | 6.1.7 `22b5243` |
| MHEXTRA_METADATA; AnalyzeArchive | 6.2.1 `635f619` |
| LinksToDirs | 6.2.3 `2ecab6b` |
| VER_PACK7, ExtraDist, FCI_DICT_FRACT*, CheckWinLimit | 7.0.1 `d52ee2f` |
| FirstWinDone guards | 7.0.3 `5faaa45` |
| array.hpp deleted; largepage; motw; MAXSFXSIZE→4MB | 7.1.1 `a2e5484` |

Working diffs saved: `d-unpack15.diff`, `d-unpack20.diff`, `d-ppmd.diff` (same scratchpad dir).
