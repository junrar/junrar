#!/usr/bin/env python3
"""Deterministic generators for the M1.4 (issue #18) FirstWinDone distance-into-void
hostile fixtures, one per legacy engine (v15/v20/v29).

Each fixture drives a *first-window void* match: a copy whose distance exceeds the
current output position (UnpPtr) while the first window pass has not completed
(FirstWinDone==false). unrar 7.0.3+ (commit 5faaa45) zero-fills such a run
deterministically (unpackinline.cpp CopyString / unpack15.cpp CopyString15). junrar's
three legacy engines instead take the masked wrap-copy in their copyString else-branch
(`window[unpPtr] = window[destPtr++ & MAXWINMASK]`), whose source pointer starts in the
never-written high window region (zero) but, once it increments past index 0, reads the
member's OWN earlier literals -- non-zero bytes unrar never emits. That byte divergence is
the M1.4 RED signal.

Design of the void (all three engines): the member writes N non-zero literals (UnpPtr=N),
then a match with distance = N+1 (one past the written region, so Distance>UnpPtr) and
length L>1. The wrap-copy emits:
  byte 0            -> window[(N-(N+1)) & MASK] = window[MAXWINSIZE-1] = 0 (never written)
  byte i (i>=1)     -> window[i-1]              = the member's literal (non-zero)
unrar zero-fills all L bytes. Divergence at output bytes N+1 .. N+L-1.

Provenance / method:
  * v20, v29: hand-assembled byte-for-byte (PARITY_PLAN.md SS4.3 class 1 -- "a corrupt
    archive is corrupt however it was made"; no RAR 2.x/pre-recording compressor is
    shipped). The RAR2.0/RAR2.9 bitstreams mirror Unpack20.ReadTables20 / Unpack.readTables
    + decodeNumber + makeDecodeTables exactly, following the existing hand-assembly
    precedent solid/generate_v20_negative_backref_fixture.py (no-go row C1). Distances that
    exceed MAXWINSIZE are unreachable in v15/v20 (max encodable ~64 KB / ~1 MB) and in this
    small v29 member, so every engine uses the Distance>UnpPtr (first-window) void, which is
    exactly the `!FirstWinDone && Distance>UnpPtr` arm of the ported guard.
  * v15: byte-patched from the committed real RAR1.5 fixture. v15's adaptive Huffman state
    machine (shortLZ/longLZ/huffDecode + evolving ChSet tables) makes clean hand-assembly
    impractical, so the smallest 15-raw-bit distance field of an existing shortLZ Length==14
    match (unpack15.cpp CopyString15 path) is overwritten in place -- raw bits, no Huffman
    symbol, so the bitstream stays byte-aligned. See the V15_* constants (located by the
    temporary instrumentation described in the manifest) for the exact offset.

Run from anywhere; writes the three .rar next to this file. Idempotent.
"""
import hashlib
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
RES = DIR.parent  # .../com/github/junrar
V15_BASE = RES / "audio" / "BoatModernEnglish-regular-unpack15-dos.rar"
V15_BASE_SHA = "9c1deb8e11baa6fa1658c453b26a128a8437cefcbdeef3f4bcc499f6f7720a98"

LIT = 0x41  # 'A' -- non-zero literal so the junrar wrap-copy leak is observable


def sha(b):
    return hashlib.sha256(b).hexdigest()


# ------------------------------- MSB-first bit IO -------------------------------
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


def le16(v):
    return struct.pack("<H", v)


def le32(v):
    return struct.pack("<I", v)


def crc16(header_from_offset2):
    """RAR4 16-bit header CRC (low 16 bits of CRC-32 over the header past headCRC)."""
    return zlib.crc32(header_from_offset2) & 0xFFFF


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
    rest = bytes([0x74]) + le16(flags) + le16(header_size)
    block = le32(pack_size)
    return le16(crc16(rest + block + body)) + rest + block + body


def wrap(body, name, unp_version, unp_method, unp_size, file_crc):
    """MarkHeader + MainHeader(non-solid) + single FileHeader + body + EndArcHeader."""
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])
    main_rest = bytes([0x73]) + le16(0x0000) + le16(13)  # no MHD_SOLID
    main_body = le16(0) + le32(0)  # highPosAv, posAv
    main_header = le16(crc16(main_rest + main_body)) + main_rest + main_body
    # 0x8000 LHD_LARGE off, 0x00C0 = dictionary-size nibble (mirrors C1 fixture);
    # SOLID bit (0x10) deliberately clear -> non-solid, junrar reallocates a fresh
    # zero window (Unpack.init(null)); the void leak is intra-member, not cross-file.
    fh = file_header(name, 0x8000, unp_version, unp_method,
                     unp_size=unp_size, pack_size=len(body), file_crc=file_crc)
    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)
    return mark + main_header + fh + body + end_arc


