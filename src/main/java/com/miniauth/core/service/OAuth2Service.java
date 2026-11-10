package com.miniauth.core.service;

import com.miniauth.core.exception.InvalidClientException;
import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.model.AuthorizationCode;
import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.TokenResponse;
import com.miniauth.core.model.User;
import com.miniauth.core.port.ClientRepository;
import com.miniauth.core.port.TokenService;
import com.miniauth.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service nghiệp vụ xử lý luồng ủy quyền OAuth 2.0 và OpenID Connect (OIDC).
 * Điều phối giữa ClientRepository, UserRepository, TokenService và PKCE Validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuthorizationCodeService authorizationCodeService;
    private final PasswordEncoder passwordEncoder;

    @Value("${miniauth.jwt.expiration-seconds:3600}")
    private long tokenExpirationSeconds;

    /**
     * Xác thực thông tin tham số gửi lên trong request GET /oauth/authorize
     */
    public Mono<ClientApplication> validateAuthorizeRequest(
            String clientId,
            String redirectUri,
            String responseType
    ) {
        if (clientId == null || clientId.isBlank()) {
            return Mono.error(OAuth2Exception.invalidRequest("Missing client_id"));
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return Mono.error(OAuth2Exception.invalidRequest("Missing redirect_uri"));
        }
        if (!"code".equalsIgnoreCase(responseType)) {
            return Mono.error(OAuth2Exception.invalidRequest("Unsupported response_type. Only 'code' is supported"));
        }

        return clientRepository.findByClientId(clientId)
                .switchIfEmpty(Mono.error(new InvalidClientException("Unknown client_id: " + clientId)))
                .flatMap(client -> {
                    if (!client.isRedirectUriAllowed(redirectUri)) {
                        log.warn("Unauthorized redirect_uri [{}] for client [{}]", redirectUri, clientId);
                        return Mono.error(OAuth2Exception.invalidRequest("Unauthorized redirect_uri for this client"));
                    }
                    return Mono.just(client);
                });
    }

    /**
     * Cấp phát Authorization Code sau khi người dùng đồng ý cấp quyền (Consent)
     */
    public String issueAuthorizationCode(
            User user,
            ClientApplication client,
            String redirectUri,
            String scopes,
            String codeChallenge,
            String codeChallengeMethod
    ) {
        AuthorizationCode code = authorizationCodeService.createAuthorizationCode(
                client.getClientId(),
                user.getId(),
                user.getUsername(),
                redirectUri,
                scopes != null ? scopes : "openid",
                codeChallenge,
                codeChallengeMethod
        );
        return code.getCode();
    }

    /**
     * Trao đổi Authorization Code lấy Token tại POST /oauth/token
     */
    public Mono<TokenResponse> exchangeCodeForToken(
            String grantType,
            String code,
            String redirectUri,
            String clientId,
            String clientSecret,
            String codeVerifier
    ) {
        if (!"authorization_code".equalsIgnoreCase(grantType)) {
            return Mono.error(new InvalidGrantException("Unsupported grant_type: " + grantType));
        }

        return clientRepository.findByClientId(clientId)
                .switchIfEmpty(Mono.error(new InvalidClientException("Client not found: " + clientId)))
                .flatMap(client -> {
                    // Nếu client có secret được cấu hình và gửi secret lên, kiểm tra tính hợp lệ
                    if (clientSecret != null && !clientSecret.isBlank()) {
                        if (!passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {
                            return Mono.error(new InvalidClientException("Invalid client_secret"));
                        }
                    }

                    // 1. Tiêu thụ mã code (xóa ngay lập tức, kiểm tra hạn dùng và client_id/redirect_uri)
                    AuthorizationCode authCode = authorizationCodeService.consumeCode(code, clientId, redirectUri);

                    // 2. Xác thực PKCE nếu có code_challenge ban đầu
                    authorizationCodeService.validatePkce(authCode, codeVerifier);

                    // 3. Lấy thông tin User để sinh Token
                    return userRepository.findById(authCode.getUserId())
                            .switchIfEmpty(Mono.error(new InvalidGrantException("User no longer exists")))
                            .map(user -> {
                                String scope = authCode.getScopes() != null ? authCode.getScopes() : "openid";
                                String accessToken = tokenService.generateAccessToken(user, client, scope);

                                String idToken = null;
                                if (scope.contains("openid")) {
                                    idToken = tokenService.generateIdToken(user, client, null);
                                }

                                return TokenResponse.builder()
                                        .accessToken(accessToken)
                                        .tokenType("Bearer")
                                        .expiresIn(tokenExpirationSeconds)
                                        .idToken(idToken)
                                        .scope(scope)
                                        .build();
                            });
                });
    }
}
