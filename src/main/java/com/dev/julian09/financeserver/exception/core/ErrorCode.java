package com.dev.julian09.financeserver.exception.core;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_VALUES("ERR-300", "Los datos enviados son inválidos"),
    DUPLICATE_RECORD("ERR-400", "Existen datos duplicados"),
    INTERNAL_ERROR("ERR-500", "Error interno del servidor");

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
