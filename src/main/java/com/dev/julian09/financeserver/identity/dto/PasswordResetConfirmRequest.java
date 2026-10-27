package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Escribe tu correo") @Email(message = "Correo no válido") @Size(max = 255, message = "Máximo 255 caracteres") String email,
        @NotNull(message = "Escribe el código") @Pattern(regexp = "^[0-9]{6}$", message = "El código tiene 6 dígitos") String code,
        @NotNull(message = "Escribe una contraseña") @Size(min = 8, max = 128, message = "Entre 8 y 128 caracteres") String newPassword) {
}
