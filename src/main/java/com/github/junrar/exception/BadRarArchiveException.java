package com.github.junrar.exception;

public class BadRarArchiveException extends RarException {
    public BadRarArchiveException(Throwable cause) {
        super(cause);
    }

    public BadRarArchiveException() {
    }

    public BadRarArchiveException(String msg) {
        super(msg);
    }
}
