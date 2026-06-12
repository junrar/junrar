package com.github.junrar.crc;

import java.util.Arrays;

/**
 * BLAKE2sp — the parallel BLAKE2s tree mode used by RAR5 archives for
 * file-data hashing. Sequential implementation: produces the same 32-byte
 * digest as the multi-threaded reference in unrar/blake2sp.cpp.
 *
 * <p>Tree shape: 8 leaves (each a {@link Blake2s} configured with
 * {@code node_offset=i, node_depth=0}) absorb the input round-robin in
 * 64-byte stripes; their digests are concatenated and absorbed by the
 * root node ({@code node_offset=0, node_depth=1}) to produce the final
 * 32-byte hash.
 */
public final class Blake2sp {

    /** BLAKE2sp digest length in bytes. */
    public static final int DIGEST_SIZE = 32;

    private static final int LANES = 8;
    private static final int STRIPE = LANES * Blake2s.BLAKE2S_BLOCKBYTES;

    private final Blake2s[] leaves = new Blake2s[LANES];
    private final Blake2s root = new Blake2s();
    private final byte[] buf = new byte[STRIPE];
    private int buflen;

    public Blake2sp() {
        for (int i = 0; i < LANES; i++) {
            leaves[i] = new Blake2s();
        }
        reset();
    }

    public void reset() {
        for (int i = 0; i < LANES; i++) {
            leaves[i].initParam(i, 0);
        }
        leaves[LANES - 1].lastNode = true;
        root.initParam(0, 1);
        root.lastNode = true;
        Arrays.fill(buf, (byte) 0);
        buflen = 0;
    }

    public void update(byte[] in, int off, int inlen) {
        int left = buflen;
        int fill = STRIPE - left;
        if (left > 0 && inlen >= fill) {
            System.arraycopy(in, off, buf, left, fill);
            for (int i = 0; i < LANES; i++) {
                leaves[i].update(buf, i * Blake2s.BLAKE2S_BLOCKBYTES, Blake2s.BLAKE2S_BLOCKBYTES);
            }
            off += fill;
            inlen -= fill;
            left = 0;
        }

        // Walk each leaf at stride STRIPE, mirroring Blake2ThreadData::Update
        // in unrar/blake2sp.cpp.
        if (inlen >= STRIPE) {
            int wholeBytes = inlen - (inlen % STRIPE);
            for (int leafIdx = 0; leafIdx < LANES; leafIdx++) {
                int p = off + leafIdx * Blake2s.BLAKE2S_BLOCKBYTES;
                int remaining = wholeBytes;
                while (remaining >= STRIPE) {
                    leaves[leafIdx].update(in, p, Blake2s.BLAKE2S_BLOCKBYTES);
                    p += STRIPE;
                    remaining -= STRIPE;
                }
            }
            off += wholeBytes;
            inlen -= wholeBytes;
        }

        if (inlen > 0) {
            System.arraycopy(in, off, buf, left, inlen);
        }
        buflen = left + inlen;
    }

    /**
     * Finalises the hash. Must be called at most once per instance — finalising
     * mutates the underlying leaf and root states.
     *
     * @return the 32-byte BLAKE2sp digest
     */
    public byte[] digest() {
        byte[] out = new byte[DIGEST_SIZE];
        byte[][] leafDigest = new byte[LANES][DIGEST_SIZE];
        for (int i = 0; i < LANES; i++) {
            int laneOffset = i * Blake2s.BLAKE2S_BLOCKBYTES;
            if (buflen > laneOffset) {
                int avail = buflen - laneOffset;
                int take = Math.min(avail, Blake2s.BLAKE2S_BLOCKBYTES);
                if (take > 0) {
                    leaves[i].update(buf, laneOffset, take);
                }
            }
            leaves[i].finish(leafDigest[i], 0);
        }
        for (int i = 0; i < LANES; i++) {
            root.update(leafDigest[i], 0, DIGEST_SIZE);
        }
        root.finish(out, 0);
        return out;
    }
}
