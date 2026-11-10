package com.miniauth.infrastructure.web;

import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.port.TokenService;
import com.miniauth.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller phục vụ endpoint hồ sơ người dùng chuẩn OpenID Connect (OIDC Core 1.0 Section 5.3):
 * GET /userinfo
 * Yêu cầu Bearer Token trong Header 'Authorization: Bearer <access_token>'.
 */
@Slf4j
@RestController
@RequestMapping("/userinfo")
@RequiredArgsConstructor
public class UserInfoController {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GetMapping
    public Mono<Map<String, Object>> getUserInfo(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return Mono.error(new OAuth2Exception("invalid_token", "Bearer access token is required", HttpStatus.UNAUTHORIZED));
        }

        String token = authHeader.substring(7).trim();

        return tokenService.validateAndExtractClaims(token)
                .flatMap(claims -> {
                    String userId = (String) claims.get("sub");
                    if (userId == null) {
                        return Mono.error(new OAuth2Exception("invalid_token", "Token missing subject claim", HttpStatus.UNAUTHORIZED));
                    }

                    return userRepository.findById(userId)
                            .map(user -> {
                                Map<String, Object> userInfo = new HashMap<>();
                                userInfo.put("sub", user.getId());
                                userInfo.put("preferred_username", user.getUsername());
                                if (user.getFullName() != null) {
                                    userInfo.put("name", user.getFullName());
                                }
                                if (user.getEmail() != null) {
                                    userInfo.put("email", user.getEmail());
                                    userInfo.put("email_verified", true);
                                }
                                return userInfo;
                            })
                            .defaultIfEmpty(claims);
                });
    }
}
