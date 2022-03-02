package com.github.junrar.unpack.ppm;

import com.github.junrar.rarfile.MainHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * For debugging purposes only.
 *
 * @author alban
 */
public class AnalyzeHeapDump {
    private static final Logger logger = LoggerFactory.getLogger(MainHeader.class);

    /** Creates a new instance of AnalyzeHeapDump */
    public AnalyzeHeapDump() {
    }

    public static void main(String[] argv) {
        File cfile = new File("P:\\test\\heapdumpc");
        File jfile = new File("P:\\test\\heapdumpj");
        if (!cfile.exists()) {
            logger.error("File not found: {}", cfile.getAbsolutePath());
            return;
        }
        if (!jfile.exists()) {
            logger.error("File not found: {}", jfile.getAbsolutePath());
            return;
        }
        long clen = cfile.length();
        long jlen = jfile.length();
        if (clen != jlen) {
            logger.info("File size mismatch");
            logger.info("clen = {}", clen);
            logger.info("jlen = {}", jlen);
        }
        // Do byte comparison
        long len = Math.min(clen, jlen);
        InputStream cin = null;
        InputStream jin = null;
        int bufferLen = 256 * 1024;
        try {
            cin = new BufferedInputStream(
                    new FileInputStream(cfile), bufferLen);
            jin = new BufferedInputStream(
                    new FileInputStream(jfile), bufferLen);
            boolean matching = true;
            boolean mismatchFound = false;
            long startOff = 0L;
            long off = 0L;
            while (off < len) {
                if (cin.read() != jin.read()) {
                    if (matching) {
                        startOff = off;
                        matching = false;
                        mismatchFound = true;
                    }
                } else { // match
                    if (!matching) {
                        printMismatch(startOff, off);
                        matching = true;
                    }
                }
                off++;
            }
            if (!matching) {
                printMismatch(startOff, off);
            }
            if (!mismatchFound) {
                logger.info("Files are identical");
            }
            logger.info("Done");
        } catch (IOException e) {
            logger.error("", e);
        } finally {
            try {
                cin.close();
                jin.close();
            } catch (IOException e) {
                logger.error("", e);
            }
        }
    }

    private static void printMismatch(long startOff, long bytesRead) {
        if (logger.isInfoEnabled()) {
            logger.info("Mismatch: off={}(0x{}), len={}", startOff, Long.toHexString(startOff), (bytesRead - startOff));
        }
    }
}
