package com.dev.julian09.financeserver.sync;

import java.util.Map;
import java.util.UUID;

import com.dev.julian09.financeserver.Contract;
import com.dev.julian09.financeserver.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/sync/pull against PostgreSQL 16 (S3-QA01): order, pages, remaining, deletions, isolation, contract. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PullServiceIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private UUID userId;
    private final UUID account = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();
    private final UUID restaurants = UUID.randomUUID();

    @BeforeEach
    void userWithData() throws Exception {
        String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = json.writeValueAsString(Map.of("displayName", "Pull", "email", name + "@correo.com", "username", name,
                "password", "clave-segura-1", "deviceId", "device-0001"));
        JsonNode session = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        token = session.get("accessToken").asText();
        userId = UUID.fromString(session.get("user").get("id").asText());

        jdbc.update("INSERT INTO user_settings (id, user_id, created_at, updated_at) VALUES (?, ?, now(), now())", userId, userId);
        jdbc.update("INSERT INTO accounts (id, user_id, name, type, initial_balance, created_at, updated_at) "
                + "VALUES (?, ?, 'Bancolombia', 'DEBIT', 1250000, now(), now())", account, userId);
        jdbc.update("INSERT INTO categories (id, user_id, name, kind, is_base, created_at, updated_at) "
                + "VALUES (?, ?, 'Comida', 'EXPENSE', true, now(), now())", food, userId);
        jdbc.update("INSERT INTO categories (id, user_id, parent_id, name, kind, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'Restaurantes', 'EXPENSE', now(), now())", restaurants, userId, food);
        jdbc.update("INSERT INTO transactions (id, user_id, type, amount, occurred_at, account_id, category_id, note, created_at, updated_at) "
                + "VALUES (?, ?, 'EXPENSE', 28000, now(), ?, ?, 'Almuerzo', now(), now())", UUID.randomUUID(), userId, account, restaurants);
        jdbc.update("INSERT INTO transactions (id, user_id, type, amount, occurred_at, account_id, category_id, created_at, updated_at, deleted_at) "
                + "VALUES (?, ?, 'EXPENSE', 5000, now(), ?, ?, now(), now(), now())", UUID.randomUUID(), userId, account, food);
    }

    @Test
    void initialDownloadComesInPagesWithRemaining() throws Exception {
        JsonNode first = read(pull(0, 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(4))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.remaining").value(2))
                .andExpect(jsonPath("$.changes[0].entity").value("settings"))
                .andExpect(jsonPath("$.changes[1].data.initialBalance").value("1250000.0000"))
                .andExpect(Contract.matchesComposed()));
        long cursor = first.get("nextCursor").asLong();
        assertThat(first.get("changes").get(3).get("version").asLong()).isEqualTo(cursor);

        JsonNode second = read(pull(cursor, 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.remaining").doesNotExist())
                .andExpect(Contract.matchesComposed()));
        // Deletions travel too, so other devices remove them (REQ-SYN-03)
        assertThat(second.get("changes").get(1).get("deletedAt").isNull()).isFalse();
        assertThat(second.get("changes").get(0).get("data").get("amount").asText()).isEqualTo("28000.0000");
    }

    @Test
    void nothingNewReturnsTheSameCursor() throws Exception {
        long last = read(pull(0, 500)).get("nextCursor").asLong();
        pull(last, 500)
                .andExpect(jsonPath("$.changes.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value(last))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void neverReturnsAnotherUsersData() throws Exception {
        UUID stranger = UUID.randomUUID();
        String name = "x" + stranger.toString().replace("-", "").substring(0, 12);
        jdbc.update("INSERT INTO users (id, username, email, display_name, password_hash) VALUES (?, ?, ?, 'Otro', '!')",
                stranger, name, name + "@correo.com");
        jdbc.update("INSERT INTO tags (id, user_id, name, created_at, updated_at) VALUES (?, ?, 'Ajena', now(), now())",
                UUID.randomUUID(), stranger);

        JsonNode page = read(pull(0, 500));
        assertThat(page.get("changes").findValuesAsString("entity")).doesNotContain("tag");
        assertThat(page.get("changes")).hasSize(6);
    }

    @Test
    void invalidCursorOrLimitIsAValidationProblem() throws Exception {
        pull(-1, 10).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FIN-VALIDATION"));
        pull(0, 501).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sync/pull").param("since", "0")).andExpect(status().isUnauthorized());
    }

    private ResultActions pull(long since, int limit) throws Exception {
        return mvc.perform(get("/api/v1/sync/pull").param("since", String.valueOf(since)).param("limit", String.valueOf(limit))
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
