package com.github.junrar.crypt;

/**
 * CRYPT_RAR20, unrar's RAR 2.0 Feistel-ish 16-byte block cipher (d861246:crypt2.cpp): {@code
 * InitSubstTable20} :9-26 (data), {@code SetKey20} :29-59, {@code EncryptBlock20} :62-85, {@code
 * DecryptBlock20} :88-113, {@code UpdKeys20} :116-125. 32-bit key state ({@code uint Key20[4]}) --
 * Java {@code int} arithmetic is bit-identical to C {@code uint} for {@code +}/{@code ^}, using
 * {@code >>>} everywhere a logical (unsigned) shift is required. No salt, no IV: keying and the
 * per-entry substitution table are password-only.
 */
final class Rar20Cipher implements LegacyCipherEngine {

    private static final int NROUNDS = 32;

    // InitSubstTable20 (crypt2.cpp:9-26), verbatim.
    private static final int[] INIT_SUBST_TABLE_20 = {
        215, 19, 149, 35, 73, 197, 192, 205, 249, 28, 16, 119, 48, 221, 2, 42,
        232, 1, 177, 233, 14, 88, 219, 25, 223, 195, 244, 90, 87, 239, 153, 137,
        255, 199, 147, 70, 92, 66, 246, 13, 216, 40, 62, 29, 217, 230, 86, 6,
        71, 24, 171, 196, 101, 113, 218, 123, 93, 91, 163, 178, 202, 67, 44, 235,
        107, 250, 75, 234, 49, 167, 125, 211, 83, 114, 157, 144, 32, 193, 143, 36,
        158, 124, 247, 187, 89, 214, 141, 47, 121, 228, 61, 130, 213, 194, 174, 251,
        97, 110, 54, 229, 115, 57, 152, 94, 105, 243, 212, 55, 209, 245, 63, 11,
        164, 200, 31, 156, 81, 176, 227, 21, 76, 99, 139, 188, 127, 17, 248, 51,
        207, 120, 189, 210, 8, 226, 41, 72, 183, 203, 135, 165, 166, 60, 98, 7,
        122, 38, 155, 170, 69, 172, 252, 238, 39, 134, 59, 128, 236, 27, 240, 80,
        131, 3, 85, 206, 145, 79, 154, 142, 159, 220, 201, 133, 74, 64, 20, 129,
        224, 185, 138, 103, 173, 182, 43, 34, 254, 82, 198, 151, 231, 180, 58, 10,
        118, 26, 102, 12, 50, 132, 22, 191, 136, 111, 162, 179, 45, 4, 148, 108,
        161, 56, 78, 126, 242, 222, 15, 175, 146, 23, 33, 241, 181, 190, 77, 225,
        0, 46, 169, 186, 68, 95, 237, 65, 53, 208, 253, 168, 9, 18, 100, 52,
        116, 184, 160, 96, 109, 37, 30, 106, 140, 104, 150, 5, 204, 117, 112, 84,
    };

    private final byte[] substTable20 = new byte[256];
    private final int[] key20 = new int[4];

    /**
     * {@code SetKey20} (crypt2.cpp:29-59): permutes a per-instance copy of {@link
     * #INIT_SUBST_TABLE_20} using every even-odd password byte pair, then runs {@link
     * #encryptBlock20} over the (zero-padded) password itself to fold it into {@link #key20}.
     *
     * <p>Ponytail: unrar truncates the working password to {@code MAXPASSWORD} before this
     * (crypt.cpp {@code SetCryptKeys}); not ported here (no fixture exercises an oversized
     * password). Upgrade trigger: a real archive with a password longer than unrar's
     * {@code MAXPASSWORD_RAR} surfaces.
     */
    Rar20Cipher(final byte[] password) {
        for (int i = 0; i < 256; i++) {
            substTable20[i] = (byte) INIT_SUBST_TABLE_20[i];
        }
        key20[0] = 0xD3A3B879;
        key20[1] = 0x3F6D12F7;
        key20[2] = 0x7515A235;
        key20[3] = 0xA4E7F123;

        final int pswLength = password.length;
        for (int j = 0; j < 256; j++) {
            for (int i = 0; i < pswLength; i += 2) {
                final int pI = password[i] & 0xff;
                // Password[I+1] may be the NUL terminator for odd-length passwords -- port as a
                // 0 byte (crypt2.cpp:46-47 porting trap), not skipped.
                final int pI1 = (i + 1 < pswLength) ? (password[i + 1] & 0xff) : 0;
                int n1 = Crc32Table.TABLE[(pI - j) & 0xff] & 0xff;
                final int n2 = Crc32Table.TABLE[(pI1 + j) & 0xff] & 0xff;
                for (int k = 1; n1 != n2; n1 = (n1 + 1) & 0xff, k++) {
                    swap(n1, (n1 + i + k) & 0xff);
                }
            }
        }

        // Zero-padded working copy of the password (Java default-initializes the padding tail).
        final int paddedLen = (pswLength == 0) ? 0 : ((pswLength + 15) & ~15);
        final byte[] psw = new byte[paddedLen];
        System.arraycopy(password, 0, psw, 0, pswLength);
        for (int i = 0; i < pswLength; i += 16) {
            encryptBlock20(psw, i);
        }
    }

