#!/usr/bin/env python3
"""Construct v20-solid-negative-backref.rar (no-go row C1, ADR docs/porting).

No RAR 2.x binary exists to produce a genuine unpVersion=20 solid archive
(only rar 7.23 and the RAR4-era 6.24 tarball were available at generation
time; neither emits method 20 - see docs/porting/PARITY_PLAN.md SS4.2). Per
SS4.3 class 1 ("a corrupt archive is corrupt however it was made"), this
fixture is instead assembled byte-for-byte to reproduce the exact hostile
condition: a solid RAR v20 member whose first LZ77 token is a back-reference
whose distance exceeds the accumulated window position, driving
Unpack20.CopyString20's destPtr negative.

Archive layout (2 file members, solid):
  MarkHeader(7) + MainHeader(13, MHD_SOLID) +
  FileHeader "f0"(34) + body0(16, hand-assembled RAR v20 bitstream) +
  FileHeader "f1"(34, LHD_SOLID) + body1(1, hand-assembled RAR v20 bitstream) +
  EndArcHeader(7)

Compressed-body design (see Unpack20.java ReadTables20/decodeNumber/
makeDecodeTables and Unpack15's BitInput for the bit-exact semantics this
mirrors):

  body0 (file "f0", solid=false -> runs ReadTables20):
    - table header: audio=0, keep-old-table=0 (2 bits)
    - BC20=19 4-bit code-length nibbles selecting a Huffman code with
      exactly two 1-bit symbols: Number=1 (RLE "assign length 1") and
      Number=18 (RLE "zero-run 11..138"), assigned by canonical order
      (bit 0 -> symbol 1, bit 1 -> symbol 18)
    - a Number=1/Number=18 program that assigns Table[]:
        Table[65]  = 1   (LD literal 'A', symbol 65)
        Table[270] = 1   (LD back-reference opcode, symbol 270)
        Table[298] = 1   (DD DistNumber=0 -> Distance=1)
        Table[299] = 1   (DD DistNumber=1 -> Distance=2)
      everything else 0 (unused), built via three "zero-run" (Number=18)
      spans plus four literal-length (Number=1) writes
    - LD body: one bit "0" -> Number=65 -> writes literal 'A' into the
      window (unpPtr 0 -> 1), then destUnpSize is exhausted and the loop
      exits

  body1 (file "f1", solid=true -> LD/DD/RD/BD persist from body0, no
  ReadTables20 call, per Unpack.unpInitData's solid branch):
    - one bit "1" -> LD decodes Number=270 (the back-reference opcode)
      -> Length = LDecode[0]+3 = 3
    - one bit "1" -> DD decodes DistNumber=1 -> Distance = DDecode[1]+1 = 2
    - CopyString20(length=3, distance=2) computes
      destPtr = unpPtr(1) - distance(2) = -1, routing into the
      guard's masked-wrap else-branch (never AIOOBE) and writing garbage
      into the window; the resulting output never matches the file's
      declared CRC, so extraction ends in CrcErrorException.

Run: python3 generate_v20_negative_backref_fixture.py
Regenerates v20-solid-negative-backref.rar next to this script.
"""
import pathlib
import struct
import zlib

OUT = pathlib.Path(__file__).parent / "v20-solid-negative-backref.rar"


class BitWriter:
    """MSB-first bit packer matching BitInput.getbits()/addbits() semantics."""

    def __init__(self):
        self.bits = []

    def write(self, value, nbits):
        for i in range(nbits - 1, -1, -1):
            self.bits.append((value >> i) & 1)

    def to_bytes(self):
        padded = self.bits + [0] * ((-len(self.bits)) % 8)
        out = bytearray()
        for i in range(0, len(padded), 8):
            byte = 0
            for b in padded[i:i + 8]:
                byte = (byte << 1) | b
            out.append(byte)
        return bytes(out)


def build_body0():
    w = BitWriter()
    w.write(0, 2)  # audio=0, keep-old-table=0

    bit_length = [0] * 19
    bit_length[1] = 1  # Number=1 opcode  (write literal-length value)
    bit_length[18] = 1  # Number=18 opcode (zero-run, 11..138)
    for nibble in bit_length:
        w.write(nibble, 4)

    def zero_run(n):
        assert 11 <= n <= 138
        w.write(1, 1)  # BD symbol "18"
        w.write(n - 11, 7)

    def literal_length():
        w.write(0, 1)  # BD symbol "1"

    zero_run(65)      # Table[0..65)   = 0   -> I=65
    literal_length()  # Table[65]      = 1   -> I=66  (LD literal 'A')
    zero_run(102)      # Table[66..168)  = 0   -> I=168
    zero_run(102)      # Table[168..270) = 0   -> I=270
    literal_length()  # Table[270]     = 1   -> I=271 (LD backref opcode)
    zero_run(27)      # Table[271..298) = 0   -> I=298
    literal_length()  # Table[298]     = 1   -> I=299 (DD DistNumber=0)
    literal_length()  # Table[299]     = 1   -> I=300 (DD DistNumber=1)
    zero_run(74)      # Table[300..374) = 0   -> I=374 (done)

    w.write(0, 1)  # LD: bit 0 -> Number=65 ('A'), write literal, exhaust destUnpSize
    return w.to_bytes()


def build_body1():
    w = BitWriter()
    w.write(1, 1)  # LD: bit 1 -> Number=270 (back-reference opcode)
    w.write(1, 1)  # DD: bit 1 -> DistNumber=1 -> Distance=2
    return w.to_bytes()


def le16(v):
    return struct.pack("<H", v)


def le32(v):
    return struct.pack("<I", v)


def file_header(name, flags, unp_version, unp_method, unp_size, pack_size, file_crc):
    name_bytes = name.encode("ascii")
    body = (
        le32(unp_size)
        + bytes([3])  # hostOS = unix
        + le32(file_crc)
        + le32(0)  # fileTime
        + bytes([unp_version, unp_method])
        + le16(len(name_bytes))
        + le32(0x20)  # fileAttr
        + name_bytes
    )
    header_size = 7 + 4 + len(body)
    base = le16(0) + bytes([0x74]) + le16(flags) + le16(header_size)
    block = le32(pack_size)
    return base + block + body


def main():
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])

    main_flags = 0x0008  # MHD_SOLID
    main_base = le16(0) + bytes([0x73]) + le16(main_flags) + le16(13)
    main_body = le16(0) + le32(0)  # highPosAv, posAv
    main_header = main_base + main_body

    body0 = build_body0()
    body1 = build_body1()

    fh0 = file_header("f0", 0x00C0, 20, 0x31, unp_size=1, pack_size=len(body0), file_crc=0)
    fh1 = file_header("f1", 0x00D0, 20, 0x31, unp_size=1, pack_size=len(body1), file_crc=0)

    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)

    archive = mark + main_header + fh0 + body0 + fh1 + body1 + end_arc
    OUT.write_bytes(archive)
    print(f"wrote {OUT} ({len(archive)} bytes)")
    print(f"body0={len(body0)} bytes, body1={len(body1)} bytes")
    print(f"body0 crc32={zlib.crc32(body0):08x} (informational only)")


if __name__ == "__main__":
    main()
