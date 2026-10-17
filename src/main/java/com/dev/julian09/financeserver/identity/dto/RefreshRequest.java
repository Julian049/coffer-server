package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotNull(message = "Falta el refresh token") @Size(min = 20, message = "Refresh token no válido") String refreshToken,
        @NotNull(message = "Falta el identificador del dispositivo") @Size(min = 8, max = 64, message = "Identificador de dispositivo no válido") String deviceId) {
}
