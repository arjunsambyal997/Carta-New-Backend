package com.equigcc.service;

/** A business rule blocked the request. Mapped to HTTP 422. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
