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
package com.github.junrar.unpack;

import com.github.junrar.Archive;
import com.github.junrar.UnrarCallback;
import com.github.junrar.crc.Blake2sp;
import com.github.junrar.crc.RarCRC;
import com.github.junrar.crypt.Rijndael;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.InitDeciphererFailedException;
import com.github.junrar.exception.RarException;
import com.github.junrar.io.RawDataIo;
import com.github.junrar.rar5.header.RAR5FileHeader;
import com.github.junrar.rar5.header.extra.Rar5FileCryptRecord;
import com.github.junrar.rarfile.ChecksumAlgorithm;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.volume.Volume;

import javax.crypto.Cipher;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class ComprDataIO {

    private final Archive archive;

    private long unpPackedSize;

    private boolean testMode;

    private boolean skipUnpCRC;

    private OutputStream outputStream;

    private FileHeader subHead;

    // cryptData Crypt;
    // cryptData Decrypt;
    private boolean packVolume;

    private boolean unpVolume;

    private boolean nextVolumeMissing;

    private long totalPackRead;

    private long unpArcSize;

    private long curPackRead, curPackWrite, curUnpRead, curUnpWrite;

    private long processedArcSize, totalArcSize;

    private long packFileCRC, unpFileCRC, packedCRC;
    private CRC32 unpCrc32;
    private CRC32 packCrc32;

    private Blake2sp blake2sp;

    private boolean skipFileCrc;

    private ChecksumAlgorithm activeAlgorithm = ChecksumAlgorithm.NONE;

    private int encryption;

    private int decryption;

    private RawDataIo underlyingDataIo;

    // RAR5 HashMAC: when non-null, the file checksum stored in the header is the
    // HMAC-SHA256 of the real hash keyed by this key, so the computed hash must
    // be converted the same way before comparison.
    private byte[] hashKey;

    public ComprDataIO(Archive arc) {
        this.archive = arc;
    }

    public void init(OutputStream outputStream) {
        this.outputStream = outputStream;
        unpPackedSize = 0;
        testMode = false;
        skipUnpCRC = false;
        packVolume = false;
        unpVolume = false;
        nextVolumeMissing = false;
        // command = null;
        encryption = 0;
        decryption = 0;
        totalPackRead = 0;
        curPackRead = curPackWrite = curUnpRead = curUnpWrite = 0;
        packFileCRC = unpFileCRC = packedCRC = 0xffffffff;
        subHead = null;
        processedArcSize = totalArcSize = 0;
        unpCrc32 = null; // created lazily in beginFileHashing when CRC32 is active
        packCrc32 = new CRC32();
        blake2sp = null;
        skipFileCrc = false;
        activeAlgorithm = ChecksumAlgorithm.NONE;
        hashKey = null;
    }

    public void init(FileHeader hd) throws IOException, RarException {
        long startPos = hd.getPositionInFile() + hd.getHeaderSize(archive.isEncrypted());
        unpPackedSize = hd.getFullPackSize();
        archive.getChannel().setPosition(startPos);
        this.underlyingDataIo = new RawDataIo(archive.getChannel());
        subHead = hd;
        curUnpRead = 0;
        curPackWrite = 0;
        packedCRC = 0xFFffFFff;
        if (unpCrc32 != null) {
            unpCrc32.reset();
        }
        packCrc32.reset();

        if (hd.isEncrypted()) {
            try {
                final Cipher cipher;
                if (hd instanceof RAR5FileHeader && ((RAR5FileHeader) hd).getCryptRecord() != null) {
                    final Rar5FileCryptRecord crypt = ((RAR5FileHeader) hd).getCryptRecord();
                    final String password = archive.getPassword();
                    final byte[] key = Rijndael.deriveRar5Key(password, crypt.getSalt(), crypt.getKdfCount());
                    cipher = Rijndael.buildDecipherer(key, crypt.getIv());
                    if (crypt.hasHashMac()) {
                        this.hashKey = Rijndael.deriveRar5HashKey(password, crypt.getSalt(), crypt.getKdfCount());
                    }
                } else {
                    cipher = Rijndael.buildDecipherer(archive.getPassword(), hd.getSalt());
                }
                this.underlyingDataIo.setCipher(cipher);
            } catch (Exception e) {
                throw new InitDeciphererFailedException(e);
            }
        }
    }

    public int unpRead(byte[] addr, int offset, int count) throws IOException, RarException {
        int retCode = 0, totalRead = 0;
        while (count > 0) {
            int readSize = (count > unpPackedSize) ? (int) unpPackedSize : count;
            retCode = underlyingDataIo.read(addr, offset, readSize);
            if (retCode < 0) {
                throw new EOFException();
            }

            if (subHead.isSplitAfter()) {
                packCrc32.update(addr, offset, retCode);
                packedCRC = packCrc32.getValue();
            }

            totalRead += retCode;
            count -= retCode;
            offset += retCode;
            unpPackedSize -= retCode;
            curUnpRead += retCode;

            archive.bytesReadRead(retCode);

            if (unpPackedSize == 0 && subHead.isSplitAfter()) {
                Volume nextVolume = archive.getVolumeManager().nextVolume(archive, archive.getVolume());
                if (nextVolume == null) {
                    nextVolumeMissing = true;
                    return -1;
                }

                FileHeader hd = this.getSubHeader();
                if (hd.getUnpVersion() >= 20 && !hd.getFileCRC().equals("FFFFFFFF")
                        && !hd.getFileCRC().equals(Long.toHexString(this.getPackedCRC()).toUpperCase(Locale.ROOT))) {
                    throw new CrcErrorException();
                }
                UnrarCallback callback = archive.getUnrarCallback();
                if ((callback != null) && !callback.isNextVolumeReady(nextVolume)) {
                    return -1;
                }
                archive.setVolume(nextVolume);
                hd = archive.nextFileHeader();
                if (hd == null) {
                    return -1;
                }
                this.init(hd);
            } else {
                break;
            }
        }

        if (retCode != -1) {
            retCode = totalRead;
        }
        return retCode;

    }

    public void unpWrite(byte[] addr, int offset, int count) throws IOException {
        if (!testMode) {
            // DestFile->Write(Addr,Count);
            outputStream.write(addr, offset, count);
        }

        curUnpWrite += count;

        if (!skipUnpCRC) {
            if (!skipFileCrc) {
                if (archive.isOldFormat()) {
                    unpFileCRC = RarCRC.checkOldCrc((short) unpFileCRC, addr, count);
                } else {
                    unpCrc32.update(addr, offset, count);
                    unpFileCRC = unpCrc32.getValue();
                }
            }
            if (activeAlgorithm == ChecksumAlgorithm.BLAKE2SP && blake2sp != null) {
                blake2sp.update(addr, offset, count);
            }
        }
        // if (!skipArcCRC) {
        // archive.updateDataCRC(Addr, offset, ReadSize);
        // }
    }

    public void setPackedSizeToRead(long size) {
        unpPackedSize = size;
    }

    public void setTestMode(boolean mode) {
        testMode = mode;
    }

    public void setSkipUnpCRC(boolean skip) {
        skipUnpCRC = skip;
    }

    public void setSubHeader(FileHeader hd) {
        subHead = hd;

    }

    public long getCurPackRead() {
        return curPackRead;
    }

    public void setCurPackRead(long curPackRead) {
        this.curPackRead = curPackRead;
    }

    public long getCurPackWrite() {
        return curPackWrite;
    }

    public void setCurPackWrite(long curPackWrite) {
        this.curPackWrite = curPackWrite;
    }

    public long getCurUnpRead() {
        return curUnpRead;
    }

    public void setCurUnpRead(long curUnpRead) {
        this.curUnpRead = curUnpRead;
    }

    public long getCurUnpWrite() {
        return curUnpWrite;
    }

    public void setCurUnpWrite(long curUnpWrite) {
        this.curUnpWrite = curUnpWrite;
    }

    public int getDecryption() {
        return decryption;
    }

    public void setDecryption(int decryption) {
        this.decryption = decryption;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
    }

    public boolean isNextVolumeMissing() {
        return nextVolumeMissing;
    }

    public void setNextVolumeMissing(boolean nextVolumeMissing) {
        this.nextVolumeMissing = nextVolumeMissing;
    }

    public long getPackedCRC() {
        return packedCRC;
    }

    public void setPackedCRC(long packedCRC) {
        this.packedCRC = packedCRC;
    }

    public long getPackFileCRC() {
        return packFileCRC;
    }

    public void setPackFileCRC(long packFileCRC) {
        this.packFileCRC = packFileCRC;
    }

    public boolean isPackVolume() {
        return packVolume;
    }

    public void setPackVolume(boolean packVolume) {
        this.packVolume = packVolume;
    }

    public long getProcessedArcSize() {
        return processedArcSize;
    }

    public void setProcessedArcSize(long processedArcSize) {
        this.processedArcSize = processedArcSize;
    }

    public long getTotalArcSize() {
        return totalArcSize;
    }

    public void setTotalArcSize(long totalArcSize) {
        this.totalArcSize = totalArcSize;
    }

    public long getTotalPackRead() {
        return totalPackRead;
    }

    public void setTotalPackRead(long totalPackRead) {
        this.totalPackRead = totalPackRead;
    }

    public long getUnpArcSize() {
        return unpArcSize;
    }

    public void setUnpArcSize(long unpArcSize) {
        this.unpArcSize = unpArcSize;
    }

    public long getUnpFileCRC() {
        return unpFileCRC;
    }

    public void setUnpFileCRC(long unpFileCRC) {
        this.unpFileCRC = unpFileCRC;
    }

    public boolean isUnpVolume() {
        return unpVolume;
    }

    public void setUnpVolume(boolean unpVolume) {
        this.unpVolume = unpVolume;
    }

    public FileHeader getSubHeader() {
        return subHead;
    }

    /**
     * Configures hashing for the next file: skips CRC32 when the RAR5 header
     * doesn't carry one, allocates a BLAKE2sp hasher when the extras contain a
     * BLAKE2 digest, and seeds the CRC32 starting value when CRC is actually
     * going to be computed. Called once per file, before
     * {@link com.github.junrar.unpack.Unpack#doUnpack}.
     */
    public void beginFileHashing(FileHeader hd) {
        // BLAKE2sp digests are 32 bytes = 64 hex chars, CRC32 is at most 8 hex chars.
        boolean isBlake2 = hd.getFileCRC().length() == 64;
        if (isBlake2) {
            skipFileCrc = true;
            blake2sp = new Blake2sp();
            activeAlgorithm = ChecksumAlgorithm.BLAKE2SP;
            unpCrc32 = null; // not needed for BLAKE2
        } else {
            skipFileCrc = false;
            blake2sp = null;
            activeAlgorithm = ChecksumAlgorithm.CRC32;
            ensureUnpCrc32();
            // Old format starts CRC16 at 0; new format CRC32 at 0xFFFFFFFF.
            unpFileCRC = archive.isOldFormat() ? 0 : 0xFFFFFFFFL;
        }
    }

    /**
     * Ensures the unpacked CRC32 hasher is initialised. Safe to call multiple
     * times — only creates a new instance on first invocation.
     */
    private void ensureUnpCrc32() {
        if (unpCrc32 == null) {
            unpCrc32 = new CRC32();
        } else {
            unpCrc32.reset();
        }
    }

    /**
     * Returns the computed checksum of the extracted data as a hex string,
     * using the algorithm configured by the most recent call to
     * {@link #beginFileHashing(FileHeader)}.
     * <p>
     * The returned format matches the format of
     * {@link com.github.junrar.rarfile.FileHeader#getFileCRC()} for the
     * same algorithm, allowing direct string comparison.
     *
     * @return hex string of the computed checksum
     */
    public String getComputedChecksum() {
        if (activeAlgorithm == ChecksumAlgorithm.BLAKE2SP && blake2sp != null) {
            byte[] digest = blake2sp.digest();
            if (hashKey != null) {
                digest = Rijndael.convertRar5Blake2ToMac(hashKey, digest);
            }
            return bytesToHex(digest);
        }
        // CRC32: for split-after volumes, use the packed (compressed) CRC;
        // otherwise use the unpacked CRC.
        long crc = (subHead != null && subHead.isSplitAfter()) ? packedCRC : unpFileCRC;
        if (hashKey != null) {
            crc = Rijndael.convertRar5Crc32ToMac(hashKey, crc);
        }
        return Long.toHexString(crc).toUpperCase(Locale.ROOT);
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
