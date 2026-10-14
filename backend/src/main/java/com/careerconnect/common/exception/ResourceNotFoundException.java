package com.careerconnect.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.NOT_FOUND, errorCode);
    }
}
