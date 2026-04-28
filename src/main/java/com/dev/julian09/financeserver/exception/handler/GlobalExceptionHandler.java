package com.dev.julian09.financeserver.exception.handler;

import com.dev.julian09.financeserver.exception.core.ApiException;
import com.dev.julian09.financeserver.exception.domain.DuplicateRecordException;
import com.dev.julian09.financeserver.exception.domain.InternalErrorException;
import com.dev.julian09.financeserver.exception.domain.InvalidValuesException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Handlers específicos ──────────────────────────────────────────────────

    @ExceptionHandler(DuplicateRecordException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateRecord(DuplicateRecordException ex) {
        log.warn("DuplicateRecordException: {}", ex.getMessage());
        return buildResponse(ex);
    }

    @ExceptionHandler(InvalidValuesException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidValues(InvalidValuesException ex) {
        log.warn("InvalidValuesException: {}", ex.getMessage());
        return buildResponse(ex);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        log.error("ApiException no manejada: {}", ex.getMessage(), ex);

        InternalErrorException internalEx = new InternalErrorException(ex.getMessage());
        return buildResponse(internalEx);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error inesperado: {}", ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "INTERNAL_SERVER_ERROR");
        body.put("message", "Ocurrió un error inesperado.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(ApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getErrorCode().name());
        body.put("message", ex.getMessage());

        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}