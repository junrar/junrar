package com.github.junrar.crypt.blake2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * M3.5 (issue #26) unit coverage for the {@link Blake2s} leaf/standalone primitive: the RFC 7693
 * "abc" vector, the full unkeyed BLAKE2s KAT set, and a chunked-update regression guard for the
 * 128-byte lazy buffer boundary in {@link Blake2s#update}.
 */
class Blake2sTest {

    private static final Pattern KAT_ENTRY =
            Pattern.compile("\"in\":\\s*\"([0-9a-f]*)\",\\s*\"out\":\\s*\"([0-9a-f]*)\"");

    private static byte[] hexToBytes(final String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] blake2s(final byte[] input) {
        final Blake2s b = new Blake2s();
        b.initParam(0x01010020, 0, 0);
        b.update(input, 0, input.length);
        final byte[] out = new byte[Blake2s.OUTBYTES];
        b.finalize(out, 0);
        return out;
    }

    @Test
    void rfc7693AbcVector() {
        final byte[] digest = blake2s("abc".getBytes(StandardCharsets.US_ASCII));
        assertThat(bytesToHex(digest))
                .isEqualTo("508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982");
    }

    // Fixtures live under com/github/junrar/blake2/, one package above this test's own
    // com/github/junrar/crypt/blake2/ -- resolve with an absolute resource path.
    static String readResource(final Class<?> anchor, final String name) throws IOException {
        try (InputStream in = anchor.getResourceAsStream("/com/github/junrar/blake2/" + name)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return out.toString("US-ASCII");
        }
    }

    @Test
    void katVectorsAllGreen() throws IOException {
        final String json = readResource(getClass(), "blake2s-kat.json");
        final List<String[]> entries = new ArrayList<>();
        final Matcher m = KAT_ENTRY.matcher(json);
        while (m.find()) {
            entries.add(new String[] {m.group(1), m.group(2)});
        }
        assertThat(entries).hasSize(256);
        for (final String[] entry : entries) {
            final byte[] input = hexToBytes(entry[0]);
            final byte[] digest = blake2s(input);
            assertThat(bytesToHex(digest)).as("input length %d", input.length).isEqualTo(entry[1]);
        }
    }

    @Test
    void chunkedUpdateMatchesSingleShot() {
        final byte[] input = new byte[500];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }
        final byte[] singleShot = blake2s(input);

        final Blake2s chunked = new Blake2s();
        chunked.initParam(0x01010020, 0, 0);
        final int[] chunkSizes = {1, 63, 64, 65, 127, 128, 129, 20};
        int off = 0;
        for (final int size : chunkSizes) {
            final int len = Math.min(size, input.length - off);
            chunked.update(input, off, len);
            off += len;
        }
        // feed any remainder in one go
        if (off < input.length) {
            chunked.update(input, off, input.length - off);
        }
        final byte[] chunkedOut = new byte[Blake2s.OUTBYTES];
        chunked.finalize(chunkedOut, 0);

        assertThat(bytesToHex(chunkedOut)).isEqualTo(bytesToHex(singleShot));
    }
}
