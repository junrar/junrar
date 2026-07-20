package com.github.junrar.crypt.blake2;

import java.util.Arrays;

/**
 * BLAKE2sp: an 8-lane BLAKE2s tree hash, single-threaded (M3.5, issue #26). Ports unrar
 * {@code 8f437ab:blake2sp.cpp} ({@code blake2sp_init}, {@code blake2sp_update},
 * {@code blake2sp_final}) -- unrar's {@code RAR_SMP} multi-threaded split is irrelevant here, only
 * the data layout (which lane consumes which bytes) matters for a byte-identical digest.
 * <p>
 * This is the only checksum RAR5 stores under {@code Rar5HashType.BLAKE2} ({@code FHEXTRA_HASH}),
 * so it is the sole {@link DataHash} implementation (see that interface's javadoc).
 */
public final class Blake2sp implements DataHash {

    private static final int PAR = 8;
    private static final int BLOCKBYTES = 64;
    private static final int SUPER = PAR * BLOCKBYTES;
    private static final int OUTBYTES = 32;

    // BLAKE2sp param word0: digest_length=0x20, key_length=0, fanout=8, depth=2 (packed LE into
    // one u32 the same way unrar's blake2s_param struct packs its first four bytes).
    private static final int PARAM_WORD0 = 0x02080020;
    // word3 = (node_depth << 16) | inner_length(0x20) << 24 ... unrar packs leaf_length/node_offset
    // /node_depth/inner_length into the param struct; word3 here is the pre-computed leaf value
    // (node_depth=0) resp. root value (node_depth=1), per the spec transcription.
    private static final int LEAF_WORD3 = 0x20000000;
    private static final int ROOT_WORD3 = (1 << 16) | 0x20000000;

    private final Blake2s[] leaves = new Blake2s[PAR];
    private final Blake2s root = new Blake2s();
    private final byte[] buf = new byte[SUPER];
    private int buflen;

    public Blake2sp() {
        for (int i = 0; i < PAR; i++) {
            leaves[i] = new Blake2s();
        }
        init();
    }

    private void init() {
        Arrays.fill(buf, (byte) 0);
        buflen = 0;
        root.initParam(PARAM_WORD0, 0, ROOT_WORD3);
        for (int i = 0; i < PAR; i++) {
            leaves[i].initParam(PARAM_WORD0, i, LEAF_WORD3);
        }
        root.lastNode = true;
        leaves[PAR - 1].lastNode = true;
    }

    /** unrar {@code blake2sp_update}, single-thread collapse of the {@code RAR_SMP} split. */
    @Override
    public void update(final byte[] in, final int inOff, final int inLen) {
        int off = inOff;
        int len = inLen;
        int left = buflen;

        if (left != 0 && len >= SUPER - left) {
            final int fill = SUPER - left;
            System.arraycopy(in, off, buf, left, fill);
            for (int i = 0; i < PAR; i++) {
                leaves[i].update(buf, i * BLOCKBYTES, BLOCKBYTES);
            }
            off += fill;
            len -= fill;
            left = 0;
        }

        for (int id = 0; id < PAR; id++) {
            int p = off + id * BLOCKBYTES;
            int rem = len;
            while (rem >= SUPER) {
                leaves[id].update(in, p, BLOCKBYTES);
                p += SUPER;
                rem -= SUPER;
            }
        }

        final int consumed = len - (len % SUPER);
        off += consumed;
        len -= consumed;
        if (len > 0) {
            System.arraycopy(in, off, buf, left, len);
        }
        buflen = left + len;
    }

    /** unrar {@code blake2sp_final}. One-shot: mutates leaf/root state. */
    @Override
    public byte[] digest() {
        final byte[][] hash = new byte[PAR][OUTBYTES];
        for (int i = 0; i < PAR; i++) {
            if (buflen > i * BLOCKBYTES) {
                int l = buflen - i * BLOCKBYTES;
                if (l > BLOCKBYTES) {
                    l = BLOCKBYTES;
                }
                leaves[i].update(buf, i * BLOCKBYTES, l);
            }
            leaves[i].finalize(hash[i], 0);
        }
        for (int i = 0; i < PAR; i++) {
            root.update(hash[i], 0, OUTBYTES);
        }
        final byte[] out = new byte[OUTBYTES];
        root.finalize(out, 0);
        return out;
    }
}
