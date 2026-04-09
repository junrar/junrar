package com.github.junrar.crc;

/**
 * Pure Java implementation of Blake2sp (parallel Blake2s).
 * Based on the C++ reference implementation in unrar.
 */
public final class Blake2sp {

    public static final int DIGEST_SIZE = 32;
    private static final int PARALLELISM = 8;
    private static final int BLOCK_SIZE = 64;
    private static final int BUFFER_SIZE = PARALLELISM * BLOCK_SIZE;

    private final Blake2s[] lanes = new Blake2s[PARALLELISM];
    private final Blake2s finalHash;
    private boolean finalized;
    private final byte[] buffer = new byte[BUFFER_SIZE];
    private int bufferPos;

    public Blake2sp() {
        for (int i = 0; i < PARALLELISM; i++) {
            lanes[i] = new Blake2s(DIGEST_SIZE);
            lanes[i].resetForBlake2spLane(i);
        }
        finalHash = new Blake2s(DIGEST_SIZE);
        finalHash.resetForTreeModeFinal();
    }

    public void reset() {
        for (int i = 0; i < PARALLELISM; i++) {
            lanes[i].resetForBlake2spLane(i);
        }
        finalHash.resetForTreeModeFinal();
        finalized = false;
        bufferPos = 0;
    }

    public void update(final byte[] data) {
        update(data, 0, data.length);
    }

    public void update(final byte[] data, final int offset, final int length) {
        if (finalized) {
            throw new IllegalStateException("Hasher has been finalized");
        }
        if (length == 0) {
            return;
        }

        int pos = offset;
        int remaining = length;

        // If there's data in buffer, try to fill it first
        if (bufferPos > 0) {
            int toCopy = Math.min(BUFFER_SIZE - bufferPos, remaining);
            System.arraycopy(data, pos, buffer, bufferPos, toCopy);
            bufferPos += toCopy;
            pos += toCopy;
            remaining -= toCopy;

            if (bufferPos == BUFFER_SIZE) {
                for (int i = 0; i < PARALLELISM; i++) {
                    lanes[i].update(buffer, i * BLOCK_SIZE, BLOCK_SIZE);
                }
                bufferPos = 0;
            }
        }

        // Process full blocks (PARALLELISM * BLOCK_SIZE = 512 bytes)
        while (remaining >= BUFFER_SIZE) {
            for (int i = 0; i < PARALLELISM; i++) {
                lanes[i].update(data, pos + i * BLOCK_SIZE, BLOCK_SIZE);
            }
            pos += BUFFER_SIZE;
            remaining -= BUFFER_SIZE;
        }

        // Copy remaining to buffer
        if (remaining > 0) {
            System.arraycopy(data, pos, buffer, 0, remaining);
            bufferPos = remaining;
        }
    }

    public byte[] digest() {
        if (finalized) {
            throw new IllegalStateException("Hasher has already been finalized");
        }

        // Process remaining bytes in buffer
        if (bufferPos > 0) {
            for (int i = 0; i < PARALLELISM; i++) {
                int laneStart = i * BLOCK_SIZE;
                int laneBytes = Math.min(BLOCK_SIZE, bufferPos - laneStart);
                if (laneBytes > 0) {
                    lanes[i].update(buffer, laneStart, laneBytes);
                }
            }
        }

        // Finalize each lane and feed into final hash in order 0-7
        for (int i = 0; i < PARALLELISM; i++) {
            byte[] laneDigest = lanes[i].digest();
            finalHash.update(laneDigest);
        }

        finalized = true;
        return finalHash.digest();
    }
}
