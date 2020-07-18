package com.github.junrar.unsigned;


import org.junit.jupiter.api.Test;

import static com.github.junrar.unsigned.UnsignedByte.add;
import static com.github.junrar.unsigned.UnsignedByte.sub;
import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedByteTest {

    @Test
    public void addTest() {
        assertThat(-1).isEqualTo(add((byte) 0xfe, (byte) 0x01));
        assertThat(0).isEqualTo(add((byte) 0xff, (byte) 0x01));
        assertThat(128).isEqualTo(add((byte) 0x7f, (byte) 0x01));
        assertThat(-2).isEqualTo(add((byte) 0xff, (byte) 0xff));
    }

    @Test
    public void subTest() {
        assertThat(-3).isEqualTo(sub((byte) 0xfe, (byte) 0x01));
        assertThat(-1).isEqualTo(sub((byte) 0x00, (byte) 0x01));
        assertThat(-129).isEqualTo(sub((byte) 0x80, (byte) 0x01));
    }
}
