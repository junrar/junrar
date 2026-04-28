package com.github.junrar.rar5.unpack;

import com.github.junrar.unpack.decode.Decode;
import com.github.junrar.unpack.vm.BitInput;

/**
 * Huffman tables for a single RAR5 compressed block.
 *
 * <p>Contains five separate Decode tables:
 * <ul>
 *   <li>LD - Literals and main codes (NC = 306 symbols)</li>
 *   <li>DD - Distance codes (DCB = 64 or DCX = 80 symbols)</li>
 *   <li>LDD - Lower distance bits (LDC = 16 symbols)</li>
 *   <li>RD - Repeat distances (RC = 44 symbols)</li>
 *   <li>BD - Bit-length decode (BC = 20 symbols)</li>
 * </ul>
 *
 * <p>Uses the shared Decode class from RAR4 infrastructure, enhanced with
 * quick-lookup tables for RAR5's 9-bit fast-path decoding.
 */
public final class UnpackBlockTables {

    /**
     * RAR5 alphabet sizes
     */
    private static final int NC = 306;
    private static final int DCB = 64;
    private static final int DCX = 80;
    private static final int LDC = 16;
    private static final int RC = 44;
    private static final int BC = 20;

    /**
     * RAR5 quick-lookup bits
     */
    private static final int QUICK_BITS = 9;

    /**
     * Huffman decode tables (shared Decode class)
     */
    private final Decode ld;
    private final Decode dd;
    private final Decode ldd;
    private final Decode rd;
    private final Decode bd;

    /**
     * Whether extended distance codes are used
     */
    private boolean useExtendedDist;

    /**
     * Creates tables for the base (non-extended) distance mode.
     */
    public UnpackBlockTables() {
        this(false);
    }

    /**
     * Creates tables for the given distance mode.
     *
     * @param extendedDist true for extended distance codes (RAR 7.0)
     */
    public UnpackBlockTables(final boolean extendedDist) {
        this.useExtendedDist = extendedDist;
        this.ld = createDecode(NC);
        this.dd = createDecode(extendedDist ? DCX : DCB);
        this.ldd = createDecode(LDC);
        this.rd = createDecode(RC);
        this.bd = createDecode(BC);

        // Enable quick-lookup for all tables
        ld.setQuickBits(QUICK_BITS);
        dd.setQuickBits(QUICK_BITS);
        ldd.setQuickBits(QUICK_BITS);
        rd.setQuickBits(QUICK_BITS);
        bd.setQuickBits(QUICK_BITS);
    }

    private Decode createDecode(final int size) {
        final Decode dec = new Decode();
        dec.setMaxNum(size);
        dec.setQuickBits(QUICK_BITS);
        dec.setDecodeNum(new int[size]);
        return dec;
    }

    /**
     * @return the literal/main table
     */
    public Decode getLd() {
        return ld;
    }

    /**
     * @return the distance table
     */
    public Decode getDd() {
        return dd;
    }

    /**
     * @return the lower distance bits table
     */
    public Decode getLdd() {
        return ldd;
    }

    /**
     * @return the repeat distance table
     */
    public Decode getRd() {
        return rd;
    }

    /**
     * @return the bit-length decode table
     */
    public Decode getBd() {
        return bd;
    }

    /**
     * @return true if extended distance codes are used
     */
    public boolean isUseExtendedDist() {
        return useExtendedDist;
    }

    /**
     * Reads and decodes the Huffman tables from the bit input.
     *
     * @param input     the bit input
     * @param extraDist whether to use extended distance codes
     * @return true if tables were read successfully
     */
    public boolean readTables(final BitInput input, final boolean extraDist) {
        this.useExtendedDist = extraDist;

        // Step 1: Read BC (bit-length code) table - 20 entries of 4 bits each
        final int[] bcLengths = new int[BC];
        for (int i = 0; i < BC; i++) {
            if (!input.hasBytes(1)) {
                return false;
            }
            final int length = input.getbits() >>> 12;
            input.addbits(4);

            if (length == 15) {
                if (!input.hasBytes(1)) {
                    return false;
                }
                final int zeroCount = input.getbits() >>> 12;
                input.addbits(4);
                if (zeroCount == 0) {
                    bcLengths[i] = 15;
                } else {
                    final int count = zeroCount + 2;
                    for (int j = 0; j < count && i < BC; j++) {
                        bcLengths[i++] = 0;
                    }
                    i--;
                }
            } else {
                bcLengths[i] = length;
            }
        }

        // Build BD table from BC bit-lengths
        makeDecodeTable(bcLengths, 0, BC, bd);

        // Step 2: Decode the combined main table using BD table
        final int dCodes = extraDist ? DCX : DCB;
        final int tableSize = NC + dCodes + LDC + RC;

        final int[] tableBits = new int[tableSize];
        int i = 0;
        while (i < tableSize) {
            if (input.Overflow(5)) {
                return false;
            }
            final int number = decodeNumber(input, bd);

            if (number < 16) {
                tableBits[i] = number;
                i++;
            } else if (number < 18) {
                // Repeat previous value
                if (!input.hasBytes(1)) {
                    return false;
                }
                final int count;
                if (number == 16) {
                    count = (input.getbits() >>> 13) + 3;
                    input.addbits(3);
                } else {
                    count = (input.getbits() >>> 9) + 11;
                    input.addbits(7);
                }
                final int prevVal = i > 0 ? tableBits[i - 1] : 0;
                for (int j = 0; j < count && i < tableSize; j++) {
                    tableBits[i++] = prevVal;
                }
            } else {
                // Zero run
                if (!input.hasBytes(1)) {
                    return false;
                }
                final int count;
                if (number == 18) {
                    count = (input.getbits() >>> 13) + 3;
                    input.addbits(3);
                } else {
                    count = (input.getbits() >>> 9) + 11;
                    input.addbits(7);
                }
                for (int j = 0; j < count && i < tableSize; j++) {
                    tableBits[i++] = 0;
                }
            }
        }

        // Step 3: Partition into sub-tables
        makeDecodeTable(tableBits, 0, NC, ld);

        int offset = NC;
        makeDecodeTable(tableBits, offset, dCodes, dd);

        offset += dCodes;
        makeDecodeTable(tableBits, offset, LDC, ldd);

        offset += LDC;
        makeDecodeTable(tableBits, offset, RC, rd);

        return true;
    }

