package com.github.junrar.unpack;

import com.github.junrar.exception.RarException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static Unpack withOneFilter() throws ReflectiveOperationException {
        Unpack unpack = new Unpack(null);
        filters(unpack).add(new UnpackFilter());
        oldFilterLengths(unpack).add(0);
        return unpack;
    }

    private static boolean addVmCode(Unpack unpack, List<Byte> vmCode)
            throws ReflectiveOperationException, RarException {
        java.lang.reflect.Method addVmCode = Unpack.class.getDeclaredMethod(
                "addVMCode", int.class, List.class, int.class);
        addVmCode.setAccessible(true);
        return (boolean) addVmCode.invoke(unpack, 0, vmCode, vmCode.size());
    }

    private static List<Byte> zeroVmCode() {
        return Arrays.asList((byte) 0, (byte) 0, (byte) 0);
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

    private static void setLastFilter(Unpack unpack, int value) throws ReflectiveOperationException {
        Field field = field("lastFilter");
        field.setInt(unpack, value);
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = Unpack.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
