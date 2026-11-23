package com.thiago.spacebooking.common;

/** The request is well-formed but violates a business rule (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    private final String rule;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    /** @param rule identifier such as "R6", exposed to API clients as the {@code rule} property */
    public BusinessRuleException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
