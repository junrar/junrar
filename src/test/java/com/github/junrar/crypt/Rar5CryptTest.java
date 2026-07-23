package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.junrar.exception.CorruptHeaderException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * M3.4 (issue #25) known-answer tests for the RAR5 KDF. The six mandated vectors come from the
 * parity plan chunk: the Key rows are unrar's own {@code TestPBKDF2} {@code Res1/Res2/Res3}
 * ({@code d861246:crypt5.cpp:219-234}) verbatim, and the V1/V2 rows are independently
 * recomputable with any PBKDF2-HMAC-SHA256 at iteration counts {@code count+16} / {@code
 * count+32}. A one-call {@code dkLen=96} implementation and a {@code count/count+16/count+17}
 * implementation both FAIL these -- the snapshot-after-each-segment semantics are asserted.
 */
class Rar5CryptTest {

    private static byte[] hex(final String s) {
        final byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void assertVectors(
            final String pwd,
            final String salt,
            final int count,
            final String key,
            final String v1,
            final String v2) {
        final byte[][] r =
                Rar5Crypt.pbkdf2(
                        pwd.getBytes(StandardCharsets.UTF_8),
                        salt.getBytes(StandardCharsets.UTF_8),
                        count);
        assertThat(r[0]).as("Key at count=%d", count).isEqualTo(hex(key));
        assertThat(r[1]).as("V1 at count+16=%d", count).isEqualTo(hex(v1));
        assertThat(r[2]).as("V2 at count+32=%d", count).isEqualTo(hex(v2));
    }

    @Test
    void kdfVectorsCount1() {
        assertVectors(
                "password",
                "salt",
                1,
                "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
                "ae4ee2d75b78ca4b52c833c7a2c46aa5afcda84910c816129180c7db94d12828",
                "8d0d186304625d4fdf487b4f929c0224d6e26bbd33a698e446fdb17e876f6975");
    }

    @Test
    void kdfVectorsCount4096() {
        assertVectors(
                "password",
                "salt",
                4096,
                "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
                "f5813aeb14cd6ad5a2714b4f82f277a5c9d3b5441199fd731cebf707eaf39f12",
                "9de7fbbc3868a043dd5a14e254be45a9afb7feaca0ce4f4a6e2713ff8e7bea7f");
    }

    @Test
    void kdfVectorsLongStringCount65536() {
        assertVectors(
                "just some long string pretending to be a password",
                "salt, salt, salt, a lot of salt",
                65536,
                "080fa31d422db047839bce3a3bce4951e262b9ff762f57e9c47196ce4b6b6ebf",
                "62c33556fc0d8238ff9a11f7d1463dbf8eb384e2f5227a060224444a7647d0f7",
                "d85dd4f96fc85298a93e2af7cf0d6555b835856e11dce700604f7fad2808a119");
    }

    @Test
    void pswCheckFoldsV2From32To8Bytes() throws Exception {
        // deriveKey folds the count+32 value V2 to 8 bytes: PswCheck[i%8] ^= V2[i].
        final byte[] v2 = hex("8d0d186304625d4fdf487b4f929c0224d6e26bbd33a698e446fdb17e876f6975");
        final byte[] expected = new byte[Rar5Crypt.SIZE_PSWCHECK];
        for (int i = 0; i < v2.length; i++) {
            expected[i % Rar5Crypt.SIZE_PSWCHECK] ^= v2[i];
        }
        // lg2Count 0 -> count 1, matching the count=1 vector above (salt "salt" is 4 bytes, but
        // deriveKey passes it as-is; use the exact 4-byte salt to hit the same V2).
        final Rar5Crypt.Kdf kdf =
                Rar5Crypt.deriveKey(
                        "password".getBytes(StandardCharsets.UTF_8),
                        "salt".getBytes(StandardCharsets.UTF_8),
                        0);
        assertThat(kdf.pswCheck).isEqualTo(expected);
        assertThat(kdf.aesKey)
                .isEqualTo(hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"));
        assertThat(kdf.hashKey)
                .isEqualTo(hex("ae4ee2d75b78ca4b52c833c7a2c46aa5afcda84910c816129180c7db94d12828"));
    }

    @Test
    void deriveKeyRejectsLg2CountAboveMax() {
        assertThat(
                        catchThrowable(
                                () ->
                                        Rar5Crypt.deriveKey(
                                                "x".getBytes(StandardCharsets.UTF_8),
                                                new byte[16],
                                                25)))
                .isExactlyInstanceOf(CorruptHeaderException.class);
    }

    @Test
    void passwordToUtf8MatchesUtf8Encoding() {
        // Mixed-range password must serialize as UTF-8 (WideToUtf), not the platform charset.
        final String pwd = "пароль密码ü";
        assertThat(Rar5Crypt.passwordToUtf8(pwd.toCharArray()))
                .isEqualTo(pwd.getBytes(StandardCharsets.UTF_8));
    }
}
