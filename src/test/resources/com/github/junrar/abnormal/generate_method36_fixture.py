#!/usr/bin/env python3
"""Construct method36.rar (issue #19 M1.5 part B): a clean v29 archive whose FILE_HEAD
declares compression method 36 ("alternative hash", RAR 3.6.1 beta), which unrar itself
dropped in 5.0.0 (reports/unrar-delta-map.md SS3) and which junrar now refuses at
extraction time with a typed UnsupportedRarMethodException instead of silently routing
it through the method-29 (RAR 3.x) decoder.

=====================================================================
Source and layout (same recipe as unicode/generate_utf8_name_fixture.py -- READ IT FIRST)
=====================================================================
Base fixture: the already-committed src/test/resources/com/github/junrar/test.rar.
Probed layout (this script's own header walk, run once to confirm before writing the
patch): test.rar has two FILE_HEAD blocks --

  block 1: offset 20,  headSize 45, unpVersion 29, nameSize 11
  block 2: offset 78,  headSize 35, unpVersion 20, nameSize 3

Only the FIRST block is unpVersion 29 (method 29, the RAR3 case that method 36 used to
alias onto pre-fix); the second is unpVersion 20 (a different unpack path entirely, not
touched by this change). This script patches ONLY the first FILE_HEAD's unpVersion byte;
the second file header is left byte-for-byte untouched.

RAR3 BaseBlock+FILE_HEAD layout (MIGRATION_MANUAL SS5.1, recapped from
unicode/generate_utf8_name_fixture.py):

  base       = FILE_HEAD BaseBlock start
  base+2     = headType (0x74 for FILE_HEAD)
  base+5     = headSize (u16)
  fhb        = base + 7 (BaseBlock) + 4 (packSize) = start of the fileHeaderBuffer
               fields FileHeader.java parses
  fhb+13     = unpVersion (u8) -- Unpack.doUnpack's `method` switch operand

=====================================================================
The patch
=====================================================================
Set fhb+13 from 29 to 36. No other byte moves (in-place, fixed-width field), so headSize
is unchanged; only the header CRC needs a refix. CRC formula (verified in
generate_utf8_name_fixture.py against a real fixture's own stored headCRC):
headCRC = zlib.crc32(header[base+2 : base+headSize]) & 0xffff.

The compressed DATA stream is never touched, so header parsing (Archive.getFileHeaders())
stays clean -- only extraction (which dispatches on unpVersion/method) is expected to
throw.

Run: python3 generate_method36_fixture.py
Writes abnormal/method36.rar next to this script from the already-committed test.rar
(no external rar binary needed).
"""
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
SOURCE = DIR.parent / "test.rar"
OUT = DIR / "method36.rar"


def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]


def main():
    data = bytearray(SOURCE.read_bytes())

    pos = 7
    pos += u16(data, pos + 5)  # skip MAIN_HEAD via its own headSize
    base = pos
    if data[base + 2] != 0x74:
        raise SystemExit(f"expected FILE_HEAD (0x74) at offset {base + 2}, "
                          f"got 0x{data[base + 2]:02x} -- source fixture changed?")
    head_size = u16(data, base + 5)
    fhb = base + 7 + 4
    unp_version = data[fhb + 13]
    if unp_version != 29:
        raise SystemExit(f"expected first FILE_HEAD unpVersion 29, got {unp_version} "
                          "-- source fixture changed, pick another v29 fixture "
                          "(e.g. audio/BoatModernEnglish-regular-unpack30.rar)")

    out = data
    out[fhb + 13] = 36
    print(f"first FILE_HEAD unpVersion: {unp_version} -> 36 (offset {fhb + 13})")

    crc_region = bytes(out[base + 2:base + head_size])
    crc = zlib.crc32(crc_region) & 0xffff
    struct.pack_into("<H", out, base, crc)
    print(f"recomputed headCRC = 0x{crc:04x}")

    OUT.write_bytes(out)
    print(f"wrote {OUT} ({len(out)} bytes)")


if __name__ == "__main__":
    main()
