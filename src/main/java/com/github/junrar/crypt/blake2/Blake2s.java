package com.github.junrar.crypt.blake2;

import com.github.junrar.io.Raw;

import java.util.Arrays;

/**
 * A single BLAKE2s-256 instance (M3.5, issue #26). Ports unrar {@code 8f437ab:blake2s.cpp} --
 * {@code blake2s_init_param}, the non-SSE {@code blake2s_update}/{@code blake2s_compress} and
 * {@code blake2s_final}. This is the leaf/root primitive used both standalone (RFC 7693 mode,
 * {@code initParam(0x01010020, 0, 0)}) and as the per-lane engine of {@link Blake2sp}.
 * <p>
 * Package-private: callers outside this package go through {@link Blake2sp} (the {@link DataHash}
 * unrar actually stores in RAR5 archives). {@code initParam} is exposed to the package so
 * {@link Blake2sp} can wire the 8-leaf + root tree parameters and the KAT test can drive the
 * standard unkeyed mode directly.
 */
final class Blake2s {

    static final int BLOCKBYTES = 64;
    static final int OUTBYTES = 32;

    static final int[] IV = {
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
        {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0}
    };

    final int[] h = new int[8];
    final int[] t = new int[2];
    final int[] f = new int[2];
    // 2*BLOCKBYTES lazy buffer, exactly as unrar (fills before compressing so the final block
    // is always known when finalize() runs).
    final byte[] buf = new byte[128];
    int buflen;
    boolean lastNode;

    /**
     * unrar {@code blake2s_init_param}: zeroes state, loads the IV, then XORs in the packed
     * parameter block words that select standalone vs. tree mode.
     */
    void initParam(final int word0, final int nodeOffset, final int word3) {
        Arrays.fill(buf, (byte) 0);
        buflen = 0;
        lastNode = false;
        t[0] = 0;
        t[1] = 0;
        f[0] = 0;
        f[1] = 0;
        for (int i = 0; i < 8; i++) {
            h[i] = IV[i];
        }
        h[0] ^= word0;
        h[2] ^= nodeOffset;
        h[3] ^= word3;
    }

    private void incrementCounter(final int inc) {
        t[0] += inc;
        if (Integer.compareUnsigned(t[0], inc) < 0) {
            t[1]++;
        }
    }

    private void setLastBlock() {
        if (lastNode) {
            f[1] = 0xFFFFFFFF;
        }
        f[0] = 0xFFFFFFFF;
    }

    private static void g(final int[] v, final int[] m, final int r, final int i,
                           final int a, final int b, final int c, final int d) {
        v[a] = v[a] + v[b] + m[SIGMA[r][2 * i]];
        v[d] = Integer.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 12);
        v[a] = v[a] + v[b] + m[SIGMA[r][2 * i + 1]];
        v[d] = Integer.rotateRight(v[d] ^ v[a], 8);
        v[c] = v[c] + v[d];
        v[b] = Integer.rotateRight(v[b] ^ v[c], 7);
    }

    private void compress(final byte[] block, final int off) {
        final int[] m = new int[16];
        for (int i = 0; i < 16; i++) {
            m[i] = Raw.readIntLittleEndian(block, off + i * 4);
        }
        final int[] v = new int[16];
        for (int i = 0; i < 8; i++) {
            v[i] = h[i];
        }
        v[8] = IV[0];
        v[9] = IV[1];
        v[10] = IV[2];
        v[11] = IV[3];
        v[12] = t[0] ^ IV[4];
        v[13] = t[1] ^ IV[5];
        v[14] = f[0] ^ IV[6];
        v[15] = f[1] ^ IV[7];

        for (int r = 0; r < 10; r++) {
            g(v, m, r, 0, 0, 4, 8, 12);
            g(v, m, r, 1, 1, 5, 9, 13);
            g(v, m, r, 2, 2, 6, 10, 14);
            g(v, m, r, 3, 3, 7, 11, 15);
            g(v, m, r, 4, 0, 5, 10, 15);
            g(v, m, r, 5, 1, 6, 11, 12);
            g(v, m, r, 6, 2, 7, 8, 13);
            g(v, m, r, 7, 3, 4, 9, 14);
        }

        for (int i = 0; i < 8; i++) {
            h[i] = h[i] ^ v[i] ^ v[i + 8];
        }
    }

    /** unrar {@code blake2s_update} (non-SSE path). */
    void update(final byte[] in, final int inOff, final int inLen) {
        int off = inOff;
        int len = inLen;
        while (len > 0) {
            final int left = buflen;
            final int fill = 128 - left;
            if (len > fill) {
                System.arraycopy(in, off, buf, left, fill);
                buflen += fill;
                incrementCounter(BLOCKBYTES);
                compress(buf, 0);
                System.arraycopy(buf, BLOCKBYTES, buf, 0, BLOCKBYTES);
                buflen -= BLOCKBYTES;
                off += fill;
                len -= fill;
            } else {
                System.arraycopy(in, off, buf, left, len);
                buflen += len;
                off += len;
                len = 0;
            }
        }
    }

    /** unrar {@code blake2s_final}. Mutates state; one-shot per instance. */
    void finalize(final byte[] out, final int outOff) {
        if (buflen > BLOCKBYTES) {
            incrementCounter(BLOCKBYTES);
            compress(buf, 0);
            buflen -= BLOCKBYTES;
            System.arraycopy(buf, BLOCKBYTES, buf, 0, buflen);
        }
        incrementCounter(buflen);
        setLastBlock();
        Arrays.fill(buf, buflen, 128, (byte) 0);
        compress(buf, 0);
        for (int i = 0; i < 8; i++) {
            Raw.writeIntLittleEndian(out, outOff + i * 4, h[i]);
        }
    }
}
