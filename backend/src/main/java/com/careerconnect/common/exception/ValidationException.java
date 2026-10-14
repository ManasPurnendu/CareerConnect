package com.careerconnect.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BaseApiException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
    }

    public ValidationException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}
