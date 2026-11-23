package com.thiago.spacebooking.common;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("This email is already registered.");
    }
}
