package com.dev.julian09.financeserver.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.dev.julian09.financeserver.identity.service.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;

import static com.dev.julian09.financeserver.identity.GoogleTokens.CLIENT_ID;
import static com.dev.julian09.financeserver.identity.GoogleTokens.KEY;
import static com.dev.julian09.financeserver.identity.GoogleTokens.OTHER_KEY;
import static com.dev.julian09.financeserver.identity.GoogleTokens.token;
import static org.assertj.core.api.Assertions.assertThat;

/** S11-QA03 (moved to v1.0.0): signature, issuer, audience, expiry and verified email of a Google ID token. */
class GoogleTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-10-19T15:00:00Z");
    private final GoogleTokenVerifier verifier = GoogleTokens.verifier(Clock.fixed(NOW, ZoneOffset.UTC));

    private String with(java.security.KeyPair key, String issuer, String audience, Instant expires, boolean verified) {
        return token(key, issuer, audience, expires, "google-sub-1", "ana@gmail.com", verified);
    }

    @Test
    void aValidTokenGivesTheGoogleIdentity() {
        var identity = verifier.verify(with(KEY, "https://accounts.google.com", CLIENT_ID, NOW.plusSeconds(600), true));
        assertThat(identity).hasValueSatisfying(i -> {
            assertThat(i.subject()).isEqualTo("google-sub-1");
            assertThat(i.email()).isEqualTo("ana@gmail.com");
            assertThat(i.name()).isEqualTo("Ana Gómez");
        });
        assertThat(verifier.verify(with(KEY, "accounts.google.com", CLIENT_ID, NOW.plusSeconds(600), true))).isPresent();
    }

    @Test
    void anotherKeyIssuerAudienceAnExpiredTokenOrAnUnverifiedEmailAreRefused() {
        Instant later = NOW.plusSeconds(600);
        assertThat(verifier.verify(with(OTHER_KEY, "https://accounts.google.com", CLIENT_ID, later, true))).isEmpty();
        assertThat(verifier.verify(with(KEY, "https://evil.example.com", CLIENT_ID, later, true))).isEmpty();
        assertThat(verifier.verify(with(KEY, "https://accounts.google.com", "other-app", later, true))).isEmpty();
        assertThat(verifier.verify(with(KEY, "https://accounts.google.com", CLIENT_ID, NOW.minusSeconds(600), true))).isEmpty();
        assertThat(verifier.verify(with(KEY, "https://accounts.google.com", CLIENT_ID, later, false))).isEmpty();
        assertThat(verifier.verify("no-es-un-token-de-google")).isEmpty();
    }

    @Test
    void withoutAClientIdEveryTokenIsRefused() {
        GoogleTokenVerifier off = new GoogleTokenVerifier("", Clock.systemUTC());
        assertThat(off.verify(GoogleTokens.valid("google-sub-1", "ana@gmail.com"))).isEmpty();
    }
}
