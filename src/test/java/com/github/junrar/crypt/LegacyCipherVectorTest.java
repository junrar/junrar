package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * P3 (issue #293) known-answer vectors for the three pre-AES RAR ciphers: {@link Rar13Cipher}
 * (CRYPT_RAR13, {@code d861246:crypt1.cpp:1-12} SetKey13 / {@code :40-49} Decrypt13), {@link
 * Rar15Cipher} (CRYPT_RAR15, {@code crypt1.cpp:15-28} SetKey15 / {@code :52-66} Crypt15), and
 * {@link Rar20Cipher} (CRYPT_RAR20, {@code crypt2.cpp:29-59} SetKey20 / {@code :62-113}
 * Encrypt/DecryptBlock20). Plus {@link RarLegacyCrypt#select} (cipher-selection switch, unrar
 * {@code arcread.cpp:290-298} RARFMT15 headers / {@code :1300} RARFMT14).
 *
 * <p>Vectors come from {@code /private/tmp/rar14-oracle/gen_crypt_vectors.py}, an INDEPENDENT
 * second port of the same C++ source (session-verified 2026-07-27, not authority -- the C++ at
 * {@code d861246} is authority; agreement here cross-checks both). Plaintext is the fixed 64-byte
 * sequence {@code 00 01 02 ... 3f} for all three passwords; "encrypted" fields are the ciphertext
 * for that plaintext under each cipher (the Python generator's docstring: "the encrypt direction
 * ... is the mathematical inverse of the ported decrypt" -- so {@code decrypt(ciphertext) ==
 * plaintext} is the assertion direction here, matching what {@link
 * com.github.junrar.unpack.ComprDataIO} actually calls at extraction time). Key-schedule vectors
 * are the internal key state immediately after construction (SetKeyNN), independent of any
 * decrypt call.
 */
class LegacyCipherVectorTest {

    private static final byte[] PLAIN_64 = new byte[64];

