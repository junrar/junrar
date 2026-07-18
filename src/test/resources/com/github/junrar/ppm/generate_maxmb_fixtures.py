#!/usr/bin/env python3
"""Construct the PPM MaxMB fixtures (no-go row S8, docs/porting/PARITY_PLAN.md
P0.5): legit-maxmb63.rar, preserved-maxmb0.rar, hostile-maxmb255.rar.

ASSUMED-probe result (docs/porting/PARITY_PLAN.md SS4.2's residual ASSUMED
claim, resolved here): rar 6.24's "-mc<order>:<mem>t+" DOES force real PPMd
encoding with an archive-supplied MaxMB > 1 even for a small payload -- unlike
"-md" LZ dictionary size, the PPM memory parameter is NOT silently reduced to
match payload size. Probed 2026-07-17 by instrumenting ModelPPM.decodeInit to
print the raw header byte: a 26 KB payload with "-mc32:64t+" already produced
rawMaxMB=63 in the header.

Binary procurement (docs/porting/PARITY_PLAN.md SS4.2): rar 7.23 cannot create
RAR4-format archives ("rar a -ma4 ... " -> "ERROR: Unknown option: ma4"), so
these RAR3/RAR4-era fixtures use the era rar 6.24 macOS x64 tarball at
https://www.rarlab.com/rar/rarmacos-x64-624.tar.gz (probed 2026-07-17: HTTP
200, application/x-gzip, 607,872 bytes, gzip magic 1f 8b -- matches the plan's
pre-verified fingerprint). Not vendored; download+extract to reproduce.

-mc<order>:<mem>t+ syntax (rar 6.24 rar.txt "-mc<par>" section): <order> is
the PPM algorithm order (2-63), <mem> is memory in MB allocated for PPM
(1-128) -- the archive header's MaxMB byte is (mem - 1), read by
ModelPPM.decodeInit as "MaxMB = unpackRead.getChar()" then (pre-fix) clamped
to 1 if > 1. "t+" forces text/PPM compression for all data (rar.txt: "Switch
-mct+ will force use of the text compression for all data").

=====================================================================
legit-maxmb63.rar / legit-maxmb63-expected.bin
=====================================================================
Payload: concatenation of 15 junrar source files (as they stood at fixture
generation time; frozen -- later source edits do not affect this committed
fixture), 48,524 bytes, in this order:
  src/main/java/com/github/junrar/Archive.java
  src/main/java/com/github/junrar/ContentDescription.java
  src/main/java/com/github/junrar/crc/RarCRC.java
  src/main/java/com/github/junrar/crypt/Rijndael.java
  src/main/java/com/github/junrar/exception/BadRarArchiveException.java
  src/main/java/com/github/junrar/exception/CorruptHeaderException.java
  src/main/java/com/github/junrar/exception/CrcErrorException.java
  src/main/java/com/github/junrar/exception/HeaderNotInArchiveException.java
  src/main/java/com/github/junrar/exception/InitDeciphererFailedException.java
  src/main/java/com/github/junrar/exception/MainHeaderNullException.java
  src/main/java/com/github/junrar/exception/NotRarArchiveException.java
  src/main/java/com/github/junrar/exception/RarException.java
  src/main/java/com/github/junrar/exception/UnsupportedRarEncryptedException.java
  src/main/java/com/github/junrar/exception/UnsupportedRarV5Exception.java
  src/main/java/com/github/junrar/io/RandomAccessInputStream.java
(diverse real-world Java source -- enough context variety to grow the PPM
model past a 2 MB ceiling; a single small text file, e.g. CHANGELOG.md 26 KB
with "-mc32:64t+", did NOT trigger the bug -- bisected empirically, see
below).

Regeneration (requires the rar 6.24 binary above, not vendored):
  cat <the 15 files above, in order> > legit-maxmb63-expected.bin
  rar a -ep1 -ma4 -m5 -mc63:64t+ legit-maxmb63.rar legit-maxmb63-expected.bin

Verified header (instrumented probe, 2026-07-17): reset=true, rawMaxMB=63
(memory=64 MB) at absolute file offset 72 (see hostile patch below).

Bisection (order=63, memory=64 MB, truncating the same concatenation):
payload sizes 5000/10000/20000/30000/35000 bytes all extract byte-identical
under the PRE-FIX clamped code (model never outgrows the clamped 2 MB); sizes
40000/45000/50000 all throw CrcErrorException under the clamped code (model
outgrows 2 MB, decoder's RestartModelRare fires at a different point than the
encoder's, corrupting the entropy-coded stream from there on). 48,524 bytes
(file-boundary-aligned, chosen over an arbitrary byte cut) sits comfortably
past the 40,000-byte threshold.

RED proof (executed 2026-07-17, untouched clamped ModelPPM.java):
  extracting legit-maxmb63.rar -> com.github.junrar.exception.CrcErrorException
GREEN proof (clamp removed): extracted 48,524 bytes, byte-identical to
legit-maxmb63-expected.bin (sha256 ce38ef7faf04e1785b1ed0142972c40105045e2
27d74295a464a2011c1ddca7e), matching real `unrar` 7.23's own extraction of
the same archive byte-for-byte (oracle cross-check).

=====================================================================
preserved-maxmb0.rar / preserved-maxmb0-expected.bin
=====================================================================
Row 2 coverage: the previously-working MaxMB<=1 population must stay green.
Payload: README.md (5,399 bytes) at generation time.
  rar a -ep1 -ma4 -m5 -mc10:1t+ preserved-maxmb0.rar preserved-maxmb0-expected.bin
memory=1 MB -> header MaxMB=0, which the pre-fix clamp ("if MaxMB > 1")
never touches -- extraction is identical before and after the fix (verified:
byte-identical under the untouched clamped code, 5,399/5,399 bytes).

=====================================================================
hostile-maxmb255.rar (byte-patched from legit-maxmb63.rar)
=====================================================================
SS4.3 class 2 ("inflated-resource headers over a valid stream"): the
compressed PPM stream is byte-identical to legit-maxmb63.rar except one
control byte, so the stream's true back-reference/context growth never
exceeds what a MaxMB=63 model already produced -- declaring a larger ceiling
to the decoder is semantically harmless in the same way an inflated LZ
dict-bits field is. No header-CRC refix is needed: this byte lives inside
the compressed data region (read raw via Unpack.getChar(), bypassing the
range coder), not inside the CRC-covered file header.

Patch (see main() below): absolute file offset 72 (0-indexed), 0x3F (99 ->
decimal MaxMB=63) -> 0xFF (decimal MaxMB=255, the one-byte format ceiling).
Offset located by instrumenting Unpack.getChar()/ModelPPM.decodeInit to print
the file's PositionInFile(20) + HeaderSize(51) + in-buffer read offset (1)
= 72 for the MaxMB byte (MaxOrder byte precedes it at offset 71 = 0xFE, whose
0x20 bit set selects the "reset" branch that reads MaxMB next).

512 MiB gradle test-worker analysis (docs/porting/MIGRATION_MANUAL.md SS8.3;
worker heap probed empirically via Runtime.maxMemory() in a throwaway test:
536,870,912 bytes = 512 MiB exactly, confirming the brief's stated cap):
MaxMB=255 -> SASize=256 -> SubAllocator.startSubAllocator allocates one
eager `new byte[realAllocSize]` where realAllocSize = t/12*12+12+1+4*38+12,
t=256<<20=268,435,456 -> ~268,435,633 bytes (~256 MiB). Probed directly
(SubAllocator.startSubAllocator(256) in isolation under a real gradle test
worker): total heap grew from 94,371,840 to 359,661,568 bytes against a
536,870,912 max -- ~150 MiB of headroom remains. MaxMB=255 (the true
one-byte ceiling) is used as-is; no smaller fallback value was needed.

Extraction outcome (both probed 2026-07-17, informational -- the shipped
test only requires "typed failure or success", never OOM/AIOOBE/hang):
pre-fix (clamped) hostile-maxmb255.rar -> CrcErrorException (same class of
divergence as the legit fixture, since MaxMB is clamped to 1 regardless of
the declared value); post-fix (clamp removed) -> succeeds, 48,524 bytes,
byte-identical to legit-maxmb63-expected.bin (the larger declared ceiling
never actually gets exercised by this small a stream).

=====================================================================
hostile-ppm-decodechar.rar (byte-patched from legit-maxmb63.rar)
=====================================================================
Patch absolute compressed-data offset 78, 0xb7 -> 0xff. Bounded probing
found this drives a nested escape PPM read to decodeChar()==-1; all four
public extraction surfaces terminate with a typed RarException on the
untouched implementation. The fixture is a defensive safety pin for the
required latch propagation.

Run: python3 generate_maxmb_fixtures.py
Regenerates all hostile fixtures next to this script from the already-committed
legit-maxmb63.rar (no external rar binary needed for this step).
"""
import hashlib
import pathlib

