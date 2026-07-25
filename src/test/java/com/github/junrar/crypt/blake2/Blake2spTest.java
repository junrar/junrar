package com.github.junrar.crypt.blake2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.digests.Blake2spDigest;
import org.junit.jupiter.api.Test;

/**
 * M3.5 (issue #26) unit coverage for {@link Blake2sp}: the unrar empty-input constant, the full
 * unkeyed BLAKE2sp KAT set, the {@code rar5-htb.rar} fixture payload oracle, a chunked-update
 * regression guard around the 512-byte super-block boundary, and a Bouncy Castle differential
 * fuzz to catch anything the fixed vectors miss.
 */
class Blake2spTest {

    private static final Pattern KAT_ENTRY =
            Pattern.compile("\"in\":\\s*\"([0-9a-f]*)\",\\s*\"out\":\\s*\"([0-9a-f]*)\"");
    private static final String EMPTY_DIGEST =
            "dd0e891776933f43c7d032b08a917e25741f8aa9a12c12e1cac8801500f2ca4f";
    private static final String PAYLOAD_DIGEST =
            "78ed63477dbe9caf6ac85c0efcddc626a1a6f73d224a056cf263521153f72c83";

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

    private static byte[] digestOf(final byte[] input) {
        final Blake2sp sp = new Blake2sp();
        sp.update(input, 0, input.length);
        return sp.digest();
    }

    @Test
    void emptyInputMatchesUnrarConstant() {
        assertThat(bytesToHex(new Blake2sp().digest())).isEqualTo(EMPTY_DIGEST);
    }

    @Test
    void katVectorsAllGreen() throws IOException {
        final String json = Blake2sTest.readResource(getClass(), "blake2sp-kat.json");
        final List<String[]> entries = new ArrayList<>();
        final Matcher m = KAT_ENTRY.matcher(json);
        while (m.find()) {
            entries.add(new String[] {m.group(1), m.group(2)});
        }
        assertThat(entries).hasSize(256);
        for (final String[] entry : entries) {
            final byte[] input = hexToBytes(entry[0]);
            assertThat(bytesToHex(digestOf(input)))
                    .as("input length %d", input.length)
                    .isEqualTo(entry[1]);
        }
    }

    private static byte[] readPayload() throws IOException {
        return hexToBytesFromStream(
                Blake2spTest.class.getResourceAsStream(
                        "/com/github/junrar/blake2/blake2-payload.bin"));
    }

    private static byte[] hexToBytesFromStream(final java.io.InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            out.write(chunk, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    @Test
    void fixturePayloadMatchesUnrarOracle() throws IOException {
        final byte[] payload = readPayload();
        assertThat(payload).hasSize(2000);
        assertThat(bytesToHex(digestOf(payload))).isEqualTo(PAYLOAD_DIGEST);
    }

    @Test
    void chunkedUpdateMatchesSingleShot() throws IOException {
        final byte[] payload = readPayload();
        final byte[] singleShot = digestOf(payload);

        final Blake2sp chunked = new Blake2sp();
        // Chunk sizes deliberately include a non-multiple-of-512 split and one that crosses the
        // 512-byte super-block boundary, to regression-guard the bulk-update offset arithmetic.
        final int[] chunkSizes = {1, 100, 411, 512, 500, 12, 300};
        int off = 0;
        for (final int size : chunkSizes) {
            final int len = Math.min(size, payload.length - off);
            chunked.update(payload, off, len);
            off += len;
        }
        if (off < payload.length) {
            chunked.update(payload, off, payload.length - off);
        }
        assertThat(bytesToHex(chunked.digest())).isEqualTo(bytesToHex(singleShot));
    }

    @Test
    void bouncyCastleDifferential() {
        final Blake2spDigest bcEmpty = new Blake2spDigest(new byte[0]);
        final byte[] bcEmptyOut = new byte[bcEmpty.getDigestSize()];
        bcEmpty.doFinal(bcEmptyOut, 0);
        assertThat(bytesToHex(bcEmptyOut)).isEqualTo(EMPTY_DIGEST);

        final Random random = new Random(0xB1A2E25L);
        for (int i = 0; i < 200; i++) {
            final int len = random.nextInt(2501);
            final byte[] input = new byte[len];
            random.nextBytes(input);

            final byte[] ours = digestOf(input);

            final Blake2spDigest bc = new Blake2spDigest(new byte[0]);
            bc.update(input, 0, input.length);
            final byte[] bcOut = new byte[bc.getDigestSize()];
            bc.doFinal(bcOut, 0);

            assertThat(bytesToHex(ours)).as("input length %d", len).isEqualTo(bytesToHex(bcOut));
        }
    }
}
