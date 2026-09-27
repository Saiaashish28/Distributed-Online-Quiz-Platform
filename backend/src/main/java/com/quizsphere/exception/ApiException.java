package com.quizsphere.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Exception carrying an HTTP status and a message that is safe to show to clients. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Object details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException unprocessable(String message, Object details) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, details);
    }
}
