package com.equigcc.common;

/** Credentials or second factor were wrong. Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
