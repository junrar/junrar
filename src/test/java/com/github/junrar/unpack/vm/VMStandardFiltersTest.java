package com.github.junrar.unpack.vm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class VMStandardFiltersTest {

    @ParameterizedTest
    @EnumSource(VMStandardFilters.class)
    void findFilter(final VMStandardFilters vmStandardFilters) {
        assertThat(VMStandardFilters.findFilter(vmStandardFilters.getFilter())).isEqualTo(vmStandardFilters);
    }

    @Test
    void unknownVMStandardFilters() {
        assertThat(VMStandardFilters.findFilter(8)).isNull();
    }

}
