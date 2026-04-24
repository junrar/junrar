package com.github.junrar.rar5.io;

/**
 * Thrown when a variable-length integer (vint) exceeds the maximum allowed
 * length of 10 bytes or when the encoded data ends prematurely.
 */
public class VIntOverflowException extends RuntimeException {

    /**
     * @param message the detail message
     */
    public VIntOverflowException(final String message) {
        super(message);
    }
}
