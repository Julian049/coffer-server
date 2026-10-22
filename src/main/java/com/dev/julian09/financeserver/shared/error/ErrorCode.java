package com.dev.julian09.financeserver.shared.error;

import org.springframework.http.HttpStatus;

/** Codes of the {@code code} member of every problem (openapi.yml, schema Problem). */
public enum ErrorCode {
    VALIDATION("FIN-VALIDATION", HttpStatus.BAD_REQUEST, "validation", "Datos inválidos"),
    AUTH_INVALID("FIN-AUTH-INVALID", HttpStatus.UNAUTHORIZED, "auth-invalid", "Credenciales inválidas"),
    AUTH_REVOKED("FIN-AUTH-REVOKED", HttpStatus.UNAUTHORIZED, "auth-revoked", "Sesión revocada"),
    // The session is fine but the extra proof (password, Google token) is not: the app must not treat it as an expired session
    CONFIRMATION("FIN-CONFIRMATION", HttpStatus.FORBIDDEN, "confirmation", "Confirmación incorrecta"),
    CONFLICT("FIN-CONFLICT", HttpStatus.CONFLICT, "conflict", "El recurso ya existe"),
    RATE_LIMIT("FIN-RATE-LIMIT", HttpStatus.TOO_MANY_REQUESTS, "rate-limit", "Demasiados intentos"),
    INTERNAL("FIN-INTERNAL", HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Error interno"),
    // openapi.yml BatchTooLarge: 413 with the validation code
    BATCH_TOO_LARGE("FIN-VALIDATION", HttpStatus.CONTENT_TOO_LARGE, "validation", "Lote demasiado grande");

    private static final String TYPE_BASE = "https://finance.app/problems/";

    private final String code;
    private final HttpStatus status;
    private final String slug;
    private final String title;

    ErrorCode(String code, HttpStatus status, String slug, String title) {
        this.code = code;
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public String code() { return code; }

    public HttpStatus status() { return status; }

    public String type() { return TYPE_BASE + slug; }

    public String title() { return title; }

    /** Code for errors raised by Spring itself (unknown route, bad JSON...), which have no domain code. */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 401 -> AUTH_INVALID;
            case 409 -> CONFLICT;
            case 429 -> RATE_LIMIT;
            default -> status >= 500 ? INTERNAL : VALIDATION;
        };
    }
}
