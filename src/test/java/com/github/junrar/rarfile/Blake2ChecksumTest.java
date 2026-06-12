package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Blake2ChecksumTest {

    @Test
    void knownDigest() {
        // BLAKE2sp digest of "file1\r\n" produced by official unrar
        byte[] digest = hex("fc30504f38dd39d30fa68ea2a702b8fd79756a4330b28c7d36e44c7139827721");
        Blake2Checksum checksum = new Blake2Checksum(digest);
        assertThat(checksum.getAlgorithm()).isEqualTo(ChecksumAlgorithm.BLAKE2SP);
        assertThat(checksum.getChecksum()).isEqualTo(
                "fc30504f38dd39d30fa68ea2a702b8fd79756a4330b28c7d36e44c7139827721");
        assertThat(checksum.getDigest()).isEqualTo(digest);
    }

    @Test
    void digestIsCopiedOnGet() {
        byte[] digest = new byte[32];
        Blake2Checksum checksum = new Blake2Checksum(digest);
        // Modify original — should not affect checksum
        digest[0] = (byte) 0xff;
        assertThat(checksum.getDigest()[0]).isEqualTo((byte) 0);
        assertThat(checksum.getChecksum()).startsWith("00");
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((Character.digit(s.charAt(2 * i), 16) << 4)
                    | Character.digit(s.charAt(2 * i + 1), 16));
        }
        return out;
    }
}
