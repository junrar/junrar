package com.github.junrar.rar5.crypt;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * InputStream that decrypts data on-the-fly using AES-256-CBC.
 *
 * <p>Matches UnRAR's chunked decryption approach (32KB chunks).
 * Reads encrypted data from underlying stream, decrypts in chunks,
 * and returns decrypted bytes to callers.
 */
public final class DecryptingInputStream extends InputStream {

    /** Chunk size matching UnRAR's BitInput::MAX_SIZE (0x8000 = 32KB) */
    private static final int CHUNK_SIZE = 0x8000;

    /** AES block size */
    private static final int BLOCK_SIZE = 16;

    /** Mask to align to block boundary */
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;

    /** Underlying encrypted data source */
    private final InputStream source;

    /** AES cipher for decryption */
    private final Cipher cipher;

    /** Buffer for decrypted data */
    private byte[] decryptedBuffer;

    /** Position in decrypted buffer */
    private int decryptedPos;

    /** Total bytes remaining to read (0 if unknown) */
    private long remaining;

    /** Whether stream is closed */
    private boolean closed;

    /**
     * Creates a new decrypting input stream.
     *
     * @param source the underlying input stream containing encrypted data
     * @param key    the 32-byte AES-256 key
     * @param iv     the 16-byte initialization vector
     * @throws GeneralSecurityException if the cipher cannot be created
     */
    public DecryptingInputStream(final InputStream source, final byte[] key, final byte[] iv)
            throws GeneralSecurityException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("key must be 32 bytes");
        }
        if (iv == null || iv.length != 16) {
            throw new IllegalArgumentException("iv must be 16 bytes");
        }

        this.source = source;
        this.cipher = createCipher(key, iv);
        this.decryptedBuffer = new byte[0];
        this.decryptedPos = 0;
        this.remaining = 0;
        this.closed = false;
    }

    /**
     * Creates a cipher for AES-256-CBC decryption.
     */
    private static Cipher createCipher(final byte[] key, final byte[] iv) throws GeneralSecurityException {
        final SecretKey keySpec = new SecretKeySpec(key, "AES");
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher;
    }

    /**
     * Sets the total bytes to read (for proper block alignment).
     * Call this before reading if the total size is known.
     *
     * @param totalBytes the total number of encrypted bytes to read
     */
    public void setRemainingBytes(final long totalBytes) {
        this.remaining = totalBytes;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }

        if (decryptedPos >= decryptedBuffer.length) {
            if (!fillDecryptedBuffer()) {
                return -1;
            }
        }

        if (decryptedPos >= decryptedBuffer.length) {
            return -1;
        }

        return decryptedBuffer[decryptedPos++] & 0xFF;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }

        if (len <= 0) {
            return 0;
        }

        int totalRead = 0;

        while (totalRead < len) {
            if (decryptedPos >= decryptedBuffer.length) {
                if (!fillDecryptedBuffer()) {
                    break;
                }
            }

            if (decryptedPos >= decryptedBuffer.length) {
                break;
            }

            final int available = decryptedBuffer.length - decryptedPos;
            final int toCopy = Math.min(len - totalRead, available);
            System.arraycopy(decryptedBuffer, decryptedPos, b, off + totalRead, toCopy);
            decryptedPos += toCopy;
            totalRead += toCopy;
        }

        return totalRead > 0 ? totalRead : -1;
    }

    /**
     * Fills the decrypted buffer by reading and decrypting the next chunk.
     *
     * @return true if data was decrypted, false if no more data
     */
    private boolean fillDecryptedBuffer() throws IOException {
        // Calculate how much to read - align to block boundary
        int toRead = CHUNK_SIZE;

        // If we know remaining bytes, calculate aligned read
        if (remaining > 0) {
            // Read aligned chunks
            toRead = (int) Math.min(CHUNK_SIZE, remaining);
            // Align to block boundary
            toRead = toRead & ~BLOCK_MASK;
            if (toRead == 0) {
                return false;
            }
        }

        // Read encrypted data
        final byte[] encrypted = new byte[toRead];
        int read = 0;
        while (read < toRead) {
            final int n = source.read(encrypted, read, toRead - read);
            if (n <= 0) {
                break;
            }
            read += n;
        }

        if (read == 0) {
            return false;
        }

        // Update remaining
        if (remaining > 0) {
            remaining -= read;
        }

        // Decrypt the data
        try {
            final byte[] decrypted = cipher.update(encrypted, 0, read);
            if (decrypted != null && decrypted.length > 0) {
                this.decryptedBuffer = decrypted;
                this.decryptedPos = 0;
                return true;
            }
        } catch (IllegalStateException e) {
            throw new IOException("Decryption failed", e);
        }

        return false;
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            return 0;
        }
        return decryptedBuffer.length - decryptedPos + source.available();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                source.close();
            } finally {
                try {
                    // Finalize cipher - may produce final output block
                    cipher.doFinal();
                } catch (GeneralSecurityException e) {
                    // Ignore - stream is closing anyway
                }
            }
        }
    }
}
