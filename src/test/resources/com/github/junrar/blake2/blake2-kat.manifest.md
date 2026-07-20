# BLAKE2 known-answer test vectors

`blake2s-kat.json` and `blake2sp-kat.json` are the **unkeyed** subsets of the
official BLAKE2 reference test vectors, `testvectors/blake2-kat.json` from
<https://github.com/BLAKE2/BLAKE2> (public domain, CC0).

Each file is an array of `{"in": <hex>, "out": <hex>}` objects: 256 entries, one
per input length 0..255, where the input is the byte sequence `00 01 02 ...`.

Only the unkeyed entries (`key == ""`) are kept, because unrar's BLAKE2sp usage
is always unkeyed (`d861246:blake2s.cpp` never sets a key); the reference file's
keyed entries exercise a key path unrar does not have.

Cross-check: the length-0 `blake2sp` vector is
`dd0e891776933f43c7d032b08a917e25741f8aa9a12c12e1cac8801500f2ca4f`, byte-identical
to the empty-data BLAKE2sp constant hard-coded in unrar `8f437ab:hash.cpp`
(`HashValue::Init`), confirming the reference vectors match the RAR variant.

Regeneration:

    curl -sSL https://raw.githubusercontent.com/BLAKE2/BLAKE2/master/testvectors/blake2-kat.json -o blake2-kat.json
    python3 -c "import json; d=json.load(open('blake2-kat.json')); \
      f=lambda h:[{'in':e['in'],'out':e['out']} for e in d if e['hash']==h and e['key']=='']; \
      json.dump(f('blake2s'), open('blake2s-kat.json','w')); \
      json.dump(f('blake2sp'), open('blake2sp-kat.json','w'))"
