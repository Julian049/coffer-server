package com.dev.julian09.financeserver.identity;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import com.dev.julian09.financeserver.Contract;
import com.dev.julian09.financeserver.TestcontainersConfiguration;
import com.dev.julian09.financeserver.identity.service.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The 5 auth endpoints against PostgreSQL 16, each response checked against openapi.yml (S2-QA01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthControllerIT.LocalGoogle.class})
class AuthControllerIT {

    /** Google's JWKS replaced by a local key (GoogleTokens). */
    @TestConfiguration
    static class LocalGoogle {
        @Bean
        @Primary
        GoogleTokenVerifier localGoogleVerifier() {
            return GoogleTokens.verifier(Clock.systemUTC());
        }
    }

    private static final String DEVICE = "device-0001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void registerReturnsASessionAndMeShowsTheProfile() throws Exception {
        String username = newUsername();
        JsonNode session = register(username, username + "@correo.com", "clave-segura-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.currency").value("COP"))
                .andExpect(jsonPath("$.user.googleLinked").value(false))
                .andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString().transform(json::readTree);

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(username + "@correo.com"))
                .andExpect(Contract.matches());
    }

    @Test
    void googleSignsUpLinksByEmailAndSignsInAgain() throws Exception {
        String email = newUsername() + "@gmail.com";
        String subject = "google-" + UUID.randomUUID();
        JsonNode created = google(GoogleTokens.valid(subject, email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.googleLinked").value(true))
                .andExpect(jsonPath("$.user.displayName").value("Ana Gómez"))
                .andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString().transform(json::readTree);
        google(GoogleTokens.valid(subject, email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(created.get("user").get("id").asText()));

        // An account with a password gets linked by its email
        String username = newUsername();
        register(username, username + "@correo.com", "clave-segura-1").andExpect(status().isCreated());
        google(GoogleTokens.valid("google-" + UUID.randomUUID(), username.toUpperCase() + "@CORREO.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.googleLinked").value(true));
    }

    @Test
    void anInvalidGoogleTokenIsRefused() throws Exception {
        google(GoogleTokens.token(GoogleTokens.OTHER_KEY, "https://accounts.google.com", GoogleTokens.CLIENT_ID,
                java.time.Instant.now().plusSeconds(600), "google-x", "x@gmail.com", true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FIN-AUTH-INVALID"))
                .andExpect(Contract.matches());
    }

    private ResultActions google(String idToken) throws Exception {
        String body = json.writeValueAsString(Map.of("idToken", idToken, "deviceId", DEVICE));
        return mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void duplicateUsernameOrEmailIgnoresCase() throws Exception {
        String username = newUsername();
        register(username, username + "@correo.com", "clave-segura-1").andExpect(status().isCreated());

        register(username, "otro-" + username + "@correo.com", "clave-segura-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIN-CONFLICT"))
                .andExpect(Contract.matches());
        register(newUsername(), username.toUpperCase() + "@CORREO.COM", "clave-segura-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ese correo ya está registrado."));
    }

    @Test
    void invalidFieldsListTheirErrors() throws Exception {
        register("No Válido", "no-es-correo", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIN-VALIDATION"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void loginWorksWithUsernameOrEmailAndFailsGenerically() throws Exception {
        String username = newUsername();
        register(username, username + "@correo.com", "clave-segura-1").andExpect(status().isCreated());

        login(username, "clave-segura-1", "10.0.0.1").andExpect(status().isOk()).andExpect(Contract.matches());
        login(username.toUpperCase() + "@Correo.com", "clave-segura-1", "10.0.0.1").andExpect(status().isOk());

        String wrongPassword = login(username, "otra-clave", "10.0.0.1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FIN-AUTH-INVALID"))
                .andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nadie-" + username, "otra-clave", "10.0.0.1")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        // Same body whether the user exists or not: nothing to enumerate
        assertThat(unknownUser.replace("nadie-" + username, ""))
                .isEqualTo(wrongPassword.replace(username, ""));
    }

    @Test
    void sixthAttemptInAMinuteIsRateLimited() throws Exception {
        String username = newUsername();
        for (int i = 0; i < 5; i++) {
            login(username, "mala", "10.0.0.66").andExpect(status().isUnauthorized());
        }
        login(username, "mala", "10.0.0.66")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("FIN-RATE-LIMIT"))
                .andExpect(Contract.matches());
        // Another IP is a different window
        login(username, "mala", "10.0.0.67").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        String username = newUsername();
        JsonNode first = register(username, username + "@correo.com", "clave-segura-1")
                .andReturn().getResponse().getContentAsString().transform(json::readTree);
        String original = first.get("refreshToken").asText();

        JsonNode second = refresh(original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", not(original)))
                .andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString().transform(json::readTree);

        refresh(original)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FIN-AUTH-REVOKED"))
                .andExpect(Contract.matches());
        // The reuse revoked the whole family, including the token issued in between
        refresh(second.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        String username = newUsername();
        JsonNode session = register(username, username + "@correo.com", "clave-segura-1")
                .andReturn().getResponse().getContentAsString().transform(json::readTree);
        String bearer = "Bearer " + session.get("accessToken").asText();
        String body = refreshBody(session.get("refreshToken").asText());

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent())
                .andExpect(Contract.matches());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FIN-AUTH-INVALID"))
                .andExpect(Contract.matches());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private ResultActions register(String username, String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "displayName", "Persona de prueba", "email", email, "username", username, "password", password, "deviceId", DEVICE));
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String identifier, String password, String ip) throws Exception {
        String body = json.writeValueAsString(Map.of("identifier", identifier, "password", password, "deviceId", DEVICE));
        MockHttpServletRequestBuilder request = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        return mvc.perform(request);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)));
    }

    private String refreshBody(String refreshToken) {
        return json.writeValueAsString(Map.of("refreshToken", refreshToken, "deviceId", DEVICE));
    }

    private static String newUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
