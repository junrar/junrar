# RAR5 filter fixtures (M3.8, issue #29)

Per-filter RAR5 archives for the `Unpack5` filter sweep (DELTA / E8 / E8E9), consumed by
`ArchiveRar5FilterTest`. Produced with `rar 7.23` (`-ma5`); the expected payload SHA-256s
in the test are the `unrar 7.23` `p` oracle output (byte-identical to the source
payloads). No `rar`/`unrar` binary or upstream source is committed — only the small
archives, the payload generator, and this recipe.

## Payloads (deterministic, `generate_filter_payloads.py`)

All payloads come from a fixed-seed 64-bit LCG; regenerating the script reproduces them
byte-identically.

- `delta.bin` — 256 KB of interleaved 2-channel 16-bit LE slowly-varying waveform
  (WAV-like), seed 38001. rar picks the DELTA filter.
- `e8.bin` — 192 KB of x86-like code: short prologue fillers plus dense `E8` (call rel32)
  opcodes targeting a small address set, seed 38002. rar picks the E8 filter.
- `e8e9.bin` — same shape but mixing `E8` calls and `E9` jmps, seed 38003. rar picks the
  E8E9 filter.
- `arm.bin` (generator only, no archive) — ARM32 words with dense `BL` (`0xEB`)
  branches, seed 38004. rar 7.23 does not emit the ARM filter when compressing (off by
  default since 5.80b3, `-mca+` does not bring it back), so the fixture is unattainable;
  the ARM transform is unit-tested against synthetic filter blocks in
  `Unpack5FilterTest` instead.

## Archives

| File | Command |
| --- | --- |
| `rar5-delta.rar` | `rar a -ma5 -m3 -md128k -ep <a> delta.bin` |
| `rar5-e8.rar`    | `rar a -ma5 -m3 -md128k -ep <a> e8.bin` |
| `rar5-e8e9.rar`  | `rar a -ma5 -m3 -md128k -ep <a> e8e9.bin` |

Filter presence in each archive was proven by extracting with the pre-M3.8 engine, which
threw `CorruptHeaderException("RAR5 filters are not supported yet (slot 256)")` for all
three; `ArchiveRar5FilterTest` keeps asserting presence via the engine-side
filters-applied counter.

## Hostile rows

The filter hostile rows (flood > `MAX_UNPACK_FILTERS`, block > `MAX_FILTER_BLOCK_SIZE`)
live as crafted engine-level streams in `Unpack5FilterTest`, not as byte-patched
archives: unlike the M3.7 dictionary-claim patch (a byte-aligned header field), filter
announcements sit bit-packed inside the compressed stream, so a crafted stream is the
deterministic route.
