package com.github.junrar;

import com.github.junrar.crypt.Rar5Crypt;
import com.github.junrar.crypt.blake2.Blake2sp;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.unpack.ComprDataIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.5 (issue #26) archive-level acceptance coverage: RAR5 FHEXTRA_HASH parsing of a BLAKE2sp
 * entry, routing the unpacked bytes through the {@link ComprDataIO} {@code DataHash} seam, and
 * the end-to-end HASHMAC path deferred from M3.4 ({@code ConvertHashToMAC} verification of a
 * stored MAC against the KDF hash key). Fixtures live under {@code blake2/} (getResource pattern
 * mirrors {@link ArchiveRar5CryptoTest}).
 */
@Timeout(30)
class ArchiveRar5Blake2Test {

    @TempDir
    Path tempDir;

    private File fixture(final String name) throws Exception {
        final Path src = Paths.get(getClass().getResource("blake2/" + name).toURI());
        final Path dst = tempDir.resolve(name);
        Files.copy(src, dst);
        return dst.toFile();
    }

    private byte[] payload() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("blake2/blake2-payload.bin")) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static byte[] hexToBytes(final String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    @Test
    void htbEntryParsesBlake2Hash() throws Exception {
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(fixture("rar5-htb.rar"))) {
            final FileHeader fh = a.getFileHeaders().get(0);
            assertThat(fh.getHashType()).isEqualTo(Rar5HashType.BLAKE2);
            assertThat(fh.getHashDigest())
                .isEqualTo(hexToBytes("78ed63477dbe9caf6ac85c0efcddc626a1a6f73d224a056cf263521153f72c83"));
        }
    }

    @Test
    void htbEntryThroughTheComprDataIoSeamMatchesOracle() throws Exception {
        final byte[] payload = payload();
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(fixture("rar5-htb.rar"))) {
            final FileHeader fh = a.getFileHeaders().get(0);

            final ComprDataIO cdio = new ComprDataIO(a);
            cdio.init(new ByteArrayOutputStream());
            cdio.setTestMode(true);
            cdio.init(fh);
            cdio.unpWrite(payload, 0, payload.length);

            assertThat(cdio.getUnpHashDigest())
                .isEqualTo(hexToBytes("78ed63477dbe9caf6ac85c0efcddc626a1a6f73d224a056cf263521153f72c83"));
        }
    }

    @Test
    void htbMacEntryEndToEndHashMacVerification() throws Exception {
        final byte[] payload = payload();
        try (Archive a = Archive.testOnlyOpenSuppressingV5Gate(fixture("rar5-htb-mac.rar"))) {
            final FileHeader fh = a.getFileHeaders().get(0);

            assertThat(fh.isUseHashKey()).isTrue();
            final byte[] storedMac =
                hexToBytes("28918c0ba5edd97e4f1ffa2214bd76c3b1896e812530fecde38adea725bbd357");
            assertThat(fh.getHashDigest()).isEqualTo(storedMac);

            final byte[] pwUtf8 = Rar5Crypt.passwordToUtf8("junrar".toCharArray());
            final Rar5Crypt.Kdf kdf = Rar5Crypt.deriveKey(pwUtf8, fh.getSalt16(), fh.getLg2Count());

            final Blake2sp sp = new Blake2sp();
            sp.update(payload, 0, payload.length);
            final byte[] rawHash = sp.digest();
            assertThat(rawHash)
                .isEqualTo(hexToBytes("78ed63477dbe9caf6ac85c0efcddc626a1a6f73d224a056cf263521153f72c83"));

            final byte[] mac = Rar5Crypt.convertBlake2ToMac(rawHash, kdf.hashKey);
            assertThat(mac).isEqualTo(storedMac);
        }
    }
}
