package com.hendisantika.springboot3jwtsecurity.exception;

/**
 * Raised when a registration targets an email address that already has an account.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account already exists for " + email);
    }
}
