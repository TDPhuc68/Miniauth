package com.miniauth.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidGrantException extends OAuth2Exception {
    public InvalidGrantException(String description) {
        super("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }
}
