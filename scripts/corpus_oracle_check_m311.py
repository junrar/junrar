#!/usr/bin/env python3
"""M3.11 all-flipped-members oracle check (parity plan §4.4 step 4, review B-S2).

For EVERY corpus JSON flipped between two revisions (not a sample), compares
the regenerated record against the real unrar oracle:

  shapes (a)/(b) (listable):    `unrar lt -p- <member>` entry names, sizes and
                                mtimes must match the record's fileHeaders.
  shape (c) (header-encrypted): `unrar l -p- <member>` must fail with
                                "Incorrect password" and the record must carry
                                WrongPasswordException.

Usage: corpus_oracle_check_m311.py --pre <rev> [--post <rev>] [--repo <dir>]
           --corpus-root <extracted rar-corpus dir> [--unrar <binary>]

Run with TZ=UTC so unrar's local-time rendering matches the recorded Instants.
"""

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone

CORPUS_PREFIX = "src/regressionTest/resources/corpus/"
WRONG_PW_EXC = "WrongPasswordException"


def git(repo, *args):
    return subprocess.run(("git", "-C", repo) + args, check=True,
                          capture_output=True, text=True).stdout


def parse_lt(output):
    """unrar lt blocks -> list of {name, size, mtime, type}."""
    entries, cur = [], None
    for raw in output.splitlines():
        line = raw.strip()
        if line.startswith("Name: "):
            cur = {"name": line[6:], "size": None, "mtime": None, "type": None}
            entries.append(cur)
        elif cur is not None and line.startswith("Type: "):
            cur["type"] = line[6:]
        elif cur is not None and line.startswith("Size: ") and cur["size"] is None:
            cur["size"] = int(line[6:].replace(",", "").split()[0])
        elif cur is not None and line.startswith("mtime: "):
            cur["mtime"] = line[7:].split(",")[0]  # "2020-07-19 15:23:12,000000000 UTC"
    return [e for e in entries if e["type"] in ("File", "Directory")]


def record_headers(record):
    out = []
    for h in record.get("fileHeaders") or []:
        mtime = h.get("lastModifiedTime")  # jackson Instant: epoch seconds (float)
        out.append({
            "name": (h.get("fileName") or "").replace("\\", "/"),
            "size": h.get("fullUnpackSize"),
            "mtime": datetime.fromtimestamp(float(mtime), timezone.utc)
                     .strftime("%Y-%m-%d %H:%M:%S") if mtime is not None else None,
        })
    return out


def check_listable(unrar, member, record):
    proc = subprocess.run((unrar, "lt", "-p-", member),
                         capture_output=True, text=True)
    # rc=1 is unrar's WARNING exit (e.g. "Unexpected end of archive" on the many
    # truncated commoncrawl members) - the listing above the warning is still the
    # oracle; only rc>1 (fatal) fails the member.
    if proc.returncode > 1:
        return "unrar lt failed (rc=%d): %s" % (proc.returncode,
                                                proc.stderr.strip()[:200])
    oracle = parse_lt(proc.stdout)
    mine = record_headers(record)
    if len(oracle) != len(mine):
        return "entry count: unrar %d vs record %d" % (len(oracle), len(mine))
    for o, m in zip(oracle, mine):
        if o["name"].replace("\\", "/") != m["name"]:
            return "name: unrar %r vs record %r" % (o["name"], m["name"])
        if o["size"] is not None and m["size"] is not None and o["size"] != m["size"]:
            return "size of %s: unrar %d vs record %d" % (o["name"], o["size"], m["size"])
        if o["mtime"] and m["mtime"] and o["mtime"] != m["mtime"]:
            return "mtime of %s: unrar %r vs record %r" % (o["name"], o["mtime"], m["mtime"])
    return None


def check_header_encrypted(unrar, member, record):
    if record.get("exception") != WRONG_PW_EXC:
        return "record exception is %r, expected WrongPasswordException" % record.get("exception")
    proc = subprocess.run((unrar, "l", "-p-", member),
                         capture_output=True, text=True)
    blob = proc.stdout + proc.stderr
    if proc.returncode == 0:
        return "unrar listed a supposedly header-encrypted member"
    if "password" not in blob.lower():
        return "unrar failure is not password-shaped: %s" % blob.strip()[:200]
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pre", required=True)
    ap.add_argument("--post", default="HEAD")
    ap.add_argument("--repo", default=".")
    ap.add_argument("--corpus-root", required=True)
    ap.add_argument("--unrar", default=os.path.expanduser("~/.local/bin/unrar"))
    args = ap.parse_args()

    flipped = [p for p in git(args.repo, "diff", "--name-only",
                              "%s..%s" % (args.pre, args.post)).splitlines()
               if p.startswith(CORPUS_PREFIX) and p.endswith(".json")]

    ok = {"listable": 0, "hp": 0}
    problems = []
    for path in flipped:
        rel = path[len(CORPUS_PREFIX):-len(".json")]
        member = os.path.join(args.corpus_root, rel)
        if not os.path.isfile(member):
            problems.append((rel, "corpus member file not found"))
            continue
        record = json.loads(git(args.repo, "show", "%s:%s" % (args.post, path)))
        if record.get("exception") is None:
            why = check_listable(args.unrar, member, record)
            kind = "listable"
        else:
            why = check_header_encrypted(args.unrar, member, record)
            kind = "hp"
        if why:
            problems.append((rel, why))
        else:
            ok[kind] += 1

    print("oracle-checked %d flipped members: %d listable OK, %d header-encrypted OK, %d mismatches"
          % (len(flipped), ok["listable"], ok["hp"], len(problems)))
    for rel, why in problems:
        print("  MISMATCH %s: %s" % (rel, why))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
