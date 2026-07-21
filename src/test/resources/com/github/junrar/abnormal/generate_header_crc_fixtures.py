#!/usr/bin/env python3
"""Construct the P0.7 header-CRC fixtures (issue #12; docs/porting/PARITY_PLAN.md
SS3 "P0.7 -- header-CRC verification scaffolding (T7)"; docs/porting/MIGRATION_MANUAL.md
SS6 T7, SS4.3).

Algorithm under test (verified against real rar 6.24 output below, three independent
header types, before writing a single line of production code): 16-bit CRC =
zlib.crc32(header_bytes[2:end]) & 0xffff, stored little-endian as the header's first two
bytes. This is unrar's GetCRC15 (d861246:rawread.cpp): CRC32(0xffffffff, Data[2:], size)
returns the RAW (pre-final-invert) accumulator, and GetCRC15 does `~raw & 0xffff` --
bitwise-NOT of a pre-invert CRC32 accumulator is exactly the STANDARD (post-invert)
CRC-32 value, which is what zlib.crc32()/java.util.zip.CRC32 already return. No extra
inversion needed on our side.

=====================================================================
abnormal/bad-header-crc.rar (class 1, SS4.3 -- "a corrupt archive is corrupt however it
was made")
=====================================================================
Base: a genuine rar 6.24 archive (RAR3/v29 format, "-ma4"), one small stored file,
byte-patched to flip one bit of the FILE header's stored headCRC. Nothing else changes:
same filename, same file data, same data CRC -- only the header CRC field is wrong.
Binary procurement (docs/porting/PARITY_PLAN.md SS4.2, same rar 6.24 macOS x64 tarball
used by the P0.6 fixtures; not vendored):
  printf 'header-crc-fixture-payload\n' > payload.txt
  touch -t 202601010000 payload.txt   # deterministic embedded DOS timestamp
  rar a -ma4 -m0 base-plain.rar payload.txt

Patch: flip bit 0 of the FILE header's headCRC low byte (offset located by parsing the
base archive's own header chain, printed below at generation time).

RED (executed against the pre-P0.7 tree, no header-CRC verification at all): the archive
OPENS, lists, and EXTRACTS successfully -- the payload bytes are untouched, so nothing
notices the corrupted header CRC. AbnormalFilesTest's four-surface pattern therefore
FAILS red (expects CorruptHeaderException on *extraction*, gets none) until the P0.7
production edit lands.
GREEN (post-P0.7): archive still OPENS and lists (record + continue, unencrypted FILE
header); FileHeader.isBrokenHeader() is true; extracting that entry throws
CorruptHeaderException through all four public surfaces.

=====================================================================
abnormal/bad-crc-enc-headers.rar
=====================================================================
Base: a genuine rar 6.24 header-encrypted archive ("-hp<password>", password "secret"),
same payload as above:
  rar a -ma4 -m0 -hpsecret base-enc.rar payload.txt

CBC ciphertext cannot be patched surgically by flipping arbitrary bytes -- AES-CBC
decryption of a modified ciphertext block K produces a FULLY garbled plaintext block K
(not just the touched bits), so a naive ciphertext bit-flip anywhere in the FILE header's
first AES block would also destroy the type/flags/headerSize fields the read loop uses to
even recognize the header, and a flip in a later block still fully garbles that block's
other fields (nameSize, salt, filename, ...) as an unwanted side effect (verified
empirically: flipping ciphertext bytes this way sends parsing into an unrelated
pre-existing ArrayIndexOutOfBoundsException in FileHeader.parseExtTime, swallowed by
Archive#setChannel's blanket catch -- not a P0.7 regression, just the wrong tool for a
surgical single-field corruption).

Correct approach (this script): replicate RAR3's header-encryption KDF (Rijndael.java
buildDecipherer -- 0x40000-round SHA-1 loop, UTF-16LE password serialization,
crypt.cpp:188-286) in pure Python (stdlib hashlib only), decrypt the FILE header's
ciphertext with the archive's own embedded salt + the known password, flip one bit of
JUST the plaintext headCRC field, and re-encrypt with the SAME derived key/IV (AES-128-
CBC-NoPadding via the system `openssl` CLI). Every other plaintext byte -- type, flags,
headerSize, nameSize, filename, salt, extTimeFlags -- is untouched, so nothing except the
stored CRC is wrong. Verified independently: decrypting the archive's real ciphertext
with this KDF reproduces the FILE header's plaintext byte-for-byte, including the literal
filename bytes, before any patching.

Reproducibility note: rar embeds a fresh random salt per encrypted header on every run, so
re-running `rar a -hp...` will NOT byte-reproduce base-enc.rar (the ciphertext depends on
that random salt) even though the plaintext content is deterministic. The corruption
technique itself is salt-independent -- re-running this script end-to-end (including a
fresh `rar a -hp...`) reproduces a VALID instance of "encrypted-header archive, one bit of
FILE headCRC flipped, password 'secret'", just not byte-identical to the committed
artifact. The committed .rar is the artifact of record (sha256 below).

RED: archive construction with the correct password currently SUCCEEDS (no verification);
GREEN (post-P0.7): construction throws CorruptHeaderException (encrypted-header CRC
mismatch is fatal at open, matching unrar: decrypt succeeds, CRC still fails to match ->
stop).

=====================================================================
abnormal/sign-header-crc-mismatch.rar, av-header-crc-mismatch.rar,
abnormal/old-uo-header-crc-mismatch.rar (exemption pins)
=====================================================================
No modern rar/unrar build emits SIGN_HEAD (0x79, digital-signature block, removed
decades ago), AV_HEAD (0x76, antivirus-report block), or a HEAD3_OLDSERVICE (0x77)
sub-block with SubType=UO_HEAD (0x101, legacy RAR-for-Unix owner/group names) -- same
situation as C7's protect-header-seek.rar (SS4.3 class 1: hand-assembled byte-for-byte).
Each fixture is Mark(7) + Main(13, unencrypted) + <the exempt header, headCRC
deliberately wrong: 0x0000> + EndArc(7). Per the issue's contract these three header
kinds skip verification entirely (upstream: "Old AV header does not have header CRC
properly set"; old Unix-owner sub-blocks "didn't include string fields into header size,
but included them into CRC, so it couldn't be verified with generic approach") -- they
must parse both BEFORE this chunk (no check existed) and AFTER (check exists but skips
these three types), so these are pin tests guarding against an over-strict
implementation, not corrupt-archive tests.

Run: python3 generate_header_crc_fixtures.py
Regenerates all five fixtures next to this script (requires the rar 6.24 binary above
and `openssl` on PATH for the first two; the exemption fixtures need neither).

=====================================================================
abnormal/lhd-comment-dual-crc.rar (issue #38 item 2, P0.7 Finding A)
=====================================================================
unrar runs TWO CRC checks on FILE/SERVICE headers: the CRCProcessedOnly-aware inline
check (d861246:arcread.cpp:430-445), which for an old-style LHD_COMMENT header covers
only the parsed fields (the trailing embedded comment blob is unread), and an
unconditional generic check re-run after the switch (:514-522) that always covers the
FULL header buffer and does not exempt FILE/SERVICE. junrar's P0.7 chunk implemented
only the first -- for hasComment()==false (every corpus-observed archive) the two
computations degenerate to the same value, so the gap is corpus-silent; for a genuine
LHD_COMMENT header the single check is measurably more lenient than unrar's dual check.

Base: bad-header-crc.rar's own (pristine, 1-bit-flip undone) FILE header -- same
class-1 "hand-assembled/byte-patched, corrupt however it was made" rationale as that
fixture and the three exemption fixtures below (no writer tool produces LHD_COMMENT
archives anymore; this deliberately targets the dual-check code path, not a claim about
a historical writer's exact CRC convention). Patch: set the LHD_COMMENT flag bit
(0x0008), append a 40-byte comment-blob tail after the original header body, grow
HeadSize to match, and set the stored headCRC to the NARROW-coverage value (type+flags+
hsize+original body, i.e. exactly what today's single check accepts) -- the full-buffer
CRC (same bytes plus the appended tail) differs by construction.

RED (pre-fix, single narrow-only check): archive opens; FileHeader.hasComment() is
true; FileHeader.isBrokenHeader() is FALSE (the narrow check matches); extraction
succeeds. GREEN (post-fix, dual check): isBrokenHeader() is true (the added full-buffer
check catches the mismatch the narrow check misses); extraction throws
CorruptHeaderException (junrar's existing conscious refuse-on-broken-header policy).

Reproducible without the rar 6.24 binary -- derives everything from the already-
committed bad-header-crc.rar.
"""
import hashlib
import pathlib
import struct
import subprocess
import sys
import zlib

