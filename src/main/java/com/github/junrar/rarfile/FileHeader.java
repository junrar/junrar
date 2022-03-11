/*
 * Copyright (c) 2007 innoSysTec (R) GmbH, Germany. All rights reserved.
 * Original author: Edmund Wagner
 * Creation date: 22.05.2007
 *
 * Source: $HeadURL$
 * Last changed: $LastChangedDate$
 *
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
package com.github.junrar.rarfile;

import com.github.junrar.io.Raw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;


/**
 * DOCUMENT ME
 *
 * @author $LastChangedBy$
 * @version $LastChangedRevision$
 */
public class FileHeader extends BlockHeader {

    private static final Logger logger = LoggerFactory.getLogger(FileHeader.class);

    private static final byte SALT_SIZE = 8;

    private static final byte NEWLHD_SIZE = 32;

    private final long unpSize;

    private final HostSystem hostOS;

    private final int fileCRC;

    private byte unpVersion;

    private byte unpMethod;

    private short nameSize;

    private final int highPackSize;

    private int highUnpackSize;

    private final byte[] fileNameBytes;

    private String fileName;
    private String fileNameW;

    private byte[] subData;

    private final byte[] salt = new byte[SALT_SIZE];

    private Date mTime;

    private Date cTime;

    private Date aTime;

    private Date arcTime;

    private long fullPackSize;

    private long fullUnpackSize;

    private int fileAttr;

    private int subFlags; // same as fileAttr (in header)

    private int recoverySectors = -1;

    public FileHeader(BlockHeader bh, byte[] fileHeader) {
        super(bh);

        int position = 0;
        unpSize = Raw.readIntLittleEndianAsLong(fileHeader, position);
        position += 4;
        hostOS = HostSystem.findHostSystem(fileHeader[4]);
        position++;

        fileCRC = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;

        int fileTime = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;

        unpVersion |= fileHeader[13] & 0xff;
        position++;
        unpMethod |= fileHeader[14] & 0xff;
        position++;
        nameSize = Raw.readShortLittleEndian(fileHeader, position);
        position += 2;

        fileAttr = Raw.readIntLittleEndian(fileHeader, position);
        position += 4;
        if (isLargeBlock()) {
            highPackSize = Raw.readIntLittleEndian(fileHeader, position);
            position += 4;

            highUnpackSize = Raw.readIntLittleEndian(fileHeader, position);
            position += 4;
        } else {
            highPackSize = 0;
            highUnpackSize = 0;
            if (unpSize == 0xffffffff) {
                highUnpackSize = Integer.MAX_VALUE;
            }

        }
        fullPackSize |= highPackSize;
        fullPackSize <<= 32;
        fullPackSize |= getPackSize();

        fullUnpackSize |= highUnpackSize;
        fullUnpackSize <<= 32;
        fullUnpackSize += unpSize;

        nameSize = nameSize > 4 * 1024 ? 4 * 1024 : nameSize;

        fileNameBytes = new byte[nameSize];
        System.arraycopy(fileHeader, position, fileNameBytes, 0, nameSize);
        position += nameSize;

        if (isFileHeader()) {
            if (isUnicode()) {
                int length = 0;
                while (length < fileNameBytes.length
                        && fileNameBytes[length] != 0) {
                    length++;
                }
                fileName = new String(fileNameBytes, 0, length);
                if (length != nameSize) {
                    length++;
                    fileNameW = FileNameDecoder.decode(fileNameBytes, length);
                } else {
                    fileNameW = "";
                }
            } else {
                fileName = new String(fileNameBytes);
                fileNameW = "";
            }
        }

        if (UnrarHeadertype.NewSubHeader.equals(headerType)) {
            int datasize = headerSize - NEWLHD_SIZE - nameSize;
            if (hasSalt()) {
                datasize -= SALT_SIZE;
            }
            if (datasize > 0) {
                subData = new byte[datasize];
                for (int i = 0; i < datasize; i++) {
                    subData[i] = (fileHeader[position]);
                    position++;
                }
            }

            if (NewSubHeaderType.SUBHEAD_TYPE_RR.byteEquals(fileNameBytes)) {
                recoverySectors = (subData[8] & 0xff) + ((subData[9] & 0xff) << 8)
                        + ((subData[10] & 0xff) << 16) + ((subData[11] & 0xff) << 24);
            }
        }

        if (hasSalt()) {
            for (int i = 0; i < SALT_SIZE; i++) {
                salt[i] = fileHeader[position];
                position++;
            }
        }
        mTime = getDateDos(fileTime);
        // TODO rartime -> extended

    }

