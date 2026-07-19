#!/usr/bin/env python3
"""Deterministic generators for the M1.3 (issue #17) hostile RAR3/RAR1.5 fixtures.

These byte-patch two committed, legitimate fixtures into corrupt variants that drive
junrar's LZ/filter/channel/flags limit guards through the full public extraction
pipeline. A corrupt archive is corrupt however it was made (PARITY_PLAN.md §4.3 class 1),
so no compressor is shipped; every mutation is recorded below with offset + before/after
bytes + the reason, and each output's oracle behaviour is the real UnRAR 7.23 binary.

Sources (SHA-256 asserted so the hard-coded bit offsets stay valid):
  tika-documents.rar   466e4703e8505ccdaaa8fda233c84ce0de7fbdd7e4ec041af45b1e06e5621330
    real RAR 2.9 (v29) archive; its testEXCEL.xls member carries one RAR3 VMSF_DELTA
    filter (FiltPos=0, BlockLength=1536, 1 channel). Filter-blob byte 0 begins at file
    bit 1892 (firstByte 0xb6 at bit 1876), established by instrumented decode of
    Unpack.readVMCode/addVMCode.
  BoatModernEnglish-regular-unpack15-dos.rar
    9c1deb8e11baa6fa1658c453b26a128a8437cefcbdeef3f4bcc499f6f7720a98
    real RAR 1.5 (v15) audio archive; its packed data starts at file offset 64 and the
    first thing unpack15() decodes (after initHuff) is GetFlagsBuf over bytes 64..65.

Field encodings mirror unrar 7.2.7 (d861246): RarVM::ReadData (rarvm.cpp) and
Unpack::AddVMCode (unpack30.cpp). Run from anywhere; writes the three .rar next to this
file. Idempotent.
"""
import hashlib
import pathlib
import struct
import zlib

DIR = pathlib.Path(__file__).parent
RES = DIR.parent  # .../com/github/junrar
TIKA = RES / "tika-documents.rar"
V15 = RES / "audio" / "BoatModernEnglish-regular-unpack15-dos.rar"

TIKA_SHA = "466e4703e8505ccdaaa8fda233c84ce0de7fbdd7e4ec041af45b1e06e5621330"
V15_SHA = "9c1deb8e11baa6fa1658c453b26a128a8437cefcbdeef3f4bcc499f6f7720a98"

# tika DELTA filter blob coordinates (from instrumented Unpack.readVMCode decode)
FIRST_BYTE_BIT = 1876  # firstByte 0xb6
BLOB_BYTE0_BIT = 1892  # blob byte 0
ORIG_LEN = 36


def sha(b):
    return hashlib.sha256(b).hexdigest()


# ------------------------------- MSB-first bit IO -------------------------------
class BitReader:
    def __init__(self, data):
        self.d = data

    def get16(self, p):
        a = p >> 3
        o = p & 7
        v = (self.d[a] << 16) | (self.d[a + 1] << 8) | self.d[a + 2]
        return (v >> (8 - o)) & 0xFFFF


def read_data(br, p):
    """RarVM::ReadData -> (value, newpos)."""
    data = br.get16(p)
    top = data & 0xC000
    if top == 0:
        return ((data >> 10) & 0xF, p + 6)
    if top == 0x4000:
        if (data & 0x3C00) == 0:
            return (0xFFFFFF00 | ((data >> 2) & 0xFF), p + 14)
        return ((data >> 6) & 0xFF, p + 10)
    if top == 0x8000:
        p += 2
        return (br.get16(p), p + 16)
    p += 2
    hi = br.get16(p)
    p += 16
    lo = br.get16(p)
    return (((hi << 16) | lo) & 0xFFFFFFFF, p + 16)


class BitWriter:
    def __init__(self):
        self.bits = []

    def put(self, value, nbits):
        for k in range(nbits):
            self.bits.append((value >> (nbits - 1 - k)) & 1)

    def write_data(self, value):
        v = value & 0xFFFFFFFF
        if v <= 0xF:
            self.put(0b00, 2)
            self.put(v, 4)
        elif v <= 0xFF:
            self.put(0b01, 2)
            self.put(0b0001, 4)
            self.put(v, 8)
        elif v <= 0xFFFF:
            self.put(0b10, 2)
            self.put(v, 16)
        else:
            self.put(0b11, 2)
            self.put((v >> 16) & 0xFFFF, 16)
            self.put(v & 0xFFFF, 16)


def byte_bits(v):
    return [(v >> (7 - k)) & 1 for k in range(8)]


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


def parse_blob_params(blob, first_byte):
    br = BitReader(blob + b"\x00\x00\x00\x00")
    p = 0
    out = {}
    filt, p = read_data(br, p)
    out["FiltPos"] = filt
    bs, p = read_data(br, p)
    out["BlockStart"] = bs
    bl, p = read_data(br, p)  # firstByte 0x20 set on tika
    out["BlockLength"] = bl
    out["InitMask"] = br.get16(p) >> 9
    p += 7
    initr = [0] * 7
    for i in range(7):
        if out["InitMask"] & (1 << i):
            initr[i], p = read_data(br, p)
    out["InitR"] = initr
    out["param_end_bit"] = p
    return out


# --------------------------------- RAR3 headers ---------------------------------
def parse_headers(d):
    hs = []
    pos = 7
    while pos < len(d):
        crc, htype, flags, hsize = struct.unpack_from("<HBHH", d, pos)
        hs.append((pos, crc, htype, flags, hsize))
        if htype in (0x74, 0x7A):
            packsize = struct.unpack_from("<I", d, pos + 7)[0]
            pos = pos + hsize + packsize
        elif htype == 0x7B:
            break
        else:
            pos = pos + hsize
    return hs


