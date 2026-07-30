package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

/**
 * Pins the secret hygiene of {@link Rijndael.Kdf3Cache}: an entry dropped by round-robin eviction
 * must be zeroed on the way out, not handed to the GC with its password, AES key and IV intact.
 *
 * <p>unrar gets this for free -- {@code KDF3Cache[4]} ({@code crypt.hpp:85}) is an array of
 * fixed-size structs, so the store in {@code CryptData::SetKey30} ({@code crypt3.cpp}) overwrites
 * the evicted secrets in place. Replacing a Java object does not, and since every encrypted RAR3
 * entry carries its own salt, an archive with more than four of them evicts continuously.
 *
 * <p>An evicted entry is unreachable from the public API by construction -- that is the whole point
 * -- so the zeroing rows reflect into the cache, using the idiom of {@code
 * PpmHeapDumpTest#privateField}. No production visibility is widened for the test.
 */
class Kdf3CacheEvictionTest {

    /** Short on purpose: every cache miss is 262,144 SHA-1 rounds. */
    private static final String PASSWORD = "junrar";

    private static final int SLOTS = 4;

    @Test
    void givenFourCachedEntries_whenFifthDerivationEvictsOldest_thenEvictedSecretsAreZeroed()
            throws Exception {
        Rijndael.Kdf3Cache cache = new Rijndael.Kdf3Cache();
        for (int i = 0; i < SLOTS; i++) {
            cache.buildDecipherer(PASSWORD, salt(i));
        }

        Object[] slots = slots(cache);
        Object victim = slots[0];

        // Before-state, which also proves we captured the entry the next miss will evict -- without
        // it the zero assertions below could pass vacuously against an already-empty slot.
        assertThat(victim).isNotNull();
        assertThat(field(victim, "pwd")).isEqualTo(PASSWORD.getBytes(StandardCharsets.UTF_16LE));
        assertThat(field(victim, "salt")).isEqualTo(salt(0));
        assertThat(field(victim, "key")).hasSize(16).isNotEqualTo(new byte[16]);

        cache.buildDecipherer(PASSWORD, salt(SLOTS));

        assertThat(slots[0]).isNotSameAs(victim);
        assertThat(field(victim, "pwd")).containsOnly((byte) 0);
        assertThat(field(victim, "salt")).containsOnly((byte) 0);
        assertThat(field(victim, "key")).containsOnly((byte) 0);
        assertThat(field(victim, "iv")).containsOnly((byte) 0);
    }

    /**
     * The precondition that makes zeroing at eviction legal: entries are never published, so a
     * decipherer handed out earlier cannot be damaged when its entry is later evicted. No
     * reflection -- this one is observable through the public surface.
     */
    @Test
    void givenCipherBuiltBeforeEviction_whenItsEntryIsEvicted_thenItStillDecryptsIdentically()
            throws Exception {
        Rijndael.Kdf3Cache cache = new Rijndael.Kdf3Cache();
        Cipher beforeEviction = cache.buildDecipherer(PASSWORD, salt(0));

        for (int i = 1; i <= SLOTS; i++) {
            cache.buildDecipherer(PASSWORD, salt(i));
        }

        Cipher uncached = Rijndael.buildDecipherer(PASSWORD, salt(0));
        assertThat(beforeEviction.getIV()).isEqualTo(uncached.getIV());
        assertThat(beforeEviction.doFinal(new byte[16])).isEqualTo(uncached.doFinal(new byte[16]));
    }

    @Test
    void givenCachedEntries_whenWipeCalled_thenSlotsAreNulledAndSecretsZeroed() throws Exception {
        Rijndael.Kdf3Cache cache = new Rijndael.Kdf3Cache();
        cache.buildDecipherer(PASSWORD, salt(0));

        Object[] slots = slots(cache);
        Object entry = slots[0];
        assertThat(field(entry, "key")).hasSize(16).isNotEqualTo(new byte[16]);

        cache.wipe();

        assertThat(slots).containsOnlyNulls();
        assertThat(field(entry, "pwd")).containsOnly((byte) 0);
        assertThat(field(entry, "salt")).containsOnly((byte) 0);
        assertThat(field(entry, "key")).containsOnly((byte) 0);
        assertThat(field(entry, "iv")).containsOnly((byte) 0);
        assertThat(privateField(Rijndael.Kdf3Cache.class, "cachePos").get(cache)).isEqualTo(0);
    }

    /** The 8-byte RAR3 salt, distinct per index. */
    private static byte[] salt(int index) {
        byte[] salt = new byte[8];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) (index + 1);
        }
        return salt;
    }

    /** The live slot array, so eviction is observed on the object the cache actually drops. */
    private static Object[] slots(Rijndael.Kdf3Cache cache) throws Exception {
        return (Object[]) privateField(Rijndael.Kdf3Cache.class, "cache").get(cache);
    }

    /** Read off the instance's own class so the private nested entry type is never named. */
    private static byte[] field(Object entry, String name) throws Exception {
        return (byte[]) privateField(entry.getClass(), name).get(entry);
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