    @Override
    public void print() {
        super.print();
        if (logger.isInfoEnabled()) {
            StringBuilder str = new StringBuilder();
            str.append("unpSize: ").append(getUnpSize());
            str.append("\nHostOS: ").append(hostOS.name());
            str.append("\nMDate: ").append(mTime);
            str.append("\nFileName: ").append(fileName);
            str.append("\nFileNameW: ").append(fileNameW);
            str.append("\nunpMethod: ").append(Integer.toHexString(getUnpMethod()));
            str.append("\nunpVersion: ").append(Integer.toHexString(getUnpVersion()));
            str.append("\nfullpackedsize: ").append(getFullPackSize());
            str.append("\nfullunpackedsize: ").append(getFullUnpackSize());
            str.append("\nisEncrypted: ").append(isEncrypted());
            str.append("\nisfileHeader: ").append(isFileHeader());
            str.append("\nisSolid: ").append(isSolid());
            str.append("\nisSplitafter: ").append(isSplitAfter());
            str.append("\nisSplitBefore:").append(isSplitBefore());
            str.append("\nunpSize: ").append(getUnpSize());
            str.append("\ndataSize: ").append(getDataSize());
            str.append("\nisUnicode: ").append(isUnicode());
            str.append("\nhasVolumeNumber: ").append(hasVolumeNumber());
            str.append("\nhasArchiveDataCRC: ").append(hasArchiveDataCRC());
            str.append("\nhasSalt: ").append(hasSalt());
            str.append("\nhasEncryptVersions: ").append(hasEncryptVersion());
            str.append("\nisSubBlock: ").append(isSubBlock());
            logger.info(str.toString());
        }
    }

    private Date getDateDos(int time) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (time >>> 25) + 1980);
        cal.set(Calendar.MONTH, ((time >>> 21) & 0x0f) - 1);
        cal.set(Calendar.DAY_OF_MONTH, (time >>> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (time >>> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (time >>> 5) & 0x3f);
        cal.set(Calendar.SECOND, (time & 0x1f) * 2);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public Date getArcTime() {
        return arcTime;
    }

    public void setArcTime(Date arcTime) {
        this.arcTime = arcTime;
    }

    public Date getATime() {
        return aTime;
    }

    public void setATime(Date time) {
        aTime = time;
    }

    public Date getCTime() {
        return cTime;
    }

    public void setCTime(Date time) {
        cTime = time;
    }

    public int getFileAttr() {
        return fileAttr;
    }

    public void setFileAttr(int fileAttr) {
        this.fileAttr = fileAttr;
    }

    public int getFileCRC() {
        return fileCRC;
    }

    public byte[] getFileNameByteArray() {
        return fileNameBytes;
    }

    /**
     * The ASCII filename.
     *
     * @return the ASCII filename
     * @deprecated As of 7.2.0, replaced by {@link #getFileName()}
     */
    @Deprecated
    public String getFileNameString() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * The unicode filename.
     *
     * @return the Unicode filename, or null if the filename is ASCII only
     * @deprecated As of 7.2.0, replaced by {@link #getFileName()}
     */
    @Deprecated
    public String getFileNameW() {
        return fileNameW;
    }

    public void setFileNameW(String fileNameW) {
        this.fileNameW = fileNameW;
    }

    public int getHighPackSize() {
        return highPackSize;
    }

    public int getHighUnpackSize() {
        return highUnpackSize;
    }

    public HostSystem getHostOS() {
        return hostOS;
    }

    public Date getMTime() {
        return mTime;
    }

    public void setMTime(Date time) {
        mTime = time;
    }

    public short getNameSize() {
        return nameSize;
    }

    public int getRecoverySectors() {
        return recoverySectors;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getSubData() {
        return subData;
    }

    public int getSubFlags() {
        return subFlags;
    }

    public byte getUnpMethod() {
        return unpMethod;
    }

    public long getUnpSize() {
        return unpSize;
    }

    public byte getUnpVersion() {
        return unpVersion;
    }

    public long getFullPackSize() {
        return fullPackSize;
    }

    public long getFullUnpackSize() {
        return fullUnpackSize;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    /**
     * the file will be continued in the next archive part
     *
     * @return isSplitAfter
     */
    public boolean isSplitAfter() {
        return (this.flags & BlockHeader.LHD_SPLIT_AFTER) != 0;
    }

    /**
     * the file is continued in this archive
     *
     * @return isSplitBefore
     */
    public boolean isSplitBefore() {
        return (this.flags & LHD_SPLIT_BEFORE) != 0;
    }

    /**
     * this file is compressed as solid (all files handeled as one)
     *
     * @return isSolid
     */
    public boolean isSolid() {
        return (this.flags & LHD_SOLID) != 0;
    }

    /**
     * the file is encrypted
     *
     * @return isEncrypted
     */
    public boolean isEncrypted() {
        return (this.flags & BlockHeader.LHD_PASSWORD) != 0;
    }

    /**
     * the filename is also present in unicode
     *
     * @return isUnicode
     */
    public boolean isUnicode() {
        return (flags & LHD_UNICODE) != 0;
    }

    public boolean isFileHeader() {
        return UnrarHeadertype.FileHeader.equals(headerType);
    }

    public boolean hasSalt() {
        return (flags & LHD_SALT) != 0;
    }

    public boolean isLargeBlock() {
        return (flags & LHD_LARGE) != 0;
    }

    /**
     * whether this fileheader represents a directory
     *
     * @return isDirectory
     */
    public boolean isDirectory() {
        return (flags & LHD_WINDOWMASK) == LHD_DIRECTORY;
    }

    /**
     * The filename either in Unicode or ASCII.
     *
     * @return the Unicode filename if it exists, else the ASCII filename
     * @since 7.2.0
     */
    public String getFileName() {
        return isUnicode() ? fileNameW : fileName;
    }
}
