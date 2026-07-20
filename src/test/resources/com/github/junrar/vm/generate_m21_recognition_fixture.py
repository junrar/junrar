#!/usr/bin/env python3
"""Deterministic generator for the M2.1 (issue #20) VM-recognition acceptance fixture.

junrar's RAR3 VM interpreter is replaced by the unrar 5.5.1 *recognition* model: a VM
program's bytecode is fingerprinted (length, CRC32) against the 6 canonical standard
filters (E8, E8E9, ITANIUM, DELTA, RGB, AUDIO); anything else is a no-op filter
(FilteredDataSize=0), regardless of whether its single-byte XOR checksum
(RarVM::Prepare's XorSum/Code[0] check) is valid. The interesting acceptance case is a
program that is *checksum-valid but fingerprint-unrecognized* -- the only path where
old (full bytecode interpretation) and new (recognition-only) junrar can disagree, since
a checksum-invalid program was already routed to an empty/no-op command list before this
change (unchanged by M2.1).

This byte-patches one committed, legitimate fixture -- same technique and rationale as
generate_m13_limit_fixtures.py (a corrupt archive is corrupt however it was made; no
compressor is shipped, every mutation is recorded here with offsets + reason, and the
oracle is the real UnRAR 7.23 binary):

Source (SHA-256 asserted so the hard-coded byte offsets stay valid):
  audio/BoatModernEnglish-audio-text-unpack30.rar
    e7c6736027e069e35e0e431b52339d263d0c7807602440672627eecf4b44d2a6
    real RAR 1.5-container / Unpack29 archive; its BoatModernEnglish.wav member carries
    one RAR3 VMSF_AUDIO filter (216-byte canonical bytecode, CRC32 0xbc85e701).

Coordinates below were established by instrumented decode of Unpack.readVMCode /
Unpack.addVMCode / RarVM.prepare (temporary debug prints of BitInput.inAddr/inBit and a
literal-byte-window search against the source file): the filter's raw VM code occupies
file bytes [93, 311) (216 bytes, bit-shifted -- each VM code byte straddles two adjacent
file bytes since the filter block is not byte-aligned in the compressed bitstream, so
VM-code byte k's zone of influence is file bytes {93+k, 94+k, 95+k}).

Because RarVM.prepare's XorSum is a plain byte-wise XOR, and the file-bytes -> VM-code-
bytes mapping is bit-linear (shift/mask, no carries) with a *constant* bit-shift across
the whole code, XORing the identical one-byte mask at two file offsets F1 and F2 with
|F2-F1| large enough that their (up to 3-VM-code-byte-wide) zones of influence do not
overlap changes each affected VM-code byte by the *same* delta at both sites. Every
delta then appears exactly twice in XorSum's running total and cancels out: the checksum
stays valid, but the 216-byte code's content -- and so its CRC32 -- changes, so it can
no longer match the AUDIO (or any other) canonical fingerprint. FiltPos/BlockStart/
BlockLength/InitMask/VMCodeSize framing fields are untouched, so the archive still
parses cleanly up to and including this filter's installation.

Chosen offsets: F1=150, F2=166 (16 bytes apart, comfortably inside [93, 309) and far
more than 3 apart), mask 0xFF (bitwise complement) at each.

Observable behaviour recorded from UnRAR 7.23: the corrupt member fails ("checksum
error" / "Total errors: 1") after writing exactly 144 bytes -- the AUDIO filter's block
covers effectively the whole rest of the file in one shot, and a no-op filter (per the
recognition model) contributes zero bytes for its block, so only the small unfiltered
prefix before the block is ever written. junrar must match that exactly: on the pre-M2.1
base commit, RarVM's now-deleted general bytecode interpreter reinterprets these 216
(corrupted, but structurally valid) bytes as VM instructions and crashes with an
ArrayIndexOutOfBoundsException (RarVM.ExecuteCode -> RarVM.getValue ->
Raw.readIntLittleEndian), wrapped in a RarException. After M2.1, junrar recognizes
Type==VMSF_NONE, applies no filter, writes the same 144-byte prefix as UnRAR, and fails
extraction with a clean CrcErrorException -- never a raw index/array exception.
"""
import hashlib
import pathlib

DIR = pathlib.Path(__file__).parent
RES = DIR.parent  # .../com/github/junrar
SOURCE = RES / "audio" / "BoatModernEnglish-audio-text-unpack30.rar"
OUT = DIR / "audio-filter-noncanonical.rar"

SOURCE_SHA = "e7c6736027e069e35e0e431b52339d263d0c7807602440672627eecf4b44d2a6"

F1 = 150
F2 = 166
MASK = 0xFF


def sha(b):
    return hashlib.sha256(b).hexdigest()


def main():
    data = bytearray(SOURCE.read_bytes())
    actual_sha = sha(bytes(data))
    assert actual_sha == SOURCE_SHA, (
        f"source fixture changed (sha256 {actual_sha} != expected {SOURCE_SHA}); "
        "re-derive F1/F2 with instrumented decode before regenerating"
    )

    data[F1] ^= MASK
    data[F2] ^= MASK

    OUT.write_bytes(data)
    print(f"wrote {OUT} ({len(data)} bytes), sha256={sha(bytes(data))}")


if __name__ == "__main__":
    main()
