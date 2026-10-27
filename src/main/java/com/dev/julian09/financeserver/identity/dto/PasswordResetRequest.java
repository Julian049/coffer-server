package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Escribe tu correo") @Email(message = "Correo no válido") @Size(max = 255, message = "Máximo 255 caracteres") String email) {
}