    /**
     * Builds a canonical Huffman decode table from bit-lengths.
     * Based on the C++ Unpack::MakeDecodeTables implementation.
     * Builds quick-lookup tables for 9-bit fast-path decoding.
     *
     * @param bitLengths array of bit lengths for all symbols
     * @param offset     starting offset within bitLengths for this table
     * @param tableSize  the alphabet size for this table
     * @param dec        the Decode object to populate
     */
    private void makeDecodeTable(final int[] bitLengths, final int offset,
                                 final int tableSize, final Decode dec) {
        final int[] decodeLen = dec.getDecodeLen();
        final int[] decodePos = dec.getDecodePos();
        final int[] decodeNum = dec.getDecodeNum();
        final byte[] quickLen = dec.getQuickLen();
        final char[] quickNum = dec.getQuickNum();
        final int quickBits = dec.getQuickBits();

        // Clear tables
        for (int i = 0; i <= 15; i++) {
            decodeLen[i] = 0;
            decodePos[i] = 0;
        }
        for (int i = 0; i < quickLen.length; i++) {
            quickLen[i] = 0;
            quickNum[i] = 0;
        }

        // Count entries per bit length (excluding zero-length codes)
        final int[] lenCount = new int[16];
        for (int i = 0; i < tableSize; i++) {
            final int len = bitLengths[offset + i] & 0xF;
            if (len > 0) {
                lenCount[len]++;
            }
        }

        // Set DecodePos[0] = 0, DecodeLen[0] = 0
        decodePos[0] = 0;
        decodeLen[0] = 0;

        // Compute DecodeLen and DecodePos (matching C++ algorithm)
        int upperLimit = 0;
        for (int i = 1; i <= 15; i++) {
            upperLimit += lenCount[i];
            // Left-aligned upper limit code
            final int leftAligned = upperLimit << (16 - i);
            // Prepare upper limit for next bit length
            upperLimit *= 2;
            // Store left-aligned upper limit
            decodeLen[i] = leftAligned;
            // Cumulative start position for each bit length
            decodePos[i] = decodePos[i - 1] + lenCount[i - 1];
        }

        // Copy of DecodePos for code assignment
        final int[] copyDecodePos = new int[decodePos.length];
        System.arraycopy(decodePos, 0, copyDecodePos, 0, copyDecodePos.length);

        // Assign codes: for each symbol, store its position in the code list
        for (int i = 0; i < tableSize; i++) {
            final int len = bitLengths[offset + i] & 0xF;
            if (len > 0) {
                decodeNum[copyDecodePos[len]] = i;
                copyDecodePos[len]++;
            }
        }

        // Build quick-lookup tables (for codes up to QUICK_BITS bits)
        if (quickBits > 0) {
            for (int bits = 1; bits <= quickBits; bits++) {
                final int startCode = decodeLen[bits - 1];
                final int endCode = decodeLen[bits];
                final int entries = (endCode - startCode) >>> (16 - bits);

                for (int j = 0; j < entries; j++) {
                    final int codeVal = (startCode >>> (16 - bits)) + j;
                    final int posIndex = decodePos[bits] + j;
                    final int symbol = decodeNum[posIndex];
                    final int tableStart = codeVal << (quickBits - bits);
                    final int tableEnd = tableStart + (1 << (quickBits - bits));

                    for (int k = tableStart; k < tableEnd && k < quickLen.length; k++) {
                        quickLen[k] = (byte) bits;
                        quickNum[k] = (char) symbol;
                    }
                }
            }
        }
    }

    /**
     * Decodes a Huffman symbol using the given Decode table.
     * Matches the C++ DecodeNumber implementation exactly.
     */
    private int decodeNumber(final BitInput inp, final Decode dec) {
        // Left aligned 15 bit length raw bit field
        final int bitField = inp.getbits() & 0xfffe;

        if (dec.getQuickBits() > 0 && bitField < dec.getDecodeLen()[dec.getQuickBits()]) {
            final int code = bitField >>> (16 - dec.getQuickBits());
            inp.addbits(dec.getQuickLen()[code]);
            return dec.getQuickNum()[code];
        }

        // Detect the real bit length for current code
        int bits = 15;
        for (int i = dec.getQuickBits() + 1; i < 15; i++) {
            if (bitField < dec.getDecodeLen()[i]) {
                bits = i;
                break;
            }
        }

        inp.addbits(bits);

        // Calculate the distance from the start code for current bit length
        int dist = bitField - dec.getDecodeLen()[bits - 1];

        // Start codes are left aligned, but we need the normal right aligned number
        dist >>= (16 - bits);

        // Now we can calculate the position in the code list
        final int pos = dec.getDecodePos()[bits] + dist;
        if (pos < 0 || pos >= dec.getDecodeNum().length) {
            return 0;
        }
        return dec.getDecodeNum()[pos];
    }
}
