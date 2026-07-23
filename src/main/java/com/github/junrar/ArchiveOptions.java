package com.github.junrar;

/**
 * Construction-time configuration for {@link Archive}: password, extraction
 * resource limits, and progress callback. Immutable; build via {@link #builder()}.
 * Scope the built instance as narrowly as the password it may carry: unlike
 * {@link Archive}, it has no {@code close()} to wipe its own copy.
 *
 * @see Archive#Archive(com.github.junrar.volume.VolumeManager, ArchiveOptions)
 */
public final class ArchiveOptions {

    /**
     * Default extraction dictionary-size budget in bytes: 4 GiB, matching
     * unrar 7.2.7's own default {@code WinSizeLimit} (d861246:options.cpp:13).
     * Raise it exactly like unrar's {@code -mdx} switch raises {@code WinSizeLimit}.
     */
    public static final long DEFAULT_MAX_DICTIONARY_SIZE = 0x100000000L;

    private final char[] password;
    private final long maxDictionarySize;
    private final UnrarCallback unrarCallback;

    private ArchiveOptions(final Builder builder) {
        this.password = builder.password == null ? null : builder.password.clone();
        this.maxDictionarySize = builder.maxDictionarySize;
        this.unrarCallback = builder.unrarCallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a defensive copy of the configured password, or {@code null} if none was set.
     */
    public char[] getPassword() {
        return this.password == null ? null : this.password.clone();
    }

    public long getMaxDictionarySize() {
        return this.maxDictionarySize;
    }

    public UnrarCallback getUnrarCallback() {
        return this.unrarCallback;
    }

    public static final class Builder {

        private char[] password;
        private long maxDictionarySize = DEFAULT_MAX_DICTIONARY_SIZE;
        private UnrarCallback unrarCallback;

        private Builder() {}

        /**
         * @param password the archive password, defensively copied; the caller may
         *                 safely wipe its own array after {@link #build()}. Preferred
         *                 over {@link #password(String)}, which cannot be wiped.
         */
        public Builder password(final char[] password) {
            this.password = password == null ? null : password.clone();
            return this;
        }

        /**
         * Convenience for {@link #password(char[])}. A {@code String} password
         * cannot be wiped from memory; prefer the {@code char[]} overload.
         */
        public Builder password(final String password) {
            return password(password == null ? null : password.toCharArray());
        }

        /**
         * @param maxDictionarySize the extraction dictionary-size budget in bytes;
         *                          must be {@code > 0}. Default
         *                          {@value #DEFAULT_MAX_DICTIONARY_SIZE} (4 GiB),
         *                          matching unrar's own default.
         */
        public Builder maxDictionarySize(final long maxDictionarySize) {
            this.maxDictionarySize = maxDictionarySize;
            return this;
        }

        public Builder unrarCallback(final UnrarCallback unrarCallback) {
            this.unrarCallback = unrarCallback;
            return this;
        }

        /**
         * @throws IllegalArgumentException if {@code maxDictionarySize <= 0}.
         */
        public ArchiveOptions build() {
            if (this.maxDictionarySize <= 0) {
                throw new IllegalArgumentException(
                        "maxDictionarySize must be > 0, was " + this.maxDictionarySize);
            }
            return new ArchiveOptions(this);
        }
    }
}
