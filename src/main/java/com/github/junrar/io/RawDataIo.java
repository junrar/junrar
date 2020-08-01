package com.github.junrar.io;

import javax.crypto.Cipher;
import java.io.IOException;
import java.util.LinkedList;

public class RawDataIo implements SeekableReadOnlyByteChannel {
    private Cipher cipher = null;
    private final SeekableReadOnlyByteChannel underlyingByteChannel;
    private boolean isEncrypted = false;
    private final LinkedList<Byte> dataPool = new LinkedList<>();
    private final byte[] reused = new byte[1];

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
        read(reused, 0, 1);
        return reused[0];
    }

    @Override
    public int read(byte[] buffer, int off, int count) throws IOException {
        byte[] tmp = new byte[count];
        int size = readFully(tmp, count);
        System.arraycopy(tmp, 0, buffer, off, count);
        return size;
    }

    @Override
    public int readFully(byte[] buffer, int count) throws IOException {
        if (isEncrypted) {
            int remainingSize = dataPool.size();
            int toRead = count - remainingSize;
            int realRead = toRead + ((~toRead + 1) & 0xF);
            byte[] tmp = new byte[realRead];

            if (realRead > 0) {
                underlyingByteChannel.readFully(tmp, realRead);
                byte[] decrypted = cipher.update(tmp);
                for (int i = 0; i < decrypted.length; i++) {
                    dataPool.add(decrypted[i]);
                }
            }

            int realReadSize = 0;
            for (int i = 0; i < count && !dataPool.isEmpty(); i++) {
                buffer[i] = dataPool.poll();
                realReadSize++;
            }
            return realReadSize;

        } else {
            return underlyingByteChannel.readFully(buffer, count);
        }
    }

    @Override
    public void close() throws IOException {
        this.underlyingByteChannel.close();
    }

}
