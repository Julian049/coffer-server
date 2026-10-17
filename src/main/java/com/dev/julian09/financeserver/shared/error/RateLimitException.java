package com.dev.julian09.financeserver.shared.error;

import java.time.Duration;

/** 429 FIN-RATE-LIMIT; the advice adds the Retry-After header the contract requires. */
public class RateLimitException extends ApiException {

    private final Duration retryAfter;

    public RateLimitException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMIT, "Demasiados intentos. Espera un momento antes de volver a intentarlo.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
