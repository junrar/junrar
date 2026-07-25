#!/usr/bin/env python3
"""Construct protect-header-seek.rar (no-go row C7, ADR docs/porting).

RAR3 recovery-record ("protect") blocks (HEAD_TYPE=0x78) are legacy: no
modern `rar`/`unrar` build (WinRAR 7.23 included) emits them, so per
docs/porting/PARITY_PLAN.md SS4.3 class 1 ("a corrupt archive is corrupt
however it was made") this fixture is hand-assembled byte-for-byte. It pins
the C7 seek-past-data behavior: after a ProtectHeader block, the channel
must land exactly past the block's declared recovery-record payload
(headerSize + dataSize), so the FOLLOWING FileHeader parses correctly
instead of reading into the payload's garbage bytes.

Archive layout:
  MarkHeader(7) + MainHeader(13) +
  ProtectHeader(26: base7+dataSize4+version1+recSectors2+totalBlocks4+mark8)
    + 48 zero-filled payload bytes (recovery-record data; content is
      irrelevant, only its declared length matters for the seek) +
  FileHeader "after-protect.txt"(49: base7+packSize4+body21+name17) + body(10, stored/method 0x30) +
  EndArcHeader(7)

If the seek under-shoots (e.g. omits "+ dataSize"), the next base-block read
lands inside the zero-filled payload: headType=0x00 matches no
UnrarHeadertype, so Archive throws CorruptHeaderException("unknown block
header!") instead of parsing "after-protect.txt" -- a loud, unambiguous
failure rather than a silent misread.

Run: python3 generate_protect_header_seek_fixture.py
Regenerates protect-header-seek.rar next to this script.
"""
import pathlib
import struct
import zlib

OUT = pathlib.Path(__file__).parent / "protect-header-seek.rar"

# T2 field values the sibling unit test (ProtectHeaderTest in the rarfile
# package) asserts the ProtectHeader constructor parses correctly.
PROTECT_VERSION = 5
PROTECT_REC_SECTORS = 1000
PROTECT_TOTAL_BLOCKS = 12345678
PROTECT_MARK = b"PROTMARK"
PROTECT_PAYLOAD_SIZE = 48

FILE_NAME = "after-protect.txt"
FILE_CONTENT = b"seek-ok-c7"


def le16(v):
    return struct.pack("<H", v)


def le32(v):
    return struct.pack("<I", v)


def main():
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])

    main_base = le16(0) + bytes([0x73]) + le16(0) + le16(13)
    main_body = le16(0) + le32(0)  # highPosAv, posAv
    main_header = main_base + main_body

    assert len(PROTECT_MARK) == 8
    protect_body = (
        bytes([PROTECT_VERSION])
        + le16(PROTECT_REC_SECTORS)
        + le32(PROTECT_TOTAL_BLOCKS)
        + PROTECT_MARK
    )
    assert len(protect_body) == 15  # SIZEOF_PROTECTHEAD(26) - SIZEOF_LONGBLOCKHEAD(11)
    protect_header_size = 7 + 4 + len(protect_body)
    assert protect_header_size == 26
    protect_base = le16(0) + bytes([0x78]) + le16(0) + le16(protect_header_size)
    protect_datasize = le32(PROTECT_PAYLOAD_SIZE)
    protect_payload = bytes(PROTECT_PAYLOAD_SIZE)  # zero-filled; content unused
    protect_block = protect_base + protect_datasize + protect_body + protect_payload

    name_bytes = FILE_NAME.encode("ascii")
    file_body = (
        le32(len(FILE_CONTENT))  # unpSize
        + bytes([3])  # hostOS = unix
        + le32(zlib.crc32(FILE_CONTENT))
        + le32(0)  # fileTime
        + bytes([29, 0x30])  # unpVersion=29, unpMethod=0x30 (store)
        + le16(len(name_bytes))
        + le32(0x20)  # fileAttr
        + name_bytes
    )
    file_header_size = 7 + 4 + len(file_body)
    file_base = le16(0) + bytes([0x74]) + le16(0) + le16(file_header_size)
    file_packsize = le32(len(FILE_CONTENT))
    file_block = file_base + file_packsize + file_body + FILE_CONTENT

    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)

    archive = mark + main_header + protect_block + file_block + end_arc
    OUT.write_bytes(archive)
    print(f"wrote {OUT} ({len(archive)} bytes)")
    print(f"protect block={len(protect_block)} bytes (header=26 + payload={PROTECT_PAYLOAD_SIZE})")
    print(f"file block={len(file_block)} bytes at offset {len(mark) + len(main_header) + len(protect_block)}")


if __name__ == "__main__":
    main()
