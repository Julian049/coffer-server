package com.dev.julian09.financeserver.exception.domain;

import com.dev.julian09.financeserver.exception.core.ApiException;
import com.dev.julian09.financeserver.exception.core.ErrorCode;
import org.springframework.http.HttpStatus;

public class InternalErrorException extends ApiException {
    public InternalErrorException(String message) {
        super(message, ErrorCode.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public InternalErrorException() {
        super(ErrorCode.INTERNAL_ERROR.getDescription(),
                ErrorCode.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