DIR = pathlib.Path(__file__).parent
RAR624 = pathlib.Path(
    "/private/tmp/claude-501/-Users-andre-git-pfBlockerNG/"
    "0ce07ab1-e80a-47e1-a1db-aa0f473eeb89/scratchpad/rar624/rar/rar"
)
PASSWORD = "secret"


def le16(v):
    return struct.pack("<H", v & 0xFFFF)


def le32(v):
    return struct.pack("<I", v & 0xFFFFFFFF)


def crc16(header_from_offset2: bytes) -> int:
    """unrar GetCRC15 (d861246:rawread.cpp): standard CRC-32 over the header bytes
    starting at offset 2 (skipping the stored headCRC field itself), low 16 bits."""
    return zlib.crc32(header_from_offset2) & 0xFFFF


def valid_main_header() -> bytes:
    """MarkHeader-following MainHeader with a REAL (matching) CRC -- highPosAv=0,
    posAv=0, no MHD_ENCRYPTVER. Used as the header immediately before the
    deliberately-mis-CRCed exempt header in the hand-assembled fixtures below, so the
    corruption is isolated to the one header under test."""
    body = le16(0) + le32(0)  # highPosAv, posAv
    rest = bytes([0x73]) + le16(0) + le16(7 + len(body)) + body
    return le16(crc16(rest)) + rest


