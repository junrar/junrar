package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The sub-block buffer is sized from the header's own declared size, so a crafted UOWNER
 * sub-block can be shorter than the two name-size fields it starts with -- reading those must not
 * run past the buffer, and a header that never held them is reported as broken. The names
 * themselves are a weaker claim: a genuine old sub-block declares names its own header size does
 * not span ({@code d861246:arcread.cpp:517-519}), so a name that does not fit is merely absent.
 */
class UnixOwnersHeaderTest {

    @Test
    void headerShorterThanItsNameSizeFieldsIsMarkedBroken() {
        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), new byte[] {0x02});

        assertThat(header.isBrokenHeader()).isTrue();
        assertThat(header.getOwner()).isNull();
        assertThat(header.getGroup()).isNull();
    }

    @Test
    void headerHoldingBothNamesParsesThem() {
        byte[] body = {
            2,
            0, // ownerNameSize
            3,
            0, // groupNameSize
            'i',
            'd', // owner
            'r',
            'o',
            'o', // group
            0 // trailing byte the sub-block's own size accounts for
        };

        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), body);

        assertThat(header.isBrokenHeader()).isFalse();
        assertThat(header.getOwner()).isEqualTo("id");
        assertThat(header.getGroup()).isEqualTo("roo");
    }

    /**
     * A name is present when its bytes end exactly at the end of the buffer; nothing follows the
     * group name for a byte to be reserved for. Covers the group name, which is read at an offset
     * the owner name has already advanced.
     */
    @Test
    void groupNameEndingExactlyAtTheEndOfTheHeaderIsParsed() {
        byte[] body = {
            2,
            0, // ownerNameSize
            3,
            0, // groupNameSize
            'i',
            'd', // owner
            'r',
            'o',
            'o' // group, last byte of the sub-block
        };

        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), body);

        assertThat(header.getOwner()).isEqualTo("id");
        assertThat(header.getGroup()).isEqualTo("roo");
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /** The same, for the owner name: it ends at the end of the buffer when no group follows. */
    @Test
    void ownerNameEndingExactlyAtTheEndOfTheHeaderIsParsed() {
        byte[] body = {
            2,
            0, // ownerNameSize
            0,
            0, // groupNameSize
            'i',
            'd' // owner, last bytes of the sub-block
        };

        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), body);

        assertThat(header.getOwner()).isEqualTo("id");
        // Declared as zero length, so present and empty -- the same answer a declared-empty name
        // gets anywhere else in the buffer. The old bound returned null for this one position
        // only, because "pos + 0 < length" happens to go false once pos reaches the end.
        assertThat(header.getGroup()).isEmpty();
    }

    /**
     * A name the sub-block declares as empty is present and empty, not absent: zero is a length
     * the header stated, unlike a name whose bytes the buffer does not span. Keeping it as the
     * empty string is also what this package returned before the bounds fix, so a caller doing
     * getOwner().isEmpty() on one does not start seeing an NPE.
     */
    @Test
    void nameSizeOfZeroIsAnEmptyNameNotAnAbsentOne() {
        byte[] body = {
            0,
            0, // ownerNameSize
            3,
            0, // groupNameSize
            'r',
            'o',
            'o' // group
        };

        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), body);

        assertThat(header.getOwner()).isEmpty();
        assertThat(header.getGroup()).isEqualTo("roo");
    }

    /**
     * A name the buffer does not span is left unset rather than read past the end -- and is not
     * marked broken: a genuine old sub-block declares names outside its own header size
     * ({@code d861246:arcread.cpp:517-519}), which is precisely this shape.
     */
    @Test
    void nameSizeBeyondTheHeaderLeavesTheNameUnset() {
        byte[] body = {
            9, 0, // ownerNameSize, past the end
            4, 0, // groupNameSize, also past the end
            'i', 'd'
        };

        UnixOwnersHeader header = new UnixOwnersHeader(subBlockHeader(), body);

        assertThat(header.getOwner()).isNull();
        assertThat(header.getGroup()).isNull();
        assertThat(header.isBrokenHeader()).isFalse();
    }

    /** Mirrors Archive's construction: a SubBlockHeader over a UOWNER sub-block. */
    private static SubBlockHeader subBlockHeader() {
        byte[] baseBlockBytes = {0, 0, 0x7a, 0, 0, 0, 0};
        BlockHeader blockHeader = new BlockHeader(new BaseBlock(baseBlockBytes), new byte[4]);
        return new SubBlockHeader(blockHeader, new byte[] {0x01, 0x01, 0}); // UO_HEAD, level 0
    }
}
