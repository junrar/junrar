package com.github.junrar.exception;

/**
 * Historical "RAR5 not supported" gate exception. Never thrown since the RAR5 extraction
 * gate was lifted (M3.11, issue #32): RAR5 archives now parse and extract end-to-end.
 * It remains in the {@code Archive.setChannel} rethrow filter until removal to avoid
 * behavioral churn for one release (parity plan &sect;5.3).
 *
 * @deprecated As of the RAR5-support major release this exception is never thrown;
 *             scheduled for removal in the following major release.
 */
@Deprecated
public class UnsupportedRarV5Exception extends RarException {
    public UnsupportedRarV5Exception(Throwable cause) {
        super(cause);
    }

    public UnsupportedRarV5Exception() {
    }
}
