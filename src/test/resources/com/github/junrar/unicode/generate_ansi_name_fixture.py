#!/usr/bin/env python3
"""Construct the two T6 plain-ANSI name fixtures (docs/porting/MIGRATION_MANUAL.md
SS6 T6): RAR3 archives whose FILE_HEAD has the LHD_UNICODE flag CLEAR (the
plain-ANSI path, `d861246:arcread.cpp:345-360`'s `hd->FileName.empty()`
fallback) and whose name field holds raw bytes that are, respectively, NOT
valid UTF-8 and valid UTF-8.

  rar3-ansi-name.rar       b'caf\\xe9.txt'          -- NOT valid UTF-8
  rar3-ansi-utf8-name.rar  'Схема3.spl' in UTF-8   -- valid UTF-8

Why these fixtures exist: unrar 7.2.7 routes every narrow (non-RLE-decoded)
RAR3 name through `ArcCharToWide(..., ACTW_OEM)`, i.e. a *strict multibyte
decode in the process locale* -- UTF-8 on any modern Unix. A name field whose
bytes are valid UTF-8 is therefore decoded as UTF-8 (issue #44), while an
invalid one is not: unrar then falls back per platform (macOS `_APPLE` builds
call `UtfToWide` unconditionally and TRUNCATE at the first bad byte -- `unrar
7.23 lb rar3-ansi-name.rar` prints `caf`; glibc builds map each bad byte into
the U+E000 private-use area behind a U+FFFE mark, `unicode.cpp:203-248`).
junrar deliberately diverges on that invalid-input fallback only, keeping the
byte-transparent ISO-8859-1 decode, which is lossless and round-trips where
both unrar fallbacks destroy information. The valid-UTF-8 case is parity.

=====================================================================
Derivation (byte-patched from the already-committed T6 base fixture,
PARITY_PLAN.md SS4.3 class 2 -- same recipe as generate_utf8_name_fixture.py)
=====================================================================
Source: rar3-utf8-name-split.rar (real rar 6.24 output, see
generate_utf8_name_fixture.py's own header for provenance). Its FILE_HEAD
name field is patched as follows:

1. Replace the entire name field (54 bytes: the UTF-8 ANSI-fallback name,
   a NUL, and a redundant RLE encoded-wide blob) with the new name bytes.

   For rar3-ansi-name.rar, 8 raw bytes: b'caf\\xe9.txt' -- "caf" + 0xE9
   (Windows-1252/ISO-8859-1 'e' with acute, i.e. the byte value for 'e' in
   "cafe" with an accent) + ".txt". 0xE9 followed by ASCII 0x2E is NOT a
   valid UTF-8 sequence (0xE9 is a 3-byte lead byte; 0x2E is not a
   continuation byte 0x80-0xBF), so the UTF-8 decode fails and the
   byte-transparent ISO-8859-1 fallback round-trips to "café.txt".

   For rar3-ansi-utf8-name.rar, the 15 UTF-8 bytes of "Схема3.spl" (five
   2-byte Cyrillic code points then ASCII "3.spl"). These are valid UTF-8,
   so the decode succeeds and the name must come out as "Схема3.spl" --
   the name unrar prints, and the byte run copied from the real corpus
   member commoncrawl3/3I/3ILZMJBO2TG2LCBV6L5AHMXEYLEXH4X2 (issue #44).
2. Clear the LHD_UNICODE bit (0x0200) in the FILE_HEAD flags field
   (0x8220 -> 0x8020 -- LHD_WINDOW128 (0x0020) and LONG_BLOCK (0x8000, the
   generic ADD_SIZE-present bit used by all FILE_HEADs) both stay set;
   only LHD_UNICODE is cleared), so FileHeader.isUnicode() is false and the
   genuinely-non-Unicode `else` branch (FileHeader.java:168-171 pre-fix)
   runs.
3. nameSize (fileHeaderBuffer offset 15) shrinks from 54 to 8; headSize
   (BaseBlock offset 5) shrinks by the same 46-byte delta (86 -> 40).
4. The header CRC (RAR 1.5/GetCRC15, MIGRATION_MANUAL.md SS6 T7:
   `zlib.crc32(header[2:headSize]) & 0xffff`, cross-validated against the
   source fixture's own stored headCRC by generate_utf8_name_fixture.py
   before this script trusted it) is refixed over the new, shorter header
   extent.

The compressed DATA stream (the "utf8-name-payload\\n" content, unrelated to
the header change) is byte-identical to the source fixture's -- only the
FILE_HEAD's flags/nameSize/headSize/name-field/headCRC change.

Oracle validation (executed 2026-07-18 and re-executed 2026-07-21, real
unrar 7.23 at ~/.local/bin/unrar, independent of junrar): `unrar lt` and
`unrar x` report both archives and their single member cleanly, no
corruption warning. `unrar lb rar3-ansi-name.rar` prints `caf` (the macOS
truncating fallback described above); `unrar lb rar3-ansi-utf8-name.rar`
prints `Схема3.spl`, matching `unrar lb` on the corpus member the byte run
came from.

Run: python3 generate_ansi_name_fixture.py
Regenerates both fixtures next to this script from the already-committed
rar3-utf8-name-split.rar (no external rar binary needed). The script is
deterministic; SHA-256 of the three archives involved (.agents/policy/testing.md
"deterministic generator or byte-patch procedure, provenance, and checksum"):

  05ae8ec771e1cc002d4d3d5669969f9a1633b0f4afb1aa0d61effc0a03fb6345  rar3-utf8-name-split.rar (input)
  73dcdf2fdc98f7aa0b405d5b5bea977b4de98ab00450d1f98eb20ce4c5c74545  rar3-ansi-name.rar
  f719dc62350fdb87fee462bc262eb7b197ab5daef5739d2081be064ec293dc3a  rar3-ansi-utf8-name.rar
"""
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
SPLIT = DIR / "rar3-utf8-name-split.rar"

