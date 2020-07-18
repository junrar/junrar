package com.github.junrar.exception;

public class CrcErrorException extends RarException {
    public CrcErrorException(Throwable cause) {
        super(cause);
    }

    public CrcErrorException() {
        super();
    }
}
