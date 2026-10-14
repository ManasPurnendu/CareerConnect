package com.careerconnect.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseApiException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
    }

    public UnauthorizedException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
    }
}