def parse_headers(data: bytes):
    """Minimal RAR3 header-chain walker -- enough to locate header boundaries for the
    byte-patch fixtures below (mirrors Archive.java's own read loop shape)."""
    headers = []
    pos = 7  # past MarkHeader
    while pos < len(data):
        crc, htype, flags, hsize = struct.unpack_from("<HBHH", data, pos)
        headers.append((pos, crc, htype, flags, hsize))
        if htype in (0x74, 0x7A):  # FILE_HEAD / NEWSUB_HEAD
            packsize = struct.unpack_from("<I", data, pos + 7)[0]
            pos = pos + hsize + packsize
        elif htype == 0x7B:  # ENDARC
            break
        else:
            pos = pos + hsize
    return headers


# ============================= KDF (Rijndael.java port) ============================

def derive_key_iv(password: str, salt: bytes):
    """Pure-Python replica of Rijndael.buildDecipherer's key schedule (0x40000-round
    SHA-1 loop over UTF-16LE password + salt, per-uint32 byte-swapped digest -> AES key).
    Verified byte-for-byte against decrypting a real rar-624 header (see docstring)."""
    aes_init = bytearray(16)
    pwd = password.encode("utf-16-le")
    rawpsw = pwd + salt
    hash_rounds = 0x40000
    xh = hash_rounds // 16
    buf = bytearray()
    digest = None
    for i in range(hash_rounds):
        buf += rawpsw
        buf.append(i & 0xFF)
        buf.append((i >> 8) & 0xFF)
        buf.append((i >> 16) & 0xFF)
        if i % xh == 0:
            digest = hashlib.sha1(bytes(buf)).digest()
            aes_init[i // xh] = digest[19]
    digest = hashlib.sha1(bytes(buf)).digest()
    aes_key = bytearray(16)
    for i in range(4):
        word = (digest[i * 4] << 24) | (digest[i * 4 + 1] << 16) | (digest[i * 4 + 2] << 8) | digest[i * 4 + 3]
        for j in range(4):
            aes_key[i * 4 + j] = (word >> (j * 8)) & 0xFF
    return bytes(aes_key), bytes(aes_init)


def aes_cbc_nopad(key: bytes, iv: bytes, data: bytes, decrypt: bool) -> bytes:
    mode = "-d" if decrypt else "-e"
    proc = subprocess.run(
        ["openssl", "enc", mode, "-aes-128-cbc", "-K", key.hex(), "-iv", iv.hex(), "-nopad"],
        input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"openssl failed: {proc.stderr.decode()}")
    return proc.stdout


# ================================ fixture builders ==================================

def make_bad_header_crc():
    out = DIR / "bad-header-crc.rar"
    src = DIR / "_base-plain.rar.tmp"
    payload_name = "payload.txt"
    payload = DIR / payload_name
    payload.write_bytes(b"header-crc-fixture-payload\n")
    import os
    os.utime(payload, (1767225600, 1767225600))  # 2026-01-01T00:00:00Z, deterministic
    subprocess.run([str(RAR624), "a", "-ma4", "-m0", str(src.name), payload_name],
                    check=True, stdout=subprocess.DEVNULL, cwd=str(DIR))
    data = bytearray(src.read_bytes())
    headers = parse_headers(bytes(data))
    file_hdr = next(h for h in headers if h[2] == 0x74)
    pos = file_hdr[0]
    stored_crc = file_hdr[1]
    computed = crc16(bytes(data[pos + 2: pos + file_hdr[4]]))
    assert stored_crc == computed, f"base archive's own CRC didn't verify: {stored_crc:#x} != {computed:#x}"
    data[pos] ^= 0x01  # flip one bit of the stored headCRC low byte -- nothing else changes
    out.write_bytes(data)
    src.unlink()
    payload.unlink()
    print(f"wrote {out} ({len(data)} bytes), FILE header @0x{pos:x} "
          f"stored crc 0x{stored_crc:04x} -> 0x{stored_crc ^ 0x01:04x} (real crc 0x{computed:04x})")


def make_bad_crc_enc_headers():
    out = DIR / "bad-crc-enc-headers.rar"
    src = DIR / "_base-enc.rar.tmp"
    payload_name = "payload.txt"
    payload = DIR / payload_name
    payload.write_bytes(b"header-crc-fixture-payload\n")
    import os
    os.utime(payload, (1767225600, 1767225600))
    subprocess.run([str(RAR624), "a", "-ma4", "-m0", f"-hp{PASSWORD}", str(src.name), payload_name],
                    check=True, stdout=subprocess.DEVNULL, cwd=str(DIR))
    data = bytearray(src.read_bytes())

    main_crc, main_type, main_flags, main_hsize = struct.unpack_from("<HBHH", data, 7)
    assert main_type == 0x73 and (main_flags & 0x0080) != 0, "base archive is not header-encrypted"

    salt_off = 7 + main_hsize
    salt = bytes(data[salt_off: salt_off + 8])
    key, iv = derive_key_iv(PASSWORD, salt)

    cipher_off = salt_off + 8
    # Decrypt a generous probe window first to learn the FILE header's declared size,
    # then decrypt exactly the padded ciphertext span it actually occupies. Clamp to
    # what the archive actually has left (block-aligned) -- the FILE header's ciphertext
    # is followed by the (also encrypted) file data, not unbounded padding.
    probe_window = min(256, (len(data) - cipher_off) // 16 * 16)
    probe_ciphertext = bytes(data[cipher_off: cipher_off + probe_window])
    probe_plaintext = aes_cbc_nopad(key, iv, probe_ciphertext, decrypt=True)

    file_crc, file_type, file_flags, file_hsize = struct.unpack_from("<HBHH", probe_plaintext, 0)
    assert file_type == 0x74, f"expected FILE header, got type=0x{file_type:02x}"
    padded_len = file_hsize + ((-file_hsize) & 0xF)
    assert padded_len <= probe_window, f"FILE header padded length {padded_len} exceeds probe window {probe_window}"

    ciphertext = bytes(data[cipher_off: cipher_off + padded_len])
    plaintext = bytearray(aes_cbc_nopad(key, iv, ciphertext, decrypt=True))
    real_crc = crc16(bytes(plaintext[2:file_hsize]))
    assert real_crc == file_crc, f"decrypted header CRC didn't verify: {real_crc:#x} != {file_crc:#x}"

    plaintext[0] ^= 0x01  # flip one bit of the stored headCRC low byte -- plaintext-exact patch
    new_ciphertext = aes_cbc_nopad(key, iv, bytes(plaintext), decrypt=False)
    assert len(new_ciphertext) == len(ciphertext)

    data[cipher_off: cipher_off + padded_len] = new_ciphertext
    out.write_bytes(data)
    src.unlink()
    payload.unlink()
    print(f"wrote {out} ({len(data)} bytes), FILE header ciphertext @0x{cipher_off:x} "
          f"re-encrypted with headCRC 0x{file_crc:04x} -> 0x{file_crc ^ 0x01:04x} "
          f"(password '{PASSWORD}')")


def make_sign_header_fixture():
    out = DIR / "sign-header-crc-mismatch.rar"
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])
    main = valid_main_header()
    sign_body = le32(0) + le16(0) + le16(0)  # creationTime, arcNameSize, userNameSize
    assert len(sign_body) == 8
    sign = le16(0x0000) + bytes([0x79]) + le16(0) + le16(7 + 8) + sign_body
    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)
    archive = mark + main + sign + end_arc
    out.write_bytes(archive)
    print(f"wrote {out} ({len(archive)} bytes), SIGN header headCRC=0x0000 (real crc would be "
          f"0x{crc16(sign[2:]):04x})")


