package com.miniauth.infrastructure.crypto;

import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private KeyManager keyManager;
    private NimbusJwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        keyManager = new KeyManager();
        keyManager.init();

        tokenService = new NimbusJwtTokenService(keyManager);
        ReflectionTestUtils.setField(tokenService, "issuer", "http://localhost:8080");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
    }

    @Test
    @DisplayName("Should generate valid RSA RS256 Access Token and verify successfully")
    void shouldGenerateAndVerifyAccessToken() {
        User user = User.builder()
                .id("u-123")
                .username("john_doe")
                .email("john@example.com")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        ClientApplication client = ClientApplication.builder()
                .clientId("demo-client")
                .clientName("Demo App")
                .build();

        String token = tokenService.generateAccessToken(user, client, "openid profile");
        assertThat(token).isNotBlank();

        // Kiểm tra verify token
        StepVerifier.create(tokenService.validateAndExtractClaims(token))
                .assertNext(claims -> {
                    assertThat(claims.get("sub")).isEqualTo("u-123");
                    assertThat(claims.get("username")).isEqualTo("john_doe");
                    assertThat(claims.get("email")).isEqualTo("john@example.com");
                    assertThat(claims.get("aud")).isEqualTo(java.util.List.of("demo-client"));
                    assertThat(claims.get("iss")).isEqualTo("http://localhost:8080");
                    assertThat(claims.get("scope")).isEqualTo("openid profile");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should generate public JWKS without exposing private key")
    void shouldGeneratePublicJwks() {
        Map<String, Object> jwks = tokenService.getPublicJwkSet();
        assertThat(jwks).containsKey("keys");

        @SuppressWarnings("unchecked")
        var keys = (java.util.List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);

        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key.get("use")).isEqualTo("sig");
        assertThat(key.get("kid")).isEqualTo(keyManager.getKeyId());
        
        // Cực kỳ quan trọng: Tuyệt đối không được chứa Private Exponent 'd' trong JWKS
        assertThat(key).doesNotContainKey("d");
        assertThat(key).doesNotContainKey("p");
        assertThat(key).doesNotContainKey("q");
    }
}
