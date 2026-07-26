/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 21.06.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
 * the unrar licence applies to all junrar source and binary distributions
 * you are not allowed to use this source to re-create the RAR compression algorithm
 *
 * Here some html entities which can be used for escaping javadoc tags:
 * "&":  "&#038;" or "&amp;"
 * "<":  "&#060;" or "&lt;"
 * ">":  "&#062;" or "&gt;"
 * "@":  "&#064;"
 */
package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import com.github.junrar.unpack.decode.AudioVariables;
import com.github.junrar.unpack.decode.BitDecode;
import com.github.junrar.unpack.decode.Compress;
import com.github.junrar.unpack.decode.Decode;
import com.github.junrar.unpack.decode.DistDecode;
import com.github.junrar.unpack.decode.LitDecode;
import com.github.junrar.unpack.decode.LowDistDecode;
import com.github.junrar.unpack.decode.MultDecode;
import com.github.junrar.unpack.decode.RepDecode;
import java.io.IOException;
import java.util.Arrays;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public abstract class Unpack20 extends Unpack15 {

    protected MultDecode[] MD = new MultDecode[4];

    protected byte[] UnpOldTable20 = new byte[Compress.MC20 * 4];

    protected int UnpAudioBlock, UnpChannels, UnpCurChannel, UnpChannelDelta;

    protected AudioVariables[] AudV = new AudioVariables[4];

    protected LitDecode LD = new LitDecode();

    protected DistDecode DD = new DistDecode();

    protected LowDistDecode LDD = new LowDistDecode();

    protected RepDecode RD = new RepDecode();

    protected BitDecode BD = new BitDecode();

    public static final int[] LDecode = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128,
        160, 192, 224
    };

    public static final byte[] LBits = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5
    };

    public static final int[] DDecode = {
        0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536,
        2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576, 32768, 49152, 65536, 98304, 131072,
        196608, 262144, 327680, 393216, 458752, 524288, 589824, 655360, 720896, 786432, 851968,
        917504, 983040
    };

    public static final int[] DBits = {
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12,
        13, 13, 14, 14, 15, 15, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16
    };

    public static final int[] SDDecode = {0, 4, 8, 16, 32, 64, 128, 192};

    public static final int[] SDBits = {2, 2, 3, 4, 5, 6, 6, 6};

    protected void unpack20(boolean solid) throws IOException, RarException {

        int Bits;

        if (suspended) {
            unpPtr = wrPtr;
        } else {
            unpInitData(solid);
            if (!unpReadBuf()) {
                return;
            }
            if (!solid) {
                if (!ReadTables20()) {
                    return;
                }
            }
            --destUnpSize;
        }

        while (destUnpSize >= 0) {
            unpPtr &= Compress.MAXWINMASK;

            firstWinDone |= (prevPtr > unpPtr);
            prevPtr = unpPtr;

            if (inAddr > readTop - 30) {
                if (!unpReadBuf()) {
                    break;
                }
            }
            if (((wrPtr - unpPtr) & Compress.MAXWINMASK) < 270 && wrPtr != unpPtr) {
                oldUnpWriteBuf();
                if (suspended) {
                    return;
                }
            }
            if (UnpAudioBlock != 0) {
                int AudioNumber = decodeNumber(MD[UnpCurChannel]);

                if (AudioNumber == 256) {
                    if (!ReadTables20()) {
                        break;
                    }
                    continue;
                }
                window[unpPtr++] = DecodeAudio(AudioNumber);
                if (++UnpCurChannel == UnpChannels) {
                    UnpCurChannel = 0;
                }
                --destUnpSize;
                continue;
            }

            int Number = decodeNumber(LD);
            if (Number < 256) {
                window[unpPtr++] = (byte) Number;
                --destUnpSize;
                continue;
            }
            if (Number > 269) {
                int Length = LDecode[Number -= 270] + 3;
                if ((Bits = LBits[Number]) > 0) {
                    Length += getbits() >>> (16 - Bits);
                    addbits(Bits);
                }

                int DistNumber = decodeNumber(DD);
                int Distance = DDecode[DistNumber] + 1;
                if ((Bits = DBits[DistNumber]) > 0) {
                    Distance += getbits() >>> (16 - Bits);
                    addbits(Bits);
                }

                if (Distance >= 0x2000) {
                    Length++;
                    if (Distance >= 0x40000L) {
                        Length++;
                    }
                }

                CopyString20(Length, Distance);
                continue;
            }
            if (Number == 269) {
                if (!ReadTables20()) {
                    break;
                }
                continue;
            }
            if (Number == 256) {
                CopyString20(lastLength, lastDist);
                continue;
            }
            if (Number < 261) {
                int Distance = oldDist[(oldDistPtr - (Number - 256)) & 3];
                int LengthNumber = decodeNumber(RD);
                int Length = LDecode[LengthNumber] + 2;
                if ((Bits = LBits[LengthNumber]) > 0) {
                    Length += getbits() >>> (16 - Bits);
                    addbits(Bits);
                }
                if (Distance >= 0x101) {
                    Length++;
                    if (Distance >= 0x2000) {
                        Length++;
                        if (Distance >= 0x40000) {
                            Length++;
                        }
                    }
                }
                CopyString20(Length, Distance);
                continue;
            }
            if (Number < 270) {
                int Distance = SDDecode[Number -= 261] + 1;
                if ((Bits = SDBits[Number]) > 0) {
                    Distance += getbits() >>> (16 - Bits);
                    addbits(Bits);
                }
                CopyString20(2, Distance);
                continue;
            }
        }
        ReadLastTables();
        oldUnpWriteBuf();
    }

    protected void CopyString20(int length, final int distance) {
        lastDist = oldDist[oldDistPtr++ & 3] = distance;
        lastLength = length;
        destUnpSize -= length;

        int destPtr = unpPtr - distance;
        if (destPtr >= 0
                && destPtr < Compress.MAXWINSIZE - 300
                && unpPtr < Compress.MAXWINSIZE - 300) {
            if (destPtr + length <= unpPtr) {
                // Case: array elements to copy from destPtr do not overlap with unpPtr target
                // values
                System.arraycopy(window, destPtr, window, unpPtr, length);
                // update values for correct crc
                unpPtr += length;
            } else {
                // Case: fallback to old copy mechanism
                window[unpPtr++] = window[destPtr++];
                window[unpPtr++] = window[destPtr++];
                while (length > 2) {
                    length--;
                    window[unpPtr++] = window[destPtr++];
                }
            }
        } else if (distance > unpPtr && (!firstWinDone || distance > Compress.MAXWINSIZE)) {
            // Distance-into-void: never-written window region. Zero-fill deterministically
            // rather than wrap-copying stale bytes (unrar 7.0.3 CopyString FirstWinDone arm).
            while ((length--) != 0) {
                window[unpPtr] = 0;
                unpPtr = (unpPtr + 1) & Compress.MAXWINMASK;
            }
        } else {
            while ((length--) != 0) {
                window[unpPtr] = window[destPtr++ & Compress.MAXWINMASK];
                unpPtr = (unpPtr + 1) & Compress.MAXWINMASK;
            }
        }
    }

    // Faithful port of unrar Unpack::MakeDecodeTables (unpack.cpp): builds the Huffman decode
    // tables and the quick-decode accelerator shared by the RAR2.0/RAR2.9/RAR3.x decoders. The
    // RAR5 path has its own copy in Unpack5#makeDecodeTables (over Decode5).
    protected void makeDecodeTables(byte[] lenTab, int offset, Decode dec, int size) {
        dec.setMaxNum(size);

        int[] lenCount = new int[16];
        for (int i = 0; i < size; i++) {
            lenCount[lenTab[offset + i] & 0xF]++;
        }
        lenCount[0] = 0; // do not count zero-length codes

        int[] decodeNum = dec.getDecodeNum();
        Arrays.fill(decodeNum, 0, size, 0);

        int[] decodePos = dec.getDecodePos();
        int[] decodeLen = dec.getDecodeLen();
        decodePos[0] = 0;
        decodeLen[0] = 0;

        int upperLimit = 0;
        for (int i = 1; i < 16; i++) {
            upperLimit += lenCount[i];
            int leftAligned = upperLimit << (16 - i);
            upperLimit *= 2;
            decodeLen[i] = leftAligned;
            decodePos[i] = decodePos[i - 1] + lenCount[i - 1];
        }

        int[] copyDecodePos = Arrays.copyOf(decodePos, decodePos.length);
        for (int i = 0; i < size; i++) {
            int curBitLength = lenTab[offset + i] & 0xF;
            if (curBitLength != 0) {
                decodeNum[copyDecodePos[curBitLength]] = i;
                copyDecodePos[curBitLength]++;
            }
        }

        // Larger alphabets (literal/length: NC for RAR2.9/3.x, NC20 for RAR2.0) get the full
        // quick-decode width; everything else -3 (unrar MakeDecodeTables switch on Size).
        int quickBits =
                (size == Compress.NC || size == Compress.NC20)
                        ? Compress.MAX_QUICK_DECODE_BITS
                        : (Compress.MAX_QUICK_DECODE_BITS > 3
                                ? Compress.MAX_QUICK_DECODE_BITS - 3
                                : 0);
        dec.setQuickBits(quickBits);

        int quickDataSize = 1 << quickBits;
        int[] quickLen = dec.getQuickLen();
        int[] quickNum = dec.getQuickNum();
        int curBitLength = 1;
        for (int code = 0; code < quickDataSize; code++) {
            int bitField = code << (16 - quickBits);
            while (curBitLength < decodeLen.length && bitField >= decodeLen[curBitLength]) {
                curBitLength++;
            }
            quickLen[code] = curBitLength;

            int dist = bitField - decodeLen[curBitLength - 1];
            dist >>>= (16 - curBitLength);
            int pos;
            if (curBitLength < decodePos.length && (pos = decodePos[curBitLength] + dist) < size) {
                quickNum[code] = decodeNum[pos];
            } else {
                quickNum[code] = 0;
            }
        }
    }

    // Faithful port of unrar Unpack::DecodeNumber (unpackinline.cpp): a quick-decode fast path
    // for short codes backed by the QuickLen/QuickNum tables, falling back to a linear bit-length
    // scan. Shared by the RAR2.0/RAR2.9/RAR3.x decoders; the RAR5 path mirrors it in Unpack5.
    protected int decodeNumber(Decode dec) {
        int bitField = getbits() & 0xfffe; // left-aligned 15-bit raw field
        int quickBits = dec.getQuickBits();
        int[] decodeLen = dec.getDecodeLen();

        if (bitField < decodeLen[quickBits]) {
            int code = bitField >>> (16 - quickBits);
            addbits(dec.getQuickLen()[code]);
            return dec.getQuickNum()[code];
        }

        int bits = 15;
        for (int i = quickBits + 1; i < 15; i++) {
            if (bitField < decodeLen[i]) {
                bits = i;
                break;
            }
        }
        addbits(bits);

        int dist = bitField - decodeLen[bits - 1];
        dist >>>= (16 - bits);
        int pos = dec.getDecodePos()[bits] + dist;
        if (pos >= dec.getMaxNum()) {
            pos = 0; // out-of-bounds safety for damaged archives
        }
        return dec.getDecodeNum()[pos];
    }

    protected boolean ReadTables20() throws IOException, RarException {
        byte[] BitLength = new byte[Compress.BC20];
        byte[] Table = new byte[Compress.MC20 * 4];
        int TableSize, N, I;
        if (inAddr > readTop - 25) {
            if (!unpReadBuf()) {
                return (false);
            }
        }
        int BitField = getbits();
        UnpAudioBlock = (BitField & 0x8000);

        if (0 == (BitField & 0x4000)) {
            // memset(UnpOldTable20,0,sizeof(UnpOldTable20));
            Arrays.fill(UnpOldTable20, (byte) 0);
        }
        addbits(2);

        if (UnpAudioBlock != 0) {
            UnpChannels = ((BitField >>> 12) & 3) + 1;
            if (UnpCurChannel >= UnpChannels) {
                UnpCurChannel = 0;
            }
            addbits(2);
            TableSize = Compress.MC20 * UnpChannels;
        } else {
            TableSize = Compress.NC20 + Compress.DC20 + Compress.RC20;
        }
        for (I = 0; I < Compress.BC20; I++) {
            BitLength[I] = (byte) (getbits() >>> 12);
            addbits(4);
        }
        makeDecodeTables(BitLength, 0, BD, Compress.BC20);
        I = 0;
        while (I < TableSize) {
            if (inAddr > readTop - 5) {
                if (!unpReadBuf()) {
                    return (false);
                }
            }
            int Number = decodeNumber(BD);
            if (Number < 16) {
                Table[I] = (byte) ((Number + UnpOldTable20[I]) & 0xf);
                I++;
            } else if (Number == 16) {
                N = (getbits() >>> 14) + 3;
                addbits(2);
                while (N-- > 0 && I < TableSize) {
                    Table[I] = Table[I - 1];
                    I++;
                }
            } else {
                if (Number == 17) {
                    N = (getbits() >>> 13) + 3;
                    addbits(3);
                } else {
                    N = (getbits() >>> 9) + 11;
                    addbits(7);
                }
                while (N-- > 0 && I < TableSize) {
                    Table[I++] = 0;
                }
            }
        }
        if (inAddr > readTop) {
            return (true);
        }
        if (UnpAudioBlock != 0) {
            for (I = 0; I < UnpChannels; I++) {
                makeDecodeTables(Table, I * Compress.MC20, MD[I], Compress.MC20);
            }
        } else {
            makeDecodeTables(Table, 0, LD, Compress.NC20);
            makeDecodeTables(Table, Compress.NC20, DD, Compress.DC20);
            makeDecodeTables(Table, Compress.NC20 + Compress.DC20, RD, Compress.RC20);
        }
        // memcpy(UnpOldTable20,Table,sizeof(UnpOldTable20));
        System.arraycopy(Table, 0, UnpOldTable20, 0, UnpOldTable20.length);
        return (true);
    }

    protected void unpInitData20(boolean Solid) {
        if (!Solid) {
            UnpChannelDelta = UnpCurChannel = 0;
            UnpChannels = 1;
            // memset(AudV,0,sizeof(AudV));
            for (int i = 0; i < AudV.length; i++) {
                AudV[i] = new AudioVariables();
            }
            // memset(UnpOldTable20,0,sizeof(UnpOldTable20));
            Arrays.fill(UnpOldTable20, (byte) 0);
            // memset(MD,0,sizeof(MD)); reset in place rather than reallocating -- each MultDecode
            // now carries the quick-decode tables, so replacing all four per entry is pure churn.
            for (int i = 0; i < MD.length; i++) {
                if (MD[i] == null) {
                    MD[i] = new MultDecode();
                } else {
                    MD[i].reset();
                }
            }
        }
    }

    protected void ReadLastTables() throws IOException, RarException {
        if (readTop >= inAddr + 5) {
            if (UnpAudioBlock != 0) {
                if (decodeNumber(MD[UnpCurChannel]) == 256) {
                    ReadTables20();
                }
            } else {
                if (decodeNumber(LD) == 269) {
                    ReadTables20();
                }
            }
        }
    }

    protected byte DecodeAudio(int Delta) {
        AudioVariables v = AudV[UnpCurChannel];
        v.setByteCount(v.getByteCount() + 1);
        v.setD4(v.getD3());
        v.setD3(v.getD2()); // ->D3=V->D2;
        v.setD2(v.getLastDelta() - v.getD1()); // ->D2=V->LastDelta-V->D1;
        v.setD1(v.getLastDelta()); // V->D1=V->LastDelta;
        // int PCh=8*V->LastChar+V->K1*V->D1 +V->K2*V->D2 +V->K3*V->D3
        // +V->K4*V->D4+ V->K5*UnpChannelDelta;
        int PCh = 8 * v.getLastChar() + v.getK1() * v.getD1();
        PCh += v.getK2() * v.getD2() + v.getK3() * v.getD3();
        PCh += v.getK4() * v.getD4() + v.getK5() * UnpChannelDelta;
        PCh = (PCh >>> 3) & 0xFF;

        int Ch = PCh - Delta;

        int D = ((byte) Delta) << 3;

        v.getDif()[0] += Math.abs(D); // V->Dif[0]+=abs(D);
        v.getDif()[1] += Math.abs(D - v.getD1()); // V->Dif[1]+=abs(D-V->D1);
        v.getDif()[2] += Math.abs(D + v.getD1()); // V->Dif[2]+=abs(D+V->D1);
        v.getDif()[3] += Math.abs(D - v.getD2()); // V->Dif[3]+=abs(D-V->D2);
        v.getDif()[4] += Math.abs(D + v.getD2()); // V->Dif[4]+=abs(D+V->D2);
        v.getDif()[5] += Math.abs(D - v.getD3()); // V->Dif[5]+=abs(D-V->D3);
        v.getDif()[6] += Math.abs(D + v.getD3()); // V->Dif[6]+=abs(D+V->D3);
        v.getDif()[7] += Math.abs(D - v.getD4()); // V->Dif[7]+=abs(D-V->D4);
        v.getDif()[8] += Math.abs(D + v.getD4()); // V->Dif[8]+=abs(D+V->D4);
        v.getDif()[9] += Math.abs(D - UnpChannelDelta); // V->Dif[9]+=abs(D-UnpChannelDelta);
        v.getDif()[10] += Math.abs(D + UnpChannelDelta); // V->Dif[10]+=abs(D+UnpChannelDelta);

        v.setLastDelta((byte) (Ch - v.getLastChar()));
        UnpChannelDelta = v.getLastDelta();
        v.setLastChar(Ch); // V->LastChar=Ch;

        if ((v.getByteCount() & 0x1F) == 0) {
            int MinDif = v.getDif()[0], NumMinDif = 0;
            v.getDif()[0] = 0; // ->Dif[0]=0;
            for (int I = 1; I < v.getDif().length; I++) {
                if (v.getDif()[I] < MinDif) {
                    MinDif = v.getDif()[I];
                    NumMinDif = I;
                }
                v.getDif()[I] = 0;
            }
            switch (NumMinDif) {
                case 1:
                    if (v.getK1() >= -16) {
                        v.setK1(v.getK1() - 1); // V->K1--;
                    }
                    break;
                case 2:
                    if (v.getK1() < 16) {
                        v.setK1(v.getK1() + 1); // V->K1++;
                    }
                    break;
                case 3:
                    if (v.getK2() >= -16) {
                        v.setK2(v.getK2() - 1); // V->K2--;
                    }
                    break;
                case 4:
                    if (v.getK2() < 16) {
                        v.setK2(v.getK2() + 1); // V->K2++;
                    }
                    break;
                case 5:
                    if (v.getK3() >= -16) {
                        v.setK3(v.getK3() - 1);
                    }
                    break;
                case 6:
                    if (v.getK3() < 16) {
                        v.setK3(v.getK3() + 1);
                    }
                    break;
                case 7:
                    if (v.getK4() >= -16) {
                        v.setK4(v.getK4() - 1);
                    }
                    break;
                case 8:
                    if (v.getK4() < 16) {
                        v.setK4(v.getK4() + 1);
                    }
                    break;
                case 9:
                    if (v.getK5() >= -16) {
                        v.setK5(v.getK5() - 1);
                    }
                    break;
                case 10:
                    if (v.getK5() < 16) {
                        v.setK5(v.getK5() + 1);
                    }
                    break;
            }
        }
        return ((byte) Ch);
    }
}
