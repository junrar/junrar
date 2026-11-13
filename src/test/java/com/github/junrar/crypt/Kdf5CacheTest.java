package com.github.junrar.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Pins the secret hygiene of {@link Rar5Crypt.Kdf5Cache}, the RAR5 twin of {@link
 * Rijndael.Kdf3Cache} (see {@code Kdf3CacheEvictionTest}): entries are zeroed on eviction and on
 * {@link Rar5Crypt.Kdf5Cache#wipe()}, and a returned {@link Rar5Crypt.Kdf} is never the cached
 * array, so zeroing cannot damage key material a caller still holds.
 */
class Kdf5CacheTest {

    private static final byte[] PASSWORD = "junrar".getBytes(StandardCharsets.UTF_8);

    /** Small on purpose: the KDF runs 2^lg2Count HMAC rounds per miss. */
    private static final int LG2_COUNT = 4;

    private static final int SLOTS = 4;

    @Test
    void givenFourCachedEntries_whenFifthDerivationEvictsOldest_thenEvictedSecretsAreZeroed()
            throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        for (int i = 0; i < SLOTS; i++) {
            cache.deriveKey(PASSWORD, salt(i), LG2_COUNT);
        }

        Object[] slots = slots(cache);
        Object victim = slots[0];
        assertThat(victim).isNotNull();
        assertThat(field(victim, "pwd")).isEqualTo(PASSWORD);
        assertThat(kdf(victim).aesKey).hasSize(32).isNotEqualTo(new byte[32]);

        cache.deriveKey(PASSWORD, salt(SLOTS), LG2_COUNT);

        assertThat(slots[0]).isNotSameAs(victim);
        assertZeroed(victim);
    }

    @Test
    void givenKdfReturnedBeforeWipe_whenCacheIsWiped_thenReturnedKdfIsIntact() throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        Rar5Crypt.Kdf missed = cache.deriveKey(PASSWORD, salt(0), LG2_COUNT);
        Rar5Crypt.Kdf hit = cache.deriveKey(PASSWORD, salt(0), LG2_COUNT);

        cache.wipe();

        Rar5Crypt.Kdf uncached = Rar5Crypt.deriveKey(PASSWORD, salt(0), LG2_COUNT);
        for (Rar5Crypt.Kdf kdf : new Rar5Crypt.Kdf[] {missed, hit}) {
            assertThat(kdf.aesKey).isEqualTo(uncached.aesKey);
            assertThat(kdf.hashKey).isEqualTo(uncached.hashKey);
            assertThat(kdf.pswCheck).isEqualTo(uncached.pswCheck);
        }
    }

    @Test
    void givenCachedEntries_whenWipeCalled_thenSlotsAreNulledAndSecretsZeroed() throws Exception {
        Rar5Crypt.Kdf5Cache cache = new Rar5Crypt.Kdf5Cache();
        cache.deriveKey(PASSWORD, salt(0), LG2_COUNT);

        Object[] slots = slots(cache);
        Object entry = slots[0];
        assertThat(kdf(entry).aesKey).hasSize(32).isNotEqualTo(new byte[32]);

        cache.wipe();

        assertThat(slots).containsOnlyNulls();
        assertZeroed(entry);
        assertThat(privateField(Rar5Crypt.Kdf5Cache.class, "cachePos").get(cache)).isEqualTo(0);
    }

    private static void assertZeroed(Object entry) throws Exception {
        assertThat(field(entry, "pwd")).containsOnly((byte) 0);
        assertThat(field(entry, "salt")).containsOnly((byte) 0);
        assertThat(kdf(entry).aesKey).containsOnly((byte) 0);
        assertThat(kdf(entry).hashKey).containsOnly((byte) 0);
        assertThat(kdf(entry).pswCheck).containsOnly((byte) 0);
    }

    /** The 16-byte RAR5 salt, distinct per index. */
    private static byte[] salt(int index) {
        byte[] salt = new byte[Rar5Crypt.SIZE_SALT50];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) (index + 1);
        }
        return salt;
    }

    private static Object[] slots(Rar5Crypt.Kdf5Cache cache) throws Exception {
        return (Object[]) privateField(Rar5Crypt.Kdf5Cache.class, "cache").get(cache);
    }

    private static Rar5Crypt.Kdf kdf(Object entry) throws Exception {
        return (Rar5Crypt.Kdf) privateField(entry.getClass(), "kdf").get(entry);
    }

    private static byte[] field(Object entry, String name) throws Exception {
        return (byte[]) privateField(entry.getClass(), name).get(entry);
    }

    private static Field privateField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
