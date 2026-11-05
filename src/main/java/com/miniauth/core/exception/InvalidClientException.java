package com.miniauth.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidClientException extends OAuth2Exception {
    public InvalidClientException(String description) {
        super("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }
}
