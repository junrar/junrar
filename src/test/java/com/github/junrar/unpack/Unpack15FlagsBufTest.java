package com.github.junrar.unpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class Unpack15FlagsBufTest {

    private static Unpack15 newUnpack15() {
        return new Unpack15() {
            @Override
            protected void unpInitData(final boolean solid) {
                // getFlagsBuf only needs the bit input and its initialized tables.
            }
        };
    }

    @Test
    void givenFlagsFieldThatDecodesPastTable_whenGetFlagsBuf_thenDoesNotThrow()
            throws ReflectiveOperationException {
        Unpack15 unpack = newUnpack15();
        byte[] input = unpack.getInBuf();
        input[0] = (byte) 0xff;
        input[1] = (byte) 0xf0;
        input[2] = 0;
        unpack.InitBitInput();

        assertThat(unpack.decodeNum(unpack.fgetbits(), 5, getDecHf2(), getPosHf2())).isEqualTo(256);
        unpack.InitBitInput();
        assertThatCode(unpack::getFlagsBuf).doesNotThrowAnyException();
    }

    @Test
    void givenFlagsFieldAtLastTableEntry_whenGetFlagsBuf_thenProcessesEntry()
            throws ReflectiveOperationException {
        Unpack15 unpack = newUnpack15();
        byte[] input = unpack.getInBuf();
        input[0] = (byte) 0xff;
        input[1] = (byte) 0x90;
        input[2] = 0;
        unpack.InitBitInput();

        assertThat(unpack.decodeNum(unpack.fgetbits(), 5, getDecHf2(), getPosHf2())).isEqualTo(255);
        unpack.InitBitInput();
        unpack.initHuff();
        assertThatCode(unpack::getFlagsBuf).doesNotThrowAnyException();
        assertThat(unpack.FlagBuf).isEqualTo(1);
    }

    private static int[] getDecHf2() throws ReflectiveOperationException {
        return getStaticIntArray("DecHf2");
    }

    private static int[] getPosHf2() throws ReflectiveOperationException {
        return getStaticIntArray("PosHf2");
    }

    private static int[] getStaticIntArray(final String name) throws ReflectiveOperationException {
        Field field = Unpack15.class.getDeclaredField(name);
        field.setAccessible(true);
        return (int[]) field.get(null);
    }
}
