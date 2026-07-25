#!/usr/bin/env python3
"""M3.11 regression-corpus flip audit (parity plan §4.4 step 3).

Classifies every corpus JSON changed between two git revisions into the three
allowed M3.11 shapes (review F10 + B-S2):

  (a) plain RAR5 member:      exception UnsupportedRarV5Exception -> null,
                              fileHeaders populated, isRarV5 retained.
  (b) file-encrypted (-p):    shape (a) plus isPasswordProtected -> true and
                              at least one populated fileHeader isEncrypted ==
                              true (real-world -p archives may mix encrypted
                              and plain entries - e.g. the cRARk distribution
                              member, verified against unrar lt; the pinned
                              invariant is isPasswordProtected == "any header
                              encrypted", junrar's isPasswordProtected()
                              semantics).
  (c) header-encrypted (-hp): exception UnsupportedRarV5Exception ->
                              WrongPasswordException (§5.3 pinned), headers
                              absent, isRarV5 retained (signature probe).

Any other changed file, any changed RAR3 member, or any field drift outside
the shape definition is a FINDING and the script exits non-zero: the merge is
blocked until it is investigated.

Usage: corpus_audit_m311.py --pre <rev> [--post <rev>] [--repo <dir>]
"""

import argparse
import json
import subprocess
import sys

CORPUS_PREFIX = "src/regressionTest/resources/corpus/"
V5_EXC = "UnsupportedRarV5Exception"
WRONG_PW_EXC = "WrongPasswordException"


def git(repo, *args):
    return subprocess.run(("git", "-C", repo) + args, check=True,
                          capture_output=True, text=True).stdout


def load(repo, rev, path):
    return json.loads(git(repo, "show", "%s:%s" % (rev, path)))


def base_pre_ok(old):
    """Every flipped member starts from the same pre-flip record shape."""
    return (old.get("exception") == V5_EXC and old.get("isRarV5") is True
            and old.get("fileHeaders") == [] and old.get("isEncrypted") is False
            and old.get("isPasswordProtected") is False
            and old.get("isOldFormat") is False)


def classify(old, new):
    if not base_pre_ok(old):
        return None, "pre-flip record is not the plain V5-exception shape"
    if (new.get("isRarV5") is not True or new.get("isOldFormat") is not False
            or new.get("isEncrypted") is not False):
        return None, "isRarV5/isOldFormat/isEncrypted drift"
    headers = new.get("fileHeaders") or []
    if new.get("exception") is None:
        if not headers:
            return None, "exception removed but fileHeaders empty"
        if new.get("isPasswordProtected") is True:
            if any(h.get("isEncrypted") for h in headers):
                return "b", None
            return None, "isPasswordProtected without any isEncrypted header"
        if new.get("isPasswordProtected") is False:
            if any(h.get("isEncrypted") for h in headers):
                return None, "encrypted header without isPasswordProtected"
            return "a", None
        return None, "isPasswordProtected missing"
    if new.get("exception") == WRONG_PW_EXC:
        if headers:
            return None, "WrongPasswordException with populated fileHeaders"
        if new.get("isPasswordProtected") is not False:
            return None, "fromException record must not set isPasswordProtected"
        return "c", None
    return None, "unexpected post-flip exception: %r" % new.get("exception")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pre", required=True, help="pre-flip revision")
    ap.add_argument("--post", default="HEAD", help="post-flip revision")
    ap.add_argument("--repo", default=".")
    args = ap.parse_args()

    changed = [line for line in git(
        args.repo, "diff", "--name-status", "%s..%s" % (args.pre, args.post)
    ).splitlines() if line]

    counts = {"a": 0, "b": 0, "c": 0}
    findings = []
    for line in changed:
        status, path = line.split("\t", 1)
        if not path.startswith(CORPUS_PREFIX) or not path.endswith(".json"):
            findings.append((path, "changed file outside the corpus JSON scope"))
            continue
        if status != "M":
            findings.append((path, "unexpected git status %r" % status))
            continue
        shape, why = classify(load(args.repo, args.pre, path),
                              load(args.repo, args.post, path))
        if shape is None:
            findings.append((path, why))
        else:
            counts[shape] += 1

    total = sum(counts.values())
    print("flipped members: %d  (a) plain: %d  (b) -p: %d  (c) -hp: %d"
          % (total, counts["a"], counts["b"], counts["c"]))
    if findings:
        print("\nFINDINGS (%d) - merge blocked:" % len(findings))
        for path, why in findings:
            print("  %s: %s" % (path, why))
        return 1
    print("audit clean: every change matches an allowed shape")
    return 0


if __name__ == "__main__":
    sys.exit(main())
