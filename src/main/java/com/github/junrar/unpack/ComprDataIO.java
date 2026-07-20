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
import com.github.junrar.crc.RarCRC;
import com.github.junrar.crypt.Rar5Crypt;
import com.github.junrar.crypt.Rijndael;
import com.github.junrar.crypt.blake2.Blake2sp;
import com.github.junrar.crypt.blake2.DataHash;
import com.github.junrar.exception.CorruptHeaderException;
import com.github.junrar.exception.CrcErrorException;
import com.github.junrar.exception.InitDeciphererFailedException;
import com.github.junrar.exception.MissingNextVolumeException;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.WrongPasswordException;
import com.github.junrar.io.RawDataIo;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.rarfile.rar5.Rar5HashType;
import com.github.junrar.volume.Volume;

import javax.crypto.Cipher;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
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
    private DataHash unpHash;
    /**
     * RAR5 per-volume packed-chunk digest for BLAKE2 split entries (unrar
     * {@code PackedDataHash}, {@code 8f437ab:rdwrfn.cpp} UnpRead / {@code volume.cpp:19-26});
     * {@code null} for CRC32 entries, which keep accumulating through {@link #packCrc32}.
     */
    private DataHash packHash;
    /** KDF hash key for encrypted files that store a HASHMAC checksum; {@code null} otherwise. */
    private byte[] unpHashMacKey;

    private int encryption;

    private int decryption;

    private RawDataIo underlyingDataIo;

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
        unpCrc32 = new CRC32();
        packCrc32 = new CRC32();
        unpHash = null;
    }

    public void init(FileHeader hd) throws IOException, RarException {
        long startPos = hd.getDataStartOffset(archive.isEncrypted());
        unpPackedSize = hd.getFullPackSize();
        archive.getChannel().setPosition(startPos);
        this.underlyingDataIo = new RawDataIo(archive.getChannel());
        subHead = hd;
        curUnpRead = 0;
        curPackWrite = 0;
        packedCRC = 0xFFffFFff;
        unpCrc32.reset();
        packCrc32.reset();
        unpHash = (hd.getHashType() == Rar5HashType.BLAKE2) ? new Blake2sp() : null;
        packHash = newPackHash(hd);
        unpHashMacKey = null;

        if (hd.getSalt16() != null) {
            initRar5Decipherer(hd);
        } else if (hd.isEncrypted()) {
            try {
                Cipher cipher = Rijndael.buildDecipherer(archive.getPassword(), hd.getSalt());
                this.underlyingDataIo.setCipher(cipher);
            } catch (Exception e) {
                throw new InitDeciphererFailedException(e);
            }
        }
    }

    /**
     * RAR5 per-file decryption setup (M3.4, issue #25; unrar {@code ComprDataIO} init +
     * {@code SetKey50}, {@code crypt5.cpp:131}). Derives the AES-256 key from the FHEXTRA_CRYPT
     * salt/count, verifies the password check value when present (a wrong or missing password
     * throws {@link WrongPasswordException}), and installs the CBC cipher on the io channel. The
     * decode that consumes the decrypted stream lands in M3.6/M3.7; {@code ConvertHashToMAC}
     * verification of a HASHMAC checksum ({@link FileHeader#isUseHashKey()}) runs there.
     */
    private void initRar5Decipherer(final FileHeader hd) throws RarException {
        final String password = archive.getPassword();
        if (password == null) {
            throw new WrongPasswordException("Missing password for encrypted RAR5 file " + hd.getFileName());
        }
        final char[] pw = password.toCharArray();
        final byte[] pwdUtf8 = Rar5Crypt.passwordToUtf8(pw);
        try {
            final Rar5Crypt.Kdf kdf = Rar5Crypt.deriveKey(pwdUtf8, hd.getSalt16(), hd.getLg2Count());
            if (hd.isUsePswCheck() && !Arrays.equals(kdf.pswCheck, hd.getPswCheck())) {
                throw new WrongPasswordException("RAR5 password check failed for " + hd.getFileName());
            }
            this.underlyingDataIo.setCipher(Rar5Crypt.buildDecipherer(kdf.aesKey, hd.getInitVector()));
            // Encrypted files store a keyed MAC, not the raw checksum (ConvertHashToMAC); keep the
            // hash key so the extract-time checksum can be transformed before comparison (M3.7).
            if (hd.isUseHashKey()) {
                this.unpHashMacKey = kdf.hashKey;
            }
        } catch (final CorruptHeaderException e) {
            throw e;
        } catch (final GeneralSecurityException e) {
            throw new InitDeciphererFailedException(e);
        } finally {
            Arrays.fill(pwdUtf8, (byte) 0);
            Arrays.fill(pw, '\0');
        }
    }

    /**
     * RAR5 volume switch (M3.9, issue #30; unrar {@code MergeArchive},
     * {@code 8f437ab:volume.cpp:10-185}): verify the finished part's packed-chunk checksum,
     * acquire the next volume through the {@link com.github.junrar.volume.VolumeManager} SPI
     * (never by constructing names here — manual §4.12), re-parse it, and continue the entry
     * from its {@code HFL_SPLITBEFORE} continuation header. Unlike the RAR3/RAR4 branch's
     * return-of-{@code -1}, every failure is a typed exception; unlike {@link #init(FileHeader)},
     * the unpacked-side accumulators survive — the final part stores the end-to-end unpacked
     * checksum ({@code extract.cpp:866}), so resetting them mid-file would break the M3.8
     * digest compare.
     */
    private void mergeRar5Volume() throws IOException, RarException {
        final FileHeader finished = this.subHead;
        checkRar5PackedHash(finished);

        final Volume nextVolume = archive.getVolumeManager().nextVolume(archive, archive.getVolume());
        UnrarCallback callback = archive.getUnrarCallback();
        if (nextVolume == null || (callback != null && !callback.isNextVolumeReady(nextVolume))) {
            nextVolumeMissing = true;
            throw new MissingNextVolumeException("Next volume of '" + finished.getFileName() + "' not supplied");
        }
        try {
            archive.setVolume(nextVolume);
        } catch (IOException e) {
            nextVolumeMissing = true;
            throw new MissingNextVolumeException(e);
        }
        final FileHeader continuation = archive.nextFileHeader();
        if (continuation == null || !continuation.isSplitBefore()) {
            throw new CorruptHeaderException("Next volume lacks the split continuation header of '"
                + finished.getFileName() + "'");
        }
        initNextVolume(continuation);
    }

    /**
     * unrar {@code MergeArchive} packed-hash check ({@code 8f437ab:volume.cpp:19-26}): every
     * RAR5 {@code HFL_SPLITAFTER} part header stores the checksum of its own <em>packed</em>
     * chunk, verified when that part is exhausted. junrar reads packed bytes through the
     * decrypting {@link RawDataIo} seam, while unrar hashes the raw pre-decryption stream —
     * the two are only comparable for unencrypted entries, so encrypted entries skip the
     * per-volume check; their end-to-end unpacked MAC digest still verifies the whole file.
     */
    private void checkRar5PackedHash(final FileHeader hd) throws RarException {
        if (hd.getSalt16() != null) {
            return;
        }
        if (hd.getHashType() == Rar5HashType.BLAKE2) {
            if (packHash == null || !Arrays.equals(packHash.digest(), hd.getHashDigest())) {
                throw new CrcErrorException();
            }
        } else if (this.getPackedCRC() != ~hd.getFileCRC()) {
            throw new CrcErrorException();
        }
    }

    /**
     * Re-arms the packed-side state on the continuation header (unrar {@code MergeArchive}
     * tail, {@code 8f437ab:volume.cpp:165-183}: {@code SetPackedSizeToRead} +
     * {@code PackedDataHash.Init} + per-volume {@code SetKey}); the unpacked-side
     * accumulators ({@link #unpHash}/{@link #unpCrc32}) deliberately keep running.
     */
    private void initNextVolume(final FileHeader hd) throws IOException, RarException {
        archive.getChannel().setPosition(hd.getDataStartOffset(archive.isEncrypted()));
        this.underlyingDataIo = new RawDataIo(archive.getChannel());
        subHead = hd;
        unpPackedSize = hd.getFullPackSize();
        curUnpRead = 0;
        packedCRC = 0xFFffFFff;
        packCrc32.reset();
        packHash = newPackHash(hd);
        if (hd.getSalt16() != null) {
            initRar5Decipherer(hd);
        }
    }

    private static DataHash newPackHash(final FileHeader hd) {
        final boolean packedBlake2 = hd.isSplitAfter()
            && hd.getHashType() == Rar5HashType.BLAKE2 && hd.getSalt16() == null;
        return packedBlake2 ? new Blake2sp() : null;
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
                if (packHash != null) {
                    packHash.update(addr, offset, retCode);
                } else {
                    packCrc32.update(addr, offset, retCode);
                    packedCRC = (int) (~packCrc32.getValue());
                }
            }

            totalRead += retCode;
            count -= retCode;
            offset += retCode;
            unpPackedSize -= retCode;
            curUnpRead += retCode;

            archive.bytesReadRead(retCode);

            if (unpPackedSize == 0 && subHead.isSplitAfter()) {
                if (subHead.getUnpVersion() == 50) {
                    mergeRar5Volume();
                    continue;
                }
                Volume nextVolume = archive.getVolumeManager().nextVolume(archive, archive.getVolume());
                if (nextVolume == null) {
                    nextVolumeMissing = true;
                    return -1;
                }

                FileHeader hd = this.getSubHeader();
                if (hd.getUnpVersion() >= 20 && hd.getFileCRC() != 0xffffffff
                        && this.getPackedCRC() != ~hd.getFileCRC()) {
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
            if (archive.isOldFormat()) {
                unpFileCRC = RarCRC.checkOldCrc((short) unpFileCRC, addr, count);
            } else if (unpHash != null) {
                unpHash.update(addr, offset, count);
            } else {
                unpCrc32.update(addr, offset, count);
                unpFileCRC = (int) (~unpCrc32.getValue());
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

    /**
     * @return the archive's extraction dictionary-size budget in bytes
     *         ({@code ArchiveOptions.maxDictionarySize}); conduit for the PPMd
     *         suballocator budget check (P0.8).
     */
    public long getMaxDictionarySize() {
        return archive.getMaxDictionarySize();
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
        if (unpHashMacKey != null) {
            // Mask the plain CRC32 into the stored HASHMAC form, preserving the (int)(~crc) storage
            // shape so the extractor's own ~ recovers the MAC the header holds.
            final int plainCrc = ~((int) unpFileCRC);
            return (int) ~Rar5Crypt.convertCrc32ToMac(plainCrc, unpHashMacKey);
        }
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
     * @return the RAR5 BLAKE2sp digest of the unpacked data accumulated through the
     *         {@link DataHash} seam — converted to MAC space ({@code ConvertHashToMAC}) when the
     *         entry is encrypted and stores a keyed MAC — or {@code null} when this entry uses
     *         the legacy CRC32 checksum instead (M3.5/M3.8). Compared against
     *         {@link FileHeader#getHashDigest()} at extract time; one-shot, as
     *         {@link DataHash#digest()} finalizes the accumulator.
     */
    public byte[] getUnpHashDigest() {
        if (unpHash == null) {
            return null;
        }
        final byte[] digest = unpHash.digest();
        return unpHashMacKey != null ? Rar5Crypt.convertBlake2ToMac(digest, unpHashMacKey) : digest;
    }
}
