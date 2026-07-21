package com.github.junrar.exception;

/**
 * An FHEXTRA_REDIR link entry (symlink, hardlink, or file copy) was refused by one of the
 * three RAR5 extraction safety layers (M3.10, issue #31):
 * <ol>
 *   <li>up-level depth / containment (unrar {@code IsRelativeSymlinkSafe},
 *       {@code 8f437ab:extinfo.cpp:110}),</li>
 *   <li>absolute / non-relative target validation (unrar {@code ExtractUnixLink50},
 *       {@code 22b5243:ulinks.cpp}),</li>
 *   <li>refusing to write through a previously-extracted directory symlink (unrar
 *       {@code LinksToDirs}, {@code 2ecab6b:extract.cpp}).</li>
 * </ol>
 * This is an <strong>extraction-time</strong> failure thrown from
 * {@link com.github.junrar.LocalFolderExtractor}, never from {@code readHeaders}, so it is
 * deliberately <strong>not</strong> added to the {@code Archive.setChannel} catch filter
 * (manual &sect;4.9): archive-open behavior is unchanged.
 */
public class UnsafeLinkException extends RarException {
    public UnsafeLinkException(final String message) {
        super(message);
    }
}
