#!/usr/bin/env python3
"""Construct encname-truncated.rar (issue #19 M1.5 part A, FileNameDecoder bounds rewrite).

Ports unrar 7.2.7 (d861246) encname.cpp EncodeFileName::Decode: every read of the
encoded-name stream is bounds-checked before the access; the C++ silently `break`s on a
truncated stream, junrar throws CorruptHeaderException instead (MIGRATION_MANUAL SS4.7).
This fixture drives that guard through the real FileHeader parsing path (not just the
FileNameDecoder unit).

=====================================================================
Source and layout (same recipe as unicode/generate_utf8_name_fixture.py -- READ IT FIRST)
=====================================================================
Base fixture: the already-committed unicode/rar3-utf8-name-split.rar (real rar 6.24
output, NUL-split ANSI+encoded-wide name field -- `length != nameSize` in
FileHeader.java's isUnicode() branch, i.e. the shape that reaches FileNameDecoder.decode
at all). FILE_HEAD layout recap (RAR3 BaseBlock+FILE_HEAD, MIGRATION_MANUAL SS5.1):

  MARK header (7 bytes) -> MAIN_HEAD -> FILE_HEAD
  base            = FILE_HEAD BaseBlock start
  base+5          = headSize (u16)
  base+2          = headType (0x74 for FILE_HEAD)
  fhb             = base + 7 (BaseBlock) + 4 (packSize) = start of the
                    fileHeaderBuffer fields FileHeader.java parses
  fhb+13          = unpVersion
  fhb+15          = nameSize (u16) -- total length of the name FIELD (ansi bytes + NUL
                    separator + encoded-wide blob), NOT the ansi-only length
  fhb+21          = name field bytes

=====================================================================
The patch
=====================================================================
Within the name field, find the embedded NUL (nul_at = ansi name length). Replace
everything after the NUL with exactly two bytes: 0x00 0x00.

Why two bytes forces the truncation: FileNameDecoder.decode reads highByte from the
first byte (0x00), then -- since encPos is still < name.length -- reads a flags byte
from the second byte (0x00). flags=0x00 selects switch case 0 (`flags >>> 6 == 0`),
which needs ONE MORE byte for the literal char. There isn't one: encPos now equals
name.length exactly. Pre-fix (no bounds check), case 0 does
`getChar(name, encPos++)` unconditionally -> raw ArrayIndexOutOfBoundsException.
Post-fix, the guard `if (encPos >= name.length) throw truncated()` fires ->
CorruptHeaderException.

New name field = ansi_bytes + b"\\x00" + b"\\x00\\x00" (length = nul_at + 3). Field
shrinks relative to source (old blob was longer), so nameSize and headSize both need
recomputing, and the header CRC needs a refix over the new (shorter) header extent.
CRC formula (verified in generate_utf8_name_fixture.py against the source fixture's own
stored headCRC): headCRC = zlib.crc32(header[base+2 : base+headSize]) & 0xffff.

Sanity preserved: nul_at != new nameSize (NUL is not at the field end), so
FileHeader.java's `length != nameSize` still selects the split/decode branch --
decode() is still reached, not the whole-field-UTF-8 branch.

Run: python3 generate_encname_fixtures.py
Writes abnormal/encname-truncated.rar next to this script from the already-committed
unicode/rar3-utf8-name-split.rar (no external rar binary needed).
"""
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
SOURCE = DIR.parent / "unicode" / "rar3-utf8-name-split.rar"
OUT = DIR / "encname-truncated.rar"


def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def main():
    data = bytearray(SOURCE.read_bytes())

    # MARK header (7 bytes) -> MAIN_HEAD -> FILE_HEAD.
    pos = 7
    pos += u16(data, pos + 5)  # skip MAIN_HEAD via its own headSize
    base = pos
    if data[base + 2] != 0x74:
        raise SystemExit(f"expected FILE_HEAD (0x74) at offset {base + 2}, "
                          f"got 0x{data[base + 2]:02x} -- source fixture changed?")
    head_size = u16(data, base + 5)
    fhb = base + 7 + 4
    name_size = u16(data, fhb + 15)
    name_off = fhb + 21
    name_field = bytes(data[name_off:name_off + name_size])
    if name_off + name_size != base + head_size:
        raise SystemExit("expected the name field to reach the header end "
                          "(no salt/exttime) -- source fixture changed?")
    nul_at = name_field.find(0)
    if nul_at == -1:
        raise SystemExit("source fixture has no embedded NUL (whole-UTF-8 shape) -- "
                          "wrong source for a split-name truncation fixture")

    new_blob = b"\x00\x00"
    new_name_field = name_field[:nul_at] + b"\x00" + new_blob
    new_name_size = len(new_name_field)
    new_head_size = head_size - (name_size - new_name_size)
    print(f"name field: ansi={name_field[:nul_at]!r} ({nul_at} bytes), "
          f"encoded blob {name_size - nul_at - 1} -> {len(new_blob)} bytes; "
          f"nameSize {name_size} -> {new_name_size}; headSize {head_size} -> {new_head_size}")
    assert nul_at != new_name_size, "NUL must not land at the new field end (split branch must still trigger)"

    out = bytearray(data[:name_off]) + bytearray(new_name_field) + bytearray(data[name_off + name_size:])
    struct.pack_into("<H", out, fhb + 15, new_name_size)
    struct.pack_into("<H", out, base + 5, new_head_size)

    crc_region = bytes(out[base + 2:base + new_head_size])
    crc = zlib.crc32(crc_region) & 0xffff
    struct.pack_into("<H", out, base, crc)
    print(f"recomputed headCRC = 0x{crc:04x}")

    OUT.write_bytes(out)
    print(f"wrote {OUT} ({len(out)} bytes, was {len(data)})")


if __name__ == "__main__":
    main()
