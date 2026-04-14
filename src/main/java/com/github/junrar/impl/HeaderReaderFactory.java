package com.github.junrar.impl;

import com.github.junrar.exception.BadRarArchiveException;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.impl.rar4.RAR4HeaderReader;
import com.github.junrar.impl.rar5.RAR5HeaderReader;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.BaseBlock;
import com.github.junrar.rarfile.MarkHeader;
import com.github.junrar.rarfile.RARVersion;
import com.github.junrar.rarfile.UnrarHeadertype;

/**
 * Factory for creating the appropriate HeaderReader based on RAR archive version.
 * Reads the MarkHeader from the archive to detect whether it is RAR4 or RAR5 format.
 */
public class HeaderReaderFactory {

    private HeaderReaderFactory() {
    }

    /**
     * Creates an appropriate HeaderReader based on the RAR archive version.
     * Reads the MarkHeader from the channel to determine the version and
     * returns the corresponding reader (RAR4 or RAR5).
     *
     * @param channel the seekable byte channel to read from
     * @return a HeaderReader appropriate for the archive version
     * @throws BadRarArchiveException if the archive is not a valid RAR file (non-V5)
     * @throws CorruptHeaderException if the MarkHeader is invalid
     * @throws Exception if an I/O error occurs
     */
    public static HeaderReader create(final SeekableReadOnlyByteChannel channel) throws Exception {
        final byte[] baseBlockBuffer = new byte[BaseBlock.BaseBlockSize];
        channel.readFully(baseBlockBuffer, baseBlockBuffer.length);

        final BaseBlock block = new BaseBlock(baseBlockBuffer);
        final UnrarHeadertype headerType = block.getHeaderType();
        if (headerType == null) {
            throw new CorruptHeaderException();
        } else if (headerType != UnrarHeadertype.MarkHeader) {
            throw new BadRarArchiveException();
        }
        final MarkHeader markHead = new MarkHeader(block);

        if (!markHead.isSignature()) {
            if (markHead.getVersion() == RARVersion.V5) {
                return new RAR5HeaderReader(markHead);
            }
            throw new BadRarArchiveException();
        }
        if (!markHead.isValid()) {
            throw new CorruptHeaderException("Invalid Mark Header");
        }

        return new RAR4HeaderReader(markHead);
    }
}
