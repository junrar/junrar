package com.github.junrar.rar5.header;

/**
 * Thrown when a RAR5 header is malformed or contains invalid data.
 */
public class Rar5HeaderException extends RuntimeException {

    /**
     * @param message the detail message
     */
    public Rar5HeaderException(final String message) {
        super(message);
    }
}
