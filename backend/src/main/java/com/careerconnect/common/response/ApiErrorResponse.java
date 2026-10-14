package com.careerconnect.common.response;

public class ApiErrorResponse {

    private final boolean success = false;
    private final Object data = null;
    private String message;
    private String errorCode;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String message, String errorCode) {
        this.message = message;
        this.errorCode = errorCode;
    }

    public static ApiErrorResponse of(String message, String errorCode) {
        return new ApiErrorResponse(message, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
