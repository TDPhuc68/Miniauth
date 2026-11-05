package com.miniauth.infrastructure.web;

import com.miniauth.core.exception.OAuth2Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Xử lý tập trung các lỗi ngoại lệ theo chuẩn OAuth 2.0 (RFC 6749 Section 5.2):
 * Trả về JSON: { "error": "...", "error_description": "..." }
 */
@Slf4j
@RestControllerAdvice
public class OAuth2ExceptionHandler {

    @ExceptionHandler(OAuth2Exception.class)
    public Mono<ResponseEntity<Map<String, String>>> handleOAuth2Exception(OAuth2Exception ex) {
        log.warn("OAuth2 error: [{}] {}", ex.getError(), ex.getErrorDescription());
        Map<String, String> body = Map.of(
                "error", ex.getError(),
                "error_description", ex.getErrorDescription()
        );
        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(body));
    }
}
