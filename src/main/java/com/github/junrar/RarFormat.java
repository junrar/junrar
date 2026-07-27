package com.github.junrar;

/**
 * The on-disk RAR format family, classified from the archive signature's version byte
 * (unrar {@code RARFORMAT}, {@code d861246:archive.cpp:100-126}). Exposed via
 * {@link Archive#getFormat()}.
 */
public enum RarFormat {

    /**
     * The ancient RAR 1.4 format (marker {@code 52 45 7e 5e}, unrar {@code RARFMT14}): a
     * pre-{@code BaseBlock} header layout read by {@code Archive}'s dedicated RAR 1.4 loop
     * (P1, issue #293). Headers and listing only -- extraction is a later phase.
     */
    RAR14,

    /**
     * The classic format family (RAR 1.5 through 4.x, signature version byte {@code 0x00}).
     * unrar {@code RARFMT15}.
     */
    RAR15,

    /**
     * The RAR 5.0 format (signature version byte {@code 0x01}). unrar {@code RARFMT50}.
     */
    RAR50
}
