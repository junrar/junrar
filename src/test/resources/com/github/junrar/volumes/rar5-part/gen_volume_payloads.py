#!/usr/bin/env python3
"""Deterministic payloads for the M3.9 RAR5 multi-volume fixtures (issue #30).

64-bit LCG (same constants as the M3.8 filter payload generator); fixed seeds make
every byte reproducible without committing binaries beyond the archives themselves.
"""
MASK = (1 << 64) - 1

def lcg(seed):
    state = seed
    while True:
        state = (state * 6364136223846793005 + 1442695040888963407) & MASK
        yield (state >> 33) & 0xFF

def write(name, seed, size):
    g = lcg(seed)
    with open(name, "wb") as f:
        f.write(bytes(next(g) for _ in range(size)))

# Main + BLAKE2 sets: one small unsplit file and one 250 KB incompressible file that
# must span all three 100k volumes.
with open("payload/note.txt", "wb") as f:
    f.write(b"junrar M3.9 RAR5 multi-volume fixture; small file, never split.\n" * 2)
write("payload/spanned.bin", 39101, 250_000)

# Solid set: six incompressible members so solid stream and file boundaries cross
# volume boundaries.
for i in range(6):
    write("payload/solid%d.bin" % i, 39200 + i, 60_000)

# Big set: larger than the 256 KB window min-alloc floor, so unpacked-data flushes
# interleave with the volume switches (see README).
write("payload/spanned2.bin", 39102, 400_000)
