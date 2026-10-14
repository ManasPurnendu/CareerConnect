package com.careerconnect.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseApiException {

    public ConflictException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}
