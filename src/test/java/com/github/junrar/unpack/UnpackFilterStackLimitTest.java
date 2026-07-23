package com.github.junrar.unpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.junrar.exception.RarException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the RAR3 filter stack limit and the null-hole reuse performed before
 * growing the stack. The archive parser reaches {@code addVMCode} through two
 * framing paths; this state-level test keeps the hostile boundary deterministic
 * without shipping a filter-flood archive.
 */
class UnpackFilterStackLimitTest {

    private static final int MAX3_UNPACK_FILTERS = 8192;

    @Test
    void givenFilterIndex8192_whenAddingNewFilter_thenAcceptsBoundary() throws Exception {
        Unpack unpack = new Unpack(null);
        filters(unpack).addAll(repeatedFilters(MAX3_UNPACK_FILTERS));
        oldFilterLengths(unpack).addAll(repeatedLengths(MAX3_UNPACK_FILTERS));
        setLastFilter(unpack, MAX3_UNPACK_FILTERS);

        assertThat(addVmCode(unpack, newFilterVmCode())).isTrue();
        assertThat(filters(unpack)).hasSize(MAX3_UNPACK_FILTERS + 1);
    }

    @Test
    void givenFilterIndex8193_whenAddingNewFilter_thenRejects() throws Exception {
        Unpack unpack = new Unpack(null);
        filters(unpack).addAll(repeatedFilters(MAX3_UNPACK_FILTERS + 1));
        oldFilterLengths(unpack).addAll(repeatedLengths(MAX3_UNPACK_FILTERS + 1));
        setLastFilter(unpack, MAX3_UNPACK_FILTERS + 1);

        assertThat(addVmCode(unpack, newFilterVmCode())).isFalse();
        assertThat(filters(unpack)).hasSize(MAX3_UNPACK_FILTERS + 1);
    }

    @Test
    void givenUnsignedFilterPosition_whenAddingFilter_thenRejectsWithoutThrowing()
            throws Exception {
        Unpack unpack = new Unpack(null);

        assertThat(addVmCode(unpack, 0x80, Arrays.asList((byte) 0x40, (byte) 0, (byte) 0)))
                .isFalse();
    }

    @Test
    void givenStackSize8192_whenAddingFilter_thenAcceptsAndGrowsOnce() throws Exception {
        Unpack unpack = withOneFilter();
        prgStack(unpack).addAll(repeatedFilters(MAX3_UNPACK_FILTERS));

        assertThat(addVmCode(unpack, zeroVmCode())).isTrue();
        assertThat(prgStack(unpack)).hasSize(MAX3_UNPACK_FILTERS + 1);
    }

    @Test
    void givenStackSize8193_whenAddingFilter_thenRejectsBeforeGrowth() throws Exception {
        Unpack unpack = withOneFilter();
        prgStack(unpack).addAll(repeatedFilters(MAX3_UNPACK_FILTERS + 1));

        assertThat(addVmCode(unpack, zeroVmCode())).isFalse();
        assertThat(prgStack(unpack)).hasSize(MAX3_UNPACK_FILTERS + 1);
    }

    @Test
    void givenAStackHole_whenAddingFilter_thenReusesHoleBeforeGrowing() throws Exception {
        Unpack unpack = withOneFilter();
        List<UnpackFilter> stack = prgStack(unpack);
        stack.addAll(repeatedFilters(MAX3_UNPACK_FILTERS));
        stack.set(17, null);

        assertThat(addVmCode(unpack, zeroVmCode())).isTrue();
        assertThat(stack).hasSize(MAX3_UNPACK_FILTERS);
        assertThat(stack).doesNotContainNull();
    }

