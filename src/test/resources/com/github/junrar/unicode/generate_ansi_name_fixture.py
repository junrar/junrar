#!/usr/bin/env python3
"""Construct the T6-fix-round fixture (docs/porting/MIGRATION_MANUAL.md SS6
T6, verifier REJECT on P0.6): rar3-ansi-name.rar -- a RAR3 archive whose
FILE_HEAD has the LHD_UNICODE flag CLEAR (the genuinely plain-ANSI path,
`2e71167:arcread.cpp:186-205`'s non-LHD_UNICODE branch) and whose name field
holds raw legacy-codepage bytes that are NOT valid UTF-8.

Why this fixture exists: the C++ non-LHD_UNICODE path does NO charset
conversion at all (`strncpyz` -- raw bytes straight into `FileHeader.FileName`,
`WideToChar`/`UtfToWide` only run inside the LHD_UNICODE branch). The
pre-fix-round FileHeader.java decoded this ANSI byte run with
StandardCharsets.UTF_8 (FileHeader.java:169, and the ANSI-fallback half of
the dual encoding at :158), which silently mangles any legacy-codepage byte
(CP1251/CP936/Windows-1252 -- the core RAR3-era demographic) that is not
also valid UTF-8, replacing it with U+FFFD irreversibly. The fix decodes
both sites with StandardCharsets.ISO_8859_1 (byte-transparent, lossless
round-trip -- the faithful port of "no conversion").

=====================================================================
Derivation (byte-patched from the already-committed T6 base fixture,
PARITY_PLAN.md SS4.3 class 2 -- same recipe as generate_utf8_name_fixture.py)
=====================================================================
Source: rar3-utf8-name-split.rar (real rar 6.24 output, see
generate_utf8_name_fixture.py's own header for provenance). Its FILE_HEAD
name field is patched as follows:

1. Replace the entire name field (54 bytes: the UTF-8 ANSI-fallback name,
   a NUL, and a redundant RLE encoded-wide blob) with 8 raw bytes:
   b'caf\\xe9.txt' -- "caf" + 0xE9 (Windows-1252/ISO-8859-1 'e' with acute,
   i.e. the byte value for 'e' in "cafe" with an accent) + ".txt". 0xE9
   followed by ASCII 0x2E is NOT a valid UTF-8 sequence (0xE9 is a 3-byte
   lead byte; 0x2E is not a continuation byte 0x80-0xBF), so a UTF-8 decode
   of this field is lossy (produces U+FFFD); an ISO-8859-1 decode is
   byte-transparent and round-trips to "café.txt" (café.txt).
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

Oracle validation (executed 2026-07-18, real unrar 7.23 at
~/.local/bin/unrar, independent of junrar): `unrar lt rar3-ansi-name.rar`
and `unrar x rar3-ansi-name.rar` both report the archive and its single
member cleanly, no corruption warning (the printed name may look garbled in
a UTF-8 terminal since the raw byte is not valid UTF-8 -- that is expected
and is exactly the point: unrar itself does not re-encode this byte run
either).

Run: python3 generate_ansi_name_fixture.py
Regenerates rar3-ansi-name.rar next to this script from the already-
committed rar3-utf8-name-split.rar (no external rar binary needed).
"""
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
SPLIT = DIR / "rar3-utf8-name-split.rar"
PATCHED = DIR / "rar3-ansi-name.rar"

LHD_UNICODE = 0x0200

# Raw legacy-codepage bytes: "caf" + 0xE9 ("e" + acute in ISO-8859-1 /
# Windows-1252) + ".txt". Not valid UTF-8 (0xE9 is a 3-byte UTF-8 lead byte;
# the following 0x2E is not a UTF-8 continuation byte).
NEW_NAME_BYTES = b"caf\xe9.txt"


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

    new_name_size = len(NEW_NAME_BYTES)
    new_head_size = head_size - (name_size - new_name_size)
    new_flags = flags & ~LHD_UNICODE
    print(f"flags 0x{flags:04x} -> 0x{new_flags:04x} (LHD_UNICODE cleared); "
          f"name field {name_size} -> {new_name_size} bytes; "
          f"headSize {head_size} -> {new_head_size}")

    out = bytearray(data[:name_off]) + bytearray(NEW_NAME_BYTES) + bytearray(data[name_off + name_size:])
    struct.pack_into("<H", out, base + 3, new_flags)
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
