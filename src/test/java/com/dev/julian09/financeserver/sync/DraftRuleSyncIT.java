package com.dev.julian09.financeserver.sync;

import java.time.Instant;
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

/** The draftRule entity (V20, coffer-server#141) through push and pull against PostgreSQL 16. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DraftRuleSyncIT {

    private static final Instant T1 = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-21T11:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private String token;
    private final UUID account = UUID.randomUUID();
    private final UUID transport = UUID.randomUUID();
    private final UUID taxi = UUID.randomUUID();

    @BeforeEach
    void user() throws Exception {
        token = register();
    }

    @Test
    void aDraftRuleSyncsBothWays() throws Exception {
        UUID exact = UUID.randomUUID();
        UUID learned = UUID.randomUUID();
        Map<String, Object> byAmountAndAccount = rule("UBER RIDES", "uber rides", taxi);
        byAmountAndAccount.put("amount", "18500.0000");
        byAmountAndAccount.put("accountId", account.toString());
        Map<String, Object> byMerchant = rule("Rappi", "rappi", transport);
        byMerchant.put("level", "PROBABLE");
        byMerchant.put("origin", "LEARNED");
        byMerchant.put("learnedSince", T1.toString());
        byMerchant.put("position", 3);
        // The rules come first: the account and the categories they point to are ordered before them
        push(token, change("draftRule", exact, T1, null, byAmountAndAccount),
                change("draftRule", learned, T1, null, byMerchant),
                change("category", taxi, T1, null, category("Taxi", transport)),
                change("category", transport, T1, null, category("Transporte", null)),
                change("account", account, T1, null, account("Visa")))
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))))
                .andExpect(Contract.matchesComposed());

        JsonNode pulled = json.readTree(mvc.perform(get("/api/v1/sync/pull").param("since", "0").header("Authorization", "Bearer " + token))
                .andExpect(Contract.matchesComposed()).andReturn().getResponse().getContentAsString());
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        pulled.get("changes").forEach(c -> byId.put(c.get("id").asText(), c));
        assertThat(byId.get(exact.toString()).get("entity").asText()).isEqualTo("draftRule");
        JsonNode first = byId.get(exact.toString()).get("data");
        assertThat(first.get("merchant").asText()).isEqualTo("UBER RIDES");
        assertThat(first.get("merchantKey").asText()).isEqualTo("uber rides");
        assertThat(first.get("amount").asText()).isEqualTo("18500.0000");
        assertThat(first.get("accountId").asText()).isEqualTo(account.toString());
        assertThat(first.get("note").asText()).isEqualTo("Título");
        assertThat(first.get("categoryId").asText()).isEqualTo(taxi.toString());
        assertThat(first.get("level").asText()).isEqualTo("SURE");
        assertThat(first.get("origin").asText()).isEqualTo("USER");
        assertThat(first.get("enabled").asBoolean()).isTrue();
        assertThat(first.get("learnedSince").isNull()).isTrue();
        JsonNode second = byId.get(learned.toString()).get("data");
        assertThat(second.get("amount").isNull()).isTrue();
        assertThat(second.get("accountId").isNull()).isTrue();
        assertThat(second.get("level").asText()).isEqualTo("PROBABLE");
        assertThat(second.get("origin").asText()).isEqualTo("LEARNED");
        assertThat(Instant.parse(second.get("learnedSince").asText())).isEqualTo(T1);
        assertThat(second.get("position").asInt()).isEqualTo(3);

        // Turning one off, then removing the other, travel like any change
        byAmountAndAccount.put("enabled", false);
        push(token, change("draftRule", exact, T2, null, byAmountAndAccount), change("draftRule", learned, T2, T2, byMerchant))
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))));
        assertThat(jdbc.queryForObject("SELECT enabled FROM draft_rules WHERE id = ?", Boolean.class, exact)).isFalse();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM draft_rules WHERE id = ?", Boolean.class, learned)).isTrue();
    }

    @Test
    void aDraftRuleNeedsTheUsersOwnAccountAndCategory() throws Exception {
        UUID theirAccount = UUID.randomUUID();
        UUID theirCategory = UUID.randomUUID();
        String other = register();
        push(other, change("account", theirAccount, T1, null, account("Ajena")),
                change("category", theirCategory, T1, null, category("Ajena", null)))
                .andExpect(jsonPath("$.results[*].status").value(everyItem(is("APPLIED"))));
        push(token, change("category", transport, T1, null, category("Transporte", null)))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        Map<String, Object> toTheirAccount = rule("Uber", "uber", transport);
        toTheirAccount.put("accountId", theirAccount.toString());
        Map<String, Object> badLevel = rule("Uber", "uber", transport);
        badLevel.put("level", "MAYBE");
        push(token, change("draftRule", UUID.randomUUID(), T1, null, toTheirAccount),
                change("draftRule", UUID.randomUUID(), T1, null, rule("Uber", "uber", theirCategory)),
                change("draftRule", UUID.randomUUID(), T1, null, badLevel))
                .andExpect(jsonPath("$.results[0].code").value("FIN-REFERENCE"))
                .andExpect(jsonPath("$.results[1].code").value("FIN-REFERENCE"))
                .andExpect(jsonPath("$.results[2].code").value("FIN-VALIDATION"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM draft_rules WHERE category_id IN (?, ?)", Long.class, transport, theirCategory))
                .isZero();
    }

    private String register() throws Exception {
        String name = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String body = json.writeValueAsString(Map.of("displayName", "Reglas", "email", name + "@correo.com", "username", name,
                "password", "clave-segura-1", "deviceId", "device-0001"));
        return json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private ResultActions push(String bearer, Object... changes) throws Exception {
        String body = json.writeValueAsString(Map.of("deviceId", "device-0001", "changes", List.of(changes)));
        return mvc.perform(post("/api/v1/sync/push").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", "Bearer " + bearer));
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

    private static Map<String, Object> rule(String merchant, String merchantKey, UUID category) {
        return new LinkedHashMap<>(Map.of("merchant", merchant, "merchantKey", merchantKey, "note", "Título",
                "categoryId", category.toString(), "level", "SURE", "origin", "USER", "enabled", true, "position", 0));
    }

    private static Map<String, Object> account(String name) {
        return Map.of("name", name, "type", "DEBIT", "initialBalance", "0.0000", "includeInTotal", true, "active", true,
                "sortOrder", 0);
    }

    private static Map<String, Object> category(String name, UUID parent) {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("name", name, "kind", "EXPENSE", "isBase", false, "sortOrder", 0));
        data.put("parentId", parent == null ? null : parent.toString());
        return data;
    }
}
