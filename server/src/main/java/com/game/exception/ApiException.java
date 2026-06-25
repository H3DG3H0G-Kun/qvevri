package com.game.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }

    // --- Factory methods for each error code in API.md ---

    public static ApiException invalidCredentials(String message) {
        return new ApiException("INVALID_CREDENTIALS", message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException sessionNotFound(String message) {
        return new ApiException("SESSION_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException sessionFull(String message) {
        return new ApiException("SESSION_FULL", message, HttpStatus.CONFLICT);
    }

    public static ApiException badRequest(String message) {
        return new ApiException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    /** Generic 404 — character/resource not found or not owned. */
    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    /** 403 Forbidden — authenticated but does not own the resource. */
    public static ApiException forbidden(String message) {
        return new ApiException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
