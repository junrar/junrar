# RAR7 compression-info fixture (M4.1, issue #33)

One genuine RAR7 archive for `ArchiveRar7HeaderTest`, covering the algorithm-version-1
routing fact and the 5-dict-bit + 5-fraction-bit dictionary decode. Produced with
`rar 7.23`; the `unrar 7.23` listing below is the oracle recorded at generation time.
No `rar`/`unrar` binary or upstream source is committed — only the archive and this recipe.

## `rar7-md6g.rar`

```
dd if=/dev/zero bs=1m count=4352 | rar a -m1 -md6g -sizeros.bin -inul rar7-md6g.rar
```

`unrar 7.23 lt` oracle:

| Field | Value |
| --- | --- |
| Name | `zeros.bin` |
| Size | 4563402752 |
| Packed size | 183701 |
| CRC32 | `8E9B8BF4` |
| Compression | `RAR 5.0(v70) -m1 -md=6g` |

The header really carries `compInfo = 0x83c81` → algorithm version 1 (`VER_PACK7`),
method 1, `dictBits = 15`, `fraction = 16`, so the dictionary decodes as
`0x20000 << 15 = 4 GB`, `+ 4 GB/32*16 = 6 GB`. Decoding the same word with the RAR5
4-bit mask yields 4 GB instead, so this fixture discriminates the two formulas.

### Why the payload is 4.25 GB

`rar` silently reduces the recorded dictionary to what the payload can realize (plan
§4.2, review F2): a 2 MB payload with `-md6g` records `-md=4m` and algorithm version
**0**. Algorithm version 1 is only written once the *recorded* dictionary exceeds the
4 GB that RAR5's 4 dict bits can encode, which needs a payload above 4 GB. Zeros
compress to a 180 KB archive, so the fixture stays committable. Probes executed
2026-07-21 with rar 7.23:

| Payload | Switch | Recorded |
| --- | --- | --- |
| 2 MB random | `-md6g` | `algo=0`, `-md=4m` |
| 2 MB random | `-md128k`/`-md1m`/`-md4m` | `algo=0`, dict as requested |
| 4.25 GB zeros | `-md6g` | `algo=1`, `dictBits=15`, `frac=16` (6 GB) |

`rar 7.23` also refuses fractional dictionary switches below the RAR7 regime
(`-md384k`, `-md768k`, `-md192m`, `-md1536m`, `-md3g` → *"Unknown option"*), while
`-md6g`, `-md12g`, `-md64g` are accepted — fractions exist only above 4 GB.

## Encodings patched in-test

Every other row of the parse matrix (128 KB … 64 GB, the 64 GB+1 refusal, an unknown
algorithm version, and `FCI_RAR5_COMPAT`) is patched over *this* valid header at test
runtime and never committed — plan §4.3's sanctioned "inflated-resource header over a
valid stream" class, the same recipe as `links/patch_hostile_targets.py` and
`rar5unpack/m3-1gb-claim.rar`. `ArchiveRar7HeaderTest#patched` overwrites the
compression-info vint and recomputes the block header CRC32.

The patch is length-preserving by construction: RAR5 vints are little-endian 7-bit
groups with a continuation bit and are **not** required to be minimal (`unrar
RawRead::GetV`, junrar `VInt` both just accumulate groups). This fixture's word needs
3 bytes, which is exactly the width of the widest word M4.1 parses (`FCI_RAR5_COMPAT`
is bit 20), so any matrix value re-encodes into the same 3 bytes and no header size
ever shifts.

`FCI_RAR5_COMPAT` is patched rather than produced because `rar` only emits it when
appending RAR7 files to a RAR5 solid stream whose dictionary is smaller — which needs
two >4 GB payloads. A probe appending a `-md1m` entry to a `-ma5 -md128k` solid archive
(executed 2026-07-21) just rewrote both entries at `algo=0`.

## Extraction

**This is now the archive-level RAR7 extraction oracle** (M4.3, issue #35). The segmented window
raised the engine capability to 64 GB, so the fixture extracts once the caller opts past the
4 GiB `maxDictionarySize` default. `ArchiveRar7BigDictTest` does exactly that and meters the
payload through a digest sink; executed 2026-07-21 it produced 4,563,402,752 bytes with CRC32
`8E9B8BF4` — the `unrar 7.23` values recorded above — in 18.9 s at `-Xmx6g`.

It is **never a PR gate**: it really unpacks 4.25 GB. Tagged `bigdict`, excluded from `test`,
run with `./gradlew bigDictTest`.

The reason it took until M4.3 still stands and is the reason no *cheaper* RAR7 extraction fixture
exists:

The M4.1 finding above generalizes: RAR5's four dict bits encode *every* dictionary `rar`
records at or below 4 GB, because below that regime only powers of two are accepted. So
`algo=1` is written **iff** the recorded dictionary exceeds 4 GB, and since `rar` reduces the
recorded dictionary to the payload size, every genuine RAR7 stream needs a >4 GB payload and
declares a >4 GB window. That is past the 1 GB engine capability, past the 4 GiB default
`maxDictionarySize`, and past what a flat Java `byte[]` can address at all.

Probes executed 2026-07-21 with rar 7.23, closing the remaining escape hatches:

| Probe | Result |
| --- | --- |
| `-md` accepted set | powers of two `128k`…`4g`, then `5g, 6g, 8g, 12g, 16g, 24g, 32g, 48g, 64g, 96g, 128g` — fractions exist only above 4 GB |
| `-mdx384k`, `-mdx768k`, `-mdx192m`, `-mdx1536m`, `-mdx3g` | *"Unknown option"*, same as the plain `-md` forms |
| `-mdx128k`/`-mdx1m`/`-mdx4m`/`-mdx6g`/`-mdx64g`, 2 MB payload | all record `algo=0`, `-md=4m` (the `-m3` default) — the `md[x]` form does not defeat the reduction; `-md1m` on the same payload records 1 MB, so `-md` is honoured and `-mdx` is not |
| `-ma6`, `-ma7` | *"Unknown option"* — no format-forcing switch |
| `-mc1g`, `-mcl1g`, `-mclong`, `-mcx`, `-mc+longrange` | accepted, no effect on the recorded word (`algo=0`, `-md=4m`) |
| append onto `rar7-md6g.rar` (`-s -md1m`, `-md1m`, `-s`) | the new entry is written at `algo=0` every time — still no `FCI_RAR5_COMPAT` |

`unrar 7.23` agrees the fixture is out of reach by default:

```
$ unrar t -qo- rar7-md6g.rar "*"
zeros.bin
6 GB dictionary exceeds 4 GB limit and needs more than 6 GB memory to unpack.
Use -md6g or -mdx6g switches to extract anyway.
```

Forced through with `-md6g` it tests `All OK` in 7.3 s at 3.0 GiB peak RSS — the declared
dictionary *is* the window (`d861246:unpack.cpp:110`, `MaxWinSize=(size_t)WinSize`, with no
clamp to the unpacked size).

M4.2 therefore drives the `ExtraDist` decode from crafted engine-level streams
(`Unpack5ExtraDistTest`) and pins version-70 routing through the dictionary gate
(`ArchiveRar7ExtractionTest`). The archive-level oracle-identical row moved to M4.3 and is
satisfied by this fixture, out of PR CI, as described above.
