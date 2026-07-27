package com.github.junrar.rarfile;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;

/**
 * RAR 1.4 FILE header reader (P1, issue #293; unrar {@code Archive::ReadHeader14}'s
 * file-header branch, {@code d861246:arcread.cpp:1277-1320}). RAR 1.4 predates the
 * {@code BaseBlock}/header-CRC layout entirely: a fixed 21-byte ({@code
 * SIZEOF_FILEHEAD14}) field block, no header CRC, no extension records -- just the OEM
 * (CP437) name bytes and packed data inline. Populates the SAME unified {@link FileHeader}
 * the RAR3/RAR5 readers use, via {@link Parsed} fed to {@code FileHeader}'s package-private
 * RAR 1.4 constructor.
 * <p>
 * Public (like {@link Rar5FileHeaderReader}) because {@code Archive}, which drives the read
 * loop and owns the channel, lives in a different package ({@code com.github.junrar}) than
 * {@code com.github.junrar.rarfile}; {@link Parsed} itself stays package-private, visible
 * only to this reader and {@link FileHeader}.
 */
public final class Rar14HeaderReader {

    /**
     * unrar {@code SIZEOF_FILEHEAD14} ({@code d861246:headers.hpp}): the fixed-width portion
     * of a RAR 1.4 file header -- DataSize(4) UnpSize(4) CRC16(2) HeadSize(2) FileTime(4)
     * FileAttr(1) Flags(1) UnpVerByte(1) NameSize(1) Method(1) = 21 bytes, offsets 0..20 in
     * the order read here. The OEM name bytes ({@code nameBytes}) follow, read separately by
     * the caller once {@code NameSize} (byte offset 19) is known.
     */
    public static final int SIZEOF_FILEHEAD14 = 21;

    private Rar14HeaderReader() {}

    /**
     * @param fixed     the {@link #SIZEOF_FILEHEAD14}-byte fixed field block.
     * @param nameBytes the raw OEM name bytes, already read by the caller ({@code NameSize}
     *                  bytes, {@code fixed[19] & 0xff}).
     * @return the populated unified {@link FileHeader}.
     * @throws CorruptHeaderException if {@code fixed} is short, the declared {@code HeadSize}
     *                                is below {@link #SIZEOF_FILEHEAD14} (unrar: {@code
     *                                if (FileHead.HeadSize<21) return 0}, i.e. a broken
     *                                header), the declared {@code HeadSize} does not cover
     *                                {@code nameBytes} (a HeadSize/NameSize inconsistency
     *                                unrar's fixed-order reads can't otherwise catch), or the
     *                                entry name fails a filename-validity gate (C10).
     */
    public static FileHeader read(final byte[] fixed, final byte[] nameBytes)
            throws CorruptHeaderException {
        if (fixed.length < SIZEOF_FILEHEAD14) {
            throw new CorruptHeaderException("Truncated RAR 1.4 file header");
        }

        final Parsed p = new Parsed();
        p.packSize = Raw.readIntLittleEndianAsLong(fixed, 0);
        p.unpSize = Raw.readIntLittleEndianAsLong(fixed, 4);
        p.fileCRC = Raw.readShortLittleEndian(fixed, 8) & 0xffff;

        final int headSize = Raw.readShortLittleEndian(fixed, 10) & 0xffff;
        if (headSize < SIZEOF_FILEHEAD14) {
            throw new CorruptHeaderException("RAR 1.4 file header too small");
        }
        if (headSize < SIZEOF_FILEHEAD14 + nameBytes.length) {
            throw new CorruptHeaderException("RAR 1.4 file name runs past HeadSize");
        }
        p.headSize = headSize;

        final int fileTimeDos = Raw.readIntLittleEndian(fixed, 12);
        p.fileAttr = fixed[16] & 0xff;

        final int rawFlags = fixed[17] & 0xff;
        p.splitBefore = (rawFlags & BaseBlock.LHD_SPLIT_BEFORE) != 0;
        p.splitAfter = (rawFlags & BaseBlock.LHD_SPLIT_AFTER) != 0;
        p.encrypted = (rawFlags & BaseBlock.LHD_PASSWORD) != 0;

        final int unpVerByte = fixed[18] & 0xff;
        // unrar: FileHead.UnpVer=(Raw.Get1()==2) ? 13 : 10 (arcread.cpp:1288).
        p.unpVersion = (byte) (unpVerByte == 2 ? 13 : 10);

        final int declaredNameSize = fixed[19] & 0xff;
        if (declaredNameSize != nameBytes.length) {
            throw new CorruptHeaderException("RAR 1.4 file name size mismatch");
        }

        final int method = fixed[20] & 0xff;
        // RAR15+ headers store Method 0x30-based and unrar normalizes with "-0x30" at read
        // time (d861246:arcread.cpp:282); the RAR 1.4 reader stores it raw (0-based,
        // arcread.cpp:1290). junrar's unified FileHeader.unpMethod keeps the RAR15+ 0x30-based
        // convention throughout (Archive/Unpack compare against 0x30), so re-add the base the
        // RAR 1.4 wire format never had.
        p.unpMethod = 0x30 + method;

        // unrar: FileHead.Dir=(FileHead.FileAttr & 0x10)!=0 (arcread.cpp:1279) -- from the DOS
        // attribute byte, NOT the flags/window-mask field RAR3/RAR5 use.
        p.directory = (p.fileAttr & 0x10) != 0;

        p.mTime = FileTime.fromMillis(FileHeader.getDateDos(fileTimeDos));

        p.fileNameBytes = nameBytes;
        p.fileName = decodeOem(nameBytes);

        return new FileHeader(p);
    }

