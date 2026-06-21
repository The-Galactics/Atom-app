package com.atom.infrastructure.adapter.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AndroidActionExecutor#parseSubmit(String)}. This test
 * lives in the same package as the class under test so it can exercise the
 * package-private helper directly — the JVM null-accessibility-service guard
 * short-circuits the public {@code execute} path before the submit-defaulting
 * logic runs, so this is the only way to cover it without an Android runtime.
 */
class AndroidActionExecutorParseSubmitTest {

    @Test
    @DisplayName("parseSubmit defaults to true when blank, else parses the boolean")
    void parseSubmitDefaultsAndParses() {
        // Absent/blank -> the common "type and search" intent: submit.
        assertThat(AndroidActionExecutor.parseSubmit(null)).isTrue();
        assertThat(AndroidActionExecutor.parseSubmit("")).isTrue();
        assertThat(AndroidActionExecutor.parseSubmit(" ")).isTrue();
        // Explicit booleans (case-insensitive via Boolean.parseBoolean).
        assertThat(AndroidActionExecutor.parseSubmit("true")).isTrue();
        assertThat(AndroidActionExecutor.parseSubmit("false")).isFalse();
        assertThat(AndroidActionExecutor.parseSubmit("FALSE")).isFalse();
        // Any non-boolean string parses to false, not the default.
        assertThat(AndroidActionExecutor.parseSubmit("garbage")).isFalse();
    }
}
