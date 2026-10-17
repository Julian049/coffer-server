package com.dev.julian09.financeserver.sync;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/v1/sync/push against PostgreSQL 16 (S3-QA01, REQ-SYN-01 to 04, RNF-17). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PushServiceIT {

    private static final Instant T1 = Instant.parse("2026-10-16T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T11:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private UUID userId;
    private final UUID account = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();
    private final UUID restaurants = UUID.randomUUID();
    private final UUID salary = UUID.randomUUID();
    private final UUID trip = UUID.randomUUID();

    @BeforeEach
    void user() throws Exception {
        String name = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = json.writeValueAsString(Map.of("displayName", "Push", "email", name + "@correo.com", "username", name,
                "password", "clave-segura-1", "deviceId", "device-0001"));
        JsonNode session = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        token = session.get("accessToken").asText();
        userId = UUID.fromString(session.get("user").get("id").asText());
    }

    @Test
    void aWholeBatchIsAppliedInDependencyOrderAndCanBePulled() throws Exception {
        UUID expense = UUID.randomUUID();
        // Deliberately out of order: the transaction comes before what it references
        push(
                change("transaction", expense, T1, null, expense(28000, restaurants)),
                change("category", restaurants, T1, null, category("Restaurantes", "EXPENSE", food)),
                change("tag", trip, T1, null, Map.of("name", "Viaje", "pinned", false)),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("account", account, T1, null, account("Bancolombia")),
                change("settings", userId, T1, null, settings()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(6))
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(jsonPath("$.results[0].id").value(expense.toString()))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString());
        assertThat(pulled.get("changes")).hasSize(6);
        assertThat(pulled.get("changes").findValuesAsString("entity")).containsExactly(
                "settings", "account", "category", "category", "tag", "transaction");
    }

    @Test
    void resendingTheSameBatchCreatesNoDuplicates() throws Exception {
        Object[] batch = {
            change("account", account, T1, null, account("Bancolombia")),
            change("category", food, T1, null, category("Comida", "EXPENSE", null)),
            change("transaction", UUID.randomUUID(), T1, null, expense(5000, food))};
        push(batch);

        push(batch)
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("STALE"))))
                .andExpect(jsonPath("$.results[0].version").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void theLatestChangeWins() throws Exception {
        push(change("account", account, T2, null, account("Nueva")));

        push(change("account", account, T1, null, account("Vieja")))
                .andExpect(jsonPath("$.results[0].status").value("STALE"));
        push(change("account", account, T2.plusSeconds(1), null, account("Más nueva")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].version").isNumber());
        assertThat(jdbc.queryForObject("SELECT name FROM accounts WHERE id = ?", String.class, account)).isEqualTo("Más nueva");
    }

    @Test
    void deletionsAreStoredAndTravel() throws Exception {
        push(change("account", account, T1, null, account("Bancolombia")));
        push(change("account", account, T2, T2, account("Bancolombia")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM accounts WHERE id = ?", Boolean.class, account)).isTrue();
    }

    @Test
    void anotherUsersIdIsRejectedWithoutTouchingIt() throws Exception {
        UUID stranger = UUID.randomUUID();
        String name = "x" + stranger.toString().replace("-", "").substring(0, 12);
        jdbc.update("INSERT INTO users (id, username, email, display_name, password_hash) VALUES (?, ?, ?, 'Otro', '!')",
                stranger, name, name + "@correo.com");
        jdbc.update("INSERT INTO tags (id, user_id, name, created_at, updated_at) VALUES (?, ?, 'Ajena', ?, ?)",
                trip, stranger, Timestamp.from(T1), Timestamp.from(T1));

        push(change("tag", trip, T2, null, Map.of("name", "Mía", "pinned", true)))
                .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[0].code").value("FIN-NOT-OWNER"))
                .andExpect(Contract.matchesComposed());
        assertThat(jdbc.queryForObject("SELECT name FROM tags WHERE id = ?", String.class, trip)).isEqualTo("Ajena");
    }

    @Test
    void oneBadChangeDoesNotSinkTheBatch() throws Exception {
        UUID orphan = UUID.randomUUID();
        UUID transferWithoutDestination = UUID.randomUUID();
        UUID wrongKind = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        Map<String, Object> transfer = new LinkedHashMap<>(expense(1000, null));
        transfer.put("type", "TRANSFER");
        push(
                change("account", account, T1, null, account("Bancolombia")),
                change("category", food, T1, null, category("Comida", "EXPENSE", null)),
                change("category", salary, T1, null, category("Salario", "INCOME", null)),
                change("transaction", orphan, T1, null, withAccount(expense(1000, food), UUID.randomUUID())),
                change("transaction", transferWithoutDestination, T1, null, transfer),
                change("transaction", wrongKind, T1, null, expense(1000, salary)),
                change("transaction", ok, T1, null, expense(1000, food)),
                change("loanDetails", UUID.randomUUID(), T1, null, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[3].code").value("FIN-REFERENCE"))
                .andExpect(jsonPath("$.results[4].code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.results[5].detail").value("La categoría no corresponde al tipo de movimiento"))
                .andExpect(jsonPath("$.results[6].status").value("APPLIED"))
                // An entity without a handler yet; its empty data breaks the contract on purpose, so no contract check here
                .andExpect(jsonPath("$.results[7].status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void aDuplicateNameRollsBackOnlyThatChange() throws Exception {
        push(
                change("tag", UUID.randomUUID(), T1, null, Map.of("name", "Viaje", "pinned", false)),
                change("tag", UUID.randomUUID(), T1, null, Map.of("name", "viaje", "pinned", false)),
                change("account", account, T1, null, account("Bancolombia")))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].detail").value("Ya existe un registro con ese nombre"))
                .andExpect(jsonPath("$.results[2].status").value("APPLIED"));
    }

    @Test
    void invalidAmountsAndOversizedBatchesAreRefused() throws Exception {
        push(change("transaction", UUID.randomUUID(), T1, null, expense(0, food)))
                .andExpect(jsonPath("$.results[0].code").value("FIN-VALIDATION"));

        Object[] tooMany = new Object[201];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = change("tag", UUID.randomUUID(), T1, null, Map.of("name", "t" + i, "pinned", false));
        }
        push(tooMany).andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("FIN-VALIDATION"));
        push().andExpect(status().isBadRequest());
    }

    private ResultActions push(Object... changes) throws Exception {
        String body = json.writeValueAsString(Map.of("deviceId", "device-0001", "changes", List.of(changes)));
        return mvc.perform(post("/api/v1/sync/push").contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", bearer()));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private static Map<String, Object> change(String entity, UUID id, Instant updatedAt, Instant deletedAt, Map<String, Object> data) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", entity);
        change.put("id", id.toString());
        change.put("createdAt", T1.toString());
        change.put("updatedAt", updatedAt.toString());
        change.put("deletedAt", deletedAt == null ? null : deletedAt.toString());
        change.put("data", data);
        return change;
    }

    private Map<String, Object> account(String name) {
        return Map.of("name", name, "type", "DEBIT", "initialBalance", "1250000.0000", "includeInTotal", true, "active", true,
                "sortOrder", 0);
    }

    private static Map<String, Object> category(String name, String kind, UUID parent) {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("name", name, "kind", kind, "isBase", true, "sortOrder", 0));
        data.put("parentId", parent == null ? null : parent.toString());
        return data;
    }

    private Map<String, Object> expense(long pesos, UUID category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "EXPENSE");
        data.put("amount", pesos + ".0000");
        data.put("occurredAt", T1.toString());
        data.put("accountId", account.toString());
        data.put("categoryId", category == null ? null : category.toString());
        data.put("installments", 1);
        return data;
    }

    private static Map<String, Object> withAccount(Map<String, Object> data, UUID accountId) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("accountId", accountId.toString());
        return copy;
    }

    private Map<String, Object> settings() {
        return Map.of("reminderLeadDays", 5, "notificationsEnabled", true, "homeBalanceView", "AVAILABLE", "amountDecimals", 0,
                "homeCards", new ArrayList<>());
    }
}
