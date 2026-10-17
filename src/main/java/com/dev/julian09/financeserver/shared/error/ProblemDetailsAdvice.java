package com.dev.julian09.financeserver.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as {@code application/problem+json} with a {@code FIN-*} code (RFC 9457).
 * Spring's own exceptions go through {@link ResponseEntityExceptionHandler} and get their code here.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException ex) {
        return problem(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    ResponseEntity<Object> handleRateLimit(RateLimitException ex) {
        // Rounded up so a client never retries a fraction of a second too early
        long seconds = Math.max(1, (ex.retryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(ErrorCode.RATE_LIMIT.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(build(ErrorCode.RATE_LIMIT, ex.getMessage()));
    }

    // A unique constraint hit (e.g. a repeated local_id) is a conflict, not a server failure
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ErrorCode.CONFLICT, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        // RNF-07: no internal details in the response
        return problem(ErrorCode.INTERNAL, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail body = build(ErrorCode.VALIDATION, null);
        body.setProperty("errors", errors);
        return respond(ErrorCode.VALIDATION, body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = ErrorCode.forStatus(status.value());
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(status);
        problem.setType(URI.create(code.type()));
        problem.setProperty("code", code.code());
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static ResponseEntity<Object> problem(ErrorCode code, String detail) {
        return respond(code, build(code, detail));
    }

    private static ProblemDetail build(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(code.type()));
        problem.setTitle(code.title());
        problem.setProperty("code", code.code());
        return problem;
    }

    private static ResponseEntity<Object> respond(ErrorCode code, ProblemDetail body) {
        return ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
