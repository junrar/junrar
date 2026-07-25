/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 31.05.2007
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
package com.github.junrar.unpack.vm;

import com.github.junrar.io.Raw;
import java.util.Vector;
import java.util.zip.CRC32;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class RarVM {

    public static final int VM_MEMSIZE = 0x40000;

    public static final int VM_MEMMASK = (VM_MEMSIZE - 1);

    public static final int VM_GLOBALMEMADDR = 0x3C000;

    public static final int VM_GLOBALMEMSIZE = 0x2000;

    public static final int VM_FIXEDGLOBALSIZE = 64;

    private static final int MAX3_UNPACK_CHANNELS = 1024;

    private static final int regCount = 8;

    private static final long UINT_MASK = 0xffffFFFF; // ((long)2*(long)Integer.MAX_VALUE);

    private byte[] mem;

    private final int[] R = new int[regCount];

    public RarVM() {
        mem = null;
    }

    public void init() {
        if (mem == null) {
            mem = new byte[VM_MEMSIZE + 4];
        }
    }

    private boolean isVMMem(byte[] mem) {
        return this.mem == mem;
    }

    private int getValue(boolean byteMode, byte[] mem, int offset) {
        if (byteMode) {
            if (isVMMem(mem)) {
                return (mem[offset]);
            } else {
                return (mem[offset] & 0xff);
            }
        } else {
            if (isVMMem(mem)) {
                // little
                return Raw.readIntLittleEndian(mem, offset);
            } else {
                // big endian
                return Raw.readIntBigEndian(mem, offset);
            }
        }
    }

    private void setValue(boolean byteMode, byte[] mem, int offset, int value) {
        if (byteMode) {
            if (isVMMem(mem)) {
                mem[offset] = (byte) value;
            } else {
                mem[offset] = (byte) ((mem[offset] & 0x00) | (byte) (value & 0xff));
            }
        } else {
            if (isVMMem(mem)) {
                Raw.writeIntLittleEndian(mem, offset, value);
                //                mem[offset + 0] = (byte) value;
                //                mem[offset + 1] = (byte) (value >>> 8);
                //                mem[offset + 2] = (byte) (value >>> 16);
                //                mem[offset + 3] = (byte) (value >>> 24);
            } else {
                Raw.writeIntBigEndian(mem, offset, value);
                //                mem[offset + 3] = (byte) value;
                //                mem[offset + 2] = (byte) (value >>> 8);
                //                mem[offset + 1] = (byte) (value >>> 16);
                //                mem[offset + 0] = (byte) (value >>> 24);
            }
        }
        // #define SET_VALUE(ByteMode,Addr,Value) SetValue(ByteMode,(uint
        // *)Addr,Value)
    }

    public void setLowEndianValue(byte[] mem, int offset, int value) {
        Raw.writeIntLittleEndian(mem, offset, value);
        //        mem[offset + 0] = (byte) (value&0xff);
        //        mem[offset + 1] = (byte) ((value >>> 8)&0xff);
        //        mem[offset + 2] = (byte) ((value >>> 16)&0xff);
        //        mem[offset + 3] = (byte) ((value >>> 24)&0xff);
    }

    public void setLowEndianValue(Vector<Byte> mem, int offset, int value) {
        mem.set(offset + 0, (byte) (value & 0xff));
        mem.set(offset + 1, (byte) ((value >>> 8) & 0xff));
        mem.set(offset + 2, (byte) ((value >>> 16) & 0xff));
        mem.set(offset + 3, (byte) ((value >>> 24) & 0xff));
    }

    public void execute(VMPreparedProgram prg) {
        for (int i = 0;
                i < prg.getInitR().length;
                i++) { // memcpy(R,Prg->InitR,sizeof(Prg->InitR));
            R[i] = prg.getInitR()[i];
        }

        // unrar 5.5.1 recognition-only model (M2.1): no generic VM interpreter.
        // Only the 6 canonical native standard filters run; anything else
        // (Type==VMSF_NONE) is a no-op filter (FilteredDataSize=0).
        if (prg.getType() != VMStandardFilters.VMSF_NONE) {
            ExecuteStandardFilter(prg.getType());
            int blockSize = prg.getInitR()[4];
            prg.setFilteredDataSize(blockSize);
            if (prg.getType() == VMStandardFilters.VMSF_DELTA
                    || prg.getType() == VMStandardFilters.VMSF_RGB
                    || prg.getType() == VMStandardFilters.VMSF_AUDIO) {
                prg.setFilteredDataOffset(2L * blockSize >= VM_MEMSIZE ? 0 : blockSize);
            } else {
                prg.setFilteredDataOffset(0);
            }
        } else {
            prg.setFilteredDataSize(0);
            prg.setFilteredDataOffset(0);
        }
    }

    public byte[] getMem() {
        return mem;
    }

    public void prepare(byte[] code, int codeSize, VMPreparedProgram prg) {
        // unrar 5.5.1 recognition-only model (M2.1): VM bytecode is no longer
        // interpreted, only fingerprinted against the 6 canonical standard
        // filters. Anything else (incl. the removed UPCASE filter and any
        // custom/non-standard program) resolves to VMSF_NONE, i.e. a no-op.
        byte xorSum = 0;
        for (int i = 1; i < codeSize; i++) {
            xorSum ^= code[i];
        }

        if (xorSum != code[0]) {
            return;
        }

        VMStandardFilters filterType = IsStandardFilter(code, codeSize);
        if (filterType != VMStandardFilters.VMSF_NONE) {
            prg.setType(filterType);
        }
    }

    public static int ReadData(BitInput rarVM) {
        int data = rarVM.fgetbits();
        switch (data & 0xc000) {
            case 0:
                rarVM.faddbits(6);
                return ((data >>> 10) & 0xf);
            case 0x4000:
                if ((data & 0x3c00) == 0) {
                    data = 0xffffff00 | ((data >>> 2) & 0xff);
                    rarVM.faddbits(14);
                } else {
                    data = (data >>> 6) & 0xff;
                    rarVM.faddbits(10);
                }
                return (data);
            case 0x8000:
                rarVM.faddbits(2);
                data = rarVM.fgetbits();
                rarVM.faddbits(16);
                return (data);
            default:
                rarVM.faddbits(2);
                data = (rarVM.fgetbits() << 16);
                rarVM.faddbits(16);
                data |= rarVM.fgetbits();
                rarVM.faddbits(16);
                return (data);
        }
    }

    private VMStandardFilters IsStandardFilter(byte[] code, int codeSize) {
        VMStandardFilterSignature[] stdList = {
            new VMStandardFilterSignature(53, 0xad576887, VMStandardFilters.VMSF_E8),
            new VMStandardFilterSignature(57, 0x3cd7e57e, VMStandardFilters.VMSF_E8E9),
            new VMStandardFilterSignature(120, 0x3769893f, VMStandardFilters.VMSF_ITANIUM),
            new VMStandardFilterSignature(29, 0x0e06077d, VMStandardFilters.VMSF_DELTA),
            new VMStandardFilterSignature(149, 0x1c2c5dc8, VMStandardFilters.VMSF_RGB),
            new VMStandardFilterSignature(216, 0xbc85e701, VMStandardFilters.VMSF_AUDIO)
        };
        CRC32 crc32 = new CRC32();
        crc32.update(code, 0, codeSize);
        int CodeCRC = (int) crc32.getValue();
        for (int i = 0; i < stdList.length; i++) {
            if (stdList[i].getCRC() == CodeCRC && stdList[i].getLength() == codeSize) {
                return (stdList[i].getType());
            }
        }
        return (VMStandardFilters.VMSF_NONE);
    }

    @SuppressWarnings("incomplete-switch")
    private void ExecuteStandardFilter(VMStandardFilters filterType) {
        switch (filterType) {
            case VMSF_E8:
            case VMSF_E8E9:
                {
                    int dataSize = R[4];
                    long fileOffset = R[6] & 0xFFffFFff;

                    if (dataSize >= VM_GLOBALMEMADDR) {
                        break;
                    }
                    int fileSize = 0x1000000;
                    byte cmpByte2 =
                            (byte) ((filterType == VMStandardFilters.VMSF_E8E9) ? 0xe9 : 0xe8);
                    for (int curPos = 0; curPos < dataSize - 4; ) {
                        byte curByte = mem[curPos++];
                        if (curByte == ((byte) 0xe8) || curByte == cmpByte2) {
                            //        #ifdef PRESENT_INT32
                            //                    sint32 Offset=CurPos+FileOffset;
                            //                    sint32 Addr=GET_VALUE(false,Data);
                            //                    if (Addr<0)
                            //                    {
                            //                      if (Addr+Offset>=0)
                            //                        SET_VALUE(false,Data,Addr+FileSize);
                            //                    }
                            //                    else
                            //                      if (Addr<FileSize)
                            //                        SET_VALUE(false,Data,Addr-Offset);
                            //        #else
                            long offset = curPos + fileOffset;
                            long Addr = getValue(false, mem, curPos);
                            if ((Addr & 0x80000000) != 0) {
                                if (((Addr + offset) & 0x80000000) == 0) {
                                    setValue(false, mem, curPos, (int) Addr + fileSize);
                                }
                            } else {
                                if (((Addr - fileSize) & 0x80000000) != 0) {
                                    setValue(false, mem, curPos, (int) (Addr - offset));
                                }
                            }
                            //        #endif
                            curPos += 4;
                        }
                    }
                    break;
                }
            case VMSF_ITANIUM:
                {
                    int dataSize = R[4];
                    long fileOffset = R[6] & 0xFFffFFff;

                    if (dataSize >= VM_GLOBALMEMADDR) {
                        break;
                    }
                    int curPos = 0;
                    final byte[] Masks = {4, 4, 6, 6, 0, 0, 7, 7, 4, 4, 0, 0, 4, 4, 0, 0};
                    fileOffset >>>= 4;

                    while (curPos < dataSize - 21) {
                        int Byte = (mem[curPos] & 0x1f) - 0x10;
                        if (Byte >= 0) {

                            byte cmdMask = Masks[Byte];
                            if (cmdMask != 0) {
                                for (int i = 0; i <= 2; i++) {
                                    if ((cmdMask & (1 << i)) != 0) {
                                        int startPos = i * 41 + 5;
                                        int opType =
                                                filterItanium_GetBits(curPos, startPos + 37, 4);
                                        if (opType == 5) {
                                            int offset =
                                                    filterItanium_GetBits(
                                                            curPos, startPos + 13, 20);
                                            filterItanium_SetBits(
                                                    curPos,
                                                    (int) (offset - fileOffset) & 0xfffff,
                                                    startPos + 13,
                                                    20);
                                        }
                                    }
                                }
                            }
                        }
                        curPos += 16;
                        fileOffset++;
                    }
                }
                break;
            case VMSF_DELTA:
                {
                    int dataSize = R[4] & 0xFFffFFff;
                    int channels = R[0] & 0xFFffFFff;
                    int srcPos = 0;
                    int border = (dataSize * 2) & 0xFFffFFff;
                    setValue(false, mem, VM_GLOBALMEMADDR + 0x20, (int) dataSize);
                    if (Integer.compareUnsigned(dataSize, VM_GLOBALMEMADDR / 2) >= 0
                            || Integer.compareUnsigned(channels, MAX3_UNPACK_CHANNELS) > 0) {
                        break;
                    }
                    //         bytes from same channels are grouped to continual data blocks,
                    //         so we need to place them back to their interleaving positions

                    for (int curChannel = 0; curChannel < channels; curChannel++) {
                        byte PrevByte = 0;
                        for (int destPos = dataSize + curChannel;
                                destPos < border;
                                destPos += channels) {
                            mem[destPos] = (PrevByte -= mem[srcPos++]);
                        }
                    }
                }
                break;
            case VMSF_RGB:
                {
                    // byte *SrcData=Mem,*DestData=SrcData+DataSize;
                    int dataSize = R[4], width = R[0] - 3, posR = R[1];
                    int channels = 3;
                    int srcPos = 0;
                    int destDataPos = dataSize;
                    setValue(false, mem, VM_GLOBALMEMADDR + 0x20, dataSize);
                    if (dataSize >= VM_GLOBALMEMADDR / 2 || posR < 0) {
                        break;
                    }
                    for (int curChannel = 0; curChannel < channels; curChannel++) {
                        long prevByte = 0;

                        for (int i = curChannel; i < dataSize; i += channels) {
                            long predicted;
                            int upperPos = i - width;
                            if (upperPos >= 3) {
                                int upperDataPos = destDataPos + upperPos;
                                int upperByte = mem[(int) upperDataPos] & 0xff;
                                int upperLeftByte = mem[upperDataPos - 3] & 0xff;
                                predicted = prevByte + upperByte - upperLeftByte;
                                int pa = Math.abs((int) (predicted - prevByte));
                                int pb = Math.abs((int) (predicted - upperByte));
                                int pc = Math.abs((int) (predicted - upperLeftByte));
                                if (pa <= pb && pa <= pc) {
                                    predicted = prevByte;
                                } else {
                                    if (pb <= pc) {
                                        predicted = upperByte;
                                    } else {
                                        predicted = upperLeftByte;
                                    }
                                }
                            } else {
                                predicted = prevByte;
                            }

                            prevByte = (predicted - mem[srcPos++] & 0xff) & 0xff;
                            mem[destDataPos + i] = (byte) (prevByte & 0xff);
                        }
                    }
                    for (int i = posR, border = dataSize - 2; i < border; i += 3) {
                        byte G = mem[destDataPos + i + 1];
                        mem[destDataPos + i] += G;
                        mem[destDataPos + i + 2] += G;
                    }
                }
                break;
            case VMSF_AUDIO:
                {
                    int dataSize = R[4], channels = R[0];
                    int srcPos = 0;
                    int destDataPos = dataSize;
                    // byte *SrcData=Mem,*DestData=SrcData+DataSize;
                    setValue(false, mem, VM_GLOBALMEMADDR + 0x20, dataSize);
                    if (dataSize >= VM_GLOBALMEMADDR / 2) {
                        break;
                    }
                    for (int curChannel = 0; curChannel < channels; curChannel++) {
                        long prevByte = 0;
                        long prevDelta = 0;
                        long[] Dif = new long[7];
                        int D1 = 0, D2 = 0, D3;
                        int K1 = 0, K2 = 0, K3 = 0;

                        for (int i = curChannel, byteCount = 0;
                                i < dataSize;
                                i += channels, byteCount++) {
                            D3 = D2;
                            D2 = (int) prevDelta - D1;
                            D1 = (int) prevDelta;

                            long predicted = 8 * prevByte + K1 * D1 + K2 * D2 + K3 * D3;
                            predicted = (predicted >>> 3) & 0xff;

                            long curByte = mem[srcPos++] & 0xff;

                            predicted = (predicted - curByte) & UINT_MASK;
                            mem[destDataPos + i] = (byte) predicted;
                            prevDelta = (byte) (predicted - prevByte);
                            prevByte = predicted;

                            int D = ((byte) curByte) << 3;

                            Dif[0] += Math.abs(D);
                            Dif[1] += Math.abs(D - D1);
                            Dif[2] += Math.abs(D + D1);
                            Dif[3] += Math.abs(D - D2);
                            Dif[4] += Math.abs(D + D2);
                            Dif[5] += Math.abs(D - D3);
                            Dif[6] += Math.abs(D + D3);

                            if ((byteCount & 0x1f) == 0) {
                                long minDif = Dif[0], numMinDif = 0;
                                Dif[0] = 0;
                                for (int j = 1; j < Dif.length; j++) {
                                    if (Dif[j] < minDif) {
                                        minDif = Dif[j];
                                        numMinDif = j;
                                    }
                                    Dif[j] = 0;
                                }
                                switch ((int) numMinDif) {
                                    case 1:
                                        if (K1 >= -16) {
                                            K1--;
                                        }
                                        break;
                                    case 2:
                                        if (K1 < 16) {
                                            K1++;
                                        }
                                        break;
                                    case 3:
                                        if (K2 >= -16) {
                                            K2--;
                                        }
                                        break;
                                    case 4:
                                        if (K2 < 16) {
                                            K2++;
                                        }
                                        break;
                                    case 5:
                                        if (K3 >= -16) {
                                            K3--;
                                        }
                                        break;
                                    case 6:
                                        if (K3 < 16) {
                                            K3++;
                                        }
                                        break;
                                }
                            }
                        }
                    }
                }
                break;
            case VMSF_UPCASE:
                {
                    int dataSize = R[4], srcPos = 0, destPos = dataSize;
                    if (dataSize >= VM_GLOBALMEMADDR / 2) {
                        break;
                    }
                    while (srcPos < dataSize) {
                        byte curByte = mem[srcPos++];
                        if (curByte == 2 && (curByte = mem[srcPos++]) != 2) {
                            curByte -= 32;
                        }
                        mem[destPos++] = curByte;
                    }
                    setValue(false, mem, VM_GLOBALMEMADDR + 0x1c, destPos - dataSize);
                    setValue(false, mem, VM_GLOBALMEMADDR + 0x20, dataSize);
                }
                break;
        }
    }

    private void filterItanium_SetBits(int curPos, int bitField, int bitPos, int bitCount) {
        int inAddr = bitPos / 8;
        int inBit = bitPos & 7;
        int andMask = 0xffffffff >>> (32 - bitCount);
        andMask = ~(andMask << inBit);

        bitField <<= inBit;

        for (int i = 0; i < 4; i++) {
            mem[curPos + inAddr + i] &= andMask;
            mem[curPos + inAddr + i] |= bitField;
            andMask = (andMask >>> 8) | 0xff000000;
            bitField >>>= 8;
        }
    }

    private int filterItanium_GetBits(int curPos, int bitPos, int bitCount) {
        int inAddr = bitPos / 8;
        int inBit = bitPos & 7;
        int bitField = (int) (mem[curPos + inAddr++] & 0xff);
        bitField |= (int) ((mem[curPos + inAddr++] & 0xff) << 8);
        bitField |= (int) ((mem[curPos + inAddr++] & 0xff) << 16);
        bitField |= (int) ((mem[curPos + inAddr] & 0xff) << 24);
        bitField >>>= inBit;
        return (bitField & (0xffffffff >>> (32 - bitCount)));
    }

    public void setMemory(int pos, byte[] data, int offset, int dataSize) {
        if (pos < VM_MEMSIZE) { // && data!=Mem+Pos)
            // memmove(Mem+Pos,Data,Min(DataSize,VM_MEMSIZE-Pos));
            for (int i = 0; i < Math.min(data.length - offset, dataSize); i++) {
                if ((VM_MEMSIZE - pos) < i) {
                    break;
                }
                mem[pos + i] = data[offset + i];
            }
        }
    }
}
