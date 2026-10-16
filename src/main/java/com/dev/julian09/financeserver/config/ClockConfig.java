package com.dev.julian09.financeserver.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Inject this instead of calling now() so tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
