#!/usr/bin/env python3
"""Construct the T6 fixture (docs/porting/MIGRATION_MANUAL.md SS6 T6,
docs/porting/PARITY_PLAN.md P0.6): rar3-utf8-name.rar -- a RAR3 archive whose
FILE_HEAD name field takes the "whole-name-UTF-8" shape (unrar 3.7.3
arcread.cpp:189-194: `Length==hd->NameSize`, no NUL-split ANSI+encoded-wide
blob), which the CURRENT FileHeader.java mis-decodes: fileNameW is forced to
""  (FileHeader.java:158-165 pre-fix) and the visible name falls back to the
"ansi" String built with the platform-default charset instead of UTF-8.

=====================================================================
Step 1: rar3-utf8-name-split.rar (real rar 6.24 output, intermediate/base)
=====================================================================
Binary procurement (docs/porting/PARITY_PLAN.md SS4.2): rar 7.23 cannot
create RAR4-format archives ("rar a -ma4 ..." -> "ERROR: Unknown option:
ma4"), so this RAR3/RAR4-era fixture uses the era rar 6.24 macOS x64 tarball
at https://www.rarlab.com/rar/rarmacos-x64-624.tar.gz (probed 2026-07-17:
HTTP 200, application/x-gzip, 607,872 bytes, gzip magic 1f 8b).

  printf 'utf8-name-payload\n' > 'café-résumé-日本語.txt'
  rar a -ep1 -ma4 -m5 rar3-utf8-name-split.rar 'café-résumé-日本語.txt'

Probed 2026-07-17: on this macOS/Unix rar 6.24 build, ANY non-ASCII name
produces the NUL-split shape (ANSI-fallback bytes = the UTF-8 encoding of
the name, then a 0x00 terminator, then a redundant RLE-encoded copy of the
same name via the FileNameDecoder/EncodeFileName algorithm) -- i.e.
`length != nameSize` for every name this CLI was made to emit. The
whole-name-UTF-8 shape (`length == nameSize`, no split) is a real,
unrar-recognized wire encoding (arcread.cpp explicitly branches on it) that
this era CLI build does not appear to emit on its own; reaching it requires
deriving it from a real-rar-produced archive (below), per SS4.3 class 2
("inflated-resource headers over a valid stream" -- same recipe: patch a
header field of a valid small archive and refix the header CRC32; here the
patched field is the name-field layout, not dict-bits, and the compressed
DATA stream is untouched).

=====================================================================
Step 2: rar3-utf8-name.rar (byte-patched from step 1, SS4.3 class 2)
=====================================================================
The patch below reads the FILE_HEAD's name field from rar3-utf8-name-split.rar,
finds the embedded NUL that starts the redundant encoded-wide blob, and
SHRINKS the field down to just the bytes before that NUL (the UTF-8 name
itself) -- dropping the NUL and the entire encoded-wide blob. This flips the
field into the "no NUL within the first nameSize bytes" shape, i.e.
`length == nameSize` once nameSize is shrunk to match. nameSize and headSize
are updated, and the header CRC (RAR 1.5/GetCRC15, MIGRATION_MANUAL.md SS5.1
/SS6 T7: `~CRC32(header bytes from offset 2) & 0xffff`, `d861246:rawread.cpp`)
is refixed over the new (shorter) header extent. The compressed payload
bytes are never touched, so the file's CONTENT stays byte-identical --
only the metadata shape of the name field changes, to a shape unrar itself
already supports.

CRC formula note (verified empirically against the untouched step-1
fixture's own headCRC before trusting it on the patched header): unrar's
internal `CRC32(StartCRC, data, len)` helper (crc.cpp) omits the final XOR
that `zlib.crc32`/the "standard" CRC32 already performs; algebraically
`GetCRC15 = ~CRC32raw(...) & 0xffff = zlib.crc32(...) & 0xffff` (the two
complements cancel) -- i.e. plain `zlib.crc32(region) & 0xffff`, NOT
`~zlib.crc32(region) & 0xffff`. Confirmed: rar3-utf8-name-split.rar's own
stored headCRC (0x5790) equals `zlib.crc32(header[2:headSize]) & 0xffff`
computed this way.

Oracle validation (executed 2026-07-17, real unrar 7.23, independent of
junrar): pre-CRC-refix the patched archive was flagged "the file header is
corrupt" by `unrar lt`/`unrar x`; after the corrected CRC refix below,
`unrar lt rar3-utf8-name.rar` lists `café-résumé-日本語.txt` cleanly (no
corruption warning) and `unrar x` extracts it byte-identical to the
18-byte payload -- i.e. real unrar's OWN implementation of the
`Length==hd->NameSize` branch decodes this patched header exactly as
intended, which is the strongest available evidence the byte layout is a
genuine, correctly-formed variant of the format and not an invented one.

Run: python3 generate_utf8_name_fixture.py
Regenerates rar3-utf8-name.rar next to this script from the already-
committed rar3-utf8-name-split.rar (no external rar binary needed for this
step).
"""
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
SPLIT = DIR / "rar3-utf8-name-split.rar"
PATCHED = DIR / "rar3-utf8-name.rar"


def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def main():
    data = bytearray(SPLIT.read_bytes())

    # MARK header (7 bytes) -> MAIN_HEAD -> FILE_HEAD.
    pos = 7
    pos += u16(data, pos + 5)  # skip MAIN_HEAD via its own headSize
    base = pos
    if data[base + 2] != 0x74:
        raise SystemExit(f"expected FILE_HEAD (0x74) at offset {base + 2}, "
                          f"got 0x{data[base + 2]:02x} -- source fixture changed?")
    head_size = u16(data, base + 5)
    # BaseBlock(7) + packSize(4) precede the fileHeaderBuffer fields parsed
    # by FileHeader.java; nameSize sits at fileHeaderBuffer offset 15,
    # the name bytes start right after fileAttr at offset 21.
    fhb = base + 7 + 4
    name_size = u16(data, fhb + 15)
    name_off = fhb + 21
    name_field = bytes(data[name_off:name_off + name_size])
    if name_off + name_size != base + head_size:
        raise SystemExit("expected the name field to reach the header end "
                          "(no salt/exttime) -- source fixture changed?")
    nul_at = name_field.find(0)
    if nul_at == -1:
        raise SystemExit("source fixture is already in the whole-UTF-8 shape "
                          "(no embedded NUL) -- nothing to patch")

    new_name_size = nul_at
    new_head_size = head_size - (name_size - new_name_size)
    print(f"name field: {name_field[:nul_at].decode('utf-8')!r} "
          f"({name_size} -> {new_name_size} bytes); "
          f"headSize {head_size} -> {new_head_size}")

    out = bytearray(data[:name_off + new_name_size]) + bytearray(data[name_off + name_size:])
    struct.pack_into("<H", out, fhb + 15, new_name_size)
    struct.pack_into("<H", out, base + 5, new_head_size)

    crc_region = bytes(out[base + 2:base + new_head_size])
    crc = zlib.crc32(crc_region) & 0xffff
    struct.pack_into("<H", out, base, crc)
    print(f"recomputed headCRC = 0x{crc:04x}")

    PATCHED.write_bytes(out)
    print(f"wrote {PATCHED} ({len(out)} bytes, was {len(data)})")


if __name__ == "__main__":
    main()
