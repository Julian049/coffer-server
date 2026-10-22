package com.dev.julian09.financeserver.identity.dto;

import jakarta.validation.constraints.Size;

/** One proof of identity: the password, or a fresh Google ID token for accounts without one. */
public record DeleteAccountRequest(
        @Size(max = 200, message = "Contraseña no válida") String password,
        @Size(max = 4096, message = "Token de Google no válido") String idToken) {
}