    static {
        for (int i = 0; i < 64; i++) {
            PLAIN_64[i] = (byte) i;
        }
    }

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static byte[] pwd(final String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    // ---- Row 1: CRYPT_RAR13 -------------------------------------------------------------

    @Test
    void rar13DecryptsTest123VectorAndPinsKeySchedule() {
        final byte[] cipherText =
                hex(
                        "1d86903b87740231017284378b80164d259eb873cfcc6aa9890a2cef5358fe4"
                            + "52db6e0ab1724d22111a2d4a71b30e63d35ce08e35f7c3a99993a7c5fe308ce35");
        final Rar13Cipher cipher = new Rar13Cipher(pwd("test123"));
        assertThat(cipher.keySchedule()).containsExactly(86, 38, 161);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    @Test
    void rar13DecryptsOddLengthSingleCharPasswordVector() {
        // Password "a" -- also the RAR20 odd-length NUL-read pin below.
        final byte[] cipherText =
                hex(
                        "846a127ca89646b8ece29a14504e0e90d4daa22c788656e83c522ac4203e1ec"
                            + "0244a32dc487666188cc2ba74f02e2ef074bac28c18667648dc324a24c01e3e20");
        final Rar13Cipher cipher = new Rar13Cipher(pwd("a"));
        assertThat(cipher.keySchedule()).containsExactly(97, 97, 194);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    @Test
    void rar13DecryptsLongerPasswordVector() {
        final byte[] cipherText =
                hex(
                        "381fe68d147bc2e9f0d79e45cc337aa1a88f56fd84eb325960470eb53ca3ea11"
                            + "18ffc66df45ba2c9d0b77e25ac135a81886f36dd64cb12394027ee951c83caf1");
        final Rar13Cipher cipher = new Rar13Cipher(pwd("longer password 1994!"));
        assertThat(cipher.keySchedule()).containsExactly(50, 38, 224);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    // ---- Row 2: CRYPT_RAR15 (self-inverse XOR keystream) ---------------------------------

    @Test
    void rar15DecryptsTest123VectorAndPinsKeySchedule() {
        final byte[] cipherText =
                hex(
                        "307099b68308067721a0b5b738575b9fbd34c24ffe817471885d59143f8110958"
                            + "5a23efa311a224382ade260d10432a485c532368ee825f9152bb9752ac81eb1");
        final Rar15Cipher cipher = new Rar15Cipher(pwd("test123"));
        assertThat(cipher.keySchedule()).containsExactly(23876, 4011, 34267, 37519);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    @Test
    void rar15DecryptsOddLengthSingleCharPasswordVector() {
        final byte[] cipherText =
                hex(
                        "752ea7474d581a955fb437467fa9fb449bfe9da23de6d0cd3f3f9474fe82ffdd"
                            + "e02b2d752326e98db9d4bfdb827f80da7f2ad8c89221de2ec9ed60ed6f2b7b60");
        final Rar15Cipher cipher = new Rar15Cipher(pwd("a"));
        assertThat(cipher.keySchedule()).containsExactly(16828, 5960, 20911, 15126);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    @Test
    void rar15DecryptsLongerPasswordVector() {
        final byte[] cipherText =
                hex(
                        "ce14baa2c33f880c76dbfd3bf7594c8fc2c87ef7ba5a667d97b803061d12a0059"
                            + "86d7191f91f7b5d2479fed46699678ba44155407e5625e74935e71ee76676e8");
        final Rar15Cipher cipher = new Rar15Cipher(pwd("longer password 1994!"));
        assertThat(cipher.keySchedule()).containsExactly(25902, 55028, 34267, 26641);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    /**
     * {@code Crypt15} is a plain XOR keystream (unrar {@code crypt1.cpp:52-66}): running it twice
     * from two independently-constructed ciphers seeded with the same password is its own
     * inverse. A one-instance round-trip would trivially pass with a broken keystream (XOR with
     * itself is always identity); two fresh instances is the real self-inverse property.
     */
    @Test
    void rar15CryptIsSelfInverseAcrossTwoFreshInstances() {
        final byte[] forward = PLAIN_64.clone();
        new Rar15Cipher(pwd("test123")).decrypt(forward, 0, forward.length);
        final byte[] roundTrip = forward.clone();
        new Rar15Cipher(pwd("test123")).decrypt(roundTrip, 0, roundTrip.length);
        assertThat(roundTrip).isEqualTo(PLAIN_64);
    }

    // ---- Row 3: CRYPT_RAR20 (16-byte block cipher) ---------------------------------------

    @Test
    void rar20DecryptsTest123VectorAndPinsKeySchedule() {
        final byte[] cipherText =
                hex(
                        "6db01df510d2f7ff1a33b1abfc0e46999ea8d333ffc2fe600a082a2dd679c5efd"
                            + "91b5c081a9aec4b0ff4bfb2eb840877637b50c50f04eabb700839191cb37645");
        final Rar20Cipher cipher = new Rar20Cipher(pwd("test123"));
        assertThat(cipher.keySchedule())
                .containsExactly(0x827db843, 0xf40ca17b, 0x62a21c76, 0x6808051);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    /**
     * Password {@code "a"} is length 1 (odd) -- {@code SetKey20}'s substitution-table loop reads
     * {@code Password[I+1]} for {@code I=0}, i.e. index 1, past the single real byte. unrar's C
     * string is NUL-terminated so that read is the terminator (0); {@link Rar20Cipher} must treat
     * a read past the end of the password array as 0, not skip the iteration or throw
     * (crypt2.cpp:46-47 porting trap, brief-mandated pin).
     */
    @Test
    void rar20DecryptsOddLengthSingleCharPasswordVectorPinningNulRead() {
        final byte[] cipherText =
                hex(
                        "0af6615c968e08c7f00cd6518eba240d7a5ea3724d9bd3c14803d0edac783766dc"
                                + "60814034a13acda6011f458ca252cddb0f7b29ffc5ba66799bceb2a9dbfe17");
        final Rar20Cipher cipher = new Rar20Cipher(pwd("a"));
        assertThat(cipher.keySchedule())
                .containsExactly(0x8a10856e, 0xb4d3aa1d, 0x6c799444, 0xf7546213);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    @Test
    void rar20DecryptsLongerPasswordVector() {
        final byte[] cipherText =
                hex(
                        "c9b49118cbd34e7bbcce1badea3addf4a771c4856eb360433920fcd6a2fa401953"
                                + "2ae8de0e6defe32dad8528aa36f76d58a10631c5bdf0ca298b97d91c952397");
        final Rar20Cipher cipher = new Rar20Cipher(pwd("longer password 1994!"));
        assertThat(cipher.keySchedule())
                .containsExactly(0xc0cf202f, 0xceb9f0b5, 0xe31c0abb, 0xdce03881);
        final byte[] buf = cipherText.clone();
        cipher.decrypt(buf, 0, buf.length);
        assertThat(buf).isEqualTo(PLAIN_64);
    }

    // ---- Row 7: cipher SELECTION ----------------------------------------------------------

    /**
     * unrar's cipher-selection switch, both arms: RARFMT15-style headers key off {@code UnpVer}
     * ({@code arcread.cpp:290-298}); the bare RARFMT14 container always uses CRYPT_RAR13
     * regardless of {@code UnpVer} ({@code arcread.cpp:1300}).
     */
    @Test
    void selectsCryptMethodByUnpVersionAndOldFormatFlag() {
        assertThat(RarLegacyCrypt.select(13, false)).isEqualTo(CryptMethod.RAR13);
        assertThat(RarLegacyCrypt.select(15, false)).isEqualTo(CryptMethod.RAR15);
        assertThat(RarLegacyCrypt.select(20, false)).isEqualTo(CryptMethod.RAR20);
        assertThat(RarLegacyCrypt.select(26, false)).isEqualTo(CryptMethod.RAR20);
        assertThat(RarLegacyCrypt.select(29, false)).isEqualTo(CryptMethod.RAR30);

        // RARFMT14 (Archive.isOldFormat()): always RAR13, UnpVer is irrelevant.
        assertThat(RarLegacyCrypt.select(10, true)).isEqualTo(CryptMethod.RAR13);
        assertThat(RarLegacyCrypt.select(13, true)).isEqualTo(CryptMethod.RAR13);
        assertThat(RarLegacyCrypt.select(29, true)).isEqualTo(CryptMethod.RAR13);
    }
}
