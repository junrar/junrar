package com.github.junrar.crypt;

/**
 * CRYPT_RAR13, unrar's original subtractive-keystream cipher (d861246:crypt1.cpp): {@code
 * SetKey13} :1-12, {@code Decrypt13} :40-49. 8-bit key state throughout (unrar {@code byte
 * Key13[3]}, {@code d861246:crypt.hpp:96}) -- every field masked to {@code 0xff} after each
 * update to reproduce C's implicit byte-width wraparound. No salt, no IV: keying is password-only.
 */
final class Rar13Cipher implements LegacyCipherEngine {

    private int key0;
    private int key1;
    private int key2;

    /**
     * {@code SetKey13} (crypt1.cpp:1-12): {@code Key13[0]+=P; Key13[1]^=P; Key13[2]=rotl8(Key13[2]
     * +P,1);} per password byte, all mod 256, starting from all-zero state.
     */
    Rar13Cipher(final byte[] password) {
        key0 = 0;
        key1 = 0;
        key2 = 0;
        for (final byte b : password) {
            final int p = b & 0xff;
            key0 = (key0 + p) & 0xff;
            key1 = (key1 ^ p) & 0xff;
            key2 = rotl8((key2 + p) & 0xff);
        }
    }

    private static int rotl8(final int x) {
        return ((x << 1) | (x >>> 7)) & 0xff;
    }

    /** {@code Decrypt13} (crypt1.cpp:40-49): {@code Key13[1]+=Key13[2]; Key13[0]+=Key13[1];
     * *Data-=Key13[0];} per byte, all mod 256. */
    @Override
    public void decrypt(final byte[] buf, final int off, final int len) {
        for (int i = off; i < off + len; i++) {
            key1 = (key1 + key2) & 0xff;
            key0 = (key0 + key1) & 0xff;
            buf[i] = (byte) (((buf[i] & 0xff) - key0) & 0xff);
        }
    }

    /** The key state right after {@link #Rar13Cipher(byte[])}, for known-answer testing. */
    int[] keySchedule() {
        return new int[] {key0, key1, key2};
    }
}
