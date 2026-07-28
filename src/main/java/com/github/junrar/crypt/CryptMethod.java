package com.github.junrar.crypt;

/**
 * unrar's {@code CRYPT_METHOD} enum (d861246:crypt.hpp:5-8), restricted to the methods this
 * library ever selects: {@code CRYPT_NONE}/{@code CRYPT_RAR50}/{@code CRYPT_UNKNOWN} are handled
 * elsewhere ({@link com.github.junrar.unpack.ComprDataIO} branches on {@code
 * FileHeader#getSalt16()} for RAR5 before this enum ever comes into play; unencrypted entries
 * never call {@link RarLegacyCrypt#select}).
 */
public enum CryptMethod {
    RAR13,
    RAR15,
    RAR20,
    RAR30
}
