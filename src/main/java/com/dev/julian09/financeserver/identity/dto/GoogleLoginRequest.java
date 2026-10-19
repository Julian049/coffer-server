package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotNull(message = "Falta el token de Google") @Size(min = 20, max = 4096, message = "Token de Google no válido") String idToken,
        @NotNull(message = "Falta el identificador del dispositivo") @Size(min = 8, max = 64, message = "Identificador de dispositivo no válido") String deviceId) {
}
