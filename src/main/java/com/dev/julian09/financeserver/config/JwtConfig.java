package com.dev.julian09.financeserver.config;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.dev.julian09.financeserver.identity.service.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256 signing and validation with our own key pair (ADR-0007). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    KeyPair jwtKeyPair(JwtProperties props) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (isBlank(props.privateKey()) || isBlank(props.publicKey())) {
            log.warn("JWT keys not configured: using a temporary pair (tokens die with this process). Never in staging or prod.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        return new KeyPair(
                rsa.generatePublic(new X509EncodedKeySpec(pemBody(props.publicKey()))),
                rsa.generatePrivate(new PKCS8EncodedKeySpec(pemBody(props.privateKey()))));
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair keys) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey((RSAPrivateKey) keys.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair keys) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    }

    // Accepts real newlines or the literal "\n" some dashboards store in single-line variables
    static byte[] pemBody(String pem) {
        String body = pem.replace("\\n", "\n").replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
