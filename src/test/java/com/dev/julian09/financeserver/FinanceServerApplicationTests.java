package com.dev.julian09.financeserver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole application against PostgreSQL 16 (Testcontainers): Flyway, schema validation and the public endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceServerApplicationTests {

    // Copy of docs/04-api/openapi.yml; refresh it when the contract changes
    private static final OpenApiInteractionValidator CONTRACT =
            OpenApiInteractionValidator.createForSpecificationUrl("contract/openapi.yml").build();

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void healthMatchesTheContract() throws Exception {
        // The app asks for application/json; without it Actuator answers with its own vnd media type
        mvc.perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(openApi().isValid(CONTRACT));
    }

    @Test
    void infoShowsThePomVersion() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").isNotEmpty());
    }

    @Test
    void generatedApiDocsAreServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Coffer Server API"));
    }

    @Test
    void moneyIsAStringAndDatesAreIso() {
        assertThat(jsonMapper.writeValueAsString(new BigDecimal("28000.0000"))).isEqualTo("\"28000.0000\"");
        assertThat(jsonMapper.writeValueAsString(OffsetDateTime.of(2026, 10, 16, 8, 30, 0, 0, ZoneOffset.UTC)))
                .isEqualTo("\"2026-10-16T08:30:00Z\"");
    }
}
