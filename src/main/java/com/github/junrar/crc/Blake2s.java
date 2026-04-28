package com.github.junrar.crc;

/**
 * Pure Java implementation of Blake2s (256-bit digest).
 *
 * <p>Based on RFC 7693 specification.
 * Blake2s is optimized for 8- to 32-bit platforms and produces digests
 * of any size between 1 and 32 bytes.
 *
 * <p>This implementation is not thread-safe. Each instance should be used
 * by a single thread.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7693">RFC 7693</a>
 */
public final class Blake2s {

    /** Size of the Blake2s digest in bytes (256 bits). */
    public static final int DIGEST_SIZE = 32;

    /** Block size in bytes (512 bits). */
    public static final int BLOCK_SIZE = 64;

    /** Double buffer size (2 blocks) for sliding window. */
    private static final int BUFFER_SIZE = 2 * BLOCK_SIZE;

    /** Number of rounds. */
    private static final int ROUNDS = 10;

    /** Initialization vector from RFC 7693 Section 2.6. */
    private static final int[] IV = {
        0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
        0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
    };

    /** Sigma permutation table from RFC 7693 Section 2.7. */
    private static final int[][] SIGMA = {
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

    private final int[] h = new int[8];
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int buflen;
    private int[] t = new int[2];
    private int[] f = new int[2];
    private boolean finalized;
    private final int digestSize;

    public Blake2s() {
        this(DIGEST_SIZE);
    }

    public Blake2s(final int digestSize) {
        if (digestSize < 1 || digestSize > DIGEST_SIZE) {
            throw new IllegalArgumentException("Digest size must be between 1 and " + DIGEST_SIZE);
        }
        this.digestSize = digestSize;
        reset();
    }

    public void reset() {
        System.arraycopy(IV, 0, h, 0, 8);
        h[0] ^= 0x01010000 ^ digestSize;
        buflen = 0;
        t[0] = 0;
        t[1] = 0;
        f[0] = 0;
        f[1] = 0;
        finalized = false;
    }

    void resetForBlake2spLane(final int laneIndex) {
        System.arraycopy(IV, 0, h, 0, 8);
        h[0] ^= 0x02080020;
        h[2] ^= laneIndex;
        h[3] ^= 0x20000000;
        buflen = 0;
        t[0] = 0;
        t[1] = 0;
        f[0] = 0;
        f[1] = 0;
        finalized = false;
    }

    void resetForTreeModeFinal() {
        System.arraycopy(IV, 0, h, 0, 8);
        h[0] ^= 0x02080020;
        h[2] ^= 0;
        h[3] ^= 0x20010000;
        buflen = 0;
        t[0] = 0;
        t[1] = 0;
        f[0] = 0;
        f[1] = 0;
        finalized = false;
    }

    public void update(final byte b) {
        if (finalized) {
            throw new IllegalStateException("Hasher has been finalized");
        }
        buffer[buflen++] = b;
        if (buflen == 2 * BLOCK_SIZE) {
            incrementCounter(BLOCK_SIZE);
            compress();
            System.arraycopy(buffer, BLOCK_SIZE, buffer, 0, BLOCK_SIZE);
            buflen -= BLOCK_SIZE;
        }
    }

    public void update(final byte[] data) {
        update(data, 0, data.length);
    }

    public void update(final byte[] data, final int offset, final int length) {
        if (finalized) {
            throw new IllegalStateException("Hasher has been finalized");
        }
        if (length == 0) {
            return;
        }

        int pos = offset;
        int remaining = length;

        while (remaining > 0) {
            final int fill = BUFFER_SIZE - buflen;

            if (remaining > fill) {
                System.arraycopy(data, pos, buffer, buflen, fill);
                buflen += fill;
                incrementCounter(BLOCK_SIZE);
                compress();
                System.arraycopy(buffer, BLOCK_SIZE, buffer, 0, BLOCK_SIZE);
                buflen -= BLOCK_SIZE;
                pos += fill;
                remaining -= fill;
            } else {
                System.arraycopy(data, pos, buffer, buflen, remaining);
                buflen += remaining;
                remaining = 0;
            }
        }
    }

    private void incrementCounter(final int inc) {
        t[0] += inc;
        t[1] += (t[0] < inc ? 1 : 0);
    }

    public byte[] digest() {
        if (finalized) {
            throw new IllegalStateException("Hasher has already been finalized");
        }

        if (buflen > BLOCK_SIZE) {
            incrementCounter(BLOCK_SIZE);
            compress();
            System.arraycopy(buffer, BLOCK_SIZE, buffer, 0, buflen - BLOCK_SIZE);
            buflen -= BLOCK_SIZE;
        }

        incrementCounter(buflen);
        setLastBlock();

        for (int i = buflen; i < BUFFER_SIZE; i++) {
            buffer[i] = 0;
        }

        compress();
        finalized = true;

        final byte[] out = new byte[digestSize];
        for (int i = 0; i < 8; i++) {
            final int hi = h[i];
            final int idx = i * 4;
            out[idx] = (byte) (hi & 0xFF);
            out[idx + 1] = (byte) ((hi >>> 8) & 0xFF);
            out[idx + 2] = (byte) ((hi >>> 16) & 0xFF);
            out[idx + 3] = (byte) ((hi >>> 24) & 0xFF);
        }

        return out;
    }

    private void setLastBlock() {
        if (f[1] != 0) {
            f[1] = 0xFFFFFFFF;
        }
        f[0] = 0xFFFFFFFF;
    }

    private void compress() {
        final int[] v = new int[16];
        v[0] = h[0];
        v[1] = h[1];
        v[2] = h[2];
        v[3] = h[3];
        v[4] = h[4];
        v[5] = h[5];
        v[6] = h[6];
        v[7] = h[7];
        v[8] = IV[0];
        v[9] = IV[1];
        v[10] = IV[2];
        v[11] = IV[3];
        v[12] = t[0] ^ IV[4];
        v[13] = t[1] ^ IV[5];
        v[14] = f[0] ^ IV[6];
        v[15] = f[1] ^ IV[7];

        final int[] m = new int[16];
        for (int i = 0; i < 16; i++) {
            final int idx = i * 4;
            m[i] = (buffer[idx] & 0xFF)
                 | ((buffer[idx + 1] & 0xFF) << 8)
                 | ((buffer[idx + 2] & 0xFF) << 16)
                 | ((buffer[idx + 3] & 0xFF) << 24);
        }

        for (int i = 0; i < ROUNDS; i++) {
            final int[] s = SIGMA[i % 10];
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]]);
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]]);
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);

            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]]);
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]]);
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
        }

        for (int i = 0; i < 8; i++) {
            h[i] = h[i] ^ v[i] ^ v[i + 8];
        }
    }

    private static void g(final int[] v, final int a, final int b,
                          final int c, final int d, final int x, final int y) {
        v[a] = v[a] + v[b] + x;
        v[d] = rotr32(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = rotr32(v[b] ^ v[c], 12);
        v[a] = v[a] + v[b] + y;
        v[d] = rotr32(v[d] ^ v[a], 8);
        v[c] = v[c] + v[d];
        v[b] = rotr32(v[b] ^ v[c], 7);
    }

    private static int rotr32(final int x, final int n) {
        return (x >>> n) | (x << (32 - n));
    }
}
