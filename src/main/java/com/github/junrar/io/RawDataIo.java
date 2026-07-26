package com.github.junrar.io;

import java.io.IOException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class RawDataIo implements SeekableReadOnlyByteChannel {
    private Cipher cipher = null;
    private final SeekableReadOnlyByteChannel underlyingByteChannel;
    private boolean isEncrypted = false;
    // Decrypted bytes produced by a previous read but not yet consumed. AES-CBC
    // works on 16-byte blocks, so a read whose length is not block-aligned leaves
    // up to 15 surplus bytes carried over to the next read.
    private final byte[] leftover = new byte[16];
    private int leftoverPos = 0;
    private int leftoverLen = 0;
    private final byte[] reused = new byte[1];
    // Reusable buffers for the hot read paths, grown on demand: offset reads
    // (readFully only fills from index 0) and the encrypted cipher/plain pair.
    private byte[] offsetScratch;
    private byte[] cipherScratch;
    private byte[] plainScratch;

    public RawDataIo(SeekableReadOnlyByteChannel channel) {
        this.underlyingByteChannel = channel;
    }

    public Cipher getCipher() {
        return cipher;
    }

    public void setCipher(Cipher cipher) {
        this.cipher = cipher;
        isEncrypted = true;
    }

    @Override
    public long getPosition() throws IOException {
        return underlyingByteChannel.getPosition();
    }

    @Override
    public void setPosition(long pos) throws IOException {
        underlyingByteChannel.setPosition(pos);
    }

    @Override
    public int read() throws IOException {
        int size = read(reused, 0, 1);
        return size <= 0 ? -1 : reused[0] & 0xFF;
    }

    @Override
    public int read(byte[] buffer, int off, int count) throws IOException {
        if (off == 0) {
            return readFully(buffer, count);
        }
        if (offsetScratch == null || offsetScratch.length < count) {
            offsetScratch = new byte[count];
        }
        // Clear before use: the scratch array is reused across calls, so a short read would
        // otherwise expose bytes from a previous one. Copying the whole count out keeps the
        // caller's tail zero-filled exactly as the old per-call `new byte[count]` did.
        Arrays.fill(offsetScratch, 0, count, (byte) 0);
        int size = readFully(offsetScratch, count);
        System.arraycopy(offsetScratch, 0, buffer, off, count);
        return size;
    }

    @Override
    public int readFully(byte[] buffer, int count) throws IOException {
        if (!isEncrypted) {
            return underlyingByteChannel.readFully(buffer, count);
        }

        int written = 0;

        // Serve bytes decrypted by a previous read but not yet consumed.
        if (leftoverLen > 0) {
            int n = Math.min(leftoverLen, count);
            System.arraycopy(leftover, leftoverPos, buffer, 0, n);
            leftoverPos += n;
            leftoverLen -= n;
            written = n;
        }

        // Decrypt more ciphertext if the request isn't satisfied yet. Read the
        // shortfall rounded up to the AES block size and carry the surplus
        // (< 16 bytes) over to the next call.
        if (written < count) {
            int need = count - written;
            int blockAligned = need + ((16 - (need & 0xF)) & 0xF);
            if (cipherScratch == null || cipherScratch.length < blockAligned) {
                cipherScratch = new byte[blockAligned];
                // +16 headroom: NoPadding produces exactly `available` bytes for a block-aligned
                // input, but a provider is free to demand getOutputSize() room anyway, which for
                // CBC adds one block. The surplus is never populated -- see the invariant below.
                plainScratch = new byte[blockAligned + 16];
            }
            // Clear before use: SeekableReadOnlyInputStream.readFully reports the requested count
            // even at EOF (RandomAccessInputStream.readFully breaks out of its fill loop), so on a
            // truncated archive the tail of this reused array is whatever the previous read left
            // there. Zeroing keeps a corrupt archive's output reproducible and identical to the
            // old per-call `new byte[]` behaviour instead of a function of earlier reads.
            Arrays.fill(cipherScratch, 0, blockAligned, (byte) 0);
            int got = underlyingByteChannel.readFully(cipherScratch, blockAligned);
            // A well-formed encrypted stream always yields the full block-aligned amount. If the
            // underlying channel reports fewer bytes (truncated/corrupt archive), decrypt only the
            // complete 16-byte blocks actually read.
            int available = got < 0 ? 0 : got - (got & 0xF);
            if (available <= 0) {
                return written;
            }
            final int decryptedLen;
            try {
                decryptedLen = cipher.update(cipherScratch, 0, available, plainScratch, 0);
            } catch (ShortBufferException e) {
                throw new IOException(e);
            }
            // `available` is always a multiple of the AES block size, so an AES/CBC/NoPadding
            // cipher's internal buffer stays empty and this holds for every input. Asserting it
            // keeps the carry-over below within `leftover`: a cipher that flushed an extra
            // buffered block would push leftoverLen to 31 and overflow it.
            if (decryptedLen != available) {
                throw new IOException(
                        "Cipher produced "
                                + decryptedLen
                                + " bytes for "
                                + available
                                + " block-aligned input bytes");
            }

            int n = Math.min(decryptedLen, need);
            System.arraycopy(plainScratch, 0, buffer, written, n);
            written += n;

            leftoverLen = decryptedLen - n;
            if (leftoverLen > 0) {
                System.arraycopy(plainScratch, n, leftover, 0, leftoverLen);
                leftoverPos = 0;
            }
        }

        return written;
    }

    @Override
    public void close() throws IOException {
        this.underlyingByteChannel.close();
    }
}
