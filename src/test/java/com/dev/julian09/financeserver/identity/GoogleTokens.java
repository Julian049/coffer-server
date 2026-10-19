package com.dev.julian09.financeserver.identity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import com.dev.julian09.financeserver.identity.service.GoogleTokenVerifier;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Google ID tokens signed with a local key, and a verifier that trusts that key instead of Google's JWKS. */
public final class GoogleTokens {

    public static final String CLIENT_ID = "web-client.apps.googleusercontent.com";
    public static final KeyPair KEY = newKey();
    public static final KeyPair OTHER_KEY = newKey();

    private GoogleTokens() {
    }

    public static GoogleTokenVerifier verifier(Clock clock) {
        return new GoogleTokenVerifier(NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build(), CLIENT_ID, clock);
    }

    public static String token(KeyPair key, String issuer, String audience, Instant expires, String subject, String email,
                               boolean emailVerified) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(expires.minusSeconds(3600)))
                .expirationTime(Date.from(expires))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", "Ana Gómez")
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String valid(String subject, String email) {
        return token(KEY, "https://accounts.google.com", CLIENT_ID, Instant.now().plusSeconds(3600), subject, email, true);
    }

    private static KeyPair newKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
