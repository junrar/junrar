# junrar ← unrar C++ translation baseline pin

Date: 2026-07-17. Repos: `~/git/junrar` (master, HEAD region 2026-06) read against
`~/git/unrar` mirror (one commit per release, 3.1.0→7.2.7).

## Verdict

**Bulk translation baseline: unrar 3.7.x — pinned to the 3.7.3–3.7.6 source window,
most plausibly 3.7.3.** The translated core is byte-for-byte on the 3.7.3 side of every
discriminating release boundary, and the 3.7.3–3.7.6 releases are *identical* in every
file junrar translates (only CLI-side files — `extract.cpp`, `pathfn.cpp`, `cmddata.cpp`,
`rar.cpp`, `system.cpp`, `volume.cpp` — plus one comment in `headers.hpp` changed in that
span), so finer resolution is impossible from code. The Java file headers carry
`Creation date: 31.05.2007`, which matches the unrar 3.7.3-current era (WinRAR 3.70
final: 2007-05-22 — ASSUMED, date from memory, not probed).

**The 3.9.x belief is refuted**: junrar is missing every 3.7.7+ marker and every 3.9.x
marker in the bulk code (details below). The user-supplied commit map is nonetheless
correct: `a0d5a16` = "Updated to 3.9.1", `ef34ae4` = "Updated to 3.9.10" (verified via
`git -C ~/git/unrar log --format='%h %s' --reverse master`).

**No per-subsystem split in the bulk port.** Every subsystem (unpack29/20/15, PPM
model+suballoc, RarVM, headers/rarfile) sits inside the same 3.6.2 ≤ … ≤ 3.7.6 envelope,
jointly pinned to 3.7.3–3.7.6 by the unpack/vm markers. The only newer-unrar material is
isolated, dated cherry-picks (see "Post-baseline deltas").

Confidence: **high** for "3.7.3 ≤ baseline ≤ 3.7.6"; **medium** for exactly 3.7.3
(date inference only — code cannot distinguish 3.7.3/4/5/6).

## Lower-bound markers (present in junrar ⇒ baseline ≥ version)

| # | Marker (introduced in) | unrar evidence | junrar counterpart | Verdict |
|---|---|---|---|---|
| 1 | `MHD_ENCRYPTVER` main-header flag (**3.6.1**) | `git show 3edd2c6:headers.hpp` grep-count 0 (3.5.4) vs `30566e3` count 1 (3.6.1) | `rarfile/MainHeader.java:38` `private byte encryptVersion;` | ≥3.6.1 |
| 2 | PPM `U.Stats <= pText \|\| > HeapEnd` guard in `DecodeChar` (**3.6.2**) | `git diff 3edd2c6 24e7ee2 -- model.cpp` (+2 guard lines) | `unpack/ppm/ModelPPM.java:234-235` | ≥3.6.2 |
| 3 | `SubAllocator::MBPtr(BasePtr,Items)` helper (**3.6.2**) | same diff, suballoc.cpp `+inline RAR_MEM_BLK* SubAllocator::MBPtr` | `unpack/ppm/SubAllocator.java:98` `private int MBPtr(int BasePtr, int Items)` | ≥3.6.2 |
| 4 | `UnpackFilter::ParentFilter` + parent-prg GlobalData copy in `UnpWriteBuf` (**3.7.1**) | `git diff dbab3e6 0e4fb8b -- unpack.cpp unpack.hpp` (`+unsigned int ParentFilter;`) | `unpack/UnpackFilter.java:40`; `Unpack.java:391,455,851,856` | ≥3.7.1 |
| 5 | `FiltPos>1024` corrupt-archive cap in `AddVMCode` (**3.7.1**) | same diff | `Unpack.java:844` `if (FiltPos > 1024)` | ≥3.7.1 |
| 6 | `Inp.Overflow(3)` checks in `AddVMCode` (**3.7.1**) | same diff (3 sites) | `Unpack.java:909,960,976`; `vm/BitInput.java:92 Overflow()` | ≥3.7.1 |
| 7 | `GetShortLen1/2` macros in unpack15 `ShortLZ` (**3.7.1**) | same boundary, unpack15.cpp `+#define GetShortLen1(pos)` | `Unpack15.java:238-243` `getShortLen1/getShortLen2` | ≥3.7.1 |
| 8 | `PosR<0` guard in VMSF_AUDIO/DELTA (**3.7.1**) | same boundary, rarvm.cpp `+... \|\| PosR<0` | `vm/RarVM.java:1023` `dataSize >= VM_GLOBALMEMADDR / 2 \|\| posR < 0` | ≥3.7.1 |
| 9 | `RarVM::Execute` DataSize clamp `VM_GLOBALMEMSIZE-VM_FIXEDGLOBALSIZE` (**3.7.1**) | same boundary | `vm/RarVM.java:192` | ≥3.7.1 |
| 10 | **`SetValue(uint*,uint)` → `SetLowEndianValue` rename (3.7.3)** | `git diff 2133f84 2e71167 -- rarvm.hpp unpack.cpp` | `vm/RarVM.java:120,127 setLowEndianValue`; `Unpack.java:940-952` (comments literally quote `VM.SetLowEndianValue(...)`) | **≥3.7.3** |

