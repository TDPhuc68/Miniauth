package com.miniauth.infrastructure.web;

import com.miniauth.core.model.TokenResponse;
import com.miniauth.core.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Controller phục vụ endpoint cấp phát Token (RFC 6749 Section 3.2):
 * POST /oauth/token
 * Hỗ trợ xác thực Client qua HTTP Basic Auth hoặc Form Data,
 * và xác minh mã kiểm tra PKCE (code_verifier).
 */
@Slf4j
@RestController
@RequestMapping("/oauth/token")
@RequiredArgsConstructor
public class TokenController {

    private final OAuth2Service oAuth2Service;

    @PostMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Mono<TokenResponse> tokenEndpoint(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        return exchange.getFormData().flatMap(formData -> {
            String grantType = formData.getFirst("grant_type");
            String code = formData.getFirst("code");
            String redirectUri = formData.getFirst("redirect_uri");
            String clientId = formData.getFirst("client_id");
            String clientSecret = formData.getFirst("client_secret");
            String codeVerifier = formData.getFirst("code_verifier");

            // Nếu Client gửi credentials qua Header Basic Auth: 'Basic <base64(client_id:client_secret)>'
            if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
                try {
                    String base64Credentials = authHeader.substring(6).trim();
                    byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
                    String credentials = new String(credDecoded, StandardCharsets.UTF_8);
                    final String[] values = credentials.split(":", 2);
                    if (values.length == 2) {
                        clientId = values[0];
                        clientSecret = values[1];
                    }
                } catch (Exception e) {
                    log.warn("Malformed Basic authorization header: {}", e.getMessage());
                }
            }

            return oAuth2Service.exchangeCodeForToken(
                    grantType,
                    code,
                    redirectUri,
                    clientId,
                    clientSecret,
                    codeVerifier
            );
        });
    }
}
