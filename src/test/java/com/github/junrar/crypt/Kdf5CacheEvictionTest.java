package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pins the secret hygiene of {@link Rar5Crypt.Kdf5Cache}, the RAR5 counterpart of {@link
 * Rijndael.Kdf3Cache} ({@code Kdf3CacheEvictionTest}): the cache is owned by the archive that
 * derived the keys, an entry dropped by round-robin eviction is zeroed on the way out, and {@link
 * com.github.junrar.Archive#close()} leaves no password, AES key or HMAC hash key behind.
 *
 * <p>unrar gets the eviction half for free -- {@code KDF5Cache[4]} ({@code crypt.hpp:88}) is an
 * array of fixed-size structs, so the store in {@code CryptData::SetKey50} ({@code crypt5.cpp})
 * overwrites the evicted secrets in place. Replacing a Java object does not, and since every
 * encrypted RAR5 entry carries its own salt, an archive with more than four of them evicts
 * continuously.
 *
 * <p>Cached entries are unreachable from the public API by construction -- that is the whole point
 * -- so the zeroing rows reflect into the cache, using the idiom of {@code
 * PpmHeapDumpTest#privateField}. No production visibility is widened for the test.
 */
class Kdf5CacheEvictionTest {

    private static final String HEADER_ENCRYPTED_FIXTURE = "password/rar5-encrypted-junrar.rar";

    private static final String PASSWORD = "junrar";

    /** Small on purpose: a cache miss is {@code 1 << LG2_COUNT} HMAC-SHA256 iterations. */
    private static final int LG2_COUNT = 5;

    private static final int SLOTS = 4;

    @Test
    void givenFourCachedEntries_whenFifthDerivationEvictsOldest_thenEvictedSecretsAreZeroed()
            throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        for (int i = 0; i < SLOTS; i++) {
            cache.deriveKey(pwd(), salt(i), LG2_COUNT);
        }

        Object[] slots = slots(cache);
        Object victim = slots[0];

        // Before-state, which also proves we captured the entry the next miss will evict -- without
        // it the zero assertions below could pass vacuously against an already-empty slot.
        assertThat(victim).isNotNull();
        assertThat(field(victim, "pwd")).isEqualTo(pwd());
        assertThat(field(victim, "salt")).isEqualTo(salt(0));
        assertThat(kdfField(victim, "aesKey")).hasSize(32).isNotEqualTo(new byte[32]);

        cache.deriveKey(pwd(), salt(SLOTS), LG2_COUNT);

        assertThat(slots[0]).isNotSameAs(victim);
        assertThat(field(victim, "pwd")).containsOnly((byte) 0);
        assertThat(field(victim, "salt")).containsOnly((byte) 0);
        assertThat(kdfField(victim, "aesKey")).containsOnly((byte) 0);
        assertThat(kdfField(victim, "hashKey")).containsOnly((byte) 0);
        assertThat(kdfField(victim, "pswCheck")).containsOnly((byte) 0);
    }

    /**
     * The precondition that makes zeroing at eviction legal: a hit hands out a copy, so key
     * material returned earlier cannot be damaged when its entry is later evicted. No reflection --
     * this one is observable through the public surface.
     */
    @Test
    void givenKdfReturnedBeforeEviction_whenItsEntryIsEvicted_thenItsKeyMaterialSurvives()
            throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        Rar5Crypt.Kdf beforeEviction = cache.deriveKey(pwd(), salt(0), LG2_COUNT);

        for (int i = 1; i <= SLOTS; i++) {
            cache.deriveKey(pwd(), salt(i), LG2_COUNT);
        }

        Rar5Crypt.Kdf uncached = Rar5Crypt.deriveKey(pwd(), salt(0), LG2_COUNT);
        assertThat(beforeEviction.aesKey).isEqualTo(uncached.aesKey);
        assertThat(beforeEviction.hashKey).isEqualTo(uncached.hashKey);
        assertThat(beforeEviction.pswCheck).isEqualTo(uncached.pswCheck);
    }

    @Test
    void givenCachedEntries_whenWipeCalled_thenSlotsAreNulledAndSecretsZeroed() throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        cache.deriveKey(pwd(), salt(0), LG2_COUNT);

        Object[] slots = slots(cache);
        Object entry = slots[0];
        assertThat(kdfField(entry, "aesKey")).hasSize(32).isNotEqualTo(new byte[32]);

        cache.wipe();

        assertThat(slots).containsOnlyNulls();
        assertThat(field(entry, "pwd")).containsOnly((byte) 0);
        assertThat(field(entry, "salt")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "aesKey")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "hashKey")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "pswCheck")).containsOnly((byte) 0);
        assertThat(privateField(Rar5Crypt.Kdf5Cache.class, "cachePos").get(cache)).isEqualTo(0);
    }

    /**
     * The row this cache exists for: a header-encrypted archive derives real keys from a real
     * password, and closing it must leave none of them reachable. With a JVM-global cache the
     * entry outlives the archive entirely, so the password bytes stay readable for the life of the
     * process even though {@code close()} zeroed its own password copy.
     */
    @Test
    void givenHeaderEncryptedArchive_whenClosed_thenDerivedKeyMaterialIsWiped() throws Exception {
        Object[] slots;
        Object entry;
        try (InputStream is = Archive.class.getResourceAsStream(HEADER_ENCRYPTED_FIXTURE);
                Archive archive =
                        new Archive(
                                is,
                                ArchiveOptions.builder()
                                        .password(PASSWORD.toCharArray())
                                        .build())) {
            assertThat(archive.isEncrypted()).isTrue();
            slots = slots(privateField(Archive.class, "rar5KdfCache").get(archive));
            entry = slots[0];
            // Before-state: the archive really did derive and cache the password's key material.
            assertThat(entry).isNotNull();
            assertThat(field(entry, "pwd")).isEqualTo(pwd());
            assertThat(kdfField(entry, "aesKey")).hasSize(32).isNotEqualTo(new byte[32]);
        }

        assertThat(slots).containsOnlyNulls();
        assertThat(field(entry, "pwd")).containsOnly((byte) 0);
        assertThat(field(entry, "salt")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "aesKey")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "hashKey")).containsOnly((byte) 0);
        assertThat(kdfField(entry, "pswCheck")).containsOnly((byte) 0);
    }

    private static byte[] pwd() {
        return PASSWORD.getBytes(StandardCharsets.UTF_8);
    }

    /** The 16-byte RAR5 KDF salt, distinct per index. */
    private static byte[] salt(int index) {
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) (index + 1);
        }
        return salt;
    }

    /** The live slot array, so eviction is observed on the object the cache actually drops. */
    private static Object[] slots(Object cache) throws Exception {
        return (Object[]) privateField(Rar5Crypt.Kdf5Cache.class, "cache").get(cache);
    }

    /** Read off the instance's own class so the private nested entry type is never named. */
    private static byte[] field(Object entry, String name) throws Exception {
        return (byte[]) privateField(entry.getClass(), name).get(entry);
    }

    private static byte[] kdfField(Object entry, String name) throws Exception {
        Object kdf = privateField(entry.getClass(), "kdf").get(entry);
        return (byte[]) privateField(kdf.getClass(), name).get(kdf);
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
