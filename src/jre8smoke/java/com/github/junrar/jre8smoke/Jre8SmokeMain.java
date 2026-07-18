package com.github.junrar.jre8smoke;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.MessageDigest;

/**
 * P0.9 (docs/porting/PARITY_PLAN.md ss3, review B-S8): framework-free JRE 8
 * runtime extraction smoke.
 *
 * <p>{@code options.release = 8} (build.gradle) proves API/bytecode
 * compatibility only; JUnit 5 needs a newer JDK than 8 to run, so the real
 * test suite cannot execute on a JRE 8 runtime. This is a minimal
 * {@code public static void main} runner instead: built with the normal
 * JDK 21 toolchain, then executed under a real Temurin 8 JRE in CI
 * ({@code .github/workflows/ci.yml}, job {@code jre8-smoke}) to prove
 * JCE *provider* behavior on that runtime (Cipher AES/CBC/NoPadding, the
 * 8u161 unlimited-policy floor documented in plan ss2.1) -- not just that the
 * bytecode targets release 8.
 *
 * <p>Not part of any Gradle source set (kept out of checkstyle/ArchUnit
 * scope on purpose): the CI job compiles and runs this one file directly
 * with {@code javac --release 8} / {@code java}. No production code is
 * touched by P0.9.
 *
 * <p>Fixtures (existing, committed under {@code src/test/resources}):
 * plain RAR3 {@code test.rar} (entry {@code foo\bar.txt}, content
 * {@code "baz\n"}) and the AES-encrypted RAR3 fixture
 * {@code password/rar3-nonascii-password.rar} (entry {@code file1.txt},
 * password {@code "пароль密码ü"},
 * content {@code "nonascii-payload\n"}) landed for T4/P0.6
 * (RijndaelNonAsciiPasswordTest). Expected digests below were derived by
 * extracting both fixtures with this project's own jar and hashing the
 * output (verified independently against the plaintext content strings).
 */
public final class Jre8SmokeMain {

    private static final String PLAIN_RAR = "src/test/resources/com/github/junrar/test.rar";
    private static final String PLAIN_ENTRY = "foo/bar.txt";
    private static final String PLAIN_SHA256 =
        "bf07a7fbb825fc0aae7bf4a1177b2b31fcf8a3feeaf7092761e18c859ee52a9c";

    private static final String AES_RAR = "src/test/resources/com/github/junrar/password/rar3-nonascii-password.rar";
    private static final String AES_PASSWORD = "пароль密码ü";
    private static final String AES_ENTRY = "file1.txt";
    private static final String AES_SHA256 =
        "efaffec1b655c3e25054629091aa6b46f71d34470bf3b587d3b7cdc1b7e8d135";

    private Jre8SmokeMain() {
    }

    public static void main(final String[] args) throws Exception {
        boolean plainOk = check("plain RAR3", new File(PLAIN_RAR), null, PLAIN_ENTRY, PLAIN_SHA256);
        boolean aesOk = check("AES password RAR3", new File(AES_RAR), AES_PASSWORD, AES_ENTRY, AES_SHA256);
        if (!plainOk || !aesOk) {
            System.out.println("jre8-smoke: FAIL");
            System.exit(1);
        }
        System.out.println("jre8-smoke: OK (java.version=" + System.getProperty("java.version") + ")");
    }

    private static boolean check(final String label, final File rar, final String password,
                                  final String entryName, final String expectedSha256) throws Exception {
        try (Archive archive = password == null ? new Archive(rar) : new Archive(rar, password)) {
            FileHeader header = findEntry(archive, entryName);
            if (header == null) {
                System.out.println(label + ": FAIL entry not found: " + entryName);
                return false;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            archive.extractFile(header, baos);
            String actualSha256 = sha256Hex(baos.toByteArray());
            if (!actualSha256.equals(expectedSha256)) {
                System.out.println(label + ": FAIL expected sha256=" + expectedSha256 + " actual=" + actualSha256);
                return false;
            }
            System.out.println(label + ": OK entry=" + entryName + " sha256=" + actualSha256);
            return true;
        }
    }

    private static FileHeader findEntry(final Archive archive, final String entryName) {
        for (FileHeader fileHeader : archive.getFileHeaders()) {
            if (fileHeader.getFileName().replace('\\', '/').equals(entryName)) {
                return fileHeader;
            }
        }
        return null;
    }

    private static String sha256Hex(final byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
