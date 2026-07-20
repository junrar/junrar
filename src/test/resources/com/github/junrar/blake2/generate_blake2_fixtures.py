#!/usr/bin/env python3
"""Construct the M3.5 fixtures (docs/porting/PARITY_PLAN.md M3.5, issue #26):
rar5-htb.rar, rar5-htb-mac.rar and blake2-payload.bin.

Binary procurement (docs/porting/PARITY_PLAN.md SS4.2): rar 7.23, the same
release whose unrar is the project oracle (/Users/andre/.local/bin/rar). RAR5
(-ma5) is rar 7.23's native format, so no era-specific binary is needed.

The payload is deterministic so the tests can reconstruct the exact plaintext
and hash it: 2000 bytes, byte i = (i * 37 + 11) % 256. 2000 bytes span several
512-byte BLAKE2sp super-blocks (8 leaves x 64 bytes = 512), so the fixture
exercises the sp tree's leaf distribution, not just a single block.

    data = bytes((i * 37 + 11) % 256 for i in range(2000))
    open("blake2-payload.bin", "wb").write(data)

=====================================================================
rar5-htb.rar -- unencrypted, BLAKE2sp checksum (-htb)
=====================================================================
Regeneration (rar 7.23; the committed .rar is the artifact of record -- RAR5
headers embed a creation timestamp, so regeneration is not byte-identical):
    rar a -ep1 -ma5 -m3 -htb rar5-htb.rar blake2-payload.bin

No key, so the stored FHEXTRA_HASH digest is the raw BLAKE2sp of the payload.
Verified (2026-07-20, real unrar 7.23 as oracle):
    unrar lta rar5-htb.rar
      -> Name: blake2-payload.bin
      -> BLAKE2: 78ed63477dbe9caf6ac85c0efcddc626a1a6f73d224a056cf263521153f72c83
    unrar t rar5-htb.rar        -> All OK
So BLAKE2sp(payload) == 78ed6347...2c83 (independent oracle for the port).

=====================================================================
rar5-htb-mac.rar -- data-only encryption (-p), BLAKE2 MAC (HASHMAC)
=====================================================================
Password: "junrar". Data-only encryption (-p, NOT -hp): the headers stay
readable, so junrar parses the FHEXTRA_HASH digest and the FHEXTRA_CRYPT
salt/Lg2Count without header decryption, derives the HashKey via the KDF, and
verifies the MAC. Header encryption (-hp) would hide the checksum inside the
encrypted header, so rar does NOT set the HASHMAC flag under -hp -- only under
-p (probed 2026-07-20). This is the fixture that runs M3.4's deferred HASHMAC
end-to-end row (Rar5Crypt.convertBlake2ToMac).

Regeneration (the salt is random per creation, so the MAC below is specific to
the committed artifact; regenerating produces a different MAC):
    rar a -ep1 -ma5 -m3 -htb -pjunrar rar5-htb-mac.rar blake2-payload.bin

Verified (2026-07-20, real unrar 7.23 as oracle):
    unrar lta -pjunrar rar5-htb-mac.rar
      -> Name: blake2-payload.bin; Flags: encrypted
      -> BLAKE2 MAC: 28918c0ba5edd97e4f1ffa2214bd76c3b1896e812530fecde38adea725bbd357
    unrar t -pjunrar rar5-htb-mac.rar   -> All OK
So the stored digest is the MAC, and
    HMAC-SHA256(HashKey, BLAKE2sp(payload)) == 28918c0b...d357
with HashKey = KDF(password="junrar", salt=<FHEXTRA_CRYPT salt>, Lg2Count).

This script only documents the reproduction steps above (no external rar binary
is invoked here); the committed fixtures are the artifacts of record.
"""

if __name__ == "__main__":
    print(__doc__)
