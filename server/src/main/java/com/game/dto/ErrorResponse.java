package com.game.dto;

/**
 * Error envelope: {"error": {"code": "...", "message": "..."}}
 */
public class ErrorResponse {

    private ErrorBody error;

    public ErrorResponse(String code, String message) {
        this.error = new ErrorBody(code, message);
    }

    public ErrorBody getError() {
        return error;
    }

    public static class ErrorBody {
        private String code;
        private String message;

        public ErrorBody(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
