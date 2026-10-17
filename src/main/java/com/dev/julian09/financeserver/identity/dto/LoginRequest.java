package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull(message = "Escribe tu usuario o correo") @Size(min = 3, max = 255, message = "Usuario o correo no válido") String identifier,
        @NotNull(message = "Escribe tu contraseña") @Size(min = 1, max = 128, message = "Escribe tu contraseña") String password,
        @NotNull(message = "Falta el identificador del dispositivo") @Size(min = 8, max = 64, message = "Identificador de dispositivo no válido") String deviceId) {
}
