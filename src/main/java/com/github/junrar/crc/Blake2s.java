package com.github.junrar.crc;

import java.util.Arrays;

/**
 * BLAKE2s core (RFC 7693) initialised for use as a leaf or root node in
 * BLAKE2sp tree mode. Ported from blake2s.cpp/blake2sp.cpp in the official
 * unrar source. see {@link Blake2sp} for the public API.
 */
final class Blake2s {

    static final int BLAKE2S_BLOCKBYTES = 64;
    static final int BLAKE2S_OUTBYTES = 32;

    private static final int[] IV = {
            0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
            0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
    };

    private static final byte[][] SIGMA = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
            {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
            {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
            {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
            {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
    };

    final int[] h = new int[8];
    final int[] t = new int[2];
    final int[] f = new int[2];
    final byte[] buf = new byte[2 * BLAKE2S_BLOCKBYTES];
    int buflen;
    boolean lastNode;

    /**
     * XOR the IV with the BLAKE2sp parameter block (digest_len=32, fanout=8,
     * depth=2, inner_length=32, plus the per-node offset and depth). Resets
     * counters, finalisation flags and buffer first to mirror the C++
     * {@code blake2s_init_param} / {@code blake2s_state::init} sequence.
     */
    void initParam(int nodeOffset, int nodeDepth) {
        Arrays.fill(t, 0);
        Arrays.fill(f, 0);
        Arrays.fill(buf, (byte) 0);
        buflen = 0;
        lastNode = false;
        System.arraycopy(IV, 0, h, 0, 8);
        h[0] ^= 0x02080020;
        h[2] ^= nodeOffset;
        h[3] ^= (nodeDepth << 16) | 0x20000000;
    }

    void update(byte[] in, int off, int inlen) {
        while (inlen > 0) {
            int left = buflen;
            int fill = 2 * BLAKE2S_BLOCKBYTES - left;
            if (inlen > fill) {
                System.arraycopy(in, off, buf, left, fill);
                buflen += fill;
                incrementCounter(BLAKE2S_BLOCKBYTES);
                compress(buf, 0);
                System.arraycopy(buf, BLAKE2S_BLOCKBYTES, buf, 0, BLAKE2S_BLOCKBYTES);
                buflen -= BLAKE2S_BLOCKBYTES;
                off += fill;
                inlen -= fill;
            } else {
                System.arraycopy(in, off, buf, left, inlen);
                buflen += inlen;
                off += inlen;
                inlen = 0;
            }
        }
    }

    void finish(byte[] digest, int digestOff) {
        if (buflen > BLAKE2S_BLOCKBYTES) {
            incrementCounter(BLAKE2S_BLOCKBYTES);
            compress(buf, 0);
            buflen -= BLAKE2S_BLOCKBYTES;
            System.arraycopy(buf, BLAKE2S_BLOCKBYTES, buf, 0, buflen);
        }
        incrementCounter(buflen);
        f[0] = 0xFFFFFFFF;
        if (lastNode) {
            f[1] = 0xFFFFFFFF;
        }
        Arrays.fill(buf, buflen, 2 * BLAKE2S_BLOCKBYTES, (byte) 0);
        compress(buf, 0);
        for (int i = 0; i < 8; i++) {
            writeLE32(h[i], digest, digestOff + 4 * i);
        }
    }

    private void incrementCounter(int inc) {
        t[0] += inc;
        if (Integer.compareUnsigned(t[0], inc) < 0) {
            t[1]++;
        }
    }

    private void compress(byte[] block, int off) {
        int[] m = new int[16];
        int[] v = new int[16];
        for (int i = 0; i < 16; i++) {
            m[i] = readLE32(block, off + i * 4);
        }
        System.arraycopy(h, 0, v, 0, 8);
        v[8]  = IV[0];
        v[9]  = IV[1];
        v[10] = IV[2];
        v[11] = IV[3];
        v[12] = t[0] ^ IV[4];
        v[13] = t[1] ^ IV[5];
        v[14] = f[0] ^ IV[6];
        v[15] = f[1] ^ IV[7];

        for (int r = 0; r <= 9; r++) {
            g(r, 0, m, v, 0, 4, 8, 12);
            g(r, 1, m, v, 1, 5, 9, 13);
            g(r, 2, m, v, 2, 6, 10, 14);
            g(r, 3, m, v, 3, 7, 11, 15);
            g(r, 4, m, v, 0, 5, 10, 15);
            g(r, 5, m, v, 1, 6, 11, 12);
            g(r, 6, m, v, 2, 7, 8, 13);
            g(r, 7, m, v, 3, 4, 9, 14);
        }
        for (int i = 0; i < 8; i++) {
            h[i] ^= v[i] ^ v[i + 8];
        }
    }

    private static void g(int r, int i, int[] m, int[] v, int a, int b, int c, int d) {
        v[a] = v[a] + v[b] + m[SIGMA[r][2 * i] & 0xff];
        v[d] = Integer.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 12);
        v[a] = v[a] + v[b] + m[SIGMA[r][2 * i + 1] & 0xff];
        v[d] = Integer.rotateRight(v[d] ^ v[a], 8);
        v[c] = v[c] + v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 7);
    }

    private static int readLE32(byte[] b, int p) {
        return (b[p] & 0xff)
                | ((b[p + 1] & 0xff) << 8)
                | ((b[p + 2] & 0xff) << 16)
                | ((b[p + 3] & 0xff) << 24);
    }

    private static void writeLE32(int v, byte[] b, int p) {
        b[p]     = (byte) v;
        b[p + 1] = (byte) (v >>> 8);
        b[p + 2] = (byte) (v >>> 16);
        b[p + 3] = (byte) (v >>> 24);
    }
}
