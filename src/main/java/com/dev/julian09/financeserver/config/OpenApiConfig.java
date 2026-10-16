package com.dev.julian09.financeserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generated docs at /v3/api-docs. The contract that rules is docs/04-api/openapi.yml. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI cofferOpenApi(ObjectProvider<BuildProperties> build) {
        String version = build.getIfAvailable() != null ? build.getIfAvailable().getVersion() : "dev";
        return new OpenAPI().info(new Info().title("Coffer Server API").version(version));
    }
}
