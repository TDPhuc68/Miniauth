package com.miniauth.infrastructure.idp;

import com.miniauth.core.exception.InvalidGrantException;
import com.miniauth.core.model.AuthenticationRequest;
import com.miniauth.core.model.User;
import com.miniauth.core.port.AuthenticationProvider;
import com.miniauth.core.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Provider giả lập đăng nhập qua bên thứ ba (Google/Social Login).
 * Minh chứng tính linh hoạt của Strategy Pattern: Tự động cấp phát tài khoản
 * nếu đăng nhập lần đầu (Just-In-Time Provisioning) tương tự Gravitee AM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockSocialAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;

    @Override
    public String getProviderType() {
        return "GOOGLE_MOCK";
    }

    @Override
    public Mono<User> authenticate(AuthenticationRequest request) {
        String email = request.getUsername();
        if (email == null || !email.contains("@")) {
            return Mono.error(new InvalidGrantException("Valid email required for Google Login"));
        }

        String username = "google_" + email.split("@")[0];

        return userRepository.findByUsername(username)
                .switchIfEmpty(
                        // Nếu chưa có, tự động tạo mới (Just-In-Time User Provisioning)
                        Mono.defer(() -> {
                            log.info("Creating new JIT user for Google account: {}", email);
                            User newUser = User.builder()
                                    .id("u-" + UUID.randomUUID().toString().substring(0, 8))
                                    .username(username)
                                    .email(email)
                                    .fullName("Google User (" + email.split("@")[0] + ")")
                                    .passwordHash("N/A_EXTERNAL_AUTH")
                                    .status("ACTIVE")
                                    .failedAttempts(0)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            return userRepository.save(newUser);
                        })
                );
    }
}
