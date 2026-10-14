package com.careerconnect.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BaseApiException {

    public BusinessRuleException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }
}
