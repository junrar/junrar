package com.github.junrar.rarfile.rar5;

import com.github.junrar.crc.RarCRC;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.io.Raw;
import com.github.junrar.io.VInt;
import com.github.junrar.rarfile.BaseBlock;

/**
 * Generic RAR5 block header (unrar {@code ReadHeader50}'s {@code ShortBlock},
 * {@code 8f437ab:arcread.cpp:555-...}; boundary + CRC verified at
 * {@code d861246:arcread.cpp:634-707}). Wire layout:
 * <pre>
 *   CRC32(4) | vint HeadSize | vint Type | vint Flags [| vint ExtraSize][| vint DataSize]
 * </pre>
 * Extends {@link BaseBlock} so RAR5 blocks live in {@code Archive}'s single
 * {@code List&lt;BaseBlock&gt; headers} (manual &sect;5.1) and reuse its broken-header
 * marking ({@link BaseBlock#setBrokenHeader}) -- the same flag {@code Archive} refuses at
 * extract time. The inherited RAR3 accessors ({@code getHeaderType}, the 16-bit
 * {@code getHeadCRC}, the {@code short} sizes) are NOT meaningful for a RAR5 block and are
 * left at their defaults; RAR5 facts are read through the {@code getRar5*} accessors and
 * the named flag predicates below.
 * <p>
 * This class only models the shared block framework (M3.2). The typed main/file/service
 * headers and their extra records are loaded in M3.3 onto the unified {@code FileHeader}.
 */
public class Rar5BaseBlock extends BaseBlock {

    /** RAR5 headers must not exceed 2 MB (unrar {@code MAX_HEADER_SIZE_RAR5}, {@code archive.hpp:24}). */
    public static final int MAX_HEADER_SIZE_RAR5 = 0x200000;

    /** Smallest possible RAR5 block, also the first-read size (unrar {@code SIZEOF_SHORTBLOCKHEAD5}, {@code FirstReadSize=7}). */
    public static final int FIRST_READ_SIZE = 7;

    /** The header-size vint occupies at most 3 bytes -- the mechanism that bounds the header to 2 MB. */
    private static final int MAX_SIZE_VINT_BYTES = 3;

    // Header flags (unrar HFL_*, d861246:headers5.hpp:10-22).
    public static final int HFL_EXTRA = 0x0001;
    public static final int HFL_DATA = 0x0002;
    public static final int HFL_SKIPIFUNKNOWN = 0x0004;
    public static final int HFL_SPLITBEFORE = 0x0008;
    public static final int HFL_SPLITAFTER = 0x0010;
    public static final int HFL_CHILD = 0x0020;
    public static final int HFL_INHERITED = 0x0040;

    private final Rar5BlockType rar5Type;
    private final long rar5TypeValue;
    private final long rar5Flags;
    private final int headerCrc32;
    private final int rar5HeaderSize;
    private final long extraSize;
    private final long dataSize;
    private int fieldsOffset;

    private Rar5BaseBlock(final Rar5BlockType rar5Type, final long rar5TypeValue, final long rar5Flags,
                          final int headerCrc32, final int rar5HeaderSize, final long extraSize, final long dataSize) {
        this.rar5Type = rar5Type;
        this.rar5TypeValue = rar5TypeValue;
        this.rar5Flags = rar5Flags;
        this.headerCrc32 = headerCrc32;
        this.rar5HeaderSize = rar5HeaderSize;
        this.extraSize = extraSize;
        this.dataSize = dataSize;
    }

    /**
     * Copy constructor for the typed M3.3 subclasses ({@code Rar5MainHeader}): the private
     * all-args constructor above blocks subclassing, so a typed header is built by parsing the
     * shared framework fields once via {@link #parse}, then copying the result into the
     * subclass and parsing its type-specific fields from the same buffer.
     *
     * @param copy the already-parsed generic block to copy the framework fields from.
     */
    protected Rar5BaseBlock(final Rar5BaseBlock copy) {
        this.rar5Type = copy.rar5Type;
        this.rar5TypeValue = copy.rar5TypeValue;
        this.rar5Flags = copy.rar5Flags;
        this.headerCrc32 = copy.headerCrc32;
        this.rar5HeaderSize = copy.rar5HeaderSize;
        this.extraSize = copy.extraSize;
        this.dataSize = copy.dataSize;
        this.fieldsOffset = copy.fieldsOffset;
        setBrokenHeader(copy.isBrokenHeader());
        setPositionInFile(copy.getPositionInFile());
    }

    /**
     * Allocation-free header-size bound (unrar {@code d861246:arcread.cpp:634-668}): from the
     * first {@link #FIRST_READ_SIZE} bytes (4-byte CRC + at most 3 header-size vint bytes),
     * decode {@code BlockSize} and derive the total {@code HeaderSize}, rejecting a size vint
     * longer than 3 bytes, a zero {@code BlockSize}, a header below the minimum block size, or
     * a header over the 2 MB cap -- all <em>before</em> any tail buffer is allocated.
     *
     * @param first the first {@link #FIRST_READ_SIZE} bytes of the block (CRC + size vint)
     * @return the total header size in bytes, guaranteed {@code >= 7} and {@code <= 2 MB}
     * @throws CorruptHeaderException on any of the boundary violations above
     */
    public static int checkHeaderSize(final byte[] first) throws CorruptHeaderException {
        final VInt sizeReader = new VInt(first, 4);
        final long blockSize = sizeReader.read();
        final int sizeBytes = sizeReader.position() - 4;
        if (sizeBytes > MAX_SIZE_VINT_BYTES) {
            throw new CorruptHeaderException("RAR5 header size field exceeds " + MAX_SIZE_VINT_BYTES + " vint bytes");
        }
        if (blockSize == 0) {
            throw new CorruptHeaderException("RAR5 block size is zero");
        }
        final long headerSize = 4L + sizeBytes + blockSize;
        if (headerSize < FIRST_READ_SIZE) {
            throw new CorruptHeaderException("RAR5 header smaller than the minimum block size");
        }
        if (headerSize > MAX_HEADER_SIZE_RAR5) {
            throw new CorruptHeaderException("RAR5 header exceeds the 2 MB maximum");
        }
        return (int) headerSize;
    }