    private void swap(final int a, final int b) {
        final byte t = substTable20[a];
        substTable20[a] = substTable20[b];
        substTable20[b] = t;
    }

    private int substLong(final int t) {
        return (substTable20[t & 0xff] & 0xff)
                | ((substTable20[(t >>> 8) & 0xff] & 0xff) << 8)
                | ((substTable20[(t >>> 16) & 0xff] & 0xff) << 16)
                | ((substTable20[(t >>> 24) & 0xff] & 0xff) << 24);
    }

    private static int rawGet4(final byte[] buf, final int off) {
        return (buf[off] & 0xff)
                | ((buf[off + 1] & 0xff) << 8)
                | ((buf[off + 2] & 0xff) << 16)
                | ((buf[off + 3] & 0xff) << 24);
    }

    private static void rawPut4(final int v, final byte[] buf, final int off) {
        buf[off] = (byte) v;
        buf[off + 1] = (byte) (v >>> 8);
        buf[off + 2] = (byte) (v >>> 16);
        buf[off + 3] = (byte) (v >>> 24);
    }

    private static int rotl32(final int x, final int n) {
        return (x << n) | (x >>> (32 - n));
    }

    /** {@code EncryptBlock20} (crypt2.cpp:62-85) -- used only during {@code SetKey20}. */
    private void encryptBlock20(final byte[] buf, final int off) {
        int a = rawGet4(buf, off) ^ key20[0];
        int b = rawGet4(buf, off + 4) ^ key20[1];
        int c = rawGet4(buf, off + 8) ^ key20[2];
        int d = rawGet4(buf, off + 12) ^ key20[3];
        for (int i = 0; i < NROUNDS; i++) {
            int t = (c + rotl32(d, 11)) ^ key20[i & 3];
            final int ta = a ^ substLong(t);
            t = (d ^ rotl32(c, 17)) + key20[i & 3];
            final int tb = b ^ substLong(t);
            a = c;
            b = d;
            c = ta;
            d = tb;
        }
        rawPut4(c ^ key20[0], buf, off);
        rawPut4(d ^ key20[1], buf, off + 4);
        rawPut4(a ^ key20[2], buf, off + 8);
        rawPut4(b ^ key20[3], buf, off + 12);
        updKeys20(buf, off);
    }

    /**
     * {@code DecryptBlock20} (crypt2.cpp:88-113). The classic porting trap: {@code UpdKeys20}
     * runs on the ORIGINAL ciphertext, captured into {@code inBuf} before the round transform
     * overwrites {@code buf} -- not on the just-decrypted plaintext.
     */
    private void decryptBlock20(final byte[] buf, final int off) {
        final byte[] inBuf = new byte[16];
        System.arraycopy(buf, off, inBuf, 0, 16);
        int a = rawGet4(buf, off) ^ key20[0];
        int b = rawGet4(buf, off + 4) ^ key20[1];
        int c = rawGet4(buf, off + 8) ^ key20[2];
        int d = rawGet4(buf, off + 12) ^ key20[3];
        for (int i = NROUNDS - 1; i >= 0; i--) {
            int t = (c + rotl32(d, 11)) ^ key20[i & 3];
            final int ta = a ^ substLong(t);
            t = (d ^ rotl32(c, 17)) + key20[i & 3];
            final int tb = b ^ substLong(t);
            a = c;
            b = d;
            c = ta;
            d = tb;
        }
        rawPut4(c ^ key20[0], buf, off);
        rawPut4(d ^ key20[1], buf, off + 4);
        rawPut4(a ^ key20[2], buf, off + 8);
        rawPut4(b ^ key20[3], buf, off + 12);
        updKeys20(inBuf, 0);
    }

    /** {@code UpdKeys20} (crypt2.cpp:116-125). */
    private void updKeys20(final byte[] buf, final int off) {
        for (int i = 0; i < 16; i += 4) {
            key20[0] ^= Crc32Table.TABLE[buf[off + i] & 0xff];
            key20[1] ^= Crc32Table.TABLE[buf[off + i + 1] & 0xff];
            key20[2] ^= Crc32Table.TABLE[buf[off + i + 2] & 0xff];
            key20[3] ^= Crc32Table.TABLE[buf[off + i + 3] & 0xff];
        }
    }

    /**
     * {@code DecryptBlock} RAR20 arm (crypt.cpp:33-36): {@code len} rounded DOWN to a 16-byte
     * multiple; any trailing partial block is left untouched, matching unrar's own loop bound.
     */
    @Override
    public void decrypt(final byte[] buf, final int off, final int len) {
        final int blocks = len / RarLegacyCrypt.CRYPT_BLOCK_SIZE;
        for (int i = 0; i < blocks; i++) {
            decryptBlock20(buf, off + i * RarLegacyCrypt.CRYPT_BLOCK_SIZE);
        }
    }

    /** The key state right after {@link #Rar20Cipher(byte[])}, for known-answer testing. */
    int[] keySchedule() {
        return key20.clone();
    }
}
