package com.github.junrar.crypt;

/**
 * CRYPT_RAR15, unrar's RAR 1.5 XOR-keystream cipher (d861246:crypt1.cpp): {@code SetKey15}
 * :15-28, {@code Crypt15} :52-66. 16-bit key state throughout (unrar {@code ushort Key15[4]},
 * {@code d861246:crypt.hpp:97}) -- every field masked to {@code 0xffff} after each update. {@code
 * Crypt15} is a pure XOR keystream, so it is its own inverse: the same {@link #decrypt} method
 * both encrypts and decrypts (unrar calls it via the shared {@code DecryptBlock}/{@code
 * EncryptBlock} dispatch either way). No salt, no IV: keying is password-only.
 */
final class Rar15Cipher implements LegacyCipherEngine {

    private int key0;
    private int key1;
    private int key2;
    private int key3;

    /**
     * {@code SetKey15} (crypt1.cpp:15-28): seeds {@code Key15[0..1]} from the running (no final
     * complement) CRC-32 of the password ({@link Crc32Table#runningCrc32}, start value {@code
     * 0xffffffff}), then folds every password byte through the CRC table into {@code
     * Key15[2..3]}.
     */
    Rar15Cipher(final byte[] password) {
        final int pswCrc = Crc32Table.runningCrc32(0xffffffff, password);
        key0 = pswCrc & 0xffff;
        key1 = (pswCrc >>> 16) & 0xffff;
        key2 = 0;
        key3 = 0;
        for (final byte b : password) {
            final int p = b & 0xff;
            final int tab = Crc32Table.TABLE[p];
            // Key15[2]^=P^CRCTab[P]; -- mixed-width XOR (byte ^ uint32) truncated on assignment
            // to the 16-bit destination.
            key2 = (key2 ^ (p ^ tab)) & 0xffff;
            // Key15[3]+=ushort(P+(CRCTab[P]>>16)); -- the ushort() cast applies to the sum first.
            final int term = (p + (tab >>> 16)) & 0xffff;
            key3 = (key3 + term) & 0xffff;
        }
    }

    private static int rotr16(final int x) {
        return ((x >>> 1) | ((x & 1) << 15)) & 0xffff;
    }

    /**
     * {@code Crypt15} (crypt1.cpp:52-66), per byte: advance {@code Key15[0]} by {@code 0x1234},
     * mix in the CRC table via the low bits of {@code Key15[0]}, rotate {@code Key15[3]} twice,
     * then XOR the input byte with the high byte of the final {@code Key15[0]}.
     */
    @Override
    public void decrypt(final byte[] buf, final int off, final int len) {
        for (int i = off; i < off + len; i++) {
            key0 = (key0 + 0x1234) & 0xffff;
            final int idx = (key0 & 0x1fe) >>> 1;
            final int tab = Crc32Table.TABLE[idx];
            key1 = (key1 ^ tab) & 0xffff;
            key2 = (key2 - ((tab >>> 16) & 0xffff)) & 0xffff;
            key0 = (key0 ^ key2) & 0xffff;
            key3 = (rotr16(key3) ^ key1) & 0xffff;
            key3 = rotr16(key3);
            key0 = (key0 ^ key3) & 0xffff;
            buf[i] = (byte) (buf[i] ^ (byte) (key0 >>> 8));
        }
    }

    /** The key state right after {@link #Rar15Cipher(byte[])}, for known-answer testing. */
    int[] keySchedule() {
        return new int[] {key0, key1, key2, key3};
    }
}
