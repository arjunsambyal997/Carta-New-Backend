package com.equigcc.service;

/** A business rule blocked the issuance (e.g. authorized share limit, currency mismatch). */
public class IssuanceRejectedException extends BusinessRuleException {
    public IssuanceRejectedException(String message) {
        super(message);
    }
}
