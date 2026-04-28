package com.github.junrar.crc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Blake2spTest {

    @Test
    void testBlake2sEmptyInput() {
        final Blake2s blake2s = new Blake2s();
        final byte[] digest = blake2s.digest();
        final byte[] expected = hexStringToByteArray(
                "69217a3079908094e11121d042354a7c1f55b6482ca1a51e1b250dfd1ed0eef9");
        assertThat(digest).isEqualTo(expected);
    }

    @Test
    void testBlake2sKnownVector() {
        final Blake2s blake2s = new Blake2s();
        final byte[] input = "abc".getBytes();
        blake2s.update(input);
        final byte[] digest = blake2s.digest();
        final byte[] expected = hexStringToByteArray(
                "508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982");
        assertThat(digest).isEqualTo(expected);
    }

    @Test
    void testBlake2spEmptyInput() {
        final Blake2sp blake2sp = new Blake2sp();
        final byte[] digest = blake2sp.digest();
        assertThat(digest).isNotNull();
        assertThat(digest).hasSize(32);
    }

    @Test
    void testBlake2spReset() {
        final Blake2sp blake2sp = new Blake2sp();
        blake2sp.update("test".getBytes());
        final byte[] digest1 = blake2sp.digest();

        blake2sp.reset();
        blake2sp.update("test".getBytes());
        final byte[] digest2 = blake2sp.digest();

        assertThat(digest1).isEqualTo(digest2);
    }

    @Test
    void testBlake2sReset() {
        final Blake2s blake2s = new Blake2s();
        blake2s.update("test".getBytes());
        final byte[] digest1 = blake2s.digest();

        blake2s.reset();
        blake2s.update("test".getBytes());
        final byte[] digest2 = blake2s.digest();

        assertThat(digest1).isEqualTo(digest2);
    }

    @Test
    void testBlake2spMultipleUpdates() {
        final Blake2sp blake2sp = new Blake2sp();
        blake2sp.update(new byte[]{0x61});
        blake2sp.update(new byte[]{0x62});
        blake2sp.update(new byte[]{0x63});
        final byte[] digest = blake2sp.digest();

        final Blake2sp blake2sp2 = new Blake2sp();
        blake2sp2.update(new byte[]{0x61, 0x62, 0x63});
        final byte[] digest2 = blake2sp2.digest();

        assertThat(digest).isEqualTo(digest2);
    }

    @Test
    void testBlake2sMultipleUpdates() {
        final Blake2s blake2s = new Blake2s();
        blake2s.update(new byte[]{0x61});
        blake2s.update(new byte[]{0x62});
        blake2s.update(new byte[]{0x63});
        final byte[] digest = blake2s.digest();

        final Blake2s blake2s2 = new Blake2s();
        blake2s2.update(new byte[]{0x61, 0x62, 0x63});
        final byte[] digest2 = blake2s2.digest();

        assertThat(digest).isEqualTo(digest2);
    }

    @Test
    void testBlake2spSimpleRoundRobin() {
        final byte[] data = new byte[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        final Blake2sp blake2sp = new Blake2sp();
        blake2sp.update(data);
        final byte[] digest = blake2sp.digest();

        final Blake2sp blake2sp2 = new Blake2sp();
        for (int i = 0; i < data.length; i++) {
            blake2sp2.update(new byte[]{data[i]});
        }
        final byte[] digest2 = blake2sp2.digest();

        assertThat(digest).isEqualTo(digest2);
    }

    private static byte[] hexStringToByteArray(final String s) {
        final int len = s.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
