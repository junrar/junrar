package com.github.junrar.exception;

/**
 * A RAR5 password check failed: a wrong password on a pswcheck-protected archive, a missing
 * password on a header-encrypted archive (unrar's own passwordless {@code -hp} open reports
 * "Incorrect password", probed 2026-07-17), or an encrypted-header CRC mismatch after
 * decryption (indistinguishable from a wrong key under CBC -- unrar {@code arcread.cpp:598-696}
 * sets {@code FailedHeaderDecryption} for both). M3.4, issue #25.
 * <p>
 * Rethrown from {@code Archive.setChannel} (manual &sect;4.9 filter): without this, the catch
 * filter would swallow it and the archive would "open" with partial (undecryptable) headers.
 */
public class WrongPasswordException extends RarException {
    public WrongPasswordException(Throwable cause) {
        super(cause);
    }

    public WrongPasswordException() {}

    public WrongPasswordException(String message) {
        super(message);
    }
}
