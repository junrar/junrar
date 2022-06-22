package com.github.junrar.exception;

public class RarException extends Exception {
    public RarException(Throwable cause) {
        super(cause);
    }

    public RarException() {
    }

    public RarException(String msg) {
        super(msg);
    }
}
