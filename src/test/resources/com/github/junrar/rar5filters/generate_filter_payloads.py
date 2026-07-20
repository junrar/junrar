import struct, math

def lcg(seed):
    s = seed
    while True:
        s = (s * 6364136223846793005 + 1442695040888963407) & (2**64 - 1)
        yield (s >> 33) & 0xFFFFFFFF

# delta: 256 KB of 16-bit LE slowly-varying waveform (WAV-like, 2 channels interleaved)
r = lcg(38001)
out = bytearray()
for i in range(65536):
    a = int(12000 * math.sin(i / 97.0)) + (next(r) % 7) - 3
    b = int(9000 * math.sin(i / 61.0 + 1.0)) + (next(r) % 5) - 2
    out += struct.pack('<hh', a, b)
open('delta.bin', 'wb').write(out)

# e8: x86-like stream, dense E8 (call rel32) to a small target set
r = lcg(38002)
targets = [0x1000 + 0x40 * (next(r) % 512) for _ in range(64)]
out = bytearray()
pos = 0
filler = bytes([0x55, 0x89, 0xE5, 0x8B, 0x45, 0x08, 0x83, 0xC0, 0x01, 0x5D, 0xC3])
while len(out) < 196608:
    out += filler[: 5 + (next(r) % 6)]
    t = targets[next(r) % 64]
    rel = (t - (len(out) + 5)) & 0xFFFFFFFF
    out += b'\xE8' + struct.pack('<I', rel)
open('e8.bin', 'wb').write(out[:196608])

# e8e9: same shape but mixes E8 calls and E9 jmps
r = lcg(38003)
targets = [0x2000 + 0x20 * (next(r) % 1024) for _ in range(64)]
out = bytearray()
while len(out) < 196608:
    out += filler[: 4 + (next(r) % 7)]
    t = targets[next(r) % 64]
    rel = (t - (len(out) + 5)) & 0xFFFFFFFF
    op = b'\xE8' if (next(r) % 2) == 0 else b'\xE9'
    out += op + struct.pack('<I', rel)
open('e8e9.bin', 'wb').write(out[:196608])

# arm: ARM32-like words, dense BL (cond=1110 -> 0xEB) to small target set
r = lcg(38004)
targets = [0x8000 + 4 * (next(r) % 4096) for _ in range(64)]
out = bytearray()
plain = [0xE1A00000, 0xE5901004, 0xE2811001, 0xE5801004, 0xE1A0F00E]
i = 0
while len(out) < 131072:
    if (next(r) % 3) == 0:
        t = targets[next(r) % 64]
        imm = ((t - (len(out) + 8)) >> 2) & 0xFFFFFF
        out += struct.pack('<I', 0xEB000000 | imm)
    else:
        out += struct.pack('<I', plain[next(r) % 5])
open('arm.bin', 'wb').write(out[:131072])