DIR = pathlib.Path(__file__).parent
LEGIT = DIR / "legit-maxmb63.rar"
HOSTILE = DIR / "hostile-maxmb255.rar"
PPM_ERROR_HOSTILE = DIR / "hostile-ppm-decodechar.rar"

LEGIT_SHA256 = "46d5f3196f150e9f384b657208e2ae9a45934c1bfbd95ecd507cb06ef18a2325"

PATCH_OFFSET = 72
BEFORE_BYTE = 0x3F  # MaxMB=63 (memory=64 MB), as generated by rar 6.24
AFTER_BYTE = 0xFF   # MaxMB=255 (memory=256 MB), the one-byte format ceiling

PPM_ERROR_PATCH_OFFSET = 78
PPM_ERROR_BEFORE_BYTE = 0xB7
PPM_ERROR_AFTER_BYTE = 0xFF
PPM_ERROR_HOSTILE_SHA256 = "9d4861d4e1a923071852198e3b3f1ee33240fabe8557fe22ff7f407ca59267e4"

PS_PATCHES = (
    ("hostile-ppm-ps-collect.rar", 254, 0x5E, 0xFF,
     "24a680b3b87f2a4f940120b31ad19d503d34fd30a56ce5415d328008fcaacf5b"),
    ("hostile-ppm-ps-count.rar", 85, 0x03, 0x00,
     "7609247ef7f9c719d25445bdb7570c063bf6046ae8bdc3800cdf4f4a914c9400"),
    ("hostile-ppm-ps-mask.rar", 199, 0x8D, 0x00,
     "77f7a23d893bbdd074071ab72552fb36124d1aebbcbc4d879ecd6cf85c4f6953"),
)