def make_av_header_fixture():
    out = DIR / "av-header-crc-mismatch.rar"
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])
    main = valid_main_header()
    av_body = bytes([0, 0, 0]) + le32(0)  # unpackVersion, method, avVersion, avInfoCRC
    assert len(av_body) == 7
    av = le16(0x0000) + bytes([0x76]) + le16(0) + le16(7 + 7) + av_body
    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)
    archive = mark + main + av + end_arc
    out.write_bytes(archive)
    print(f"wrote {out} ({len(archive)} bytes), AV header headCRC=0x0000 (real crc would be "
          f"0x{crc16(av[2:]):04x})")


def make_old_uo_header_fixture():
    out = DIR / "old-uo-header-crc-mismatch.rar"
    mark = bytes([0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00])
    main = valid_main_header()

    owner = b"root"
    group = b"wheel"
    # UnixOwnersHeader.java's bounds check is `pos + groupNameSize < uoHeader.length`
    # (strict, pre-existing, unrelated to P0.7) -- the group name is only read if at
    # least one byte of slack follows it, so pad the sub-block body by one trailing
    # byte to exercise the intended owner+group readback rather than a pre-existing,
    # off-topic off-by-one.
    uo_body = le16(len(owner)) + le16(len(group)) + owner + group + b"\x00"
    sub_block_header = le16(0x0101) + bytes([0])  # subType=UO_HEAD, level=0
    block_header = le32(0)  # packSize=0, no trailing sub-block payload
    body = block_header + sub_block_header + uo_body
    header_size = 7 + len(body)
    sub = le16(0x0000) + bytes([0x77]) + le16(0) + le16(header_size) + body
    end_arc = le16(0x3DC4) + bytes([0x7B]) + le16(0x4000) + le16(7)
    archive = mark + main + sub + end_arc
    out.write_bytes(archive)
    print(f"wrote {out} ({len(archive)} bytes), old-UO sub-block headCRC=0x0000 (real crc would be "
          f"0x{crc16(sub[2:]):04x})")


