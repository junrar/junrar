# RAR5 multi-volume fixtures (M3.9, issue #30)

`.partN.rar` RAR5 volume sets for `ArchiveRar5VolumeTest`, produced with `rar 7.23`
(`-ma5`). The expected payload SHA-256s in the test are the `unrar 7.23` `p` oracle
output (verified byte-identical to the deterministic source payloads). No `rar`/`unrar`
binary or upstream source is committed — only the small archives, the payload
generator, and this recipe.

## Payloads (deterministic, `gen_volume_payloads.py`)

Fixed-seed 64-bit LCG (same constants as the M3.8 filter generator); regenerating
reproduces every byte.

- `note.txt` — 128 bytes of repeated ASCII; small file that is never split.
- `spanned.bin` — 250 000 incompressible bytes, seed 39101; must span all three
  100k volumes.
- `solid0.bin` … `solid5.bin` — six 60 000-byte incompressible members, seeds
  39200-39205; solid stream and file boundaries cross volume boundaries.
- `spanned2.bin` — 400 000 incompressible bytes, seed 39102; larger than the decode
  window's 256 KB min-alloc floor, so unpacked-data flushes interleave with the
  volume switches and a mid-file reset of the unpacked checksum accumulators is
  observable (the smaller sets flush once at the end and cannot see it).
- `stored2.bin` — 120 000 incompressible bytes, seed 39103; stored (`-m0`) so the
  packed stream carries no version-specific encoding at all (M4.2, issue #34).

## Archives

| Set | Command |
| --- | --- |
| `vols.partN.rar` (N=1..3)  | `rar a -ma5 -m3 -md128k -ep -v100k vols.rar note.txt spanned.bin` |
| `solid.partN.rar` (N=1..4) | `rar a -ma5 -m3 -md128k -ep -s -v100k solid.rar solid0.bin … solid5.bin` |
| `blake.partN.rar` (N=1..3) | `rar a -ma5 -m3 -md128k -ep -htb -v100k blake.rar note.txt spanned.bin` |
| `big.partN.rar` (N=1..4)   | `rar a -ma5 -m3 -md128k -ep -htb -v100k big.rar spanned2.bin` |
| `stored.partN.rar` (N=1..3)| `rar a -ma5 -m0 -ep -v50k -qo- stored.rar stored2.bin` |
| `nochecksum.partN.rar` (N=1..3) | the `stored` set, `FHFL_CRC32` stripped — see below |

`vols` pins CRC32 split-entry checksum semantics, `blake` pins the BLAKE2 pair
(per-volume packed digest on every `HFL_SPLITAFTER` part, end-to-end unpacked digest
on the final part — unrar `volume.cpp:19-26` / `extract.cpp:866` at 6.2.12 `8f437ab`),
`solid` pins solid-stream continuation across a volume switch, `nochecksum` pins the
third hash type unrar knows: `HASH_NONE`.

### `nochecksum.partN.rar` — the no-checksum split entry

A RAR5 file header may omit its checksum entirely: `FHFL_CRC32` clear, no CRC32 word,
no `FHEXTRA_HASH` record. unrar calls that `HASH_NONE`, treats it as *valid* (`HashValue::
operator==` returns true whenever either side is `HASH_NONE`, `8f437ab:hash.cpp:31-32`)
and prints `?` instead of `OK`. `Rar5NoChecksumVolumeTest` needs it on the split path,
where each `HFL_SPLITAFTER` part header would otherwise be checked against its packed
chunk.

`rar` cannot write such an archive — `-ht[b|c]` selects BLAKE2 or CRC32, never "none" —
so this set is the `stored` set with each volume's FILE header edited: `FHFL_CRC32`
cleared in place (its vint keeps its width), the 4-byte CRC word excised, the header-size
vint decremented by 4 at its original width, header CRC32 refixed. Payload bytes are
untouched; only the promise to check them is gone. Every volume is 4 bytes shorter than
its `stored` counterpart, which is the whole diff.

`unrar 7.20` probe (2026-07-25): `unrar x nochecksum.part1.rar` merges all three volumes,
prints `100%   ?`, reports `All OK` (exit 0), and writes 120 000 bytes byte-identical to
the `stored` set's own extraction — SHA-256
`16a25156ddff0139ef7eb37a3243b314b754f020e9450227915dee0bbb4a9e10`, the digest the test
pins. The single-file counterpart is `../../rar5-nochecksum.rar`.

## Hostile rows (synthesized at test runtime, nothing extra committed)

- *Missing part2*: the test copies `part1` + `part3` only; the volume switch must fail
  with `MissingNextVolumeException`.
- *Started mid-set*: the test opens `part2` alone; extracting its `HFL_SPLITBEFORE`
  continuation entry must fail with `MissingPreviousVolumeException` (unrar 7.23 probe
  2026-07-21: "You need to start extraction from a previous volume", nothing
  extracted, exit 6; the full-set silent rewind is `AnalyzeArchive` CLI convenience —
  recorded non-goal, `unrar-delta-map.md` §2.9).
- *Lying packed hash*: the test byte-patches the 4-byte little-endian Data CRC32 field
  in `part1`'s split file header and re-computes the header CRC32 (M3.7 patch recipe),
  so the merge-time packed-hash check must fail with `CrcErrorException` while the
  end-to-end unpacked checksum alone would still pass.

## Version-70 promotion of the stored set (M4.2, issue #34)

`ArchiveRar7ExtractionTest` promotes every `stored.partN.rar` header to algorithm
version 1 at test runtime — a length-preserving overwrite of the 2-byte compression-info
vint plus a header CRC32 refix — and asserts the set still extracts byte-identically.

This is plan §4.3 class 2, not an inflated-resource claim: a **stored** stream is
byte-identical under version 50 and version 70, so the promoted header describes the
bytes truthfully, and the oracle is the unpromoted set's own output. `unrar 7.23` agrees
(`unrar t -qo-` on the promoted set: `All OK`). It is the only way to reach the
volume-merge path with a version-70 entry, because every *compressed* RAR7 stream
declares a > 4 GB dictionary and is refused long before any packed byte is read
(see `rar7/README.md`). Nothing promoted is committed.
