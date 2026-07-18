# PPM `ps`-array fixture provenance

These RAR files are deterministic one-byte patches of
`legit-maxmb63.rar` (source SHA-256:
`46d5f3196f150e9f384b657208e2ae9a45934c1bfbd95ecd507cb06ef18a2325`).
They exercise the defensive 5.6.1 `ps` bounds checks described in
`docs/porting/PARITY_PLAN.md` §4.3.

| Fixture | Patch | Site and rationale | Bounded probe maxima (`create/collect/count/mask`) | Public outcome and classification | Fixture SHA-256 |
| --- | --- | --- | --- | --- | --- |
| `hostile-ppm-ps-collect.rar` | Offset 254: `0x5e` → `0xff` | `decodeSymbol2` collection write; reaches `collect` 255, closest observed collection-write boundary. | `60/255/248/43` | All four public surfaces: typed `CrcErrorException`; no honest RED, defensive. | `24a680b3b87f2a4f940120b31ad19d503d34fd30a56ce5415d328008fcaacf5b` |
| `hostile-ppm-ps-count.rar` | Offset 85: `0x03` → `0x00` | `decodeSymbol2` count-selection increment/access; reaches `count` 253, closest observed count-selection boundary. | `58/255/253/48` | All four public surfaces: typed `CrcErrorException`; no honest RED, defensive. | `7609247ef7f9c719d25445bdb7570c063bf6046ae8bdc3800cdf4f4a914c9400` |
| `hostile-ppm-ps-mask.rar` | Offset 199: `0x8d` → `0x00` | `decodeSymbol2` mask-loop increment/access; reaches `mask` 252, closest observed mask-loop boundary. | `48/255/253/252` | All four public surfaces: typed `CrcErrorException`; no honest RED, defensive. | `77f7a23d893bbdd074071ab72552fb36124d1aebbcbc4d879ecd6cf85c4f6953` |

The bounded search covered offsets 71–326 with values `0x00` and `0xff`:
512 attempts, 510 byte changes. Each of Junrar File, Junrar InputStream,
Archive File + `extractFile`, and Archive InputStream + `extractFile` completed
within 5 seconds; no unchecked OOB/OOM/hang occurred.

Regenerate the patched fixtures from the committed source with:

```sh
python3 generate_maxmb_fixtures.py
```
