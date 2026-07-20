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

## Archives

| Set | Command |
| --- | --- |
| `vols.partN.rar` (N=1..3)  | `rar a -ma5 -m3 -md128k -ep -v100k vols.rar note.txt spanned.bin` |
| `solid.partN.rar` (N=1..4) | `rar a -ma5 -m3 -md128k -ep -s -v100k solid.rar solid0.bin … solid5.bin` |
| `blake.partN.rar` (N=1..3) | `rar a -ma5 -m3 -md128k -ep -htb -v100k blake.rar note.txt spanned.bin` |
| `big.partN.rar` (N=1..4)   | `rar a -ma5 -m3 -md128k -ep -htb -v100k big.rar spanned2.bin` |

`vols` pins CRC32 split-entry checksum semantics, `blake` pins the BLAKE2 pair
(per-volume packed digest on every `HFL_SPLITAFTER` part, end-to-end unpacked digest
on the final part — unrar `volume.cpp:19-26` / `extract.cpp:866` at 6.2.12 `8f437ab`),
`solid` pins solid-stream continuation across a volume switch.

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