def main():
    source = LEGIT.read_bytes()
    source_sha256 = hashlib.sha256(source).hexdigest()
    if source_sha256 != LEGIT_SHA256:
        raise SystemExit(
            f"expected legit-maxmb63.rar sha256 {LEGIT_SHA256}, "
            f"got {source_sha256} -- fixture changed?"
        )
    data = bytearray(source)
    if data[PATCH_OFFSET] != BEFORE_BYTE:
        raise SystemExit(
            f"expected 0x{BEFORE_BYTE:02x} at offset {PATCH_OFFSET}, "
            f"got 0x{data[PATCH_OFFSET]:02x} -- legit-maxmb63.rar changed?"
        )
    if data[PATCH_OFFSET - 1] & 0x20 == 0:
        raise SystemExit(
            f"offset {PATCH_OFFSET - 1} (MaxOrder byte) must have the 0x20 "
            "reset bit set for the following byte to be read as MaxMB"
        )
    data[PATCH_OFFSET] = AFTER_BYTE
    HOSTILE.write_bytes(data)
    hostile_sha256 = hashlib.sha256(data).hexdigest()
    print(
        f"patched offset {PATCH_OFFSET}: 0x{BEFORE_BYTE:02x} -> 0x{AFTER_BYTE:02x}; "
        f"sha256={hostile_sha256}"
    )

    data = bytearray(source)
    if data[PPM_ERROR_PATCH_OFFSET] != PPM_ERROR_BEFORE_BYTE:
        raise SystemExit(
            f"expected 0x{PPM_ERROR_BEFORE_BYTE:02x} at offset {PPM_ERROR_PATCH_OFFSET}, "
            f"got 0x{data[PPM_ERROR_PATCH_OFFSET]:02x} -- legit-maxmb63.rar changed?"
        )
    data[PPM_ERROR_PATCH_OFFSET] = PPM_ERROR_AFTER_BYTE
    PPM_ERROR_HOSTILE.write_bytes(data)
    ppm_error_sha256 = hashlib.sha256(data).hexdigest()
    if ppm_error_sha256 != PPM_ERROR_HOSTILE_SHA256:
        raise SystemExit(
            f"generated {PPM_ERROR_HOSTILE.name} sha256 {ppm_error_sha256}, "
            f"expected {PPM_ERROR_HOSTILE_SHA256}"
        )
    print(
        f"patched offset {PPM_ERROR_PATCH_OFFSET}: "
        f"0x{PPM_ERROR_BEFORE_BYTE:02x} -> 0x{PPM_ERROR_AFTER_BYTE:02x}; "
        f"sha256={ppm_error_sha256}"
    )

    for name, offset, before_byte, after_byte, expected_sha256 in PS_PATCHES:
        data = bytearray(source)
        if data[offset] != before_byte:
            raise SystemExit(
                f"expected 0x{before_byte:02x} at offset {offset}, "
                f"got 0x{data[offset]:02x} -- legit-maxmb63.rar changed?"
            )
        data[offset] = after_byte
        output = DIR / name
        output.write_bytes(data)
        sha256 = hashlib.sha256(data).hexdigest()
        if sha256 != expected_sha256:
            raise SystemExit(
                f"generated {name} sha256 {sha256}, expected {expected_sha256}"
            )
        print(
            f"patched offset {offset}: 0x{before_byte:02x} -> 0x{after_byte:02x}; "
            f"sha256={sha256}"
        )


if __name__ == "__main__":
    main()
