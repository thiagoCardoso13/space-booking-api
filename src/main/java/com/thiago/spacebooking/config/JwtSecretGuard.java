package com.thiago.spacebooking.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in the {@code prod} profile unless JWT_SECRET is set explicitly.
 * Without it the app would fall back to the development secret that is public in this repository,
 * and anyone could forge tokens.
 */
@Component
@Profile("prod")
class JwtSecretGuard {

    JwtSecretGuard(Environment environment) {
        String secret = environment.getProperty("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set when running with the 'prod' profile (at least 32 characters).");
        }
    }
}
