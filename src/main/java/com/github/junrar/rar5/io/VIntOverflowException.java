package com.github.junrar.rar5.io;

import com.github.junrar.exception.RarException;

/**
 * Thrown when a variable-length integer (vint) exceeds the maximum allowed
 * length of 10 bytes or when the encoded data ends prematurely.
 */
public class VIntOverflowException extends RarException {

    /**
     * @param message the detail message
     */
    public VIntOverflowException(final String message) {
        super(message);
    }
}
