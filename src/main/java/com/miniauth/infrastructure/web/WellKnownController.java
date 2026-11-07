package com.miniauth.infrastructure.web;

import com.miniauth.core.port.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Controller phục vụ các endpoint công khai theo chuẩn OpenID Connect Discovery:
 * 1. /.well-known/jwks.json: Danh sách Public Keys để Resource Server & Client tự verify JWT.
 * 2. /.well-known/openid-configuration: Metadata chuẩn OIDC giúp các Client SDK tự động cấu hình.
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
public class WellKnownController {

    private final TokenService tokenService;

    @Value("${miniauth.issuer:http://localhost:8080}")
    private String issuer;

    @GetMapping("/jwks.json")
    public Mono<Map<String, Object>> getJwks() {
        return Mono.just(tokenService.getPublicJwkSet());
    }

    @GetMapping("/openid-configuration")
    public Mono<Map<String, Object>> getOpenIdConfiguration() {
        Map<String, Object> config = Map.ofEntries(
                Map.entry("issuer", issuer),
                Map.entry("authorization_endpoint", issuer + "/oauth/authorize"),
                Map.entry("token_endpoint", issuer + "/oauth/token"),
                Map.entry("userinfo_endpoint", issuer + "/userinfo"),
                Map.entry("jwks_uri", issuer + "/.well-known/jwks.json"),
                Map.entry("response_types_supported", List.of("code")),
                Map.entry("subject_types_supported", List.of("public")),
                Map.entry("id_token_signing_alg_values_supported", List.of("RS256")),
                Map.entry("scopes_supported", List.of("openid", "profile", "email")),
                Map.entry("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post")),
                Map.entry("code_challenge_methods_supported", List.of("S256", "plain"))
        );
        return Mono.just(config);
    }
}
