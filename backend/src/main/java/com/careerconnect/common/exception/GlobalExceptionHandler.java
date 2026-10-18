package com.careerconnect.common.exception;

import com.careerconnect.common.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<ApiErrorResponse> handleBaseApiException(BaseApiException ex) {
        log.warn("Application exception [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of(ex.getMessage(), ex.getErrorCode().getCode());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        log.warn("Bean Validation failed: {}", detailMessage);
        ApiErrorResponse error = ApiErrorResponse.of(detailMessage, ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON payload: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("Malformed request body.", ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid");
        log.warn("Parameter type mismatch: {}", message);
        ApiErrorResponse error = ApiErrorResponse.of(message, ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKeyException(DuplicateKeyException ex) {
        log.warn("Database duplicate key constraint violated: {}", ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        ErrorCode code = ErrorCode.VALIDATION_FAILED;

        if (msg.contains("uk_users_email") || msg.contains("email")) {
            code = ErrorCode.EMAIL_ALREADY_EXISTS;
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("An account with this email address already exists.", code.getCode()));
        } else if (msg.contains("uk_application_candidate_job") || msg.contains("application")) {
            code = ErrorCode.DUPLICATE_APPLICATION;
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("You have already applied to this job.", code.getCode()));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("Resource conflict detected.", code.getCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("You do not have permission to access this resource.", ErrorCode.FORBIDDEN.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("Authentication is required to access this resource.", ErrorCode.UNAUTHENTICATED.getCode());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getResourcePath());
        ApiErrorResponse error = ApiErrorResponse.of("Requested resource not found.", ErrorCode.RESOURCE_NOT_FOUND.getCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("File size exceeds maximum allowed size.", ErrorCode.RESUME_TOO_LARGE.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(Exception ex) {
        log.warn("Missing request part or parameter: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of(ex.getMessage(), ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException ex) {
        log.warn("Multipart processing failed: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("Invalid multipart request: " + ex.getMessage(), ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        ApiErrorResponse error = ApiErrorResponse.of("HTTP method not supported for this endpoint: " + ex.getMethod(), ErrorCode.VALIDATION_FAILED.getCode());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ApiErrorResponse error = ApiErrorResponse.of("An unexpected error occurred. Please try again later.", ErrorCode.INTERNAL_ERROR.getCode());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private String formatFieldError(FieldError error) {
        return String.format("%s: %s", error.getField(), error.getDefaultMessage());
    }
}
