#!/usr/bin/env python3
"""Construct the M3.4 fixture (docs/porting/PARITY_PLAN.md M3.4, issue #25):
rar5-nonascii-password.rar.

Binary procurement (docs/porting/PARITY_PLAN.md SS4.2): rar 7.23 (the same
release whose unrar is the project oracle, /Users/andre/.local/bin/rar).
Unlike the RAR3/RAR4 fixture (generate_nonascii_password_fixture.py), no
era-specific binary is needed: RAR5 (-ma5) is rar 7.23's native format.

=====================================================================
rar5-nonascii-password.rar
=====================================================================
Payload: file1.txt, 17 bytes, content "nonascii-payload\n" (LF-terminated).
Password: "пароль密码ü"
  (Cyrillic "пароль" [password] + CJK "密码" [password] + Latin-1 "ü"
  [u-umlaut] -- three encoding ranges, so a naive platform-charset
  .getBytes() cannot round-trip it. RAR5's KDF wants UTF-8 password bytes
  (WideToUtf, crypt5.cpp:158-160); this fixture exercises that.)
Header encryption ("-hp"): both the file data AND the file names/headers are
encrypted. archive.isEncrypted()==true; header decryption must succeed
(correct KDF + AES-256-CBC) before the file name is even readable, so this
exercises the RAR5 KDF/pswcheck path unconditionally, not just data decrypt.

Regeneration (rar 7.23; the committed .rar is the artifact of record -- RAR5
headers embed a creation timestamp, so regeneration is not byte-identical):
  printf 'nonascii-payload\n' > file1.txt
  rar a -ep1 -ma5 -m3 -hp'пароль密码ü' rar5-nonascii-password.rar file1.txt

Verified (2026-07-20, real unrar 7.23 as oracle -- the password lives in the
archive's own AES-encrypted headers, so a successful `unrar lt -p<pw>` through
the SAME KDF unrar itself uses is the fixture's own correctness proof,
independent of junrar):
  unrar lt -p'пароль密码ü' rar5-nonascii-password.rar
    -> Details: RAR 5, encrypted headers
    -> Name: file1.txt; CRC32: E557DA6B
    -> Compression: RAR 5.0(v50) -m3 -md=128k; Flags: encrypted

This script only documents the reproduction steps above (no external rar
binary is invoked here); the committed fixture is the artifact of record.
"""

if __name__ == "__main__":
    print(__doc__)
