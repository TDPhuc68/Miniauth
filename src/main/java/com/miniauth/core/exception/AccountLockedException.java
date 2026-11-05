package com.miniauth.core.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends OAuth2Exception {
    public AccountLockedException(String description) {
        super("account_locked", description, HttpStatus.LOCKED);
    }
}
