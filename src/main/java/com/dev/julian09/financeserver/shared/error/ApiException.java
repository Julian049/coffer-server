package com.dev.julian09.financeserver.shared.error;

/** Domain error turned into an RFC 9457 problem by {@link ProblemDetailsAdvice}. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
