package com.thiago.spacebooking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected wherever time is needed so tests can replace it with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
