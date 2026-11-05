package com.miniauth.infrastructure.crypto;

import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.User;
import com.miniauth.core.port.TokenService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Hiện thực TokenService sử dụng thư viện Nimbus JOSE+JWT.
 * Đảm bảo token được ký theo chuẩn RFC 7519 và RS256.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NimbusJwtTokenService implements TokenService {

    private final KeyManager keyManager;

    @Value("${miniauth.issuer:http://localhost:8080}")
    private String issuer;

    @Value("${miniauth.jwt.expiration-seconds:3600}")
    private long expirationSeconds;

    @Override
    public String generateAccessToken(User user, ClientApplication client, String scope) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyManager.getKeyId())
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.getId())
                .audience(client.getClientId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                .claim("scope", scope != null ? scope : "openid");

        if (user.getEmail() != null) {
            claimsBuilder.claim("email", user.getEmail());
        }

        try {
            SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
            signedJWT.sign(keyManager.getSigner());
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to sign access token for user: {}", user.getUsername(), e);
            throw new OAuth2Exception("server_error", "Failed to issue access token", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String generateIdToken(User user, ClientApplication client, String nonce) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationSeconds, ChronoUnit.SECONDS);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyManager.getKeyId())
                .type(JOSEObjectType.JWT)
                .build();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.getId())
                .audience(client.getClientId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("auth_time", now.getEpochSecond())
                .claim("preferred_username", user.getUsername());

        if (user.getFullName() != null) {
            claimsBuilder.claim("name", user.getFullName());
        }
        if (user.getEmail() != null) {
            claimsBuilder.claim("email", user.getEmail());
        }
        if (nonce != null && !nonce.isBlank()) {
            claimsBuilder.claim("nonce", nonce);
        }

        try {
            SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
            signedJWT.sign(keyManager.getSigner());
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to sign ID token for user: {}", user.getUsername(), e);
            throw new OAuth2Exception("server_error", "Failed to issue ID token", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Mono<Map<String, Object>> validateAndExtractClaims(String token) {
        return Mono.fromCallable(() -> {
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);

                // 1. Xác minh chữ ký bằng Public Key
                if (!signedJWT.verify(keyManager.createVerifier())) {
                    throw new OAuth2Exception("invalid_token", "JWT signature verification failed", HttpStatus.UNAUTHORIZED);
                }

                // 2. Kiểm tra thời hạn hết hạn (Expiration time)
                Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
                if (exp != null && new Date().after(exp)) {
                    throw new OAuth2Exception("invalid_token", "JWT token has expired", HttpStatus.UNAUTHORIZED);
                }

                // 3. Kiểm tra Issuer
                String tokenIssuer = signedJWT.getJWTClaimsSet().getIssuer();
                if (!this.issuer.equals(tokenIssuer)) {
                    throw new OAuth2Exception("invalid_token", "Invalid token issuer", HttpStatus.UNAUTHORIZED);
                }

                return signedJWT.getJWTClaimsSet().getClaims();
            } catch (OAuth2Exception e) {
                throw e;
            } catch (Exception e) {
                log.warn("Invalid JWT token received: {}", e.getMessage());
                throw new OAuth2Exception("invalid_token", "Invalid or malformed JWT", HttpStatus.UNAUTHORIZED);
            }
        });
    }

    @Override
    public Map<String, Object> getPublicJwkSet() {
        return keyManager.getPublicJwkSet().toJSONObject();
    }
}
