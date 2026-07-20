package com.github.junrar.rarfile.rar5;

import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;
import com.github.junrar.io.VInt;

import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

/**
 * RAR5 MAIN/CRYPT/ENDARC facts (M3.3, issue #24; unrar {@code ReadHeader50}'s
 * {@code HEAD_MAIN}/{@code HEAD_CRYPT}/{@code HEAD_ENDARC} cases, {@code 8f437ab:arcread.cpp
 * :730-857}, plus the MHEXTRA records of {@code ProcessExtra50}, {@code :955-1010}). One class
 * for all three block kinds (manual plan: "New Rar5MainHeader for MAIN/CRYPT/ENDARC facts") --
 * {@link #from} parses only the fields for {@link Rar5BaseBlock#getRar5Type()}'s kind, so an
 * accessor for a field that was never parsed simply returns its zero-value default (false/0/
 * null), a "wrong kind" query is never a crash.
 */
public final class Rar5MainHeader extends Rar5BaseBlock {

    // MHFL_* (RAR 5.0 main archive header specific flags, d861246:headers5.hpp:23-27).
    public static final int MHFL_VOLUME = 0x0001;
    public static final int MHFL_VOLNUMBER = 0x0002;
    public static final int MHFL_SOLID = 0x0004;
    public static final int MHFL_PROTECT = 0x0008;
    public static final int MHFL_LOCK = 0x0010;

    private static final int MHEXTRA_METADATA_NAME = 0x01;
    private static final int MHEXTRA_METADATA_CTIME = 0x02;
    private static final int MHEXTRA_METADATA_UNIXTIME = 0x04;
    private static final int MHEXTRA_METADATA_UNIX_NS = 0x08;

    // HEAD_CRYPT constants (unrar crypt.hpp / headers5.hpp).
    public static final long CRYPT_VERSION = 0;
    public static final int CRYPT5_KDF_LG2_COUNT_MAX = 24;
    public static final int SIZE_SALT50 = 16;
    public static final int SIZE_PSWCHECK = 8;
    public static final int SIZE_PSWCHECK_CSUM = 4;
    private static final int CHFL_CRYPT_PSWCHECK = 0x0001;

    // EHFL_* (RAR 5.0 end of archive header specific flags).
    private static final int EHFL_NEXTVOLUME = 0x0001;

    // MAIN
    private long archiveFlags;
    private long volumeNumber;
    private boolean locator;
    private String metadataName;
    private FileTime metadataTime;

    // CRYPT
    private long cryptVersion;
    private boolean usePswCheck;
    private int lg2Count;
    private byte[] salt16;
    private byte[] pswCheck;

    // ENDARC
    private boolean nextVolume;

    private Rar5MainHeader(final Rar5BaseBlock base) {
        super(base);
    }

    /**
     * @param base   the already-parsed generic block ({@link Rar5BaseBlock#parse}), for its
     *               type/flags/CRC/extra-size/data-size and {@link Rar5BaseBlock#getFieldsOffset()}.
     * @param header the full header buffer, length == {@code base.getRar5HeaderSize()}.
     * @return the typed header, with only the fields for {@code base.getRar5Type()}'s kind set.
     * @throws CorruptHeaderException on an unsupported CRYPT encryption version, an
     *                                out-of-range KDF iteration count, or a truncated buffer.
     */
    public static Rar5MainHeader from(final Rar5BaseBlock base, final byte[] header) throws CorruptHeaderException {
        final Rar5MainHeader h = new Rar5MainHeader(base);
        final Rar5BlockType type = base.getRar5Type();
        if (type == Rar5BlockType.MAIN) {
            h.parseMain(header);
        } else if (type == Rar5BlockType.CRYPT) {
            h.parseCrypt(header);
        } else if (type == Rar5BlockType.ENDARC) {
            h.parseEndArc(header);
        }
        return h;
    }

    private void parseMain(final byte[] header) throws CorruptHeaderException {
        final VInt r = new VInt(header, getFieldsOffset());
        this.archiveFlags = r.read();
        if ((this.archiveFlags & MHFL_VOLNUMBER) != 0) {
            this.volumeNumber = r.read();
        }
        if (getExtraSize() != 0) {
            parseMainExtras(header);
        }
    }

    private void parseMainExtras(final byte[] header) throws CorruptHeaderException {
        final int headerSize = getRar5HeaderSize();
        int pos = headerSize - (int) getExtraSize();
        while (headerSize - pos >= 2) {
            final VInt sizeReader = new VInt(header, pos);
            final long fieldSize = sizeReader.read();
            final int afterSize = sizeReader.position();
            if (fieldSize <= 0 || fieldSize > headerSize - afterSize) {
                break;
            }
            final int nextPos = (int) (afterSize + fieldSize);
            final VInt typeReader = new VInt(header, afterSize);
            final long fieldType = typeReader.read();
            final int payloadStart = typeReader.position();
            if (payloadStart > nextPos) {
                // unrar ProcessExtra50: FieldType ran past the record's own declared
                // FieldSize ("FieldSize=int64(NextPos-GetPos()); if (FieldSize<0) break;") --
                // abort the whole extra-area loop, don't let the handler read into the next
                // record's bytes.
                break;
            }

            final Rar5MainExtraType extraType = Rar5MainExtraType.findType(fieldType);
            if (extraType == Rar5MainExtraType.LOCATOR) {
                // MHEXTRA_LOCATOR consciously skipped: the QLIST/RR offsets aren't consumed
                // anywhere in the M3.3 read path, only presence is recorded.
                this.locator = true;
            } else if (extraType == Rar5MainExtraType.METADATA) {
                parseMetadata(header, payloadStart, nextPos);
            }
            pos = nextPos;
        }
    }

