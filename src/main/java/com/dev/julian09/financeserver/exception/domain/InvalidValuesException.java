package com.dev.julian09.financeserver.exception.domain;

import com.dev.julian09.financeserver.exception.core.ApiException;
import com.dev.julian09.financeserver.exception.core.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidValuesException extends ApiException {
    public InvalidValuesException(String message) {
        super(message, ErrorCode.INVALID_VALUES, HttpStatus.BAD_REQUEST);
    }
}
