package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class NewSubHeaderTypeTest {

    @Test
    public void testByteEquals() {
        assertThat(NewSubHeaderType.SUBHEAD_TYPE_CMT.byteEquals(new byte[]{'C', 'M', 'T'})).isTrue();
        assertThat(NewSubHeaderType.SUBHEAD_TYPE_CMT.byteEquals(new byte[]{'A', 'C', 'L'})).isFalse();
        assertThat(NewSubHeaderType.SUBHEAD_TYPE_CMT.byteEquals(null)).isFalse();
    }

    @Test
    public void testToString() {
        assertThat(NewSubHeaderType.SUBHEAD_TYPE_CMT.toString()).isEqualTo("CMT");
        assertThat(NewSubHeaderType.SUBHEAD_TYPE_STREAM.toString()).isEqualTo("STM");
    }
}
