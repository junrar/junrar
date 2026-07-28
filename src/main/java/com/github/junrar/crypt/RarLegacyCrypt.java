package com.github.junrar.crypt;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cipher selection and construction for the three pre-AES RAR generations (P3, issue #293):
 * CRYPT_RAR13 / CRYPT_RAR15 / CRYPT_RAR20 (unrar {@code d861246:crypt.hpp:5-8}). Wires {@link
 * Rar13Cipher}/{@link Rar15Cipher}/{@link Rar20Cipher} behind a genuine {@code
 * javax.crypto.Cipher} (via {@link LegacyCipherSpi}) so {@link
 * com.github.junrar.io.RawDataIo}'s existing Cipher-shaped seam needs no changes -- the same
 * seam {@link Rijndael} and {@link Rar5Crypt} already use.
 */
public final class RarLegacyCrypt {

    /** {@code CRYPT_BLOCK_SIZE}, {@code d861246:crypt.hpp:15}. */
    public static final int CRYPT_BLOCK_SIZE = 16;

    private RarLegacyCrypt() {}

    /**
     * Selects the legacy-era decryption cipher exactly as unrar does ({@code
     * d861246:arcread.cpp:290-298} for the RARFMT15 switch, {@code :1300} for the RARFMT14 case).
     * Callers must already know the entry is encrypted ({@code FileHeader#isEncrypted()}) -- this
     * method does not re-check that flag.
     *
     * @param unpVersion       the entry's {@code UnpVer} byte ({@code
     *                         FileHeader#getUnpVersion()})
     * @param archiveOldFormat whether the archive is the bare RAR 1.4 container ({@code
     *                         Archive#isOldFormat()}) -- unlike every later format, RAR 1.4
     *                         ignores {@code UnpVer} for cipher choice and always uses CRYPT_RAR13
     * @return the cipher method to use; {@link CryptMethod#RAR30} means "not a legacy cipher",
     *         signalling the caller to fall back to {@link Rijndael#buildDecipherer}
     */
    public static CryptMethod select(final int unpVersion, final boolean archiveOldFormat) {
        if (archiveOldFormat) {
            return CryptMethod.RAR13;
        }
        switch (unpVersion & 0xff) {
            case 13:
                return CryptMethod.RAR13;
            case 15:
                return CryptMethod.RAR15;
            case 20:
            case 26:
                return CryptMethod.RAR20;
            default:
                return CryptMethod.RAR30;
        }
    }

    /**
     * Builds a decrypting {@code Cipher} for one of the three legacy methods (never {@link
     * CryptMethod#RAR30}, which stays on {@link Rijndael#buildDecipherer}). No salt, no IV --
     * keying is password-only (crypt.hpp; unrar {@code SetCryptKeys}, {@code crypt.cpp:70-93}).
     *
     * <p><b>Password encoding divergence (documented, accepted):</b> unrar derives {@code PwdA}
     * via {@code WideToChar} -- a single-byte string, NOT UTF-16LE like the RAR30/{@link
     * Rijndael} path. This ports it as ISO-8859-1 bytes. unrar's own DOS-OEM codepage conversion
     * for these ciphers is Windows-only ({@code extract.cpp:1355-1370}, {@code #if
     * defined(_WIN_ALL)}) and is deliberately NOT ported here; a non-ASCII password on a RAR
     * 1.3/1.5/2.0 archive is an accepted, documented divergence -- no fixture in this suite
     * exercises one.
     *
     * @throws InvalidKeyException if {@code password} is {@code null}
     */
    public static Cipher buildDecipherer(final CryptMethod method, final String password)
            throws InvalidKeyException {
        if (method == CryptMethod.RAR30) {
            throw new IllegalArgumentException("RAR30 is not a legacy cipher; use Rijndael");
        }
        if (password == null) {
            throw new InvalidKeyException("password should be specified");
        }
        final byte[] passwordBytes = password.getBytes(StandardCharsets.ISO_8859_1);
        final String transformation = method.name();
        final Cipher cipher = new LegacyCipher(new LegacyCipherSpi(method), transformation);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(passwordBytes, transformation));
        return cipher;
    }

    /**
     * Minimal {@code Cipher} subclass exposing the protected {@code Cipher(CipherSpi, Provider,
     * String)} constructor (manual &sect;4.11 pattern: primitives from the JDK) -- avoids
     * registering a JCE {@link Provider} globally via {@code Security.addProvider}, which would
     * leak into every consumer of this library's JVM. The {@code double}-version {@link Provider}
     * constructor is deprecated since Java 9 but is the only one available under this project's
     * Java 8 API surface ({@code compileJava} release 8); the version number itself is otherwise
     * meaningless here.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyCipher extends Cipher {
        private static final Provider PROVIDER =
                new Provider("Junrar-Legacy", 1.0, "junrar legacy RAR 1.3/1.5/2.0 ciphers") {};

        LegacyCipher(final LegacyCipherSpi spi, final String transformation) {
            super(spi, PROVIDER, transformation);
        }
    }
}
