package com.github.junrar.rarfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class HostSystemTest {

    @ParameterizedTest
    @EnumSource(HostSystem.class)
    void findHostSystem(final HostSystem hostSystem) {
        assertThat(HostSystem.findHostSystem(hostSystem.getHostByte())).isEqualTo(hostSystem);
    }

    @Test
    void unknownHostSystem() {
        assertThat(HostSystem.findHostSystem((byte) 99)).isNull();
    }

}