    @Test
    void givenUnsignedVmCodeSize_whenAddingNewFilter_thenRejectsWithoutAllocating()
            throws Exception {
        Unpack unpack = new Unpack(null);

        assertThatCode(() -> assertThat(addVmCode(unpack, 0, bytes(0x03, 0x80, 0, 0, 0))).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    void givenUnsignedOptionalDataSize_whenAddingFilter_thenRejects() throws Exception {
        Unpack unpack = withOneFilter();

        assertThat(addVmCode(unpack, 0x08, bytes(0x03, 0xff, 0xff, 0xff, 0xff, 0x04, 0))).isFalse();
    }

    @Test
    void givenUnsignedBlockStart_whenAddingFilter_thenMarksNextWindow() throws Exception {
        Unpack unpack = withOneFilter();
        unpack.wrPtr = 2;
        unpack.unpPtr = 1;

        assertThat(addVmCode(unpack, 0, bytes(0xff, 0xff, 0xff, 0xff, 0xc1, 0))).isTrue();
        assertThat(lastStackFilter(unpack).isNextWindow()).isTrue();
    }

    @Test
    void givenUnsignedBlockLength_whenWritingBuffer_thenDefersFilter() throws Exception {
        Unpack unpack = withOneFilter();
        unpack.wrPtr = 0;
        unpack.unpPtr = 1;

        assertThat(addVmCode(unpack, 0x20, bytes(0x03, 0xff, 0xff, 0xff, 0xff, 0x04, 0))).isTrue();
        lastStackFilter(unpack).setBlockStart(0);
        assertThat(lastStackFilter(unpack).getBlockLength()).isEqualTo(-1);
        assertThatCode(() -> unpWriteBuf(unpack)).doesNotThrowAnyException();
        assertThat(unpack.wrPtr).isZero();
    }

    private static Unpack withOneFilter() throws ReflectiveOperationException {
        Unpack unpack = new Unpack(null);
        filters(unpack).add(new UnpackFilter());
        oldFilterLengths(unpack).add(0);
        return unpack;
    }

    private static boolean addVmCode(Unpack unpack, List<Byte> vmCode)
            throws ReflectiveOperationException, RarException {
        return addVmCode(unpack, 0, vmCode);
    }

    private static boolean addVmCode(Unpack unpack, int firstByte, List<Byte> vmCode)
            throws ReflectiveOperationException, RarException {
        java.lang.reflect.Method addVmCode =
                Unpack.class.getDeclaredMethod("addVMCode", int.class, List.class, int.class);
        addVmCode.setAccessible(true);
        try {
            return (boolean) addVmCode.invoke(unpack, firstByte, vmCode, vmCode.size());
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof RarException) {
                throw (RarException) cause;
            }
            throw e;
        }
    }

    private static List<Byte> zeroVmCode() {
        return Arrays.asList((byte) 0, (byte) 0, (byte) 0);
    }

    private static List<Byte> bytes(int... values) {
        List<Byte> bytes = new ArrayList<>(values.length);
        for (int value : values) {
            bytes.add((byte) value);
        }
        return bytes;
    }

    private static List<Byte> newFilterVmCode() {
        // ReadData(0), ReadData(1), then one zero-byte VM program.
        return Arrays.asList((byte) 0, (byte) 0x10, (byte) 0);
    }

    private static List<UnpackFilter> repeatedFilters(int size) {
        List<UnpackFilter> filters = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            filters.add(new UnpackFilter());
        }
        return filters;
    }

    private static List<Integer> repeatedLengths(int size) {
        List<Integer> lengths = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lengths.add(0);
        }
        return lengths;
    }

    private static UnpackFilter lastStackFilter(Unpack unpack) throws ReflectiveOperationException {
        List<UnpackFilter> stack = prgStack(unpack);
        return stack.get(stack.size() - 1);
    }

    private static void unpWriteBuf(Unpack unpack)
            throws ReflectiveOperationException, java.io.IOException {
        java.lang.reflect.Method method = Unpack.class.getDeclaredMethod("UnpWriteBuf");
        method.setAccessible(true);
        try {
            method.invoke(unpack);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.io.IOException) {
                throw (java.io.IOException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<UnpackFilter> filters(Unpack unpack) throws ReflectiveOperationException {
        return (List<UnpackFilter>) field("filters").get(unpack);
    }

    @SuppressWarnings("unchecked")
    private static List<UnpackFilter> prgStack(Unpack unpack) throws ReflectiveOperationException {
        return (List<UnpackFilter>) field("prgStack").get(unpack);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> oldFilterLengths(Unpack unpack)
            throws ReflectiveOperationException {
        return (List<Integer>) field("oldFilterLengths").get(unpack);
    }

    private static void setLastFilter(Unpack unpack, int value)
            throws ReflectiveOperationException {
        Field field = field("lastFilter");
        field.setInt(unpack, value);
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = Unpack.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
