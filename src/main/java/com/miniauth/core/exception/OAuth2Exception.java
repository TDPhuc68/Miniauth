package com.miniauth.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OAuth2Exception extends RuntimeException {
    private final String error;
    private final String errorDescription;
    private final HttpStatus httpStatus;

    public OAuth2Exception(String error, String errorDescription, HttpStatus httpStatus) {
        super(errorDescription);
        this.error = error;
        this.errorDescription = errorDescription;
        this.httpStatus = httpStatus;
    }

    public static OAuth2Exception invalidRequest(String description) {
        return new OAuth2Exception("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuth2Exception unauthorizedClient(String description) {
        return new OAuth2Exception("unauthorized_client", description, HttpStatus.UNAUTHORIZED);
    }
}