## Upper-bound markers (absent from junrar ⇒ baseline < version)

| # | Marker (introduced in) | unrar evidence | junrar state | Verdict |
|---|---|---|---|---|
| 11 | **`PPMError` field removed; `PPM.CleanUp()+UnpBlockType=BLOCK_LZ` on `Ch==-1` (3.7.7)** | `git diff 02aecf1 37cc750 -- unpack.cpp unpack.hpp model.cpp` | `Unpack.java:74 private boolean ppmError;` + `:168 if (ppmError) return;` + `:197 ppmError = true;` — the exact 3.7.1–3.7.6 shape; `ModelPPM.java` has **no** CleanUp (the `Unpack.java:1047 cleanUp()` is junrar's own memory-release helper, body = stopSubAllocator only) | **≤3.7.6** |
| 12 | `ModelPPM::CleanUp()` (**3.7.7**) | same diff, model.cpp/hpp | absent (grep `cleanUp` in ppm/ = 0 hits) | ≤3.7.6 |
| 13 | `DataSize<4` / `DataSize<21` guards in VMSF_E8/E8E9/ITANIUM (**3.7.7**) | same diff, rarvm.cpp | `RarVM.java` VMSF_E8/E8E9 case: `if (dataSize >= VM_GLOBALMEMADDR)` only — no `< 4`/`< 21` | ≤3.7.6 |
| 14 | `UnpInitData` memsets of `LD/DD/LDD/RD/BD` + `UnpBlockType=BLOCK_LZ` (**3.7.7**) | same diff, unpack.cpp | `Unpack.java:604-627 unpInitData` — none of these | ≤3.7.6 |
| 15 | `UnpAudioBlock=` added to `UnpInitData20` reset + `memset(MD,...)` (**3.7.7**) | same diff, unpack20.cpp | `Unpack20.java unpInitData20`: `UnpChannelDelta = UnpCurChannel = 0;` (old form). The `MD[i]=new MultDecode()` + `// memset(MD,0,sizeof(MD))` lines are a **2022 delta** (commit `952436b2`), absent from the 2010 initial import (`git show 6f32323c:...Unpack20.java`) | ≤3.7.6 |
| 16 | `SafePPMDecodeChar()` + `NextCh==2`/`-1` split (**3.9.2–3.9.10 series**) | `git diff a0d5a16 ef34ae4 -- unpack.cpp` | grep `safePPM` in junrar = 0 hits; `Unpack.java:207 if (NextCh == 2 \|\| NextCh == -1) break;` = pre-3.9.x shape | ≤3.9.1-era (subsumed by ≤3.7.6) |
| 17 | SubAllocator `AllocSize=...+2*UNIT_SIZE` rework (**3.9.x series**) | `git diff a0d5a16 ef34ae4 -- suballoc.cpp` | `SubAllocator.java:137 int allocSize = t / FIXED_UNIT_SIZE * UNIT_SIZE + UNIT_SIZE;` = old form | pre-3.9.x rework |

## Non-discriminating checks (consistency, no contradiction)

- **VMSF signature table** `{53,0xad576887,E8} … {40,0x46b9c560,UPCASE}`: identical in
  unrar 3.4.1→3.9.10 (and `VMSF_UPCASE` still present at 4.2.4); junrar
  `RarVM.IsStandardFilter` matches exactly, same order. Confirms fidelity, pins nothing.
- **headers.hpp 3.7.3→3.9.10**: comment-only when filtered
  (`git diff 2e71167 ef34ae4 -- headers.hpp | grep -vE '^[+-]\s*(//|/\*|\*)'` → empty),
  so `rarfile/*` cannot discriminate 3.7 vs 3.9 — headers evidence rests on marker 1.
- **3.7.8→3.8.5 changes** to translated files are C-only (size_t widening, MSC pragmas,
  comments, Windows-only `':'→'_'` rename in `ConvertUnknownHeader`) — not translatable,
  so 3.8.x can be neither proven nor excluded from those files alone; excluded instead by
  markers 11–15 (a 3.8 base would have contained the 3.7.7 changes).
- **crypt/**: `crypt/Rijndael.java` is a JCE (`javax.crypto`) reimplementation, not a
  `crypt.cpp` translation — carries no version pin.
- **FileNameDecoder.java** (author alpha_lam) = `unicode.cpp DecodeFileName`, stable
  across 3.x — no pin.
- `Unpack.java:949-950` zeroes GlobalData `0x24/0x28` where C++ (any 3.x) does not — a
  translator-local initialization, not a version signal.

## Post-baseline deltas spotted (do not move the baseline)

From `git -C ~/git/junrar log` (dates short):

- `d5cc784c` 2022-03-20 **feat: parse extended time** — `FileHeader.java` +240 lines
  porting `arcread.cpp ReadTimes` behaviour wholesale (function existed since 3.x but was
  never in the 2010 import).
- `1d52d5d3` 2022-04-28 **fix: out of bounds read on corrupt extended time** — commit body:
  "UnRAR never reads more bytes than available in the header. This ports this behavior"
  — the newer-unrar hardening the task brief anticipated.
- `952436b2` 2022-03-10 + `15f4afa2` 2022-03-07 — audio-decompression NPE/CRC fixes; this
  is where the 3.7.7-flavoured `// memset(MD,0,sizeof(MD))` init entered `Unpack20.java`
  (Java-necessity object init, not a resync).
- `7b16b3d9` 2022-01-27 — invalid subheader type NPE/infinite-extract-loop fix.
- `b1f96385` 2020-07-21 — inaccurate file times fix.
- `5270d235` 2026-04-26 — `RarCRC.checkCrc` → `java.util.zip.CRC32` (drops translated crc.cpp path).
- `9b69c6b7` 2026-04-10 — AIOOBE in solid RAR v20 extraction.
- `ad7ad33b` 2026-02-28 — seek past SubHeader packed data.
- `947ff1d3`/`d77e9a83` 2026 — extraction path-traversal handling.
- `e0874d21` 2026-05-13 — random access for solid RAR4 (new feature, no C++ counterpart).

Also historical: `6f42cef1` 2011-09-23 removed the javolution dependency;
volume handling (`Volume/VolumeManager`) was redesigned from scratch in 2011
(`90d12884`) — never a translation of `volume.cpp`.

## Commands used (reproducible)

```sh
git -C ~/git/unrar log --format='%h %s' --reverse master        # release→commit map (pipe to file; wrapper truncates)
git -C ~/git/unrar diff --stat <c1> <c2> -- unpack.cpp unpack15.cpp unpack20.cpp \
    model.cpp suballoc.cpp rarvm.cpp headers.hpp arcread.cpp crypt.cpp crc.cpp unicode.cpp
git -C ~/git/unrar diff dbab3e6 0e4fb8b -- unpack.cpp unpack.hpp      # 3.6.8→3.7.1 markers
git -C ~/git/unrar diff 2133f84 2e71167 -- rarvm.cpp rarvm.hpp        # 3.7.2→3.7.3 (SetLowEndianValue)
git -C ~/git/unrar diff 02aecf1 37cc750 -- model.cpp unpack.cpp unpack20.cpp rarvm.cpp  # 3.7.6→3.7.7
git -C ~/git/unrar diff a0d5a16 ef34ae4 -- unpack.cpp suballoc.cpp    # 3.9-series markers
git -C ~/git/unrar show <commit>:model.cpp                            # per-version function bodies
grep -rn '<marker>' ~/git/junrar/src/main/java                        # junrar counterparts
git -C ~/git/junrar show 6f32323c:<path>                              # 2010 initial-import state
git -C ~/git/junrar log --format='%h %ad %s' --date=short --follow -- '<file>'  # delta dating
```

Key junrar files: `src/main/java/com/github/junrar/unpack/Unpack.java`,
`Unpack15.java`, `Unpack20.java`, `unpack/ppm/ModelPPM.java`, `unpack/ppm/SubAllocator.java`,
`unpack/vm/RarVM.java`, `unpack/vm/BitInput.java`, `rarfile/MainHeader.java`,
`rarfile/FileNameDecoder.java`, `crypt/Rijndael.java`.
