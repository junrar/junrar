#!/usr/bin/env python3
"""Byte-patch RAR5 fixture fields + recompute header CRC32 (M3.7 recipe).

Usage: patch.py <archive> <old> <new> [...]
Each (old,new) pair must be equal length; replaces first occurrence, fixes the
enclosing RAR5 block header CRC32 (stored LE at block start, computed over the
bytes after the 4-byte CRC field).
"""
import sys, zlib

def read_vint(b, i):
    v = 0; shift = 0
    while True:
        c = b[i]; i += 1
        v |= (c & 0x7F) << shift
        if not c & 0x80:
            return v, i
        shift += 7

def find_block(b, p):
    # scan backwards for a plausible block start s: crc32(header) matches
    for s in range(p - 5, max(-1, p - 300), -1):
        try:
            size, j = read_vint(b, s + 4)
        except IndexError:
            continue
        end = j + size
        if end <= p or end > len(b) or size == 0 or size > 0x1000:
            continue
        stored = int.from_bytes(b[s:s + 4], 'little')
        if zlib.crc32(b[s + 4:end]) & 0xFFFFFFFF == stored:
            return s, end
    raise SystemExit(f'no enclosing block for offset {p}')

def main():
    path = sys.argv[1]
    b = bytearray(open(path, 'rb').read())
    pairs = sys.argv[2:]
    for old, new in zip(pairs[::2], pairs[1::2]):
        o, n = old.encode(), new.encode()
        assert len(o) == len(n), (old, new)
        p = b.find(o)
        assert p >= 0, old
        s, end = find_block(b, p)
        b[p:p + len(o)] = n
        b[s:s + 4] = (zlib.crc32(b[s + 4:end]) & 0xFFFFFFFF).to_bytes(4, 'little')
        print(f'patched {old!r}->{new!r} at {p} block {s}..{end}')
    open(path, 'wb').write(b)

main()