# --------------------------------- v20 body ---------------------------------
def build_v20_body():
    """RAR2.0 stream: tables giving LD={65,270}, DD DistNumbers {3,4}; body writes
    'A','A','A' then backref opcode 270 (Length=3) + DD DistNumber 3 (Distance=4).
    At the match UnpPtr=3 < Distance=4 -> first-window void."""
    w = BitWriter()
    w.write(0, 2)  # audio=0, keep-old-table=0 (memset UnpOldTable20)

    # BD bit-lengths: symbol 1 (set len) and symbol 18 (zero-run 11..138), both length 1.
    bit_length = [0] * 19
    bit_length[1] = 1
    bit_length[18] = 1
    for nib in bit_length:
        w.write(nib, 4)

    def zrun(n):          # BD symbol 18 -> zero run
        assert 11 <= n <= 138
        w.write(1, 1)     # canonical: symbol 18 -> '1'
        w.write(n - 11, 7)

    def one():            # BD symbol 1 -> Table[i] = 1
        w.write(0, 1)     # canonical: symbol 1 -> '0'

    # TableSize = NC20+DC20+RC20 = 298+48+28 = 374. DD region starts at offset 298.
    zrun(65)              # Table[0..64]=0   -> i=65
    one()                # Table[65]=1       (LD literal 'A')            i=66
    zrun(138); zrun(66)  # Table[66..269]=0  (204)                       i=270
    one()                # Table[270]=1      (LD backref opcode)         i=271
    zrun(30)             # Table[271..300]=0 (30)                        i=301
    one()                # Table[301]=1      (DD DistNumber 3)           i=302
    one()                # Table[302]=1      (DD DistNumber 4)           i=303
    zrun(71)             # Table[303..373]=0 (71)                        i=374 done

    # body: canonical LD {65->'0', 270->'1'}, DD {3->'0', 4->'1'}
    w.write(0, 1)        # 'A'  (unpPtr 0->1)
    w.write(0, 1)        # 'A'  (unpPtr 1->2)
    w.write(0, 1)        # 'A'  (unpPtr 2->3)
    w.write(1, 1)        # Number=270 -> Length = LDecode[0]+3 = 3
    w.write(0, 1)        # DD DistNumber=3 -> Distance = DDecode[3]+1 = 4
    return w.to_bytes()


# --------------------------------- v29 body ---------------------------------
def build_v29_body():
    """RAR2.9 stream: LD={65,271}, DD DistNumbers {3,4}; body writes 'A','A','A' then
    backref opcode 271 (Length=3) + DD DistNumber 3 (Distance=4). v29 has no
    destUnpSize loop bound, so trailing 0-bits decode as harmless 'A' literals
    (LD 65 -> '0') until packed input is exhausted; UnpWriteArea caps output at unpSize."""
    w = BitWriter()
    w.write(0, 1)  # not PPM (bit 0x8000 clear)
    w.write(0, 1)  # keep-old-table clear -> memset unpOldTable
    # 20 BD bit-length nibbles: symbol 1 (set len) and 19 (zero-run 11..138) both length 1.
    bit_length = [0] * 20
    bit_length[1] = 1
    bit_length[19] = 1
    for nib in bit_length:
        w.write(nib, 4)

    def zrun(n):          # BD symbol 19 -> zero run (v29: Number==19 -> N=(bits>>>9)+11)
        assert 11 <= n <= 138
        w.write(1, 1)     # canonical: symbol 19 -> '1'
        w.write(n - 11, 7)

    def one():            # BD symbol 1 -> table[i] = 1
        w.write(0, 1)     # canonical: symbol 1 -> '0'

    # TableSize = NC+DC+LDC+RC = 299+60+17+28 = 404. DD region starts at offset NC=299.
    zrun(65)              # table[0..64]=0    -> i=65
    one()                # table[65]=1        (LD literal 'A')           i=66
    zrun(138); zrun(67)  # table[66..270]=0   (205)                      i=271
    one()                # table[271]=1       (LD backref opcode)        i=272
    zrun(30)             # table[272..301]=0  (30)                       i=302
    one()                # table[302]=1       (DD DistNumber 3)          i=303
    one()                # table[303]=1       (DD DistNumber 4)          i=304
    zrun(100)            # table[304..403]=0  (100)                      i=404 done

    # body: canonical LD {65->'0', 271->'1'}, DD {3->'0', 4->'1'}
    w.write(0, 1)        # 'A'
    w.write(0, 1)        # 'A'
    w.write(0, 1)        # 'A'
    w.write(1, 1)        # Number=271 -> Length = LDecode[0]+3 = 3
    w.write(0, 1)        # DD DistNumber=3 -> Distance = DDecode[3]+1 = 4
    return w.to_bytes()