LHD_UNICODE = 0x0200

FIXTURES = [
    # Raw legacy-codepage bytes: "caf" + 0xE9 ("e" + acute in ISO-8859-1 /
    # Windows-1252) + ".txt". Not valid UTF-8 (0xE9 is a 3-byte UTF-8 lead
    # byte; the following 0x2E is not a UTF-8 continuation byte).
    ("rar3-ansi-name.rar", b"caf\xe9.txt"),
    # Valid UTF-8: the name unrar decodes as "Схема3.spl".
    ("rar3-ansi-utf8-name.rar", "Схема3.spl".encode("utf-8")),
]


def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def patch(data, new_name_bytes, patched):
    data = bytearray(data)

    # MARK header (7 bytes) -> MAIN_HEAD -> FILE_HEAD.
    pos = 7
    pos += u16(data, pos + 5)  # skip MAIN_HEAD via its own headSize
    base = pos
    if data[base + 2] != 0x74:
        raise SystemExit(f"expected FILE_HEAD (0x74) at offset {base + 2}, "
                          f"got 0x{data[base + 2]:02x} -- source fixture changed?")
    flags = u16(data, base + 3)
    head_size = u16(data, base + 5)
    # BaseBlock(7) + packSize(4) precede the fileHeaderBuffer fields parsed
    # by FileHeader.java; nameSize sits at fileHeaderBuffer offset 15,
    # the name bytes start right after fileAttr at offset 21.
    fhb = base + 7 + 4
    name_size = u16(data, fhb + 15)
    name_off = fhb + 21
    if name_off + name_size != base + head_size:
        raise SystemExit("expected the name field to reach the header end "
                          "(no salt/exttime) -- source fixture changed?")
    if not (flags & LHD_UNICODE):
        raise SystemExit("source fixture no longer has LHD_UNICODE set -- "
                          "nothing to clear, script assumptions stale")

    new_name_size = len(new_name_bytes)
    new_head_size = head_size - (name_size - new_name_size)
    new_flags = flags & ~LHD_UNICODE
    print(f"flags 0x{flags:04x} -> 0x{new_flags:04x} (LHD_UNICODE cleared); "
          f"name field {name_size} -> {new_name_size} bytes; "
          f"headSize {head_size} -> {new_head_size}")

    out = bytearray(data[:name_off]) + bytearray(new_name_bytes) + bytearray(data[name_off + name_size:])
    struct.pack_into("<H", out, base + 3, new_flags)
    struct.pack_into("<H", out, fhb + 15, new_name_size)
    struct.pack_into("<H", out, base + 5, new_head_size)

    crc_region = bytes(out[base + 2:base + new_head_size])
    crc = zlib.crc32(crc_region) & 0xffff
    struct.pack_into("<H", out, base, crc)
    print(f"recomputed headCRC = 0x{crc:04x}")

    patched.write_bytes(out)
    print(f"wrote {patched} ({len(out)} bytes, was {len(data)})")


if __name__ == "__main__":
    source = SPLIT.read_bytes()
    for name, name_bytes in FIXTURES:
        patch(source, name_bytes, DIR / name)
