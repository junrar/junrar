package com.github.junrar.crypt;

import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code javax.crypto.CipherSpi} adapter plugging {@link Rar13Cipher}/{@link Rar15Cipher}/{@link
 * Rar20Cipher} into a real {@code javax.crypto.Cipher} so {@link
 * com.github.junrar.io.RawDataIo}'s existing {@code cipher.update(byte[])} seam needs no changes
 * -- the same seam {@link Rijndael} and {@link Rar5Crypt} already use. Decrypt-only (P3
 * non-goal: no encryption/write support for these formats); {@link #engineDoFinal} exists only
 * because {@code CipherSpi} requires it, RawDataIo never calls it.
 */
final class LegacyCipherSpi extends CipherSpi {

    private final CryptMethod method;
    private LegacyCipherEngine engine;

    LegacyCipherSpi(final CryptMethod method) {
        this.method = method;
    }

    @Override
    protected void engineSetMode(final String mode) throws java.security.NoSuchAlgorithmException {
        throw new java.security.NoSuchAlgorithmException(
                "legacy RAR ciphers have no selectable mode: " + mode);
    }

    @Override
    protected void engineSetPadding(final String padding) throws NoSuchPaddingException {
        throw new NoSuchPaddingException("legacy RAR ciphers use no padding: " + padding);
    }

    @Override
    protected int engineGetBlockSize() {
        return RarLegacyCrypt.CRYPT_BLOCK_SIZE;
    }

    @Override
    protected int engineGetOutputSize(final int inputLen) {
        return inputLen;
    }

    @Override
    protected byte[] engineGetIV() {
        return null; // No salt, no IV -- keying is password-only (crypt.hpp).
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    @Override
    protected void engineInit(final int opmode, final Key key, final SecureRandom random)
            throws InvalidKeyException {
        if (opmode != Cipher.DECRYPT_MODE) {
            throw new InvalidKeyException(
                    "legacy RAR ciphers support DECRYPT_MODE only (no encryption support)");
        }
        if (!(key instanceof SecretKeySpec)) {
            throw new InvalidKeyException(
                    "expected a SecretKeySpec carrying the raw password bytes");
        }
        final byte[] passwordBytes = key.getEncoded();
        switch (method) {
            case RAR13:
                engine = new Rar13Cipher(passwordBytes);
                break;
            case RAR15:
                engine = new Rar15Cipher(passwordBytes);
                break;
            case RAR20:
                engine = new Rar20Cipher(passwordBytes);
                break;
            default:
                throw new InvalidKeyException("not a legacy cipher method: " + method);
        }
    }

    @Override
    protected void engineInit(
            final int opmode,
            final Key key,
            final AlgorithmParameterSpec params,
            final SecureRandom random)
            throws InvalidKeyException {
        engineInit(opmode, key, random);
    }

    @Override
    protected void engineInit(
            final int opmode,
            final Key key,
            final AlgorithmParameters params,
            final SecureRandom random)
            throws InvalidKeyException {
        engineInit(opmode, key, random);
    }

    @Override
    protected byte[] engineUpdate(final byte[] input, final int inputOffset, final int inputLen) {
        final byte[] out = new byte[inputLen];
        System.arraycopy(input, inputOffset, out, 0, inputLen);
        engine.decrypt(out, 0, inputLen);
        return out;
    }

    @Override
    protected int engineUpdate(
            final byte[] input,
            final int inputOffset,
            final int inputLen,
            final byte[] output,
            final int outputOffset)
            throws ShortBufferException {
        if (output.length - outputOffset < inputLen) {
            throw new ShortBufferException("output buffer too small for legacy cipher update");
        }
        System.arraycopy(input, inputOffset, output, outputOffset, inputLen);
        engine.decrypt(output, outputOffset, inputLen);
        return inputLen;
    }

    @Override
    protected byte[] engineDoFinal(final byte[] input, final int inputOffset, final int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        return engineUpdate(input, inputOffset, inputLen);
    }

    @Override
    protected int engineDoFinal(
            final byte[] input,
            final int inputOffset,
            final int inputLen,
            final byte[] output,
            final int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return engineUpdate(input, inputOffset, inputLen, output, outputOffset);
    }
}
