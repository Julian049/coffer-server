package com.dev.julian09.financeserver.config;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Money travels as a string ("28000.0000") so no client parses it as a binary double (ADR-0004).
     * Dates are already ISO-8601: Jackson 3 does not write them as timestamps by default.
     */
    @Bean
    JsonMapperBuilderCustomizer moneyAsString() {
        return builder -> builder.withConfigOverride(BigDecimal.class,
                o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
    }
}
