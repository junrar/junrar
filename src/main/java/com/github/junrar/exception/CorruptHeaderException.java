package com.github.junrar.exception;

public class CorruptHeaderException extends RarException {
    public CorruptHeaderException(Throwable cause) {
        super(cause);
    }

    public CorruptHeaderException() {
    }
}
