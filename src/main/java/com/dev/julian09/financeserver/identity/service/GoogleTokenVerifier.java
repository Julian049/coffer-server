package com.dev.julian09.financeserver.identity.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * ADR-0007, SEQ-01 §1.3: a Google ID token is valid when Google signed it (JWKS, cached by Nimbus), it was issued by
 * Google for our web client id ({@code GOOGLE_CLIENT_ID}), it has not expired and its email is verified. Without a
 * client id (local and tests) every token is refused.
 */
@Component
public class GoogleTokenVerifier {

    /** Who the token says the user is. */
    public record GoogleIdentity(String subject, String email, String name) {
    }

    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;

    @Autowired
    public GoogleTokenVerifier(@Value("${finance.google.client-id:}") String clientId, Clock clock) {
        this(clientId.isBlank() ? null : NimbusJwtDecoder.withJwkSetUri(JWKS).build(), clientId, clock);
    }

    /** With any decoder, so tests can sign tokens with their own key. */
    public GoogleTokenVerifier(NimbusJwtDecoder decoder, String clientId, Clock clock) {
        if (decoder != null) {
            decoder.setJwtValidator(validator(clientId, clock));
        }
        this.decoder = decoder;
    }

    public Optional<GoogleIdentity> verify(String idToken) {
        if (decoder == null) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) || jwt.getClaimAsString("email") == null) {
                return Optional.empty();
            }
            return Optional.of(new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name")));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    private static OAuth2TokenValidator<Jwt> validator(String clientId, Clock clock) {
        JwtTimestampValidator time = new JwtTimestampValidator();
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not issued by Google", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not for this app", null));
        return new DelegatingOAuth2TokenValidator<>(time, issuer, audience);
    }
}
