package com.dev.julian09.financeserver.identity.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * finance.jwt.* (detailed-design §6.6.4). Keys are PEM (PKCS#8 private, X.509 public).
 * Empty keys mean "generate a temporary pair": only local and tests allow that; staging and prod require them.
 */
@ConfigurationProperties("finance.jwt")
public record JwtProperties(String privateKey, String publicKey, Duration accessTtl, Duration refreshTtl) {
}
