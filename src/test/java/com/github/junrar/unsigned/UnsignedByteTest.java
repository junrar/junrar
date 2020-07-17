package com.github.junrar.unsigned;


import org.junit.jupiter.api.Test;

import static com.github.junrar.unsigned.UnsignedByte.add;
import static com.github.junrar.unsigned.UnsignedByte.sub;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnsignedByteTest {

    @Test
    public void addTest() {
        assertEquals(-1, add((byte) 0xfe, (byte) 0x01));
        assertEquals(0, add((byte) 0xff, (byte) 0x01));
        assertEquals(128, add((byte) 0x7f, (byte) 0x01));
        assertEquals(-2, add((byte) 0xff, (byte) 0xff));
    }

    @Test
    public void subTest() {
        assertEquals(-3, sub((byte) 0xfe, (byte) 0x01));
        assertEquals(-1, sub((byte) 0x00, (byte) 0x01));
        assertEquals(-129, sub((byte) 0x80, (byte) 0x01));
    }
}
