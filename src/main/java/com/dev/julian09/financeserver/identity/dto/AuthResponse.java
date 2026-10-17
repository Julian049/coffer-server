package com.dev.julian09.financeserver.identity.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken, UserDto user) {
}
