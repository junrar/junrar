package com.github.junrar.exception;

/**
 * Thrown when a header-declared dictionary requirement exceeds the configured
 * {@code ArchiveOptions.maxDictionarySize} budget (extraction-time, one entry
 * per offending file). NOT part of {@code Archive.setChannel}'s rethrow filter
 * (manual section 4.9): headers still parse fine, only that entry's extraction
 * fails, matching the "allow extraction of working files in a corrupt archive"
 * tolerance.
 */
public class UnsupportedDictionarySizeException extends RarException {
    public UnsupportedDictionarySizeException(Throwable cause) {
        super(cause);
    }

    public UnsupportedDictionarySizeException() {}

    public UnsupportedDictionarySizeException(String message) {
        super(message);
    }
}
