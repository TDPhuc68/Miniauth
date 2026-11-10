package com.miniauth.core.service;

import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.model.AuthorizationCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý vòng đời của Authorization Code tạm thời (RFC 6749 Section 4.1.2)
 * và đối chiếu mã xác minh PKCE (Proof Key for Code Exchange - RFC 7636).
 */
@Slf4j
@Service
public class AuthorizationCodeService {

    private static final int CODE_TTL_SECONDS = 300; // 5 phút
    private final Map<String, AuthorizationCode> codeStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sinh mã code ngẫu nhiên, lưu vào bộ nhớ đệm và gắn thời hạn 5 phút
     */
    public AuthorizationCode createAuthorizationCode(
            String clientId,
            String userId,
            String username,
            String redirectUri,
            String scopes,
            String codeChallenge,
            String codeChallengeMethod
    ) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .userId(userId)
                .username(username)
                .redirectUri(redirectUri)
                .scopes(scopes)
                .codeChallenge(codeChallenge)
                .codeChallengeMethod(codeChallengeMethod != null ? codeChallengeMethod.toUpperCase() : "S256")
                .expiresAt(Instant.now().plus(CODE_TTL_SECONDS, ChronoUnit.SECONDS))
                .build();

        codeStore.put(code, authCode);
        log.info("Issued authorization code for user [{}] and client [{}]", username, clientId);
        return authCode;
    }

    /**
     * Tiêu thụ mã code (Single-use policy: Mã code bị xóa ngay lập tức sau 1 lần đọc)
     */
    public AuthorizationCode consumeCode(String code, String clientId, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new InvalidGrantException("Authorization code is required");
        }

        // Tiêu thụ ngay lập tức (xóa khỏi store)
        AuthorizationCode authCode = codeStore.remove(code);
        if (authCode == null) {
            log.warn("Attempt to use invalid or previously consumed authorization code");
            throw new InvalidGrantException("Invalid or already consumed authorization code");
        }

        if (authCode.isExpired()) {
            log.warn("Authorization code has expired for client: {}", clientId);
            throw new InvalidGrantException("Authorization code has expired");
        }

        if (!authCode.getClientId().equals(clientId)) {
            log.warn("Client mismatch for authorization code. Expected [{}], received [{}]", authCode.getClientId(), clientId);
            throw new InvalidGrantException("Client mismatch for authorization code");
        }

        if (!authCode.getRedirectUri().equals(redirectUri)) {
            log.warn("Redirect URI mismatch for authorization code. Expected [{}], received [{}]", authCode.getRedirectUri(), redirectUri);
            throw new InvalidGrantException("Redirect URI mismatch for authorization code");
        }

        return authCode;
    }

    /**
     * Xác thực PKCE (RFC 7636 Section 4.6):
     * So khớp SHA-256(code_verifier) với code_challenge ban đầu
     */
    public void validatePkce(AuthorizationCode authCode, String codeVerifier) {
        String codeChallenge = authCode.getCodeChallenge();
        if (codeChallenge == null || codeChallenge.isBlank()) {
            // Client không dùng PKCE
            return;
        }

        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new InvalidGrantException("code_verifier is required for PKCE-enabled authorization");
        }

        String method = authCode.getCodeChallengeMethod() != null ? authCode.getCodeChallengeMethod() : "S256";

        if ("S256".equalsIgnoreCase(method)) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String calculatedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

                if (!calculatedChallenge.equals(codeChallenge)) {
                    log.warn("PKCE verification failed: Calculated challenge does not match stored challenge");
                    throw new InvalidGrantException("PKCE verification failed: Invalid code_verifier");
                }
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not available", e);
            }
        } else if ("PLAIN".equalsIgnoreCase(method)) {
            if (!codeVerifier.equals(codeChallenge)) {
                log.warn("PKCE plain verification failed");
                throw new InvalidGrantException("PKCE verification failed: Invalid code_verifier");
            }
        } else {
            throw new InvalidGrantException("Unsupported code_challenge_method: " + method);
        }
    }
}
