package com.miniauth.core.service;

import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.model.AuthorizationCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationCodeServiceTest {

    private AuthorizationCodeService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationCodeService();
    }

    @Test
    @DisplayName("Should create authorization code and consume it successfully once (Single-use)")
    void shouldCreateAndConsumeCodeOnce() {
        AuthorizationCode code = service.createAuthorizationCode(
                "demo-client",
                "u-123",
                "john_doe",
                "http://localhost:3000/callback",
                "openid profile",
                null,
                null
        );

        assertThat(code.getCode()).isNotBlank();
        assertThat(code.isExpired()).isFalse();

        // 1. Tiêu thụ lần đầu tiên: Thành công
        AuthorizationCode consumed = service.consumeCode(
                code.getCode(),
                "demo-client",
                "http://localhost:3000/callback"
        );
        assertThat(consumed.getUserId()).isEqualTo("u-123");

        // 2. Tiêu thụ lần thứ hai: Phải thất bại vì chính sách Single-use
        assertThatThrownBy(() -> service.consumeCode(code.getCode(), "demo-client", "http://localhost:3000/callback"))
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("already consumed");
    }

    @Test
    @DisplayName("Should validate PKCE S256 challenge and verifier successfully")
    void shouldValidatePkceS256() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        AuthorizationCode code = service.createAuthorizationCode(
                "demo-client",
                "u-123",
                "john_doe",
                "http://localhost:3000/callback",
                "openid",
                challenge,
                "S256"
        );

        // Đối chiếu với verifier chuẩn -> Thành công
        service.validatePkce(code, verifier);

        // Đối chiếu với verifier sai -> Phải ném ngoại lệ
        assertThatThrownBy(() -> service.validatePkce(code, "wrong-verifier-1234567890"))
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("Invalid code_verifier");
    }
}
