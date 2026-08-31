package com.github.junrar.rarfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class RARVersionTest {

    @Test
    public void testIsOldFormat() {
        assertThat(RARVersion.isOldFormat(RARVersion.OLD)).isTrue();
        assertThat(RARVersion.isOldFormat(RARVersion.V4)).isFalse();
        assertThat(RARVersion.isOldFormat(RARVersion.V5)).isFalse();
        assertThat(RARVersion.isOldFormat(null)).isFalse();
    }
}
