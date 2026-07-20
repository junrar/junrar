# RAR5 decode fixtures (M3.7, issue #28)

Core RAR5 extraction matrix for the `Unpack5` decode engine, consumed by
`ArchiveRar5UnpackTest`. Produced with `rar 7.23` (`-ma5`); the expected payload
SHA-256s in the test are the `unrar 7.23` `p` oracle output. No `rar`/`unrar`
binary or upstream source is committed — only the small archives and this recipe.

## Payloads (deterministic)

- `small` — `"The quick brown fox. " * 400` (~8 KB), for the store row.
- `med` — 75 × 4 KB blocks drawn from a 16-block pool, seed 1 (300 KB > 128 KB
  dict, forces a window wrap).
- `big` — 10240 × 4 KB blocks from a 64-block pool, seed 2 (40 MB > 32 MB dict,
  exercises per-archive window sizing + multi-MB write metering).
- `s0..s4` — 10 × 4 KB blocks from an 8-block pool, seeds 100..104 (solid set).

## Archives

| File | Command |
| --- | --- |
| `m0-plain-128k.rar` | `rar a -ma5 -m0 -md128k -ep <a> small.bin` |
| `m3-plain-128k.rar` | `rar a -ma5 -m3 -md128k -ep <a> med.bin` |
| `m5-plain-128k.rar` | `rar a -ma5 -m5 -md128k -ep <a> med.bin` |
| `m3-plain-32m.rar`  | `rar a -ma5 -m3 -md32m  -ep <a> big.bin` |
| `m3-solid-128k.rar` | `rar a -ma5 -m3 -md128k -ep -s <a> s0..s4.bin` |
| `m5-solid-128k.rar` | `rar a -ma5 -m5 -md128k -ep -s <a> s0..s4.bin` |
| `m3-enc-p.rar`      | `rar a -ma5 -m3 -md128k -ep -pjunrar <a> med.bin` |
| `m3-enc-hp.rar`     | `rar a -ma5 -m3 -md128k -ep -hpjunrar <a> med.bin` |

## Byte-patched hostile fixture

`m3-1gb-claim.rar` — a `-m3 -md128k` archive of a ~3.2 KB payload whose file-header
compression-info dictionary bits were patched from 0 to 13 (a 1 GB dictionary
claim) and the header CRC32 recomputed. The decoder must still extract it under
DEFAULT options while its growth-capped window stays kilobyte-scale (review B-S3).
The two remaining hostile rows (corrupted data, truncated stream) are byte-patched
in-test from `m3-plain-128k.rar`.
