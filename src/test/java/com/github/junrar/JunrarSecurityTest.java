package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * getInputStream() thread exhaustion DoS.
 *
 * Each getInputStream() call spawns one background thread that extracts into a
 * PipedOutputStream (32 KB buffer). For files larger than 32 KB, the thread fills
 * the buffer and BLOCKS — it never becomes idle, so the pool cannot reuse it and
 * creates a new thread for every subsequent call.
 *
 * Fix applied: default pool cap changed from Integer.MAX_VALUE to
 * availableProcessors() * 2. RejectedExecutionException is now surfaced as
 * IOException instead of propagating unchecked.
 */
public class JunrarSecurityTest {

    /**
     * Demonstrates each getInputStream() call on a file larger
     * than the 32 KB pipe buffer blocks a background thread permanently.
     *
     * stream.read() is the synchronisation point — blocks until the thread has
     * written at least 1 byte, proving it is live. We stop reading; the thread
     * has ~34 KB remaining to write into a full 32 KB buffer and blocks.
     */
    @Test
    void eachGetInputStreamCallBlocksOneThread() throws Exception {
        final int CALLS = 5;
        List<InputStream> openStreams = new ArrayList<>();
        List<Archive> openArchives = new ArrayList<>();

        int threadsBefore = countJunrarThreads();

        for (int i = 0; i < CALLS; i++) {
            Archive archive = new Archive(
                TestCommons.class.getResourceAsStream("tika-documents.rar"));
            openArchives.add(archive);

            // testPDF.pdf — 34,824 bytes > 32 KB pipe buffer
            InputStream stream = archive.getInputStream(findEntry(archive, ".pdf"));
            stream.read(); // blocks until thread writes ≥ 1 byte — no sleep needed
            openStreams.add(stream);
        }

        int threadsAfter = countJunrarThreads();
        System.out.printf("junrar threads — before: %d  after: %d  blocked: %d%n",
            threadsBefore, threadsAfter, threadsAfter - threadsBefore);

        assertThat(threadsAfter - threadsBefore)
            .as("one blocked thread per getInputStream() call — unbounded without a cap")
            .isGreaterThanOrEqualTo(CALLS);

        for (InputStream s : openStreams) { try { s.close(); } catch (IOException ignored) {} }
        for (Archive a : openArchives)   { try { a.close(); } catch (IOException ignored) {} }
    }

    /**
     * Verifies the fix: the thread pool's maximum size is now bounded to
     * availableProcessors() * 2 instead of Integer.MAX_VALUE.
     *
     * Reads the pool's maximumPoolSize via reflection — deterministic regardless
     * of test execution order, since the value is set at pool creation time.
     */
    @Test
    void fix_poolCapIsNoLongerUnbounded() throws Exception {
        ThreadPoolExecutor pool = getExecutorPool();

        int cap = pool.getMaximumPoolSize();
        int expectedCap = Runtime.getRuntime().availableProcessors() * 2;

        System.out.printf("pool maximumPoolSize: %d  (availableProcessors * 2 = %d)%n",
            cap, expectedCap);

        assertThat(cap)
            .as("pool cap should be availableProcessors * 2, not Integer.MAX_VALUE")
            .isEqualTo(expectedCap)
            .isLessThan(Integer.MAX_VALUE);
    }

    // -------------------------------------------------------------------------

    private FileHeader findEntry(Archive archive, String suffix) throws Exception {
        for (FileHeader fh : archive) {
            if (fh.getFileName().toLowerCase().endsWith(suffix)) return fh;
        }
        throw new IllegalStateException("No entry ending with " + suffix);
    }

    private int countJunrarThreads() {
        return (int) Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().startsWith("junrar-extractor-"))
            .count();
    }

    private ThreadPoolExecutor getExecutorPool() throws Exception {
        // ExtractorExecutorHolder is a private static inner class of Archive.
        // Trigger its lazy initialisation by accessing via reflection.
        Class<?>[] inner = Archive.class.getDeclaredClasses();
        Class<?> holderClass = null;
        for (Class<?> c : inner) {
            if (c.getSimpleName().equals("ExtractorExecutorHolder")) {
                holderClass = c;
                break;
            }
        }
        assertThat(holderClass).as("ExtractorExecutorHolder class").isNotNull();

        Field field = holderClass.getDeclaredField("cachedExecutorService");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(null);
    }
}