    /**
     * Parses a full RAR5 block header (unrar {@code d861246:arcread.cpp:674-707}). The buffer
     * must be exactly {@code HeaderSize} bytes, as returned by {@link #checkHeaderSize}.
     * Verifies the header CRC ({@code GetCRC50}), then reads Type, Flags and the optional
     * ExtraSize/DataSize descriptors.
     * <p>
     * CRC handling mirrors P0.7's record-vs-fatal split (manual &sect;5.1): an unencrypted
     * mismatch marks the block broken and keeps parsing (unrar "report, but attempt to
     * process" -- extraction of a broken-header entry throws later); an encrypted mismatch is
     * fatal at open (unrar sets {@code FailedHeaderDecryption} -- M3.4 maps this onto
     * {@code WrongPasswordException}, indistinguishable from a wrong key under CBC).
     *
     * @param header    the full header buffer (CRC .. end), length == {@code HeaderSize}
     * @param encrypted whether the archive's headers are encrypted (drives the fatal split)
     * @return the parsed block, with {@link BaseBlock#isBrokenHeader()} set on an unencrypted
     *         CRC mismatch
     * @throws CorruptHeaderException on encrypted CRC mismatch, {@code ExtraSize >= HeadSize},
     *                                an unknown non-skippable type, or a vint running past the
     *                                header buffer (truncated mid-header)
     */
    public static Rar5BaseBlock parse(final byte[] header, final boolean encrypted) throws CorruptHeaderException {
        final int storedCrc = Raw.readIntLittleEndian(header, 0);
        final int computedCrc = RarCRC.computeHeaderCrc32(header, 4, header.length - 4);
        final boolean broken = storedCrc != computedCrc;
        if (broken && encrypted) {
            throw new CorruptHeaderException("RAR5 encrypted header CRC mismatch at open");
        }

        // Skip the header-size vint (already validated by checkHeaderSize) to reach Type.
        final VInt reader = new VInt(header, 4);
        reader.read();
        final long typeValue = reader.read();
        final long flags = reader.read();

        final Rar5BlockType type = Rar5BlockType.findType(typeValue);
        if (type == null && (flags & HFL_SKIPIFUNKNOWN) == 0) {
            throw new CorruptHeaderException("Unknown non-skippable RAR5 block type: " + typeValue);
        }

        long extraSize = 0;
        if ((flags & HFL_EXTRA) != 0) {
            extraSize = reader.read();
            // unrar: ExtraSize >= HeadSize is corrupt (d861246:arcread.cpp:700-707).
            if (extraSize >= header.length) {
                throw new CorruptHeaderException("RAR5 ExtraSize >= HeaderSize");
            }
        }
        long dataSize = 0;
        if ((flags & HFL_DATA) != 0) {
            dataSize = reader.read();
        }
        final int fieldsOffset = reader.position();

        final Rar5BaseBlock block = new Rar5BaseBlock(type, typeValue, flags, storedCrc, header.length, extraSize, dataSize);
        block.fieldsOffset = fieldsOffset;
        block.setBrokenHeader(broken);
        return block;
    }

    /** @return the parsed block type, or {@code null} for an unknown (skippable) type. */
    public Rar5BlockType getRar5Type() {
        return this.rar5Type;
    }

    /** @return the raw type wire value (a vint). */
    public long getRar5TypeValue() {
        return this.rar5TypeValue;
    }

    /** @return the raw flags word (a vint). */
    public long getRar5Flags() {
        return this.rar5Flags;
    }

    /** @return the full 32-bit header CRC as stored on disk. */
    public int getHeaderCrc32() {
        return this.headerCrc32;
    }

    /** @return the total header size in bytes ({@code HeadSize}). */
    public int getRar5HeaderSize() {
        return this.rar5HeaderSize;
    }

    /** @return the declared extra-area size, or 0 if {@link #hasExtra()} is false. */
    public long getExtraSize() {
        return this.extraSize;
    }

    /** @return the declared packed-data size following the header, or 0 if {@link #hasData()} is false. */
    public long getDataSize() {
        return this.dataSize;
    }

    /**
     * @return the header-buffer offset of the first byte past the common framework fields
     *         (Size/Type/Flags and, when present, ExtraSize/DataSize) -- where a typed
     *         subclass's type-specific fixed fields begin (manual &sect;M3.3).
     */
    public int getFieldsOffset() {
        return this.fieldsOffset;
    }

    public boolean hasExtra() {
        return (this.rar5Flags & HFL_EXTRA) != 0;
    }

    public boolean hasData() {
        return (this.rar5Flags & HFL_DATA) != 0;
    }

    public boolean isSkipIfUnknown() {
        return (this.rar5Flags & HFL_SKIPIFUNKNOWN) != 0;
    }

    public boolean isSplitBefore() {
        return (this.rar5Flags & HFL_SPLITBEFORE) != 0;
    }

    public boolean isSplitAfter() {
        return (this.rar5Flags & HFL_SPLITAFTER) != 0;
    }

    public boolean isChild() {
        return (this.rar5Flags & HFL_CHILD) != 0;
    }

    public boolean isInherited() {
        return (this.rar5Flags & HFL_INHERITED) != 0;
    }
}
