package com.github.junrar.crypt;

import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import java.security.InvalidAlgorithmParameterException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RijndaelTest {

    @Test
    public void testBuildDeciphererNullPassword() {
        assertThatThrownBy(() -> Rijndael.buildDecipherer(null, new byte[8]))
                .isInstanceOf(InvalidAlgorithmParameterException.class)
                .hasMessageContaining("password should be specified");
    }

    @Test
    public void testBuildDecipherer() throws Exception {
        String password = "password";
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        Cipher cipher = Rijndael.buildDecipherer(password, salt);

        assertThat(cipher).isNotNull();
        assertThat(cipher.getAlgorithm()).isEqualTo("AES/CBC/NoPadding");

        // Verify we can actually use it
        byte[] input = new byte[16]; // Must be multiple of block size (16)
        byte[] output = cipher.update(input);
        assertThat(output).isNotNull();
    }

    @Test
    public void testConsistency() throws Exception {
        String password = "secret_password";
        byte[] salt = new byte[]{8, 7, 6, 5, 4, 3, 2, 1};

        Cipher cipher1 = Rijndael.buildDecipherer(password, salt);
        Cipher cipher2 = Rijndael.buildDecipherer(password, salt);

        byte[] input = new byte[32];
        for (int i = 0; i < 32; i++) input[i] = (byte) i;

        byte[] out1 = cipher1.doFinal(input);
        byte[] out2 = cipher2.doFinal(input);

        assertThat(out1).isEqualTo(out2);
    }
}
