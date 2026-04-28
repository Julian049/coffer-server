package com.dev.julian09.financeserver.exception.domain;

import com.dev.julian09.financeserver.exception.core.ApiException;
import com.dev.julian09.financeserver.exception.core.ErrorCode;
import org.springframework.http.HttpStatus;

public class DuplicateRecordException extends ApiException {
    public DuplicateRecordException(String message) {
        super(message + " ya existe.", ErrorCode.DUPLICATE_RECORD, HttpStatus.CONFLICT);
    }
}
