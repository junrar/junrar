#!/bin/sh
# Regenerate the M3.10 (issue #31) RAR5 link fixtures. Deterministic; requires rar 7.23.
# No rar/unrar binary or upstream source is committed -- only the small archives, this
# generator, this recipe (README.md), and the hostile byte-patch helper (patch_hostile_targets.py).
set -eu
RAR="${RAR:-/Users/andre/.local/bin/rar}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

# ---- benign set: rar5-links.rar --------------------------------------------------------------
mkdir benign && cd benign
printf 'junrar M3.10 link fixture payload line\n' > file.txt          # 39 bytes
mkdir sub
printf 'inner payload for up-level symlink row\n' > sub/inner.txt      # 39 bytes
ln -s file.txt link-flat            # flat relative symlink
ln -s ../file.txt sub/link-up       # up-level relative symlink (stays inside)
ln file.txt hard.txt                # hardlink (shared inode)
i=1; : > copy-a.bin
while [ "$i" -le 128 ]; do printf 'copy payload line %03d abcdefghijklmno\n' "$i" >> copy-a.bin; i=$((i+1)); done
cp copy-a.bin copy-b.bin            # identical content -> rar stores copy-b as a FILE_COPY (-oi)
"$RAR" a -ma5 -m3 -ol -oh -oi1:1024 "$work/rar5-links.rar" \
    file.txt sub link-flat hard.txt copy-a.bin copy-b.bin >/dev/null
cd "$work"

# ---- hostile template: rar5-links-hostile.rar ------------------------------------------------
# All committed targets are BENIGN, equal-length placeholders. The Java tests byte-patch them to
# the hostile strings at runtime (rar refuses to create hostile symlinks), recomputing the block
# header CRC32 -- see patch_hostile_targets.py and ArchiveRar5LinkTest#patchTarget.
mkdir hostile && cd hostile
ln -s 'aaaaaaaaa' h-up          # patched -> ../../etc   (up-level escape, layer 5.2.5)
ln -s 'bbbbbbbbb' h-abs         # patched -> /etc/shd0   (absolute, layer 6.1.7)
ln -s 'ccccccccc' h-bsl         # patched -> ..\zz\bad   (backslash traversal, S5 cross-platform)
printf 'hardlink master payload\n' > h-file.txt
ln h-file.txt h-hard.txt        # redir target h-file.txt patched -> ../../pt.x (hardlink escape)
ln -s '.' dlnk                  # benign dir-symlink; write-through refused by layer 6.2.3
"$RAR" a -ma5 -m3 -ol -oh "$work/rar5-links-hostile.rar" h-up h-abs h-bsl h-file.txt h-hard.txt dlnk >/dev/null
mkdir dqqq
printf 'payload that must not be written through a dir-symlink\n' > dqqq/evil.txt
"$RAR" a -ma5 -m3 "$work/rar5-links-hostile.rar" dqqq/evil.txt >/dev/null  # name patched dqqq -> dlnk

cd "$work"
cp rar5-links.rar rar5-links-hostile.rar "$(dirname "$0")/"
echo "regenerated rar5-links.rar and rar5-links-hostile.rar"
