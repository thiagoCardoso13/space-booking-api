package com.thiago.spacebooking.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtSecretGuardTest {

    @Test
    void failsWhenSecretIsMissing() {
        assertThatThrownBy(() -> new JwtSecretGuard(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsWhenSecretIsBlank() {
        assertThatThrownBy(() -> new JwtSecretGuard(new MockEnvironment().withProperty("JWT_SECRET", "  ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsAnExplicitSecret() {
        assertThatCode(() -> new JwtSecretGuard(new MockEnvironment().withProperty("JWT_SECRET", "x".repeat(32))))
                .doesNotThrowAnyException();
    }
}
