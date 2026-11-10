package com.miniauth.infrastructure.web;

import com.miniauth.core.exception.OAuth2Exception;
import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.ClientApplication;
import com.miniauth.core.model.User;
import com.miniauth.core.port.ClientRepository;
import com.miniauth.core.port.UserRepository;
import com.miniauth.core.service.AuditService;
import com.miniauth.core.service.AuthService;
import com.miniauth.core.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Controller xử lý luồng ủy quyền OAuth 2.0 / OIDC:
 * 1. GET /oauth/authorize: Hiển thị giao diện đăng nhập (Login)
 * 2. POST /oauth/authorize/login: Xác thực người dùng, hiển thị màn hình cấp quyền (Consent)
 * 3. POST /oauth/authorize/consent: Xử lý quyết định cấp quyền, sinh mã code và redirect về Client
 */
@Slf4j
@Controller
@RequestMapping("/oauth/authorize")
@RequiredArgsConstructor
public class AuthorizationController {

    private final OAuth2Service oAuth2Service;
    private final AuthService authService;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @GetMapping
    public Mono<String> showLoginPage(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "response_type", defaultValue = "code") String responseType,
            @RequestParam(value = "scope", defaultValue = "openid profile email") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            Model model
    ) {
        return oAuth2Service.validateAuthorizeRequest(clientId, redirectUri, responseType)
                .map(client -> {
                    model.addAttribute("clientId", clientId);
                    model.addAttribute("clientName", client.getClientName());
                    model.addAttribute("redirectUri", redirectUri);
                    model.addAttribute("scope", scope);
                    model.addAttribute("state", state);
                    model.addAttribute("codeChallenge", codeChallenge);
                    model.addAttribute("codeChallengeMethod", codeChallengeMethod);
                    return "login";
                });
    }

    @PostMapping("/login")
    public Mono<String> processLogin(ServerWebExchange exchange, Model model) {
        String clientIp = resolveClientIp(exchange);

        return exchange.getFormData().flatMap(formData -> {
            String username = formData.getFirst("username");
            String password = formData.getFirst("password");
            String clientId = formData.getFirst("client_id");
            String redirectUri = formData.getFirst("redirect_uri");
            String scope = formData.getFirst("scope");
            String state = formData.getFirst("state");
            String codeChallenge = formData.getFirst("code_challenge");
            String codeChallengeMethod = formData.getFirst("code_challenge_method");

            model.addAttribute("clientId", clientId);
            model.addAttribute("redirectUri", redirectUri);
            model.addAttribute("scope", scope);
            model.addAttribute("state", state);
            model.addAttribute("codeChallenge", codeChallenge);
            model.addAttribute("codeChallengeMethod", codeChallengeMethod);

            AuthenticationRequest authRequest = AuthenticationRequest.builder()
                    .username(username)
                    .password(password)
                    .providerType("LOCAL")
                    .build();

            return clientRepository.findByClientId(clientId)
                    .flatMap(client -> {
                        model.addAttribute("clientName", client.getClientName());

                        return authService.authenticate(authRequest)
                                .flatMap(user -> {
                                    // Đăng nhập thành công -> Ghi Audit Log & Hiển thị trang Consent
                                    auditService.recordEvent(
                                            "USER_LOGIN",
                                            user.getUsername(),
                                            clientId,
                                            "SUCCESS",
                                            "User authenticated via LOCAL IDP",
                                            clientIp
                                    );

                                    model.addAttribute("userId", user.getId());
                                    model.addAttribute("username", user.getUsername());
                                    return Mono.just("consent");
                                })
                                .onErrorResume(OAuth2Exception.class, ex -> {
                                    log.warn("Login failed for user [{}]: {}", username, ex.getMessage());

                                    // Đăng nhập thất bại -> Ghi Audit Log
                                    auditService.recordEvent(
                                            "USER_LOGIN",
                                            username,
                                            clientId,
                                            "FAILURE",
                                            ex.getErrorDescription(),
                                            clientIp
                                    );

                                    model.addAttribute("error", ex.getErrorDescription());
                                    return Mono.just("login");
                                });
                    });
        });
    }

    @PostMapping("/consent")
    public Mono<Void> processConsent(ServerWebExchange exchange) {
        String clientIp = resolveClientIp(exchange);

        return exchange.getFormData().flatMap(formData -> {
            String decision = formData.getFirst("decision");
            String clientId = formData.getFirst("client_id");
            String redirectUri = formData.getFirst("redirect_uri");
            String scope = formData.getFirst("scope");
            String state = formData.getFirst("state");
            String codeChallenge = formData.getFirst("code_challenge");
            String codeChallengeMethod = formData.getFirst("code_challenge_method");
            String userId = formData.getFirst("user_id");

            // Nếu người dùng từ chối cấp quyền
            if (!"approve".equalsIgnoreCase(decision)) {
                log.info("User denied consent for client: {}", clientId);

                auditService.recordEvent(
                        "CONSENT_DENIED",
                        userId,
                        clientId,
                        "FAILURE",
                        "User explicitly denied consent",
                        clientIp
                );

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("error", "access_denied")
                        .queryParam("error_description", "User denied authorization consent");
                if (state != null && !state.isBlank()) {
                    uriBuilder.queryParam("state", state);
                }

                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(uriBuilder.build().toUri());
                return exchange.getResponse().setComplete();
            }

            // Người dùng đồng ý cấp quyền -> Sinh mã code và chuyển hướng về redirect_uri kèm ?code=...
            Mono<User> userMono = userRepository.findById(userId);
            Mono<ClientApplication> clientMono = clientRepository.findByClientId(clientId);

            return Mono.zip(userMono, clientMono).flatMap(tuple -> {
                User user = tuple.getT1();
                ClientApplication client = tuple.getT2();

                String code = oAuth2Service.issueAuthorizationCode(
                        user,
                        client,
                        redirectUri,
                        scope,
                        codeChallenge,
                        codeChallengeMethod
                );

                auditService.recordEvent(
                        "CONSENT_GRANTED",
                        user.getUsername(),
                        clientId,
                        "SUCCESS",
                        "User approved scopes: " + scope,
                        clientIp
                );

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("code", code);
                if (state != null && !state.isBlank()) {
                    uriBuilder.queryParam("state", state);
                }

                URI targetUri = uriBuilder.build().toUri();
                log.info("Redirecting to client callback: {}", targetUri);

                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(targetUri);
                return exchange.getResponse().setComplete();
            });
        });
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "UNKNOWN_IP";
    }
}
