package com.github.junrar.rarfile.rar5;

/**
 * RAR5 file-system redirection fact (FHEXTRA_REDIR, unrar {@code FileHeader::RedirType}/
 * {@code RedirName}/{@code DirTarget}, {@code 8f437ab:arcread.cpp:1179-1195}). Parse-and-flag
 * only: {@link #getTarget()} is decoded verbatim, including a hostile {@code ../} path --
 * sanitizing or rejecting a traversal target is an extraction-time concern (M3.10), not a
 * header-parse one.
 */
public final class Rar5Redirection {

    private final Rar5RedirType type;
    private final boolean directory;
    private final String target;

    public Rar5Redirection(final Rar5RedirType type, final boolean directory, final String target) {
        this.type = type;
        this.directory = directory;
        this.target = target;
    }

    public Rar5RedirType getType() {
        return this.type;
    }

    public boolean isDirectory() {
        return this.directory;
    }

    public String getTarget() {
        return this.target;
    }
}
