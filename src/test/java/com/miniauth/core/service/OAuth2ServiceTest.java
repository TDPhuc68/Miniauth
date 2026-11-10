package com.miniauth.core.service;

import com.miniauth.core.exception.InvalidClientException;
import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.TokenResponse;
import com.miniauth.core.model.User;
import com.miniauth.core.port.ClientRepository;
import com.miniauth.core.port.TokenService;
import com.miniauth.core.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2ServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthorizationCodeService authorizationCodeService;
    private OAuth2Service oAuth2Service;

    @BeforeEach
    void setUp() {
        authorizationCodeService = new AuthorizationCodeService();
        oAuth2Service = new OAuth2Service(
                clientRepository,
                userRepository,
                tokenService,
                authorizationCodeService,
                passwordEncoder
        );
        ReflectionTestUtils.setField(oAuth2Service, "tokenExpirationSeconds", 3600L);
    }

    @Test
    @DisplayName("Should validate authorize request successfully for valid client")
    void shouldValidateAuthorizeRequest() {
        ClientApplication client = ClientApplication.builder()
                .clientId("demo-client")
                .redirectUris("http://localhost:3000/callback,http://localhost:8080/callback")
                .build();

        when(clientRepository.findByClientId("demo-client")).thenReturn(Mono.just(client));

        StepVerifier.create(oAuth2Service.validateAuthorizeRequest("demo-client", "http://localhost:3000/callback", "code"))
                .assertNext(res -> assertThat(res.getClientId()).isEqualTo("demo-client"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should exchange authorization code for access token and id token with PKCE")
    void shouldExchangeCodeForTokensWithPkce() throws Exception {
        String verifier = "secret-pkce-verifier-12345678901234567890";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        User user = User.builder()
                .id("u-101")
                .username("john_doe")
                .build();

        ClientApplication client = ClientApplication.builder()
                .clientId("demo-client")
                .clientSecretHash("secret_hash")
                .redirectUris("http://localhost:3000/callback")
                .build();

        // Cấp mã code
        String code = oAuth2Service.issueAuthorizationCode(
                user,
                client,
                "http://localhost:3000/callback",
                "openid profile",
                challenge,
                "S256"
        );

        when(clientRepository.findByClientId("demo-client")).thenReturn(Mono.just(client));
        when(passwordEncoder.matches("demo-secret", "secret_hash")).thenReturn(true);
        when(userRepository.findById("u-101")).thenReturn(Mono.just(user));
        when(tokenService.generateAccessToken(eq(user), eq(client), any())).thenReturn("mock-access-token-jwt");
        when(tokenService.generateIdToken(eq(user), eq(client), any())).thenReturn("mock-id-token-jwt");

        // Thực hiện đổi code lấy token
        Mono<TokenResponse> tokenMono = oAuth2Service.exchangeCodeForToken(
                "authorization_code",
                code,
                "http://localhost:3000/callback",
                "demo-client",
                "demo-secret",
                verifier
        );

        StepVerifier.create(tokenMono)
                .assertNext(res -> {
                    assertThat(res.getAccessToken()).isEqualTo("mock-access-token-jwt");
                    assertThat(res.getIdToken()).isEqualTo("mock-id-token-jwt");
                    assertThat(res.getTokenType()).isEqualTo("Bearer");
                    assertThat(res.getExpiresIn()).isEqualTo(3600L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail exchange if client_secret is invalid")
    void shouldFailIfClientSecretInvalid() {
        ClientApplication client = ClientApplication.builder()
                .clientId("demo-client")
                .clientSecretHash("hashed_secret")
                .build();

        when(clientRepository.findByClientId("demo-client")).thenReturn(Mono.just(client));
        when(passwordEncoder.matches("wrong-secret", "hashed_secret")).thenReturn(false);

        StepVerifier.create(oAuth2Service.exchangeCodeForToken(
                        "authorization_code",
                        "any-code",
                        "http://localhost:3000/callback",
                        "demo-client",
                        "wrong-secret",
                        null
                ))
                .expectError(InvalidClientException.class)
                .verify();
    }
}