    private void parseMetadata(final byte[] header, final int start, final int end) throws CorruptHeaderException {
        if (start >= end) {
            return;
        }
        final VInt r = new VInt(header, start);
        final long metaFlags = r.read();
        int pos = r.position();

        if ((metaFlags & MHEXTRA_METADATA_NAME) != 0 && pos < end) {
            final VInt nameReader = new VInt(header, pos);
            final long nameSize = nameReader.read();
            pos = nameReader.position();
            if (nameSize > 0 && nameSize < 0x10000 && pos + nameSize <= end) {
                final int len = (int) nameSize;
                if (header[pos] != 0) {
                    this.metadataName = new String(header, pos, len, StandardCharsets.UTF_8);
                }
                pos += len;
            }
        }

        if ((metaFlags & MHEXTRA_METADATA_CTIME) != 0) {
            if ((metaFlags & MHEXTRA_METADATA_UNIXTIME) != 0 && (metaFlags & MHEXTRA_METADATA_UNIX_NS) != 0) {
                if (pos + 8 <= end) {
                    this.metadataTime = Rar5Time.fromUnixNanos(Raw.readLongLittleEndian(header, pos));
                }
            } else if ((metaFlags & MHEXTRA_METADATA_UNIXTIME) != 0) {
                if (pos + 4 <= end) {
                    this.metadataTime = Rar5Time.fromUnixSeconds(Raw.readIntLittleEndianAsLong(header, pos));
                }
            } else if (pos + 8 <= end) {
                this.metadataTime = Rar5Time.fromWindowsFileTime(Raw.readLongLittleEndian(header, pos));
            }
        }
    }

    private void parseCrypt(final byte[] header) throws CorruptHeaderException {
        final VInt r = new VInt(header, getFieldsOffset());
        this.cryptVersion = r.read();
        if (this.cryptVersion != CRYPT_VERSION) {
            throw new CorruptHeaderException("Unsupported RAR5 encryption version " + this.cryptVersion);
        }
        final long encFlags = r.read();
        this.usePswCheck = (encFlags & CHFL_CRYPT_PSWCHECK) != 0;

        int pos = r.position();
        if (pos >= header.length) {
            throw new CorruptHeaderException("Truncated RAR5 CRYPT header");
        }
        this.lg2Count = header[pos++] & 0xff;
        if (this.lg2Count > CRYPT5_KDF_LG2_COUNT_MAX) {
            throw new CorruptHeaderException("RAR5 KDF Lg2Count exceeds " + CRYPT5_KDF_LG2_COUNT_MAX);
        }
        if (pos + SIZE_SALT50 > header.length) {
            throw new CorruptHeaderException("Truncated RAR5 CRYPT header");
        }
        this.salt16 = Arrays.copyOfRange(header, pos, pos + SIZE_SALT50);
        pos += SIZE_SALT50;
        if (this.usePswCheck && pos + SIZE_PSWCHECK + SIZE_PSWCHECK_CSUM <= header.length) {
            // csum (SHA-256 prefix) verification deferred to M3.4; not read further here.
            this.pswCheck = Arrays.copyOfRange(header, pos, pos + SIZE_PSWCHECK);
        }
    }

    private void parseEndArc(final byte[] header) throws CorruptHeaderException {
        final VInt r = new VInt(header, getFieldsOffset());
        final long endFlags = r.read();
        this.nextVolume = (endFlags & EHFL_NEXTVOLUME) != 0;
    }

    // ---- MAIN accessors ----------------------------------------------------

    public boolean isVolume() {
        return (this.archiveFlags & MHFL_VOLUME) != 0;
    }

    public boolean isSolid() {
        return (this.archiveFlags & MHFL_SOLID) != 0;
    }

    public boolean isLocked() {
        return (this.archiveFlags & MHFL_LOCK) != 0;
    }

    public boolean isProtected() {
        return (this.archiveFlags & MHFL_PROTECT) != 0;
    }

    public boolean hasLocator() {
        return this.locator;
    }

    public long getVolumeNumber() {
        return this.volumeNumber;
    }

    public boolean isFirstVolume() {
        return isVolume() && this.volumeNumber == 0;
    }

    public String getMetadataName() {
        return this.metadataName;
    }

    public FileTime getMetadataTime() {
        return this.metadataTime;
    }

    // ---- CRYPT accessors ----------------------------------------------------

    public long getCryptVersion() {
        return this.cryptVersion;
    }

    public boolean isUsePswCheck() {
        return this.usePswCheck;
    }

    public int getLg2Count() {
        return this.lg2Count;
    }

    public byte[] getSalt16() {
        return this.salt16 == null ? null : this.salt16.clone();
    }

    public byte[] getPswCheck() {
        return this.pswCheck == null ? null : this.pswCheck.clone();
    }

    // ---- ENDARC accessors ----------------------------------------------------

    public boolean isNextVolume() {
        return this.nextVolume;
    }
}
