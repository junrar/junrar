#!/usr/bin/env python3
"""Construct the T4 fixture (docs/porting/MIGRATION_MANUAL.md SS6 T4,
docs/porting/PARITY_PLAN.md P0.6): rar3-nonascii-password.rar.

Binary procurement (docs/porting/PARITY_PLAN.md SS4.2): rar 7.23 cannot
create RAR4-format archives ("rar a -ma4 ..." -> "ERROR: Unknown option:
ma4"), so this RAR3/RAR4-era fixture uses the era rar 6.24 macOS x64 tarball
at https://www.rarlab.com/rar/rarmacos-x64-624.tar.gz (probed 2026-07-17:
HTTP 200, application/x-gzip, 607,872 bytes, gzip magic 1f 8b -- matches the
plan's pre-verified fingerprint). Not vendored; download+extract to
reproduce.

Format-default trap (probed 2026-07-17): rar 6.24 defaults to RAR5 output
even without any -ma5 switch ("rar a -hp<pw> f.rar file1.txt" -> unrar l
reports "Details: RAR 5, encrypted headers"). "-ma4" is REQUIRED to force
the RAR3/RAR4-compatible ("v29") format this fixture needs -- confirmed via
unrar lt afterward: "Compression: RAR 1.5(v29) -m5 -md=128k".

=====================================================================
rar3-nonascii-password.rar
=====================================================================
Payload: file1.txt, 17 bytes, content "nonascii-payload\n" (LF-terminated).
Password: "пароль密码ü"
  (Cyrillic "пароль" [password] + CJK
  "密码" [password] + Latin-1 "ü" [u-umlaut] -- deliberately
  mixes three encoding ranges so a naive single-byte-per-char translation
  (platform charset .getBytes()) cannot round-trip it, per T4's bug: RAR3's
  KDF wants UTF-16LE, not platform-charset bytes).
Header encryption ("-hp"): both the file data AND the file names/headers are
encrypted, matching the RAR3NonAsciiPasswordTest fixture shape (archive.
isEncrypted()==true, header decrypt must succeed before the file name is
even readable -- exercises the KDF path unconditionally, not just data
decrypt).

Regeneration (requires the rar 6.24 binary above, not vendored):
  printf 'nonascii-payload\n' > file1.txt
  rar a -ep1 -ma4 -m5 -hp'<password above>' rar3-nonascii-password.rar file1.txt

Verified (2026-07-17, real unrar 7.23 as oracle -- this fixture's password
is embedded in the archive's own protected headers, so a successful `unrar
lt -p<pw>` / `unrar x -p<pw>` round-trip through the SAME KDF unrar itself
uses is the fixture's own correctness proof, independent of junrar):
  unrar lt -p'<password>' rar3-nonascii-password.rar
    -> Compression: RAR 1.5(v29) -m5 -md=128k; CRC32: E557DA6B; Flags: encrypted
  unrar x -p'<password>' rar3-nonascii-password.rar
    -> file1.txt extracts byte-identical to the 17-byte payload above.

This script only documents the reproduction steps above (no external rar
binary is invoked here); the committed fixture is the artifact of record.
"""

if __name__ == "__main__":
    print(__doc__)