def make_lhd_comment_dual_crc():
    """Issue #38 item 2 (P0.7 Finding A): derives from the already-committed
    bad-header-crc.rar, so it needs neither the rar 6.24 binary nor openssl."""
    out = DIR / "lhd-comment-dual-crc.rar"
    base = DIR / "bad-header-crc.rar"
    data = bytearray(base.read_bytes())

    headers = parse_headers(bytes(data))
    file_hdr = next(h for h in headers if h[2] == 0x74)
    pos, _stored_crc, htype, flags, hsize = file_hdr
    assert htype == 0x74

    orig_body = bytes(data[pos + 7: pos + hsize])
    assert len(orig_body) == hsize - 7

    LHD_COMMENT = 0x0008
    new_flags = flags | LHD_COMMENT
    comment_blob = b"OLD-STYLE-COMMENT-BLOB-PATCH-TEST-DATA-"  # 40 bytes, arbitrary
    new_hsize = hsize + len(comment_blob)

    # Narrow coverage (BaseBlockSize=7 + blockHeaderSize=4 + parsedLength(=hsize-11))
    # equals the ORIGINAL hsize -- i.e. header[2:hsize_orig]: type(1)+flags(2)+hsize(2)+
    # orig_body(hsize_orig-7).
    narrow_input = bytes([0x74]) + le16(new_flags) + le16(new_hsize) + orig_body
    full_input = narrow_input + comment_blob

    narrow_crc = crc16(narrow_input)
    full_crc = crc16(full_input)
    assert narrow_crc != full_crc, "comment blob must change the full-buffer CRC"

    new_prefix = le16(narrow_crc) + narrow_input[:5]  # crc(2) + type(1)+flags(2)+hsize(2)
    new_header = new_prefix + orig_body + comment_blob
    assert len(new_header) == new_hsize

    new_data = bytes(data[:pos]) + new_header + bytes(data[pos + hsize:])
    out.write_bytes(new_data)
    print(f"wrote {out} ({len(new_data)} bytes), FILE header @0x{pos:x} narrow crc "
          f"0x{narrow_crc:04x} (stored, matches today's single check) vs full crc "
          f"0x{full_crc:04x} (mismatches -- the check the dual-CRC fix adds)")


def main():
    if not RAR624.exists():
        print(f"rar 6.24 binary not found at {RAR624}; the two byte-patched-from-real-rar "
              f"fixtures (bad-header-crc.rar, bad-crc-enc-headers.rar) cannot be regenerated. "
              f"The three hand-assembled exemption fixtures and the dual-CRC fixture do not "
              f"need it (the latter derives from the committed bad-header-crc.rar).", file=sys.stderr)
    else:
        make_bad_header_crc()
        make_bad_crc_enc_headers()
    make_sign_header_fixture()
    make_av_header_fixture()
    make_old_uo_header_fixture()
    make_lhd_comment_dual_crc()


if __name__ == "__main__":
    main()