def make_v20():
    body = build_v20_body()
    data = wrap(body, "void20", 20, 0x35, unp_size=6, file_crc=0)
    out = DIR / "void-dist-v20.rar"
    out.write_bytes(data)
    print(f"void-dist-v20.rar  {sha(data)}  body={len(body)}B unpSize=6 "
          f"(AAA + backref dist=4 len=3 @unpPtr=3)")


def make_v29():
    body = build_v29_body()
    data = wrap(body, "void29", 29, 0x35, unp_size=6, file_crc=0)
    out = DIR / "void-dist-v29.rar"
    out.write_bytes(data)
    print(f"void-dist-v29.rar  {sha(data)}  body={len(body)}B unpSize=6 "
          f"(AAA + backref dist=4 len=3 @unpPtr=3)")


# --------------------------------- v15 patch --------------------------------
# The first shortLZ Length==14 match (unpack15.cpp CopyString15 path, junrar
# Unpack15.shortLZ line ~288) reads a 15-bit RAW distance field:
#   Distance = (fgetbits() >>> 1) | 0x8000   (Distance in [0x8000, 0xffff])
# so distance = field15 | 0x8000, with field15 = the 15 raw bits. Located by temporary
# instrumentation (BitInput.DBG_absShift + a print in shortLZ; reverted): the first such
# match decodes at UnpPtr=34759 (0x87C7), length=5, natural distance=34484, and its 15-bit
# field begins at file byte 28041, bit 1 (MSB-first), value 1716.
# We overwrite that field with 1992 so distance = 1992|0x8000 = 34760 = UnpPtr+1: a
# first-window void (Distance>UnpPtr, FirstWinDone still false) with k=1 < length=5, so the
# masked wrap-copy crosses window index 0 and leaks the member's own literals (byte 0 of the
# decoded WAV, 'R'=0x52, ...) where unrar zero-fills. Raw bits -> no Huffman symbol shift ->
# the rest of the bitstream stays byte-aligned.
V15_MATCH_UNPPTR = 34759
V15_BIT_OFFSET = 28041 * 8 + 1  # absolute MSB-first bit position of the 15-bit field
V15_FIELD_ORIG = 1716
V15_FIELD_NEW = (V15_MATCH_UNPPTR + 1) & 0x7FFF  # 1992 -> distance 34760 = UnpPtr+1


def setbit(buf, bitpos, val):
    byte = bitpos >> 3
    off = 7 - (bitpos & 7)
    if val:
        buf[byte] |= 1 << off
    else:
        buf[byte] &= ~(1 << off)


def writebits(buf, bitpos, value, nbits):
    for k in range(nbits):
        setbit(buf, bitpos + k, (value >> (nbits - 1 - k)) & 1)


def read15(buf, bitpos):
    v = 0
    for k in range(15):
        b = (buf[(bitpos + k) >> 3] >> (7 - ((bitpos + k) & 7))) & 1
        v = (v << 1) | b
    return v


def make_v15():
    data = bytearray(V15_BASE.read_bytes())
    assert sha(bytes(data)) == V15_BASE_SHA, "v15 base fixture changed"
    assert read15(data, V15_BIT_OFFSET) == V15_FIELD_ORIG, \
        "v15 base distance field drifted: %d" % read15(data, V15_BIT_OFFSET)
    before = data[28041:28043].hex()
    writebits(data, V15_BIT_OFFSET, V15_FIELD_NEW, 15)
    assert read15(data, V15_BIT_OFFSET) == V15_FIELD_NEW
    out = DIR / "void-dist-v15.rar"
    out.write_bytes(bytes(data))
    print("void-dist-v15.rar  %s  bytes28041-42 %s->%s  dist 34484->%d (=UnpPtr+1) len=5"
          % (sha(bytes(data)), before, data[28041:28043].hex(), V15_FIELD_NEW | 0x8000))


if __name__ == "__main__":
    make_v20()
    make_v29()
    make_v15()
