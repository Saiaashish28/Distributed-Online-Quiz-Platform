package com.quizsphere.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts exceptions to a consistent JSON error body. Never leaks stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String error, String message,
                            Map<String, String> fieldErrors, Object details) {
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), null, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ErrorBody> handleBadInput(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> handleUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (max 5 MB)", null, null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ErrorBody> handleDenied(Exception ex) {
        return build(HttpStatus.FORBIDDEN, "Access denied", null, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorBody> handleOptimistic(OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "The record was changed by another request. Please retry.", null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", null, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Not found", null, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null, null);
    }

    private ResponseEntity<ErrorBody> build(HttpStatus status, String message, Map<String, String> fields, Object details) {
        return ResponseEntity.status(status)
                .body(new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(), message, fields, details));
    }
}
