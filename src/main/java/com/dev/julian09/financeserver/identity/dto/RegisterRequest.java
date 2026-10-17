package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Escribe tu nombre") @Size(max = 100, message = "Máximo 100 caracteres") String displayName,
        @NotBlank(message = "Escribe tu correo") @Email(message = "Correo no válido") @Size(max = 255, message = "Máximo 255 caracteres") String email,
        @NotNull(message = "Escribe un nombre de usuario")
        @Pattern(regexp = "^[a-z0-9._]{3,40}$", message = "Entre 3 y 40 caracteres: minúsculas, números, punto o guion bajo") String username,
        @NotNull(message = "Escribe una contraseña") @Size(min = 8, max = 128, message = "Entre 8 y 128 caracteres") String password,
        @NotNull(message = "Falta el identificador del dispositivo") @Size(min = 8, max = 64, message = "Identificador de dispositivo no válido") String deviceId) {
}