    /**
     * unrar {@code OemToExt}: the DOS OEM codepage, CP437 on every real RAR 1.4 archive.
     * Pinned to {@code IBM437} rather than ported as unrar's locale-dependent {@code
     * OemToCharA} so decoding is deterministic across platforms (P1 brief) -- a knowing
     * divergence, not a bug: unrar's own behavior varies by OS locale, so there is no single
     * "correct" byte-for-char mapping to match, and CP437 is what real RAR 1.4 archives (DOS
     * era) actually used. {@code IBM437} ships in every desktop JRE's charsets provider but is
     * not guaranteed by {@code java.base} alone; the {@code isSupported} probe upstream would
     * be over-engineering for a JVM this codebase already assumes has it, so this simply
     * falls back to a byte-transparent ISO-8859-1 decode if the provider is ever absent.
     */
    private static String decodeOem(final byte[] bytes) {
        try {
            return new String(bytes, "IBM437");
        } catch (final UnsupportedEncodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Mutable field bag fed to {@link FileHeader}'s package-private RAR 1.4 constructor --
     * kept package-private because it is a wire-format transcription detail, not part of the
     * {@code FileHeader} public contract (mirrors {@link Rar5FileHeaderReader.Parsed}).
     */
    static final class Parsed {
        long packSize;
        long unpSize;
        int fileCRC;
        int fileAttr;
        boolean splitBefore;
        boolean splitAfter;
        boolean encrypted;
        byte unpVersion;
        int unpMethod;
        boolean directory;
        FileTime mTime;
        byte[] fileNameBytes;
        String fileName;

        /**
         * The wire {@code HeadSize} field (P2, issue #293): distance from this header's start
         * to where its packed data begins -- {@code SIZEOF_FILEHEAD14 + NameSize} for a
         * well-formed entry, but the DECLARED field is what unrar itself uses for positioning
         * ({@code NextBlockPos=CurBlockPos+HeadSize+PackSize}, {@code
         * d861246:arcread.cpp:1322}), so this reader threads the same declared value through
         * rather than recomputing {@code 21 + nameBytes.length}. Fed to {@link
         * FileHeader#headerSize} so the inherited {@link FileHeader#getDataStartOffset} formula
         * ({@code positionInFile + getHeaderSize(...)}) -- already correct for RAR3 -- becomes
         * correct for RAR 1.4 too, with no new field or seek formula needed.
         */
        int headSize;
    }
}