def head_crc(hdr_from_offset2):
    return zlib.crc32(hdr_from_offset2) & 0xFFFF


# ---------------------------------- generators ----------------------------------
def make_filter_flood():
    """FiltPos overflow: overwrite the FiltPos ReadData field (blob bit 0) with the
    32-bit form value 0xFFFFFFFF. AddVMCode reads FiltPos first and rejects on
    FiltPos > Filters.size() (junrar Integer.compareUnsigned) before touching the rest
    of the blob, so no downstream re-alignment is needed: same size, same headers.
    Exercises the unsigned filter-position guard (unpack30.cpp:383)."""
    data = bytearray(TIKA.read_bytes())
    before = data[234:239].hex()
    writebits(data, BLOB_BYTE0_BIT, 0b11, 2)
    writebits(data, BLOB_BYTE0_BIT + 2, 0xFFFFFFFF, 32)
    out = DIR / "filter-flood.rar"
    out.write_bytes(data)
    print(f"filter-flood.rar   {sha(data)}  bytes234-238 {before}->{data[234:239].hex()}")


def make_channel_flood(channels=0x7FFFFFF0):
    """Delta channel overflow: re-encode the DELTA filter blob with InitR[0]=channels
    (> MAX3_UNPACK_CHANNELS 1024), preserving the VM-code bit sequence so IsStandardFilter
    still resolves VMSF_DELTA. The wider ReadData grows the blob; the inserted bits shift
    the member's remaining stream, so the owning FILE header packSize is bumped and its
    header CRC refixed. channels chosen so the ungurded delta loop overflows destPos into a
    negative index (raw AIOOBE) -- the guard turns that into a clean rejection
    (rarvm.cpp:203)."""
    data = TIKA.read_bytes()
    br = BitReader(data)
    first_byte = (br.get16(FIRST_BYTE_BIT) >> 8) & 0xFF
    assert first_byte == 0xB6, hex(first_byte)
    blob = bytes((br.get16(BLOB_BYTE0_BIT + i * 8) >> 8) & 0xFF for i in range(ORIG_LEN))
    info = parse_blob_params(blob, first_byte)
    assert info["FiltPos"] == 0 and info["BlockLength"] == 1536 and info["InitR"][0] == 1, info

    blob_bits = [b for byteval in blob for b in byte_bits(byteval)]
    tail_of_blob = blob_bits[info["param_end_bit"]:]  # VMCodeSize + VMCode, unchanged
    bw = BitWriter()
    bw.write_data(info["FiltPos"])
    bw.write_data(info["BlockStart"])
    bw.write_data(info["BlockLength"])
    bw.put(info["InitMask"], 7)
    bw.write_data(channels)
    new_blob_bits = bw.bits + tail_of_blob
    new_len = (len(new_blob_bits) + 7) // 8
    while len(new_blob_bits) % 8:
        new_blob_bits.append(0)
    new_extra = new_len - 7
    assert 0 <= new_extra <= 255

    all_bits = []
    for i in range(FIRST_BYTE_BIT >> 3):
        all_bits += byte_bits(data[i])
    all_bits += byte_bits(data[FIRST_BYTE_BIT >> 3])[: FIRST_BYTE_BIT & 7]
    all_bits += byte_bits(first_byte)
    all_bits += byte_bits(new_extra)
    all_bits += new_blob_bits
    tail_start = BLOB_BYTE0_BIT + ORIG_LEN * 8
    for bp in range(tail_start, len(data) * 8):
        all_bits.append((data[bp >> 3] >> (7 - (bp & 7))) & 1)
    while len(all_bits) % 8:
        all_bits.append(0)
    out = bytearray()
    for i in range(0, len(all_bits), 8):
        v = 0
        for k in range(8):
            v = (v << 1) | all_bits[i + k]
        out.append(v)

    delta = len(out) - len(data)
    owner = None
    for (pos, crc, htype, flags, hsize) in parse_headers(data):
        if htype in (0x74, 0x7A):
            packsize = struct.unpack_from("<I", data, pos + 7)[0]
            dstart = pos + hsize
            if dstart <= 234 < dstart + packsize:
                owner = (pos, hsize, packsize)
                break
    assert owner, "owner FILE header not found"
    opos, ohsize, opack = owner
    struct.pack_into("<I", out, opos + 7, opack + delta)
    struct.pack_into("<H", out, opos, head_crc(bytes(out[opos + 2:opos + ohsize])))

    dst = DIR / "channel-flood.rar"
    dst.write_bytes(out)
    print(f"channel-flood.rar  {sha(out)}  grew {delta}B  packSize {opack}->{opack + delta}  channels {channels}")


def make_flagsplace_oob():
    """FlagsPlace overflow: overwrite the first two packed bytes (file offset 64-65) with
    0xff 0xf0, so the first GetFlagsBuf DecodeNum (fresh Huffman tables) yields FlagsPlace
    == 256 == ChSetC.length (unpack15.cpp:412 guard). Data-only change; header CRC intact.
    Also drives the downstream LongLZ ChSetB write-back (unpack15.cpp:291 `& 0xff`)."""
    data = bytearray(V15.read_bytes())
    before = data[64:66].hex()
    data[64], data[65] = 0xFF, 0xF0
    out = DIR / "flagsplace-oob.rar"
    out.write_bytes(data)
    print(f"flagsplace-oob.rar {sha(data)}  bytes64-65 {before}->{data[64:66].hex()}")


if __name__ == "__main__":
    assert sha(TIKA.read_bytes()) == TIKA_SHA, "tika base fixture changed"
    assert sha(V15.read_bytes()) == V15_SHA, "v15 base fixture changed"
    make_filter_flood()
    make_channel_flood()
    make_flagsplace_oob()
