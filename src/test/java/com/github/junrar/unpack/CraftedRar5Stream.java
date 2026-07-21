/*
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 */
package com.github.junrar.unpack;

import com.github.junrar.unpack.decode.Compress;

import java.io.ByteArrayOutputStream;

/**
 * Crafted RAR5/RAR7 packed streams for engine-level tests (M4.2, issue #34), extending the M3.6
 * crafted-table and M3.8 hostile-row precedent. junrar can never ship a compressor and no
 * {@code rar} build emits the streams these tests need — the extended distance slots only appear
 * above a 4 GB dictionary — so the bit stream is assembled by hand.
 */
final class CraftedRar5Stream {

    private CraftedRar5Stream() {
    }

    /**
     * Write the BD (bit-length) table as an identity map — symbols 0..15 carry their own value as
     * a 4-bit code — followed by a main table sized for {@code dCodes} distance slots.
     *
     * <p>The main table gives literal {@code 'A'} the 1-bit code {@code 0} and match slot 262 the
     * 2-bit code {@code 10} (symbol 300 completes the length-2 pair), then hands the top two
     * distance slots and the top two low-distance symbols the 1-bit codes {@code 0} and {@code 1}.
     * Canonical order assigns equal-length codes by ascending symbol, so the higher symbol of each
     * pair is the {@code 1} code. Sizing the table from {@code dCodes} is the point: with
     * {@code ExtraDist} the LDD and RD slices sit 16 entries further along
     * ({@code d861246:unpack50.cpp:710}).
     */
    static void writeTables(final BitWriter bw, final int dCodes) {
        for (int i = 0; i < Compress.BC5; i++) {
            bw.writeBits(i < 16 ? 4 : 0, 4);
        }
        final int ddOff = Compress.NC5;
        final int lddOff = ddOff + dCodes;
        final int tableSize = Compress.NC5 + dCodes + Compress.RC5 + Compress.LDC5;
        for (int i = 0; i < tableSize; i++) {
            final int len;
            if (i == 'A') {
                len = 1;
            } else if (i == 262 || i == 300) {
                len = 2;
            } else if (i == ddOff + dCodes - 2 || i == ddOff + dCodes - 1) {
                len = 1;
            } else if (i == lddOff + Compress.LDC5 - 2 || i == lddOff + Compress.LDC5 - 1) {
                len = 1;
            } else {
                len = 0;
            }
            bw.writeBits(len, 4);
        }
    }

    /**
     * Wrap crafted block content in a RAR5 block header ({@code d861246:unpack50.cpp:564}) whose
     * size and trailing bit count land exactly on the last content bit, the way a real archive's
     * final block does, and mark it {@code TablePresent | LastBlockInFile}.
     */
    static byte[] frame(final BitWriter content) {
        final int contentBits = content.bitCount();
        final int blockSizeBytes = (contentBits + 7) / 8;
        final int lastByteBits = contentBits - (blockSizeBytes - 1) * 8;
        int byteCount = 1;
        while (byteCount < 3 && (blockSizeBytes >>> (8 * byteCount)) != 0) {
            byteCount++;
        }
        final int flags = 0x80 | 0x40 | ((byteCount - 1) << 3) | ((lastByteBits - 1) & 7);
        final int checksum =
            (0x5a ^ flags ^ blockSizeBytes ^ (blockSizeBytes >>> 8) ^ (blockSizeBytes >>> 16)) & 0xff;

        final BitWriter bw = new BitWriter();
        bw.writeBits(flags, 8);
        bw.writeBits(checksum, 8);
        for (int i = 0; i < byteCount; i++) {
            bw.writeBits((blockSizeBytes >>> (8 * i)) & 0xff, 8);
        }
        for (final byte b : content.toByteArray()) {
            bw.writeBits(b & 0xff, 8);
        }
        bw.pad(64); // slack for the 30-byte read-border margin getbits/getbits64 rely on
        return bw.toByteArray();
    }

    /** {@link ComprDataIO} stub: serves the crafted packed stream, collects unpacked output. */
    static ComprDataIO collectingIO(final byte[] packed, final ByteArrayOutputStream out) {
        return new ComprDataIO(null) {
            private int pos;

            @Override
            public int unpRead(final byte[] addr, final int offset, final int count) {
                final int n = Math.min(count, packed.length - pos);
                if (n <= 0) {
                    return 0;
                }
                System.arraycopy(packed, pos, addr, offset, n);
                pos += n;
                return n;
            }

            @Override
            public void unpWrite(final byte[] addr, final int offset, final int count) {
                out.write(addr, offset, count);
            }
        };
    }

    /** Minimal MSB-first bit writer matching {@code getbits} order. */
    static final class BitWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int acc;
        private int nBits;
        private int bitCount;

        void writeBits(final long value, final int count) {
            bitCount += count;
            for (int i = count - 1; i >= 0; i--) {
                acc = (acc << 1) | (int) ((value >>> i) & 1);
                if (++nBits == 8) {
                    out.write(acc & 0xff);
                    acc = 0;
                    nBits = 0;
                }
            }
        }

        int bitCount() {
            return bitCount;
        }

        void pad(final int bytes) {
            if (nBits > 0) {
                out.write((acc << (8 - nBits)) & 0xff);
                acc = 0;
                nBits = 0;
            }
            for (int i = 0; i < bytes; i++) {
                out.write(0);
            }
        }

        byte[] toByteArray() {
            if (nBits > 0) {
                pad(0);
            }
            return out.toByteArray();
        }
    }
}
